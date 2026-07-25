import argparse
import os
import time
import requests
from playwright.sync_api import sync_playwright
from ruamel.yaml import YAML
from ruamel.yaml.scalarstring import SingleQuotedScalarString

def update_enscan_config(ens_dir, account, site, cookie_str):
    """在容器内直接更新挂载进来的 YAML 配置文件，强制使用单引号包裹字符串"""
    if not ens_dir or not os.path.exists(ens_dir):
        print(f"⚠️ 未检测到配置目录 {ens_dir}，跳过 YAML 更新")
        return

    # 映射站点名：代码里的 fengniao 对应 YAML 里的 risk_bird
    yaml_key = "risk_bird" if site == "fengniao" else site
    file_path = os.path.join(ens_dir, f"config_{account}.yaml")

    yaml = YAML()
    yaml.preserve_quotes = True
    config_data = {}

    # 1. 读取（如果存在）
    if os.path.exists(file_path):
        with open(file_path, 'r', encoding='utf-8') as f:
            config_data = yaml.load(f)

    # 2. 初始化模板（如果不存在）
    if not config_data:
        # 使用你提供的完整 UA，并强制用单引号包裹
        target_ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0"

        config_data = {
            'version': 0.7,
            'user_agent': SingleQuotedScalarString(target_ua),
            'cookies': {
                'aiqicha': SingleQuotedScalarString(''),
                'risk_bird': SingleQuotedScalarString('')
            }
        }

    # 3. 更新对应的 Cookie 字段，强制用单引号包裹 Cookie 长文本
    if 'cookies' not in config_data:
        config_data['cookies'] = {}

    config_data['cookies'][yaml_key] = SingleQuotedScalarString(cookie_str)

    # 4. 立即写回硬盘
    with open(file_path, 'w', encoding='utf-8') as f:
        yaml.dump(config_data, f)
    print(f"✅ [容器内] 已直接更新配置文件: {file_path}")

def parse_cookie_str_for_playwright(cookie_str, site):
    """将普通的 cookie 字符串组装成 Playwright 要求的字典格式"""
    domain = ".baidu.com" if site == "aiqicha" else ".riskbird.com"
    cookies = []
    if cookie_str:
        for item in cookie_str.split(';'):
            if '=' in item:
                k, v = item.split('=', 1)
                cookies.append({
                    "name": k.strip(),
                    "value": v.strip(),
                    "domain": domain,
                    "path": "/"
                })
    return cookies

def sync_to_browser_profile(p, profile_dir, cookie_str, site):
    """黑客级操作：利用 Playwright 强行初始化外部目录并注入 Cookie，确保物理落盘"""
    if not profile_dir or profile_dir == 'None':
        return

    print(f"\n[跨模块同步] 正在向 {profile_dir} 注入 {site} 的免密登录态...")
    os.makedirs(profile_dir, exist_ok=True)

    cookie_list = parse_cookie_str_for_playwright(cookie_str, site)
    if not cookie_list:
        print("[-] Cookie 解析为空，放弃同步。")
        return

    context = None
    try:
        # 1. 拉起持久化上下文
        context = p.chromium.launch_persistent_context(
            user_data_dir=profile_dir,
            headless=True,
            args=["--disable-dev-shm-usage", "--no-sandbox"]
        )

        # 2. 【核心修复】：必须新建一个页面并跳转，否则 Chrome 不会生成底层 Default 文件夹
        page = context.new_page()
        target_domain = "https://aiqicha.baidu.com" if site == "aiqicha" else "https://www.riskbird.com"
        page.goto(target_domain)
        page.wait_for_timeout(1000) # 给它 1 秒钟建立数据库的时间

        # 3. 强行注入 Cookie
        context.add_cookies(cookie_list)

        # 4. 【核心修复】：带上 Cookie 再刷新一次，并硬休眠 2 秒，强制 SQLite 写前日志(WAL)刷入硬盘
        page.reload()
        page.wait_for_timeout(2000)

        print(f"✅ 同步成功！外部目录已被激活并完成物理落盘。")
    except Exception as e:
        print(f"❌ 同步到目录 {profile_dir} 失败: {e}")
    finally:
        if context:
            context.close()

def do_login_fengniao(page, account, password):
    """纯粹的登录与等待下发 Cookie 操作"""
    print(f"[{account}] 开始模拟登录风鸟...")
    page.goto("https://www.riskbird.com/")
    page.wait_for_timeout(2000)
    page.locator("img.Login-mode-img").click()
    page.wait_for_timeout(1000)
    page.get_by_text("密码登录", exact=True).last.click()
    page.wait_for_timeout(1000)
    page.get_by_placeholder("请输入手机号").fill(account)
    page.locator(".el-input__wrapper input[type='password']").first.fill(password)
    page.locator("button.login-form-item-btn").click()
    print("等待5秒让后台反爬计算并下发业务Cookie...")
    page.wait_for_timeout(5000)

def validate_cookie_fengniao(page):
    """检测当前的 Cookie 是否真正处于登录态"""
    print("正在检测风鸟 Cookie 有效性...")
    page.goto("https://www.riskbird.com/")
    page.wait_for_timeout(3000)
    is_not_logged_in = page.get_by_text("登录/注册").count() > 0
    return not is_not_logged_in

def process_task(p, args):
    # ==================== 新增：纯提取模式 (Extract) ====================
    ens_config_dir = args.ens_config_dir

    if args.mode == "extract":
        profile_dir = "/app/chrome_profile"
        cookie_file_path = os.path.join(profile_dir, "cookie_state.json")
        print(f"[{args.site}] 正在接管遗留的持久化目录 {profile_dir} 提取 Cookie...")

        browser_context = None
        try:
            # 启动持久化环境
            browser_context = p.chromium.launch_persistent_context(
                user_data_dir=profile_dir,
                headless=True,
                args=["--disable-dev-shm-usage", "--no-sandbox"]
            )

            # 强制开页面加载 Cookie
            page = browser_context.new_page()
            target_url = "https://aiqicha.baidu.com" if args.site == "aiqicha" else "https://www.riskbird.com"
            page.goto(target_url)
            page.wait_for_timeout(3000)

            # 提取 Cookie
            cookies = browser_context.cookies()
            cookie_str = "; ".join([f"{c['name']}={c['value']}" for c in cookies])

            if cookie_str and len(cookie_str) > 20:
                # 落盘并更新 YAML
                browser_context.storage_state(path=cookie_file_path)
                update_enscan_config(ens_config_dir, args.account, args.site, cookie_str)
                sync_to_browser_profile(p, getattr(args, 'v1_sync_dir', None), cookie_str, args.site)
                sync_to_browser_profile(p, getattr(args, 'v2_sync_dir', None), cookie_str, args.site)
                print(f"🎉 成果提取成功！Cookie长度: {len(cookie_str)}，已物理落盘至: {cookie_file_path}")
                return {"status": "success", "cookie_str": cookie_str}
            else:
                return {"status": "failure", "reason": f"提取到的 Cookie 过短或为空 (长度: {len(cookie_str) if cookie_str else 0})"}

        except Exception as e:
            # 捕获所有异常，防止空回调
            error_msg = f"提取模式执行异常: {str(e)}"
            print(f"❌ {error_msg}")
            return {"status": "failure", "reason": error_msg}

        finally:
            # 无论成功失败，必须释放锁
            if browser_context:
                browser_context.close()

    # ==================== 1. 构建符合要求的层级目录 ====================
    # 容器内的 args.pool_dir 对应宿主机的 /usr/local/server/ai/AutoGUI/ENS/Profiles
    # 构建路径: /app/cookie_pool/账号/数据源
    account_site_dir = args.pool_dir
    os.makedirs(account_site_dir, exist_ok=True)

    # 最终的持久化文件路径: /app/cookie_pool/账号/数据源/cookie_state.json
    cookie_file_path = os.path.join(account_site_dir, "cookie_state.json")

    # ==================== 2. 心跳检测模式 (纯读不写) ====================
    if args.mode == "heartbeat":
        if not os.path.exists(cookie_file_path):
            return {"status": "failure", "reason": f"未在 {cookie_file_path} 找到本地持久化文件，无法进行心跳检测"}

        print(f"心跳检测：加载本地文件 {cookie_file_path}")
        browser = p.chromium.launch(headless=True, args=["--disable-dev-shm-usage", "--no-sandbox"])
        context = browser.new_context(storage_state=cookie_file_path)
        page = context.new_page()

        try:
            is_valid = validate_cookie_fengniao(page) if args.site == "fengniao" else False
            if is_valid:
                print("心跳检测通过：Cookie 依然存活！")
                cookie_str = "; ".join([f"{c['name']}={c['value']}" for c in context.cookies()])

                # 心跳检测成功也可以同步一次配置，防止宿主机 YAML 被意外删改
                update_enscan_config(ens_config_dir, args.account, args.site, cookie_str)
                sync_to_browser_profile(p, getattr(args, 'v1_sync_dir', None), cookie_str, args.site)
                sync_to_browser_profile(p, getattr(args, 'v2_sync_dir', None), cookie_str, args.site)

                return {"status": "success", "cookie_str": cookie_str}
            else:
                print("心跳检测失败：Cookie 已失效！")
                return {"status": "failure", "reason": "Cookie已失效(Heartbeat)"}
        finally:
            context.close()
            browser.close()

    # ==================== 3. 读写获取模式 (Fetch) ====================
    for attempt in range(1, 4):
        print(f"\n========== 第 {attempt} 次调度开始 ==========")
        browser = p.chromium.launch(headless=True, args=["--disable-dev-shm-usage", "--no-sandbox", "--disable-blink-features=AutomationControlled"])

        # --- 阶段 A：先尝试从本地读取并验证 ---
        if os.path.exists(cookie_file_path):
            print(f"-> 发现本地持久化 Cookie：{cookie_file_path}，开始优先验证...")
            context = browser.new_context(storage_state=cookie_file_path)
            page = context.new_page()

            is_valid = validate_cookie_fengniao(page) if args.site == "fengniao" else False
            if is_valid:
                print("-> 🎉 本地缓存 Cookie 验证成功！跳过登录动作。")
                cookie_str = "; ".join([f"{c['name']}={c['value']}" for c in context.cookies()])

                # --- 核心同步：缓存验证成功，直接同步配置文件 ---
                update_enscan_config(ens_config_dir, args.account, args.site, cookie_str)
                sync_to_browser_profile(p, getattr(args, 'v1_sync_dir', None), cookie_str, args.site)
                sync_to_browser_profile(p, getattr(args, 'v2_sync_dir', None), cookie_str, args.site)
                context.close()
                browser.close()
                # 已经是有效的了，直接返回给 Java
                return {"status": "success", "cookie_str": cookie_str}
            else:
                print("-> ⚠️ 本地缓存 Cookie 已失效，准备进行网页自动化登录...")
                context.close() # 失效则关闭当前上下文，清理环境

        # --- 阶段 B：本地没有或已失效，执行自动化登录 ---
        print("-> 启动全新浏览器上下文环境...")
        context = browser.new_context()
        page = context.new_page()

        try:
            if args.site == "fengniao":
                do_login_fengniao(page, args.account, args.password)
                # 登录完立刻验证一次
                is_valid = validate_cookie_fengniao(page)

            # --- 阶段 C：登录后的持久化动作 ---
            if is_valid:
                # 极其关键：通过 storage_state 将最新的浏览器凭证写入到刚才创建的嵌套目录中
                context.storage_state(path=cookie_file_path)
                print(f"-> 🎉 新 Cookie 获取成功，已持久化写入服务器本地：{cookie_file_path}")

                cookie_str = "; ".join([f"{c['name']}={c['value']}" for c in context.cookies()])

                # --- 核心同步：新登录成功，立即同步写回 YAML ---
                update_enscan_config(ens_config_dir, args.account, args.site, cookie_str)
                sync_to_browser_profile(p, getattr(args, 'v1_sync_dir', None), cookie_str, args.site)
                sync_to_browser_profile(p, getattr(args, 'v2_sync_dir', None), cookie_str, args.site)
                return {"status": "success", "cookie_str": cookie_str}
            else:
                print("-> ❌ 新获取的 Cookie 未能通过验证逻辑。")

        except Exception as e:
            print(f"-> ❌ 运行时发生异常: {e}")
        finally:
            context.close()
            browser.close()

        # 失败后休眠 2 秒进行下一次重试
        time.sleep(2)

    return {"status": "failure", "reason": "重试3次均未能获取或验证成功"}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", required=True)
    parser.add_argument("--account", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--pool_dir", default="/app/cookie_pool")
    parser.add_argument("--callback", required=True)
    parser.add_argument("--mode", choices=["fetch", "heartbeat", "extract"], required=True)
    parser.add_argument("--ens_config_dir", default="/app/ens_configs")
    # --- 新增：接收 Java 传来的跨模块同步路径 ---
    parser.add_argument("--v1_sync_dir", default=None)
    parser.add_argument("--v2_sync_dir", default=None)
    args = parser.parse_args()

    # 捕获业务执行结果
    with sync_playwright() as p:
        result = process_task(p, args)

    # 组装参数回调 Java (Java 那边负责入库)
    payload = {
        "site": args.site,
        "account": args.account,
        "status": result["status"],
        "mode": args.mode
    }
    if result["status"] == "success":
        payload["cookie"] = result["cookie_str"]
    else:
        payload["reason"] = result["reason"]

    print(f"\n==> 准备回调 Java 端: {args.callback}")
    print(payload)
    requests.post(args.callback, json=payload, timeout=10)

if __name__ == "__main__":
    main()