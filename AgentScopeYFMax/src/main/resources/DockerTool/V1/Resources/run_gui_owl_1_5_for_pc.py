"""
Usage:
    cd Mobile-Agent-v3.5/computer_use
    xvfb-run -a -s "-screen 0 1000x1000x24" python run_gui_owl_1_5_for_pc.py \
        --api_key "Your API key" \
        --base_url "Your base url of vllm service" \
        --instruction "The instruction you want the agent to complete" \
        --model "Model name"
"""

import argparse
import json
import os
import time
import threading
import socket
import urllib.request
import urllib.error

import uvicorn
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from PIL import Image

from utils import (
    ComputerTools,
    annotate_screenshot,
    build_messages,
    extract_tool_calls,
    get_output_dir,
    sanitize_filename,
    smart_resize,
    GUIOwlWrapper
)

# --- 1. 初始化内嵌 FastAPI ---
app = FastAPI()
# 提前确保目录存在并挂载静态文件服务
output_dir_path = get_output_dir()
app.mount("/images", StaticFiles(directory=output_dir_path), name="images")

def start_fastapi():
    """在后台静默运行的文件服务器"""
    uvicorn.run(app, host="0.0.0.0", port=8000, log_config=None)

def get_container_ip():
    """获取容器内部网桥 IP"""
    try:
        return socket.gethostbyname(socket.gethostname())
    except Exception:
        return "127.0.0.1"

# --- 2. 回调发送函数 ---
def send_callback(callback_url, task_id, payload_data):
    if not callback_url:
        return
    payload = {
        "task_id": task_id,
        "data": payload_data
    }
    try:
        req = urllib.request.Request(
            callback_url, 
            data=json.dumps(payload).encode('utf-8'), 
            headers={'Content-Type': 'application/json'}, 
            method='POST'
        )
        with urllib.request.urlopen(req, timeout=5):
            pass # 成功发出即可，无需处理返回值
    except Exception as e:
        print(f"[WARN] Failed to send callback to Java backend: {e}")


def parse_args():
    parser = argparse.ArgumentParser(description="Computer-Agent-v3.5: Desktop GUI automation agent")
    parser.add_argument("--api_key", type=str, required=True, help="API key")
    parser.add_argument("--base_url", type=str, required=True, help="Base URL for the VLM service.")
    parser.add_argument("--instruction", type=str, required=True, help="The task instruction for the agent to complete")
    parser.add_argument("--model", type=str, default="", help="Model name for the VLM service")
    parser.add_argument("--add_info", type=str, default="", help="Optional supplementary knowledge for the task")
    parser.add_argument("--max_steps", type=int, default=50, help="Maximum number of interaction steps (default: 50)")
    parser.add_argument("--task_id", type=str, default="default_task", help="Unique task ID from backend")
    parser.add_argument("--callback_url", type=str, default="", help="HTTP URL to send callbacks to Java backend")
    return parser.parse_args()

def rescale_coordinates(action_parameter, resized_width, resized_height):
    for key in ("coordinate", "coordinate1", "coordinate2"):
        if key in action_parameter:
            action_parameter[key][0] = int(action_parameter[key][0] / 1000 * resized_width)
            action_parameter[key][1] = int(action_parameter[key][1] / 1000 * resized_height)

def execute_action(computer_tools, action_parameter, args):
    """
    Execute a single action on the desktop.
    Returns: stop (bool)
    """
    action_type = action_parameter["action"]

    if action_type in ("click", "left_click"):
        computer_tools.left_click(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
    elif action_type == "mouse_move":
        computer_tools.mouse_move(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
    elif action_type == "middle_click":
        computer_tools.middle_click(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
    elif action_type in ("right click", "right_click"):
        computer_tools.right_click(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
    elif action_type == "open app":
        computer_tools.open_app(action_parameter["app_name"])
    elif action_type in ("key", "hotkey"):
        computer_tools.press_key(action_parameter["keys"])
    elif action_type == "type":
        computer_tools.type(action_parameter["text"])
    elif action_type == "drag":
        computer_tools.left_click_drag(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
    elif action_type == "scroll":
        if "coordinate" in action_parameter:
            computer_tools.mouse_move(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
        computer_tools.scroll(action_parameter.get("pixels", 1))
    elif action_type in ("computer_double_click", "double_click"):
        computer_tools.double_click(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
    elif action_type == "triple_click":
        computer_tools.triple_click(action_parameter["coordinate"][0], action_parameter["coordinate"][1])
    elif action_type == "wait":
        time.sleep(action_parameter.get("time", 2))
        
    elif action_type == "answer":
        result_json = {
            "AGENT_RESULT_TYPE": "success",
            "action": "answer",
            "message": action_parameter.get("text", "")
        }
        print(f"\n<<<AGENT_OUTPUT_START>>>\n{json.dumps(result_json)}\n<<<AGENT_OUTPUT_END>>>\n")
        send_callback(args.callback_url, args.task_id, {"status": "completed", "result": result_json})
        return True

    elif action_type in ("stop", "terminate", "done"):
        status = action_parameter.get("status", "success")
        result_json = {
            "AGENT_RESULT_TYPE": "terminated",
            "status": status
        }
        print(f"\n<<<AGENT_OUTPUT_START>>>\n{json.dumps(result_json)}\n<<<AGENT_OUTPUT_END>>>\n")
        send_callback(args.callback_url, args.task_id, {"status": "terminated", "result": result_json})
        return True

    elif action_type in ("call_user", "interact"):
        result_json = {
            "AGENT_RESULT_TYPE": "failure",
            "reason": "Human interaction required but running in headless mode."
        }
        print(f"\n<<<AGENT_OUTPUT_START>>>\n{json.dumps(result_json)}\n<<<AGENT_OUTPUT_END>>>\n")
        send_callback(args.callback_url, args.task_id, {"status": "failure", "result": result_json})
        return True
    else:
        print(f"[WARN] Unsupported action type: {action_type}")

    return False

def get_output_dir(task_id):
    """
    获取任务专属的输出目录。
    目录结构：/app/anno/{task_id}/
    """
    out_dir = os.path.join("/app/anno", task_id)
    os.makedirs(out_dir, exist_ok=True)
    return out_dir

def main():
    args = parse_args()

    # --- 3. 启动后台图片服务 ---
    threading.Thread(target=start_fastapi, daemon=True).start()
    container_ip = get_container_ip()

    computer_tools = ComputerTools()
    computer_tools.reset()

    # 使用任务ID作为子目录
    output_dir = get_output_dir(args.task_id)
    safe_instruction = sanitize_filename(args.instruction)[:10]

    history = []
    stop_flag = False

    for step_id in range(args.max_steps):
        if stop_flag:
            break

        screen_shot = os.path.join(output_dir, f"{args.task_id}_{safe_instruction}_{step_id}.png")
        if not computer_tools.get_screenshot(screen_shot):
            continue

        messages = build_messages(screen_shot, args.instruction, history, args.model)

        vllm = GUIOwlWrapper(args.api_key, args.base_url, args.model)
        output_text, _, _ = vllm.predict_mm(messages)

        if not output_text or output_text == 'Error calling LLM':
            continue

        action_list = extract_tool_calls(output_text)

        dummy_image = Image.open(screen_shot)
        resized_height, resized_width = smart_resize(
            dummy_image.height, dummy_image.width,
            factor=16, min_pixels=3136, max_pixels=1003520 * 200,
        )

        for action_id, action in enumerate(action_list):
            action_parameter = action["arguments"]
            rescale_coordinates(action_parameter, resized_width, resized_height)
            
            # 注意：传入 args 以支持内部回调
            should_stop = execute_action(computer_tools, action_parameter, args)

            # 标注图名称
            anno_filename = f"anno_{args.task_id}_{safe_instruction}_{step_id}_{action_id}.png"
            annotate_screenshot(
                screen_shot, action_parameter,
                os.path.join(output_dir, anno_filename)
            )

            # --- 4. 组装图片的 HTTP 访问地址，并回调给 Java ---
            image_url = f"http://{container_ip}:8000/images/{anno_filename}"
            send_callback(args.callback_url, args.task_id, {
                "status": "processing",
                "step": step_id,
                "action": action_parameter.get("action", "unknown"),
                "image_url": image_url
            })

            if should_stop:
                stop_flag = True
                break

        history.append({"output": output_text, "image": screen_shot})
        time.sleep(2)

    if not stop_flag:
        result_json = {
            "AGENT_RESULT_TYPE": "timeout",
            "reason": f"Reached maximum steps ({args.max_steps}) without completion."
        }
        print(f"\n<<<AGENT_OUTPUT_START>>>\n{json.dumps(result_json,ensure_ascii=False)}\n<<<AGENT_OUTPUT_END>>>\n")
        send_callback(args.callback_url, args.task_id, {"status": "timeout", "result": result_json})

if __name__ == "__main__":
    main()