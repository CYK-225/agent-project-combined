"""
V3 代理服务器 - 纯 GUI 工具 API 服务（异步回调版）。
LLM 思考功能已迁移至外部 AgentScope 服务，本服务只暴露 GUI 操作接口。
每个动作异步执行，执行完后回调 callback_url，同时返回结果。
"""

import os
import time
import signal
import logging
import threading
import requests as http_requests
from typing import Dict, Any

from flask import Flask, request, jsonify
from flask_cors import CORS

from utils import ComputerTools, pil_to_base64

logging.basicConfig(level=logging.INFO, format='%(asctime)s - [V3] - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# ---------------------------------------------------------------------------
# 全局单例 + 线程锁
# ---------------------------------------------------------------------------

gui_tools = ComputerTools(output_dir="/app/anno")
gui_lock = threading.Lock()

container_id: str = os.environ.get('CONTAINER_ID', 'unknown')
task_id: str = os.environ.get('TASK_ID', 'unknown')
output_dir: str = os.environ.get('OUTPUT_DIR', '/app/anno')
registration_dto: Dict[str, Any] = {}  # 保存 AgentScope 后端注册时传入的完整 DTO
callback_url: str = ""                 # 从 DTO 中提取，回调地址

# 容器内截图基础目录（固定，与 volume 挂载对应）
CONTAINER_ANNO_DIR = "/app/anno"


# ---------------------------------------------------------------------------
# 注册接口 - AgentScope 在创建 agent 前调用
# ---------------------------------------------------------------------------

@app.route('/gui/register', methods=['POST'])
def gui_register():
    global registration_dto, callback_url
    data = request.get_json()

    if not data.get('agent_id'):
        return jsonify({"success": False, "error": "agent_id 不能为空"}), 400

    # 保存完整 DTO，后续回调时基于它追加/覆盖字段
    registration_dto = data
    callback_url = data.get('callback_url', '')
    logger.info(f"[V3] 已注册完整 DTO，agent_id={data.get('agent_id')}，callback_url={callback_url}")
    return jsonify({"success": True, "agent_id": data.get('agent_id'), "callback_url": callback_url})


# ---------------------------------------------------------------------------
# 核心辅助函数：异步执行动作 + 自动截图 + 回调
# ---------------------------------------------------------------------------

def execute_and_screenshot_async(step: int, action_func):
    """
    在后台线程中执行 GUI 动作、截图、回调。
    """
    # 容器内保存路径
    container_dir = os.path.join(CONTAINER_ANNO_DIR, task_id)
    os.makedirs(container_dir, exist_ok=True)
    container_screenshot_path = os.path.join(container_dir, f"step_{step:03d}.png")

    # 宿主机返回路径
    host_screenshot_path = os.path.join(output_dir, task_id, f"step_{step:03d}.png")

    result_text = ""
    success = True

    with gui_lock:
        try:
            if action_func is not None:
                result_text = action_func()
        except Exception as e:
            logger.error(f"[V3] 动作执行失败：{e}")
            success = False
            result_text = f"错误：{str(e)}"

        try:
            gui_tools.get_screenshot(container_screenshot_path)
        except Exception as e:
            logger.error(f"[V3] 截图失败：{e}")

    # 读取截图转 base64
    screenshot_b64 = ""
    try:
        if os.path.exists(container_screenshot_path):
            from PIL import Image
            img = Image.open(container_screenshot_path)
            screenshot_b64 = f"data:image/png;base64,{pil_to_base64(img)}"
    except Exception as e:
        logger.error(f"[V3] 读取截图失败：{e}")

    # 基于 DTO 构造回调数据：复制注册时的完整 DTO，覆盖/追加容器端产生的字段
    response = dict(registration_dto)
    response["step"] = step
    response["success"] = success
    response["result"] = result_text if success else f"错误：{result_text}"
    response["screenshot"] = screenshot_b64
    response["screenshotPath"] = host_screenshot_path

    # 异步回调
    if callback_url:
        try:
            http_requests.post(callback_url, json=response, timeout=10)
            logger.info(f"[V3] step {step} 回调成功 → {callback_url}")
        except Exception as e:
            logger.error(f"[V3] step {step} 回调失败：{e}")

    return response


def async_dispatch(step: int, action_func):
    """启动后台线程执行，立即返回 202"""
    threading.Thread(
        target=execute_and_screenshot_async,
        args=(step, action_func),
        daemon=True
    ).start()
    return jsonify({"status": "accepted", "step": step, "agent_id": registration_dto.get('agent_id', '')}), 202


# ---------------------------------------------------------------------------
# GUI 操作接口 - 每个动作一个独立端点，异步执行
# ---------------------------------------------------------------------------

@app.route('/gui/left_click', methods=['POST'])
def gui_left_click():
    data = request.get_json()
    x, y = data.get('x', 0), data.get('y', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.left_click(x, y), f"左键单击于：[{x}, {y}]")[1]
    )


@app.route('/gui/right_click', methods=['POST'])
def gui_right_click():
    data = request.get_json()
    x, y = data.get('x', 0), data.get('y', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.right_click(x, y), f"右键单击于：[{x}, {y}]")[1]
    )


@app.route('/gui/double_click', methods=['POST'])
def gui_double_click():
    data = request.get_json()
    x, y = data.get('x', 0), data.get('y', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.double_click(x, y), f"双击于：[{x}, {y}]")[1]
    )


@app.route('/gui/triple_click', methods=['POST'])
def gui_triple_click():
    data = request.get_json()
    x, y = data.get('x', 0), data.get('y', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.triple_click(x, y), f"三击于：[{x}, {y}]")[1]
    )


@app.route('/gui/middle_click', methods=['POST'])
def gui_middle_click():
    data = request.get_json()
    x, y = data.get('x', 0), data.get('y', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.middle_click(x, y), f"中键单击于：[{x}, {y}]")[1]
    )


@app.route('/gui/mouse_move', methods=['POST'])
def gui_mouse_move():
    data = request.get_json()
    x, y = data.get('x', 0), data.get('y', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.mouse_move(x, y), f"移动到：[{x}, {y}]")[1]
    )


@app.route('/gui/drag', methods=['POST'])
def gui_drag():
    data = request.get_json()
    x, y = data.get('x', 0), data.get('y', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.left_click_drag(x, y), f"拖动到：[{x}, {y}]")[1]
    )


@app.route('/gui/type', methods=['POST'])
def gui_type():
    data = request.get_json()
    text = data.get('text', '')
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.type_text(text), f"输入：{text[:50]}..." if len(text) > 50 else f"输入：{text}")[1]
    )


@app.route('/gui/key', methods=['POST'])
def gui_key():
    data = request.get_json()
    keys = data.get('keys', [])
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.press_key(keys), f"按下按键：{keys}")[1]
    )


@app.route('/gui/scroll', methods=['POST'])
def gui_scroll():
    data = request.get_json()
    pixels = data.get('pixels', 0)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.scroll(pixels), f"滚动：{pixels} 像素")[1]
    )


@app.route('/gui/open_app', methods=['POST'])
def gui_open_app():
    data = request.get_json()
    app_name = data.get('app_name', '')
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.open_app(app_name), f"打开应用：{app_name}")[1]
    )


@app.route('/gui/wait', methods=['POST'])
def gui_wait():
    data = request.get_json()
    seconds = data.get('seconds', 1)
    return async_dispatch(
        data.get('step', 0),
        lambda: (time.sleep(seconds), f"等待：{seconds}秒")[1]
    )


@app.route('/gui/reset', methods=['POST'])
def gui_reset():
    data = request.get_json(force=True)
    return async_dispatch(
        data.get('step', 0),
        lambda: (gui_tools.reset(), "重置桌面")[1]
    )


# ---------------------------------------------------------------------------
# 系统接口
# ---------------------------------------------------------------------------

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy',
        'container_id': container_id,
        'version': 'V3-GUI-API'
    })


@app.route('/screenshot', methods=['GET'])
def take_screenshot():
    """直接返回截图图片文件（供 VNC 替代查看）"""
    temp_path = "/tmp/quick_screenshot.png"
    with gui_lock:
        gui_tools.get_screenshot(temp_path)
    try:
        from flask import send_file
        return send_file(temp_path, mimetype='image/png')
    except Exception as e:
        return jsonify({'error': f'截取屏幕截图失败：{e}'}), 500


# 任务中止标志
abort_flag = threading.Event()


@app.route('/shutdown', methods=['POST'])
def shutdown():
    def shutdown_handler():
        time.sleep(1)
        os.kill(os.getpid(), signal.SIGTERM)
    threading.Thread(target=shutdown_handler, daemon=True).start()
    return jsonify({'status': '正在关闭'})


@app.route('/abort', methods=['POST'])
def abort_task():
    """中止当前任务（软中止，不关闭容器）"""
    data = request.get_json(force=True) if request.is_json else {}
    task_id_to_abort = data.get('task_id', '')
    logger.info(f"[V3] 收到中止请求，task_id={task_id_to_abort}")
    abort_flag.set()
    return jsonify({'status': 'aborted', 'task_id': task_id_to_abort, 'message': '任务已中止'})


@app.route('/abort/reset', methods=['POST'])
def reset_abort_flag():
    """重置中止标志（新任务开始前调用）"""
    abort_flag.clear()
    logger.info("[V3] 中止标志已重置")
    return jsonify({'status': 'ok', 'message': '中止标志已重置'})


@app.route('/abort/status', methods=['GET'])
def get_abort_status():
    """查询中止标志状态"""
    return jsonify({'aborted': abort_flag.is_set()})


if __name__ == '__main__':
    server_port = int(os.environ.get('AGENT_PORT', 9301))
    vnc_port = int(os.environ.get('VNC_PORT', 9302))

    logger.info(f"[V3] GUI API 服务启动中，端口：{server_port}，VNC 端口：{vnc_port}")
    app.run(host='0.0.0.0', port=server_port, debug=False, threaded=True)
