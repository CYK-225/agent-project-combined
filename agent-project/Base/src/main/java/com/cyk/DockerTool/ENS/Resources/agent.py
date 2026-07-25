import argparse
import subprocess
import requests
import os
import json
import glob
import time
import shutil
import re
from fastapi import FastAPI, BackgroundTasks
import uvicorn
import traceback


app = FastAPI(title="ENS Autonomous Agent")

RECORDS_DIR = "/app/records"

def send_callback(url, task_id, status, data_overrides):
    payload = {
        "task_id": task_id,
        "status": status,
        **data_overrides
    }
    print(f"\n[*] 准备发送回调 -> {url} | 状态: {status}", flush=True)
    try:
        response = requests.post(url, json=payload, timeout=10)
        print(f"[*] 回调发送完成, 响应状态码: {response.status_code}", flush=True)
    except Exception as e:
        print(f"[!] 回调发送失败: {e}", flush=True)

def get_valid_value(info_dict, keys):
    """
    🛠️ 智能获取有效字段：依次尝试多个潜在的 Key，找到第一个有效值则返回
    """
    for k in keys:
        val = info_dict.get(k)
        if val and str(val).strip() not in ['', '-', 'null', 'None', '无', '[]']:
            return str(val).strip()
    return "-"

def parse_enscan_json(output_dir, task_id, source_name):
    """
    解析指定目录下的 JSON 文件，返回 (基础字段字典, 电话号码集合)
    """
    search_pattern = os.path.join(output_dir, "**", "*.json")
    json_files = glob.glob(search_pattern, recursive=True)

    if not json_files:
        return None, set()

    source_json = json_files[0]
    data_map = {}
    all_phones = set()

    try:
        os.makedirs(RECORDS_DIR, exist_ok=True)
        record_path = os.path.join(RECORDS_DIR, f"{task_id}_{source_name}.json")
        shutil.copy2(source_json, record_path)

        with open(source_json, 'r', encoding='utf-8') as f:
            raw_content = f.read()
            raw_data = json.loads(raw_content)

            ent_list = raw_data.get("enterprise_info", [])
            info = ent_list[0] if (isinstance(ent_list, list) and len(ent_list) > 0) else raw_data

            # 🛠️ 核心修复 1：电话清洗小函数，剔除 JSON 字符串残留的符号
            def clean_and_add_phone(val):
                val_str = str(val)
                # 剔除方括号、单双引号、空格
                cleaned = re.sub(r'[\[\]"\'\s]', '', val_str)
                # 以逗号分割，防止多个号码连在一起
                for p in cleaned.split(','):
                    if p and p not in ['', '-', '无', 'null', 'None']:
                        all_phones.add(p)

            # --- 深度提取电话 (递归 + 正则) ---
            def deep_search_phones(obj):
                if isinstance(obj, dict):
                    for k, v in obj.items():
                        if any(key in k.lower() for key in ['phone', 'tel', 'mobile', 'contact']):
                            if isinstance(v, str) or isinstance(v, list):
                                clean_and_add_phone(v)
                            elif isinstance(v, dict):
                                deep_search_phones(v)
                        else:
                            deep_search_phones(v)
                elif isinstance(obj, list):
                    for item in obj:
                        deep_search_phones(item)

            deep_search_phones(raw_data)

            # 正则兜底
            regex_phones = re.findall(r'1[3-9]\d{9}|0\d{2,3}[-]?\d{7,8}', raw_content)
            for p in regex_phones:
                all_phones.add(p)

            # 🛠️ 核心修复 2：加入所有可能出现的资本/时间 Key
            data_map['recConcat'] = get_valid_value(info, ['registered_capital', 'reg_capital', 'regCapital', 'regCap', '注册资本'])
            data_map['esDate'] = get_valid_value(info, ['incorporation_date', 'estiblishTime', 'estDate', '成立日期'])
            data_map['opScope'] = get_valid_value(info, ['scope', 'business_scope', 'opScope', '经营范围'])
            data_map['yrAddress'] = get_valid_value(info, ['address', 'regLocation', '企业地址', '注册地址'])
            data_map['legalPerson'] = get_valid_value(info, ['legal_person', 'operName', '法人', '法人代表'])
            data_map['entName'] = get_valid_value(info, ['name', 'entName', '企业名称'])

            return data_map, all_phones

    except Exception as e:
        print(f"[!] {source_name} JSON 解析异常: {e}", flush=True)
        return None, set()

def execute_enscan(company, source, task_id):
    """
    独立执行一次 enscan 查询，并返回底层完整日志
    """
    output_dir = f"/app/output/{task_id}_{source}"
    os.makedirs(output_dir, exist_ok=True)

    print(f"\n==========================================", flush=True)
    print(f"[*] 🔄 正在执行源: [{source}] | 查询: {company} | 延迟: 10s", flush=True)

    cmd = ["./enscan", "-n", company, "-type", source, "-field", "all", "-out-dir", output_dir, "-json", "-delay", "10"]

    raw_logs = "" # 用于收集底层日志

    try:
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        # 增加 300 秒（5分钟）强制超时限制
        stdout, stderr = process.communicate(timeout=300)

        # 拼接标准输出和错误输出
        raw_logs = f"[STDOUT]\n{stdout}\n[STDERR]\n{stderr}"

        if process.returncode == 0:
            data_map, phones = parse_enscan_json(output_dir, task_id, source)
            shutil.rmtree(output_dir, ignore_errors=True)
            # 返回时加上 raw_logs
            return data_map, phones, raw_logs
        else:
            # 返回非0状态码的情况
            shutil.rmtree(output_dir, ignore_errors=True)
            return None, set(), f"进程异常退出(Code:{process.returncode})\n{raw_logs}"

    except subprocess.TimeoutExpired as e:
            print(f"[!] 源 {source} 执行超时，正在强制终止...", flush=True)
            process.kill()
            # 1. 尝试从异常对象中直接获取已捕获的输出 (stdout/stderr 可能在 e 对象里)
            # 2. 如果 e 里没有，再通过 communicate 取
            stdout, stderr = process.communicate()

            raw_logs = "[超时中止]\nSTDOUT:\n{}\nSTDERR:\n{}".format(stdout, stderr)

            shutil.rmtree(output_dir, ignore_errors=True)
            return None, set(), raw_logs
    except Exception as e:
        print(f"[!] 源 {source} 执行失败: {e}", flush=True)
        if 'process' in locals():
            process.kill()
        shutil.rmtree(output_dir, ignore_errors=True)
        return None, set(), f"执行异常: {str(e)}"

def analyze_failure_reason(raw_logs):

    if not raw_logs:
        return "UNKNOWN_ERROR: 进程无日志输出"

    # 1. 公司不存在/查不到关键词
    if "没有查询到关键词" in raw_logs or "未查找到相关企业" in raw_logs:
        return "COMPANY_NOT_FOUND: 目标公司不存在或未查询到记录"

    # 2. 账号查询次数上限
    elif "查询次数已达到上限" in raw_logs or "频率过快" in raw_logs:
        return "ACCOUNT_LIMIT_REACHED: 账号今日查询次数已达上限"

    # 3. Cookie 过期或未登录
    elif "Cookie有问题" in raw_logs or "过期" in raw_logs:
        return "COOKIE_EXPIRED: 账号未登录或Cookie已失效"

    # 4. 超时处理
    elif "超时中止" in raw_logs or "TimeoutExpired" in raw_logs:
        return "TIMEOUT_ERROR: 采集脚本执行超时，遭遇网络阻断"

    # 5. 兜底情况：保留部分原生日志供排查
    else:
        # 截取最后 800 个字符即可，防止过长
        log_snippet = raw_logs[-800:] if len(raw_logs) > 800 else raw_logs
        return f"OTHER_ERROR: 未知采集失败\n--- 部分底层日志 ---\n{log_snippet}"



def run_enscan_task(company, mode, task_id, callback_url):
    send_callback(callback_url, task_id, "started", {"company": company})

    # --- 接收底层日志 ---
    data_rb, phones_rb, raw_logs = execute_enscan(company, "rb", task_id)

    is_valid_data = False
    if data_rb is not None:
        for val in data_rb.values():
            if val not in ["-", None]:
                is_valid_data = True
                break

    rb_success = is_valid_data

    print(f"[*] ⏳ 任务执行完毕，准备休眠 10 秒以满足限流需求...", flush=True)
    time.sleep(10)

    if rb_success:
            print(f"[*] 🎉 源 [rb] 抓取完成！共获取去重电话: {len(phones_rb)} 个", flush=True)
            data_rb['telList'] = ";".join(phones_rb) if phones_rb else None

            send_callback(callback_url, task_id, "completed", {
                "company": company,
                "data": data_rb,
                "source_used": "rb_only"
            })
    else:
        print(f"[*] ❌ 源 [rb] 未抓取到有效字段，判定为失败。", flush=True)

        clean_error_message = analyze_failure_reason(raw_logs)

        send_callback(callback_url, task_id, "failure", {
            "company": company,
            "error": clean_error_message
        })

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--company", help="Target company name")
    parser.add_argument("--mode", default="pro")
    parser.add_argument("--task_id", help="Unique task ID")
    parser.add_argument("--callback_url", help="Webhook callback URL")

    args, unknown = parser.parse_known_args()

    if args.company and args.task_id and args.callback_url:
        # 全局 Try-Catch 兜底
        try:
            run_enscan_task(args.company.strip("'"), args.mode.strip("'"), args.task_id.strip("'"), args.callback_url.strip("'"))
        except Exception as e:
            error_details = traceback.format_exc()
            print(f"[致命错误] Python 脚本崩溃: {e}\n{error_details}", flush=True)
            send_callback(
                args.callback_url.strip("'"),
                args.task_id.strip("'"),
                "failure",
                {"company": args.company.strip("'"), "error": f"Python脚本内部崩溃: {str(e)}"}
            )
    else:
        uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("ENSCAN_API_PORT", 8100)))