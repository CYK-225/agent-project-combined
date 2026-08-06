"""
V2 代理服务器 - 用于接收和处理GUI自动化任务的HTTP服务器。
此服务器在Docker容器内运行，并与Java后端通信。
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
from queue import Queue
from dataclasses import asdict

from flask import Flask, request, jsonify
from flask_cors import CORS

# 从同一目录导入工具
from utils import (
    ComputerTools, TaskRequest, TaskResult, GUIOwlWrapper,
    build_messages, extract_tool_calls, annotate_screenshot,
    format_step_text, sanitize_filename, send_callback, smart_resize
)
from utils import image_to_base64

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

# Flask应用
app = Flask(__name__)
CORS(app)

# 全局状态
computer_tools: Optional[ComputerTools] = None
current_task: Optional[TaskRequest] = None
task_queue: Queue = Queue()
is_processing: bool = False
server_port: int = 9001
vnc_port: int = 9002
container_id: str = os.environ.get('CONTAINER_ID', 'unknown')
profile_name: str = os.environ.get('PROFILE_NAME', 'default')

# Xvfb显示
DISPLAY = ":99"


def setup_display():
    """初始化无头操作的虚拟显示器。"""
    logger.info("正在设置虚拟显示器...")

    # 终止任何现有的Xvfb进程
    subprocess.run(["pkill", "-9", "Xvfb"], capture_output=True)

    # 启动Xvfb
    subprocess.Popen([
        "Xvfb", DISPLAY, "-ac", "-screen", "0", "1000x1000x24"
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    time.sleep(2)

    # 启动fluxbox窗口管理器
    os.makedirs("/root/.fluxbox", exist_ok=True)
    subprocess.Popen(["fluxbox"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    time.sleep(1)
    os.environ["DISPLAY"] = DISPLAY
    logger.info("虚拟显示器设置完成")


def setup_profile(is_update_profile: bool, profile_name: str):
    """为会话设置Chrome配置文件。"""
    host_profile_base = f"/root/AutoGUI/profiles/{profile_name}"
    container_profile = "/app/chrome_profile"

    if is_update_profile:
        # 可写模式 - 直接挂载配置文件
        os.makedirs(container_profile, exist_ok=True)
        subprocess.run(["chmod", "-R", "777", container_profile])
        # 清理锁文件
        for pattern in ["Singleton*", "Crashpad"]:
            subprocess.run(["rm", "-rf", f"{container_profile}/{pattern}"],
                           capture_output=True)
    else:
        # 只读模式 - 从基础配置文件复制
        base_profile = f"/app/base_profile/{profile_name}"
        if os.path.exists(base_profile):
            subprocess.run(["cp", "-a", f"{base_profile}/.", f"{container_profile}/"])
        else:
            os.makedirs(container_profile, exist_ok=True)

        # 清理锁和缓存
        for pattern in ["Singleton*", "Crashpad", "Default/Cache", "Default/Code Cache"]:
            subprocess.run(["rm", "-rf", f"{container_profile}/{pattern}"],
                           capture_output=True)


def execute_task(task: TaskRequest) -> TaskResult:
    """执行GUI自动化任务。"""
    global computer_tools

    logger.info(f"开始执行任务：{task.task_id}")

    # 设置配置文件
    setup_profile(task.is_update_profile, task.profile_name)

    # 使用输出目录初始化工具
    output_dir = f"/app/anno/{task.task_id}"
    os.makedirs(output_dir, exist_ok=True)
    computer_tools = ComputerTools(output_dir=output_dir)

    # 初始化LLM包装器
    llm = GUIOwlWrapper(
        api_key=task.api_key,
        base_url=task.base_url,
        model_name=task.model
    )

    # 任务执行状态
    history_output = []
    step = 0
    max_steps = task.max_steps

    try:
        while step < max_steps:
            step += 1
            logger.info(f"执行步骤 {step}/{max_steps}")

            # 截取屏幕截图
            screenshot_path = os.path.join(output_dir, f"step_{step:03d}.png")
            if not computer_tools.get_screenshot(screenshot_path):
                logger.error("截取屏幕截图失败")
                return TaskResult(
                    task_id=task.task_id,
                    status="failure",
                    step=step,
                    result="截取屏幕截图失败"
                )

            # 构建消息并获取预测
            messages = build_messages(
                image_path=screenshot_path,
                instruction=task.instruction,
                history_output=history_output,
                model_name=task.model
            )

            prediction, payload, response = llm.predict_mm(messages)

            if prediction == "调用LLM出错":
                logger.error("LLM调用失败")
                return TaskResult(
                    task_id=task.task_id,
                    status="failure",
                    step=step,
                    result="LLM调用失败"
                )

            # 解析并执行动作
            actions = extract_tool_calls(prediction)

            if not actions:
                logger.warning("未能从预测中提取动作")
                
                # 记录到历史
                history_output.append({
                    "image": screenshot_path,
                    "output": prediction
                })
                
                # 发送处理中-异常输出回调
                screenshot_filename = f"step_{step:03d}.png"
                result = TaskResult(
                    task_id=task.task_id,
                    status="processing_error",
                    step=step,
                    action="extraction_failed",
                    image_url=screenshot_filename,
                    result=f"无法从模型输出中提取有效动作。原始输出：{prediction[:500]}..."
                )
                
                if task.callback_url:
                    send_callback(task.callback_url, result)
                
                continue

            # 执行每个动作
            action_result = None
            for action_obj in actions:
                action_name = action_obj.get("name", "")
                args = action_obj.get("arguments", {})

                if action_name != "computer_use":
                    continue

                action_type = args.get("action", "")
                action_result = execute_action(computer_tools, action_type, args)

                # 创建标注截图
                anno_path = os.path.join(output_dir, f"step_{step:03d}_anno.png")
                annotate_screenshot(screenshot_path, args, anno_path)

                # 记录到历史
                history_output.append({
                    "image": screenshot_path,
                    "output":prediction
                })

                # 构建截图URL（相对于nginx基础路径）
                screenshot_filename = f"step_{step:03d}_anno.png" if os.path.exists(anno_path) else f"step_{step:03d}.png"

                # 发送处理中回调
                result = TaskResult(
                    task_id=task.task_id,
                    status="processing",
                    step=step,
                    action=action_type,
                    image_url=screenshot_filename,  # 相对路径
                    result=action_result or ""
                )

                if task.callback_url:
                    send_callback(task.callback_url, result)

                # 检查是否终止
                if action_type in ["terminate", "answer"]:
                    logger.info(f"任务被动作终止：{action_type}")
                    return TaskResult(
                        task_id=task.task_id,
                        status="completed",
                        step=step,
                        action=action_type,
                        image_url=screenshot_filename,
                        result=args.get("text", args.get("status", "任务完成"))
                    )

            time.sleep(0.5)  # 步骤之间的短暂延迟

        # 达到最大步骤数
        logger.warning(f"达到最大步骤数（{max_steps}）")
        return TaskResult(
            task_id=task.task_id,
            status="timeout",
            step=step,
            result=f"达到最大步骤数（{max_steps}）但未完成"
        )

    except Exception as e:
        logger.exception(f"任务执行失败：{e}")
        return TaskResult(
            task_id=task.task_id,
            status="failure",
            step=step,
            result=f"执行错误：{str(e)}"
        )


def execute_action(tools: ComputerTools, action: str, args: Dict[str, Any]) -> str:
    """执行单个计算机动作。"""
    try:
        if action == "key":
            keys = args.get("keys", [])
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

        elif action == "terminate":
            return "任务终止"

        elif action == "answer":
            return f"回答：{args.get('text', '')}"

        else:
            return f"未知动作：{action}"

    except Exception as e:
        logger.error(f"动作执行失败：{e}")
        return f"错误：{str(e)}"


def task_worker():
    """从队列处理任务的后台工作线程。"""
    global is_processing

    while True:
        try:
            task = task_queue.get()
            if task is None:
                break

            is_processing = True
            logger.info(f"正在处理任务：{task.task_id}")

            result = execute_task(task)

            # 发送最终回调
            if task.callback_url:
                send_callback(task.callback_url, result)

            is_processing = False
            logger.info(f"任务完成：{task.task_id}，状态：{result.status}")

            task_queue.task_done()

        except Exception as e:
            logger.exception(f"工作线程错误：{e}")
            is_processing = False


# 启动后台工作线程
worker_thread = threading.Thread(target=task_worker, daemon=True)
worker_thread.start()


# ---------------------------------------------------------------------------
# HTTP端点
# ---------------------------------------------------------------------------

@app.route('/health', methods=['GET'])
def health_check():
    """健康检查端点。"""
    return jsonify({
        'status': 'healthy',
        'container_id': container_id,
        'profile': profile_name,
        'api_port': server_port,
        'vnc_port': vnc_port,
        'processing': is_processing,
        'queue_size': task_queue.qsize()
    })


@app.route('/task', methods=['POST'])
def submit_task():
    """提交新任务用于执行。"""
    global current_task

    try:
        data = request.get_json()

        if not data:
            return jsonify({'error': '未提供JSON数据'}), 400

        task = TaskRequest.from_dict(data)

        if is_processing:
            return jsonify({
                'error': '容器正在处理另一个任务',
                'current_task': current_task.task_id if current_task else None
            }), 409

        current_task = task
        task_queue.put(task)

        logger.info(f"任务已排队：{task.task_id}")

        return jsonify({
            'status': 'accepted',
            'task_id': task.task_id,
            'message': '任务已排队等待执行'
        })

    except Exception as e:
        logger.exception(f"提交任务失败：{e}")
        return jsonify({'error': str(e)}), 500


@app.route('/task/<task_id>/status', methods=['GET'])
def get_task_status(task_id: str):
    """获取任务状态。"""
    if current_task and current_task.task_id == task_id:
        return jsonify({
            'task_id': task_id,
            'processing': is_processing,
            'queue_position': 0 if is_processing else task_queue.qsize()
        })

    return jsonify({'error': '任务未找到'}), 404


@app.route('/task/<task_id>/cancel', methods=['POST'])
def cancel_task(task_id: str):
    """取消待处理的任务。"""
    # 注意：这是一个简单实现
    # 完整实现需要适当的任务跟踪
    return jsonify({
        'status': 'cancelled',
        'task_id': task_id
    })


@app.route('/status', methods=['GET'])
def container_status():
    """获取容器状态。"""
    return jsonify({
        'container_id': container_id,
        'profile': profile_name,
        'api_port': server_port,
        'vnc_port': vnc_port,
        'processing': is_processing,
        'queue_size': task_queue.qsize(),
        'display': os.environ.get('DISPLAY', '未设置')
    })


@app.route('/screenshot', methods=['GET'])
def take_screenshot():
    """截取并返回屏幕截图。"""
    global computer_tools

    if computer_tools is None:
        return jsonify({'error': '计算机工具未初始化'}), 500

    screenshot_path = "/tmp/quick_screenshot.png"
    if computer_tools.get_screenshot(screenshot_path):
        from flask import send_file
        return send_file(screenshot_path, mimetype='image/png')

    return jsonify({'error': '截取屏幕截图失败'}), 500


@app.route('/shutdown', methods=['POST'])
def shutdown():
    """优雅关闭服务器。"""
    logger.info("收到关闭请求")

    def shutdown_handler():
        time.sleep(1)
        os.kill(os.getpid(), signal.SIGTERM)

    threading.Thread(target=shutdown_handler, daemon=True).start()

    return jsonify({'status': '正在关闭'})


# ---------------------------------------------------------------------------
# 主入口点
# ---------------------------------------------------------------------------

if __name__ == '__main__':
    # 从环境变量获取端口或使用默认值
    server_port = int(os.environ.get('AGENT_PORT', 9001))
    vnc_port = int(os.environ.get('VNC_PORT', 9002))
    container_id = os.environ.get('CONTAINER_ID', 'unknown')
    profile_name = os.environ.get('PROFILE_NAME', 'default')

    # 设置虚拟显示器


    logger.info(f"在端口 {server_port} 上启动代理服务器")
    logger.info(f"VNC 服务在端口 {vnc_port} 上可用")
    logger.info(f"容器ID：{container_id}")
    logger.info(f"配置文件：{profile_name}")

    # 运行Flask应用
    app.run(
        host='0.0.0.0',
        port=server_port,
        debug=False,
        threaded=True
    )
