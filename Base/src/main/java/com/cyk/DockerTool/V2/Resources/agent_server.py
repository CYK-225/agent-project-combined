"""
V2 代理服务器 - 修复连击与幻觉版 (含端口动态绑定修复)
"""

import os
import sys
import json
import time
import signal
import logging
import threading
import subprocess
from typing import Dict, Any, Optional

from flask import Flask, request, jsonify
from flask_cors import CORS

from utils import (
    ComputerTools, TaskRequest, TaskResult, GUIOwlWrapper,
    build_messages, extract_tool_calls, annotate_screenshot,
    format_step_text, sanitize_filename, send_callback, smart_resize
)
from utils import image_to_base64

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

task_sessions: Dict[str, Dict[str, Any]] = {}

container_id: str = os.environ.get('CONTAINER_ID', 'unknown')
profile_name: str = os.environ.get('PROFILE_NAME', 'default')

DISPLAY = ":99"

def setup_profile(is_update_profile: bool, profile_name: str):
    container_profile = "/app/chrome_profile"
    if is_update_profile:
        os.makedirs(container_profile, exist_ok=True)
        subprocess.run(["chmod", "-R", "777", container_profile])
        for pattern in ["Singleton*", "Crashpad"]:
            subprocess.run(["rm", "-rf", f"{container_profile}/{pattern}"], capture_output=True)
    else:
        base_profile = f"/app/base_profile/{profile_name}"
        if os.path.exists(base_profile):
            subprocess.run(["cp", "-a", f"{base_profile}/.", f"{container_profile}/"])
        else:
            os.makedirs(container_profile, exist_ok=True)
        for pattern in ["Singleton*", "Crashpad", "Default/Cache", "Default/Code Cache"]:
            subprocess.run(["rm", "-rf", f"{container_profile}/{pattern}"], capture_output=True)

def execute_action(tools: ComputerTools, action: str, args: Dict[str, Any]) -> str:
    """执行物理动作，已修复 PyAutoGUI 大小写崩溃问题"""
    try:
        if action == "key":
            keys = args.get("keys", [])
            # 强制转小写，防止按键为 "Enter" 时直接崩溃
            if isinstance(keys, list):
                keys = [str(k).lower() for k in keys]
            elif isinstance(keys, str):
                keys = [keys.lower()]
            tools.press_key(keys)
            return f"按下按键：{keys}"

        elif action == "type":
            text = args.get("text", "")
            tools.type_text(text)
            return f"输入：{text[:50]}..." if len(text) > 50 else f"输入：{text}"

        elif action == "mouse_move":
            coord = args.get("coordinate", [0, 0])
            tools.mouse_move(coord[0], coord[1])
            return f"移动到：{coord}"

        elif action == "left_click":
            coord = args.get("coordinate", [0, 0])
            tools.left_click(coord[0], coord[1])
            return f"左键单击于：{coord}"

        elif action == "left_click_drag":
            coord = args.get("coordinate", [0, 0])
            tools.left_click_drag(coord[0], coord[1])
            return f"拖动到：{coord}"

        elif action == "right_click":
            coord = args.get("coordinate", [0, 0])
            tools.right_click(coord[0], coord[1])
            return f"右键单击于：{coord}"

        elif action == "middle_click":
            coord = args.get("coordinate", [0, 0])
            tools.middle_click(coord[0], coord[1])
            return f"中键单击于：{coord}"

        elif action == "double_click":
            coord = args.get("coordinate", [0, 0])
            tools.double_click(coord[0], coord[1])
            return f"双击于：{coord}"

        elif action == "triple_click":
            coord = args.get("coordinate", [0, 0])
            tools.triple_click(coord[0], coord[1])
            return f"三击于：{coord}"

        elif action == "scroll":
            pixels = args.get("pixels", 0)
            tools.scroll(pixels)
            return f"滚动：{pixels} 像素"

        elif action == "open app":
            app_name = args.get("app_name", "")
            tools.open_app(app_name)
            return f"打开应用：{app_name}"

        elif action == "wait":
            wait_time = args.get("time", 1)
            time.sleep(wait_time)
            return f"等待：{wait_time}秒"

        elif action in ["terminate", "answer"]:
            return f"回答：{args.get('text', '')}"

        else:
            return f"未知动作：{action}"

    except Exception as e:
        logger.error(f"动作执行失败：{e}")
        return f"错误：{str(e)}"

def execute_single_step_worker(task: TaskRequest):
    """后台工作线程：执行动作，循环解析所有操作，然后回调"""
    logger.info(f"开始执行单步动作：{task.task_id}")

    if task.task_id not in task_sessions:
        setup_profile(task.is_update_profile, task.profile_name)
        output_dir = f"/app/anno/{task.task_id}"
        os.makedirs(output_dir, exist_ok=True)
        task_sessions[task.task_id] = {
            "history_output": [],
            "tools": ComputerTools(output_dir=output_dir),
            "step_count": 0
        }

    session = task_sessions[task.task_id]

    # ==========================================
    # 记忆清洗
    # ==========================================
    if session.get("last_instruction") != task.instruction:
        logger.info(f"检测到追加新指令：'{task.instruction}'")
        logger.info("触发记忆清洗：清空历史截图上下文，重置容器步数！")
        session["last_instruction"] = task.instruction
        session["step_count"] = 0
        session["history_output"] = []  # 极其重要：清空多模态记忆，防止幻觉和Token爆炸

    session['aborted'] = False
    session["step_count"] += 1
    current_step = session["step_count"]
    tools = session["tools"]
    history_output = session["history_output"]

    llm = GUIOwlWrapper(api_key=task.api_key, base_url=task.base_url, model_name=task.model)
    output_dir = f"/app/anno/{task.task_id}"

    try:
        screenshot_path = os.path.join(output_dir, f"step_{current_step:03d}.png")
        if not tools.get_screenshot(screenshot_path):
            send_callback(task.callback_url, TaskResult(task.task_id, "failure", current_step, "截屏失败"))
            return

        # 给提示词加上强制的系统约束，治愈幻觉
        enhanced_instruction = task.instruction + "\n\n[系统强制约束：1. 如果当前指令需要连续多个动作（比如先输入文字后必须按回车键），你必须在本次回复中连续输出多个 <tool_call > 标签！ 2. 所有键盘按键名（如 enter）必须全小写！]"

        messages = build_messages(screenshot_path, enhanced_instruction, history_output, task.model)
        prediction, payload, response = llm.predict_mm(messages)

        # 修复XML标签缺失空格导致提取失败的概率
        prediction = prediction.replace("<tool_call>", "<tool_call >").replace("</tool_call>", "</tool_call >")

        actions = extract_tool_calls(prediction)
        if not actions:
            history_output.append({"image": screenshot_path, "output": prediction})
            result = TaskResult(task.task_id, "processing_error", current_step, "extraction_failed", f"step_{current_step:03d}.png", "无法提取动作")
            send_callback(task.callback_url, result)
            return

        action_result = "未执行"
        status = "processing"
        final_action_type = "none"
        screenshot_filename = f"step_{current_step:03d}.png"
        last_args = {}

        # 使用 For 循环执行所有提取出来的动作
        for i, action_obj in enumerate(actions):
            if session.get('aborted', False):
                logger.warning(f"任务 {task.task_id} 已被强行中止，打断连招！")
                status = "terminated"
                action_result = "用户强行中止了任务"
                final_action_type = "terminate"
                break  # 直接跳出循环，不再执行后续动作
            action_name = action_obj.get("name", "")
            args = action_obj.get("arguments", {})
            last_args = args
            action_type = args.get("action", "")
            final_action_type = action_type

            if action_name == "computer_use":
                action_result = execute_action(tools, action_type, args)

                if i == len(actions) - 1:
                    anno_path = os.path.join(output_dir, f"step_{current_step:03d}_anno.png")
                    annotate_screenshot(screenshot_path, args, anno_path)
                    if os.path.exists(anno_path):
                        screenshot_filename = f"step_{current_step:03d}_anno.png"

                if action_type in ["terminate", "answer"]:
                    status = "completed"
                    break

                # 给网页留出 1 秒加载时间
                if len(actions) > 1 and i < len(actions) - 1:
                    time.sleep(1.0)

        # 记录真实历史
        history_output.append({"image": screenshot_path, "output": prediction})

        if status != "completed":
            status = "completed" if final_action_type in ["terminate", "answer"] else "processing"

        result_text = last_args.get("text", last_args.get("status", action_result))
        result = TaskResult(task.task_id, status, current_step, final_action_type, screenshot_filename, result_text)
        send_callback(task.callback_url, result)

    except Exception as e:
        logger.exception(f"执行异常：{e}")
        send_callback(task.callback_url, TaskResult(task.task_id, "failure", current_step, "错误", "", str(e)))

# 任务锁字典
task_locks = {}
@app.route('/step', methods=['POST'])
def trigger_step():
    data = request.get_json()
    task = TaskRequest.from_dict(data)
    #添加锁
    task_id = task.task_id

    if task_id not in task_locks:
        task_locks[task_id] = threading.Lock()

    def locked_worker(task):
        # 尝试获取锁，获取不到直接丢弃（非阻塞）
        if task_locks[task_id].acquire(timeout=2.0):
            try:
                execute_single_step_worker(task)
            finally:
                task_locks[task_id].release()
        else:
            logger.warning(f"任务 {task_id} 正在执行中，拦截了并发的幽灵请求！")

    threading.Thread(target=locked_worker, args=(task,), daemon=True).start()
    return jsonify({"status": "accepted", "message": "已接收单步指令，正在后台执行并稍后回调"})

@app.route('/abort', methods=['POST'])
def abort_task():
    """接收 Java 发来的强行中止信号"""
    data = request.get_json()
    task_id = data.get('task_id')
    if task_id in task_sessions:
        task_sessions[task_id]['aborted'] = True  # 植入中止标记
        logger.info(f"任务 {task_id} 收到中止信号，即将急刹车！")
        return jsonify({"status": "success", "message": "已下达中止指令"})
    return jsonify({"status": "not_found"}), 404

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({'status': 'healthy', 'container_id': container_id, 'active_sessions': len(task_sessions)})

@app.route('/screenshot', methods=['GET'])
def take_screenshot():
    temp_tools = ComputerTools(output_dir="/tmp")
    screenshot_path = "/tmp/quick_screenshot.png"
    if temp_tools.get_screenshot(screenshot_path):
        from flask import send_file
        return send_file(screenshot_path, mimetype='image/png')
    return jsonify({'error': '截取屏幕截图失败'}), 500

@app.route('/shutdown', methods=['POST'])
def shutdown():
    def shutdown_handler():
        time.sleep(1)
        os.kill(os.getpid(), signal.SIGTERM)
    threading.Thread(target=shutdown_handler, daemon=True).start()
    return jsonify({'status': '正在关闭'})

if __name__ == '__main__':
    # 【核心修复】：必须从环境变量动态获取分配的端口！
    server_port = int(os.environ.get('AGENT_PORT', 9001))
    vnc_port = int(os.environ.get('VNC_PORT', 9002))

    logger.info(f"在端口 {server_port} 上启动代理服务器, VNC 端口 {vnc_port}")
    app.run(host='0.0.0.0', port=server_port, debug=False, threaded=True)