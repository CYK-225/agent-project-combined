"""
Weixin 模块代理服务器 - 用于接收和处理微信 GUI 自动化任务的 HTTP 服务器。
此服务器在 Docker 容器内运行，与 Java 后端通信。
与现有的 Chrome 实现完全隔离。
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

# Flask 应用
app = Flask(__name__)
CORS(app)

# 全局状态
computer_tools: Optional[ComputerTools] = None
current_task: Optional[TaskRequest] = None
task_queue: Queue = Queue()
is_processing: bool = False
server_port: int = 9001  # 容器内部固定端口，外部映射由 Java 通过 docker run -p 决定
vnc_port: int = 9002
container_id: str = os.environ.get('CONTAINER_ID', 'unknown')
profile_name: str = os.environ.get('PROFILE_NAME', 'default')
username: str = os.environ.get('USERNAME', 'default')

# Xvfb 显示
DISPLAY = ":99"

# 微信数据路径
WECHAT_DATA_DIR = "/root/.xwechat"
WECHAT_FILES_DIR = "/root/xwechat_files"


def setup_display():
    """初始化无头操作的虚拟显示器。"""
    logger.info("正在设置虚拟显示器...")

    # 终止任何现有的 Xvfb 进程
    subprocess.run(["pkill", "-9", "Xvfb"], capture_output=True)

    # 启动 Xvfb
    subprocess.Popen([
        "Xvfb", DISPLAY, "-ac", "-screen", "0", "1000x1000x24"
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    time.sleep(2)

    # 启动 fluxbox 窗口管理器
    os.makedirs("/root/.fluxbox", exist_ok=True)
    subprocess.Popen(["fluxbox"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    time.sleep(1)
    os.environ["DISPLAY"] = DISPLAY
    logger.info("虚拟显示器设置完成")


def setup_profile(is_update_profile: bool, profile_name: str):
    """
    为会话设置微信数据目录。
    
    与 Chrome 版本不同，微信版本直接使用挂载的数据目录：
    - /root/.xwechat：微信登录状态
    - /root/xwechat_files：微信聊天文件
    
    这些目录在容器创建时已由 Docker 挂载，无需复制。
    """
    logger.info(f"设置微信数据目录，配置文件：{profile_name}")
    
    # 确保微信数据目录存在
    os.makedirs(WECHAT_DATA_DIR, exist_ok=True)
    os.makedirs(WECHAT_FILES_DIR, exist_ok=True)
    
    # 设置权限
    subprocess.run(["chmod", "-R", "777", WECHAT_DATA_DIR], capture_output=True)
    subprocess.run(["chmod", "-R", "777", WECHAT_FILES_DIR], capture_output=True)
    
    # 清理微信锁文件（防止启动冲突）
    lock_patterns = ["*.lock", "*.pid", "Singleton*"]
    for pattern in lock_patterns:
        try:
            subprocess.run(
                ["sh", "-c", f"rm -rf {WECHAT_DATA_DIR}/{pattern}"],
                capture_output=True
            )
        except Exception as e:
            logger.warning(f"清理锁文件失败：{e}")
    
    logger.info(f"微信数据目录设置完成：{WECHAT_DATA_DIR}")
    logger.info(f"微信文件目录设置完成：{WECHAT_FILES_DIR}")


def check_wechat_installed() -> bool:
    """检查微信是否已安装。"""
    try:
        result = subprocess.run(
            ["which", "wechat"],
            capture_output=True,
            text=True
        )
        if result.returncode == 0:
            logger.info(f"微信已安装：{result.stdout.strip()}")
            return True
        else:
            logger.warning("微信未安装或不在 PATH 中")
            return False
    except Exception as e:
        logger.error(f"检查微信安装失败：{e}")
        return False


def execute_task(task: TaskRequest) -> TaskResult:
    """执行微信 GUI 自动化任务。"""
    global computer_tools

    logger.info(f"开始执行微信任务：{task.task_id}")

    # 检查微信是否已安装
    if not check_wechat_installed():
        logger.error("微信未安装，无法执行任务")
        return TaskResult(
            task_id=task.task_id,
            status="failure",
            result="微信客户端未安装"
        )

    # 设置配置文件
    setup_profile(task.is_update_profile, task.profile_name)

    # 使用输出目录初始化工具
    output_dir = f"/app/anno/{task.task_id}"
    os.makedirs(output_dir, exist_ok=True)
    computer_tools = ComputerTools(output_dir=output_dir)

    # 初始化 LLM 包装器
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
            logger.info(f"[步骤 {step}] 正在截取屏幕截图: {screenshot_path}")

            if not computer_tools.get_screenshot(screenshot_path):
                logger.error("截取屏幕截图失败")
                return TaskResult(
                    task_id=task.task_id,
                    status="failure",
                    step=step,
                    result="截取屏幕截图失败"
                )

            logger.info(f"[步骤 {step}] 截图完成，文件大小: {os.path.getsize(screenshot_path)} bytes")

            # 构建消息并获取预测
            logger.info(f"[步骤 {step}] 正在构建 LLM 消息...")
            messages = build_messages(
                image_path=screenshot_path,
                instruction=task.instruction,
                history_output=history_output,
                model_name=task.model
            )

            logger.info(f"[步骤 {step}] 正在调用 LLM API ({task.model})...")
            prediction, payload, response = llm.predict_mm(messages)
            logger.info(f"[步骤 {step}] LLM API 调用完成，响应长度: {len(prediction) if prediction else 0}")

            if prediction == "调用 LLM 出错":
                logger.error("LLM 调用失败")
                return TaskResult(
                    task_id=task.task_id,
                    status="failure",
                    step=step,
                    result="LLM 调用失败"
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
                    "output": prediction
                })

                # 构建截图 URL
                screenshot_filename = f"step_{step:03d}_anno.png" if os.path.exists(anno_path) else f"step_{step:03d}.png"

                # 发送处理中回调
                result = TaskResult(
                    task_id=task.task_id,
                    status="processing",
                    step=step,
                    action=action_type,
                    image_url=screenshot_filename,
                    result=action_result or ""
                )

                if task.callback_url:
                    send_callback(task.callback_url, result)

                # 检查是否终止
                if action_type in ["terminate", "answer"]:
                    logger.info(f"微信任务被动作终止：{action_type}")
                    return TaskResult(
                        task_id=task.task_id,
                        status="completed",
                        step=step,
                        action=action_type,
                        image_url=screenshot_filename,
                        result=args.get("text", args.get("status", "任务完成"))
                    )

            time.sleep(0.5)

        # 达到最大步骤数
        logger.warning(f"达到最大步骤数（{max_steps}）")
        return TaskResult(
            task_id=task.task_id,
            status="timeout",
            step=step,
            result=f"达到最大步骤数（{max_steps}）但未完成"
        )

    except Exception as e:
        logger.exception(f"微信任务执行失败：{e}")
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
            logger.info(f"正在处理微信任务：{task.task_id}")

            result = execute_task(task)

            # 发送最终回调
            if task.callback_url:
                send_callback(task.callback_url, result)

            is_processing = False
            logger.info(f"微信任务完成：{task.task_id}，状态：{result.status}")

            task_queue.task_done()

        except Exception as e:
            logger.exception(f"微信工作线程错误：{e}")
            is_processing = False


# 启动后台工作线程
worker_thread = threading.Thread(target=task_worker, daemon=True)
worker_thread.start()


# ---------------------------------------------------------------------------
# HTTP 端点
# ---------------------------------------------------------------------------

@app.route('/health', methods=['GET'])
def health_check():
    """健康检查端点。"""
    # 检查微信是否已安装
    wechat_installed = check_wechat_installed()
    
    return jsonify({
        'status': 'healthy' if wechat_installed else 'degraded',
        'container_id': container_id,
        'profile': profile_name,
        'username': username,
        'api_port': server_port,
        'vnc_port': vnc_port,
        'processing': is_processing,
        'queue_size': task_queue.qsize(),
        'wechat_installed': wechat_installed,
        'wechat_data_dir': WECHAT_DATA_DIR,
        'app_type': 'wechat'
    })


@app.route('/task', methods=['POST'])
def submit_task():
    """提交新任务用于执行。"""
    global current_task

    try:
        data = request.get_json()

        if not data:
            return jsonify({'error': '未提供 JSON 数据'}), 400

        task = TaskRequest.from_dict(data)

        if is_processing:
            return jsonify({
                'error': '容器正在处理另一个任务',
                'current_task': current_task.task_id if current_task else None
            }), 409

        current_task = task
        task_queue.put(task)

        logger.info(f"微信任务已排队：{task.task_id}")

        return jsonify({
            'status': 'accepted',
            'task_id': task.task_id,
            'message': '微信任务已排队等待执行',
            'app_type': 'wechat'
        })

    except Exception as e:
        logger.exception(f"提交微信任务失败：{e}")
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
        'username': username,
        'api_port': server_port,
        'vnc_port': vnc_port,
        'processing': is_processing,
        'queue_size': task_queue.qsize(),
        'display': os.environ.get('DISPLAY', '未设置'),
        'wechat_installed': check_wechat_installed(),
        'wechat_data_dir': WECHAT_DATA_DIR,
        'wechat_files_dir': WECHAT_FILES_DIR,
        'app_type': 'wechat'
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
# 微信专用端点
# ---------------------------------------------------------------------------

@app.route('/wechat/status', methods=['GET'])
def wechat_status():
    """获取微信客户端状态。"""
    # 检查微信进程是否运行
    try:
        result = subprocess.run(
            ["pgrep", "-f", "wechat"],
            capture_output=True,
            text=True
        )
        wechat_running = result.returncode == 0
    except Exception:
        wechat_running = False
    
    return jsonify({
        'wechat_installed': check_wechat_installed(),
        'wechat_running': wechat_running,
        'wechat_data_exists': os.path.exists(WECHAT_DATA_DIR),
        'wechat_files_exists': os.path.exists(WECHAT_FILES_DIR),
        'wechat_data_dir': WECHAT_DATA_DIR,
        'wechat_files_dir': WECHAT_FILES_DIR
    })


@app.route('/wechat/start', methods=['POST'])
def start_wechat():
    """启动微信客户端。"""
    try:
        if computer_tools is None:
            computer_tools = ComputerTools()
        
        computer_tools.open_app("wechat")
        
        return jsonify({
            'status': 'success',
            'message': '微信客户端启动命令已发送'
        })
    except Exception as e:
        logger.error(f"启动微信失败：{e}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500


# ---------------------------------------------------------------------------
# 主入口点
# ---------------------------------------------------------------------------

if __name__ == '__main__':
    # 容器内部使用固定端口，外部映射由 Java 通过 docker run -p 参数决定
    server_port = 9001  # 固定 API 端口
    vnc_port = 9002     # 固定 VNC 端口
    container_id = os.environ.get('CONTAINER_ID', 'unknown')
    profile_name = os.environ.get('PROFILE_NAME', 'default')
    username = os.environ.get('USERNAME', 'default')

    logger.info("========================================")
    logger.info("Weixin 模块代理服务器")
    logger.info("========================================")
    logger.info(f"API 端口：{server_port}")
    logger.info(f"VNC 端口：{vnc_port}")
    logger.info(f"容器 ID：{container_id}")
    logger.info(f"配置文件：{profile_name}")
    logger.info(f"用户名：{username}")
    logger.info(f"微信数据目录：{WECHAT_DATA_DIR}")
    logger.info(f"微信文件目录：{WECHAT_FILES_DIR}")
    logger.info("========================================")

    # 运行 Flask 应用
    app.run(
        host='0.0.0.0',
        port=server_port,
        debug=False,
        threaded=True
    )
