import ast
import json
import math
import os
import re
import sys
import textwrap
import time
import abc
import base64
import subprocess
import numpy as np
from io import BytesIO
from openai import OpenAI
from typing import Any, Optional

import pyautogui
import pyperclip
from PIL import Image, ImageDraw

# ---------------------------------------------------------------------------
# Computer interaction tools
# ---------------------------------------------------------------------------

class ComputerTools:
    """Cross-platform wrapper for desktop GUI automation via pyautogui."""

    def __init__(self):
        self.image_info = None

    def _load_image_info(self, path):
        """Cache the width and height of the latest screenshot."""
        width, height = Image.open(path).size
        self.image_info = (width, height)

    # -- screenshot -------------------------------------------------------

    def get_screenshot(self, image_path, retry_times=3):
        """
        Capture a desktop screenshot and save to *image_path*.
        Returns True on success, False after exhausting retries.
        """
        if os.path.exists(image_path):
            os.remove(image_path)

        for _ in range(retry_times):
            screenshot = pyautogui.screenshot()
            screenshot.save(image_path)
            if os.path.exists(image_path):
                self._load_image_info(image_path)
                return True
            time.sleep(0.1)
        return False

    # -- window management ------------------------------------------------

    def reset(self):
        """Minimize all windows and show the desktop."""
        pyautogui.hotkey("win", "d")

    # -- keyboard actions -------------------------------------------------

    def press_key(self, keys):
        if isinstance(keys, list):
            cleaned = []
            for key in keys:
                if isinstance(key, str):
                    key = key.strip()
                    for prefix in ("keys=[", "['", '["'):
                        if key.startswith(prefix):
                            key = key[len(prefix):]
                    for suffix in ("]", "']", '"]'):
                        if key.endswith(suffix):
                            key = key[: -len(suffix)]
                    key = key.strip()

                    arrow_map = {
                        "arrowleft": "left",
                        "arrowright": "right",
                        "arrowup": "up",
                        "arrowdown": "down",
                    }
                    key = arrow_map.get(key, key)
                    cleaned.append(key)
                else:
                    cleaned.append(key)
            keys = cleaned
        else:
            keys = [keys]

        if len(keys) > 1:
            pyautogui.hotkey(*keys)
        else:
            pyautogui.press(keys[0])

    def type(self, text):
        """
        Type text by copying to clipboard and pasting.
        Note: In Linux headless, ensure 'xclip' or 'xsel' is installed.
        """
        pyperclip.copy(text)
        pyautogui.keyDown("ctrl")
        pyautogui.keyDown("v")
        pyautogui.keyUp("v")
        pyautogui.keyUp("ctrl")

    # -- app launching ----------------------------------------------------

    def open_app(self, app_name, wait=0.5):
        if app_name == "File Explorer":
            app_name = "文件资源管理器"

        if sys.platform == "win32":
            pyautogui.hotkey("winleft", "s")
            time.sleep(wait)
            pyperclip.copy(app_name)
            pyautogui.hotkey("ctrl", "v")
            time.sleep(0.3)
            pyautogui.press("enter")
            time.sleep(0.5)

        elif sys.platform == "darwin":
            pyautogui.hotkey("command", "space")
            time.sleep(wait)
            pyperclip.copy(app_name)
            pyautogui.hotkey("command", "v")
            time.sleep(0.3)
            pyautogui.press("enter")

        else:
            # 修改为适配 Linux 无头环境的启动方式，并接入用户数据目录映射
            try:
                if "chrome" in app_name.lower():
                    subprocess.Popen([
                        "chromium",
                        "--no-sandbox", 
                        "--window-position=0,0",
                        "--window-size=1000,1000",
                        "--disable-gpu", 
                        "--disable-dev-shm-usage", 
                        "--disable-infobars",
                        "--force-device-scale-factor=1",
                        "--disable-dbus",             # 禁用 D-Bus，清理报错日志
                        "--disable-features=dbus",
                        "--user-data-dir=/app/chrome_profile"  # <--- 核心改动：接管挂载的登录态配置
                        # ====== 新增：强力屏蔽弹窗参数 ======
                        "--test-type",                       # 消除 "--no-sandbox" 警告横幅
                        "--disable-session-crashed-bubble",  # 消除 "Restore pages?" 崩溃恢复弹窗
                        "--no-first-run",                    # 跳过新用户的欢迎设置向导
                        "--password-store=basic"             # 禁用密码管理器弹窗
                    ])
                elif "firefox" in app_name.lower():
                    subprocess.Popen(["firefox", "--window-size=1000,1000"])
                else:
                    subprocess.Popen([app_name])
                time.sleep(max(wait, 2.0)) # 为冷启动多预留一点时间
            except Exception as e:
                print(f"[ERROR] Failed to launch {app_name} via subprocess: {e}")

    # -- mouse actions ----------------------------------------------------

    def mouse_move(self, x, y):
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.moveTo(x, y)

    def left_click(self, x, y):
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.click()

    def left_click_drag(self, x, y):
        pyautogui.dragTo(x, y, duration=0.5)
        pyautogui.moveTo(x, y)

    def right_click(self, x, y):
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.rightClick()

    def middle_click(self, x, y):
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.middleClick()

    def double_click(self, x, y):
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.doubleClick()

    def triple_click(self, x, y):
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.tripleClick()

    def scroll(self, pixels):
        pyautogui.scroll(pixels)

# ---------------------------------------------------------------------------
# Text formatting
# ---------------------------------------------------------------------------

def format_step_text(thought, action_list, explanation, max_width=88):
    def wrap(s):
        if isinstance(s, str):
            return "\n".join(textwrap.wrap(s, width=max_width))
        return str(s)

    parts = [f"Thought:\n{wrap(thought or '')}"]
    parts.append("\nActions:")
    if isinstance(action_list, list):
        for i, a in enumerate(action_list, 1):
            parts.append(f"  {i}. {json.dumps(a, ensure_ascii=False)}")
    else:
        parts.append(f"  {wrap(str(action_list))}")
    parts.append(f"\nExplanation:\n{wrap(explanation or '')}")
    return "\n".join(parts)

# ---------------------------------------------------------------------------
# Smart image resize & Annotation (Unchanged)
# ---------------------------------------------------------------------------

def smart_resize(height, width, factor=28, min_pixels=56 * 56, max_pixels=14 * 14 * 4 * 1280, max_long_side=8192):
    def _round(n): return round(n / factor) * factor
    def _floor(n): return math.floor(n / factor) * factor
    def _ceil(n): return math.ceil(n / factor) * factor

    if height < 2 or width < 2:
        raise ValueError(f"height ({height}) and width ({width}) must be >= 2")
    if max(height, width) / min(height, width) > 200:
        raise ValueError(f"Aspect ratio must be < 200")

    if max(height, width) > max_long_side:
        beta = max(height, width) / max_long_side
        height, width = int(height / beta), int(width / beta)

    h_bar, w_bar = _round(height), _round(width)

    if h_bar * w_bar > max_pixels:
        beta = math.sqrt((height * width) / max_pixels)
        h_bar, w_bar = _floor(height / beta), _floor(width / beta)
    elif h_bar * w_bar < min_pixels:
        beta = math.sqrt(min_pixels / (height * width))
        h_bar, w_bar = _ceil(height * beta), _ceil(width * beta)

    return h_bar, w_bar

def annotate_screenshot(image_path, action_parameter, save_path="screenshot_anno.png"):
    image = Image.open(image_path)
    draw = ImageDraw.Draw(image)

    if "coordinate" in action_parameter:
        radius = 15
        cx, cy = action_parameter["coordinate"]
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill="red", outline="red")
    elif "coordinate1" in action_parameter and "coordinate2" in action_parameter:
        x1, y1 = action_parameter["coordinate1"]
        x2, y2 = action_parameter["coordinate2"]
        color, arrow_size = "red", 10
        draw.line((x1, y1, x2, y2), fill=color, width=2)
        angle = math.atan2(y2 - y1, x2 - x1)
        ax1 = x2 - arrow_size * math.cos(angle - math.pi / 6)
        ay1 = y2 - arrow_size * math.sin(angle - math.pi / 6)
        ax2 = x2 - arrow_size * math.cos(angle + math.pi / 6)
        ay2 = y2 - arrow_size * math.sin(angle + math.pi / 6)
        draw.polygon([(x2, y2), (ax1, ay1), (ax2, ay2)], fill=color)
    else:
        return None
    image.save(save_path)
    return save_path

# ---------------------------------------------------------------------------
# VLM message construction (Unchanged)
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = (
    '# Tools\n\n'
    'You may call one or more functions to assist with the user query.\n\n'
    'You are provided with function signatures within <tools></tools> XML tags:\n'
    '<tools>\n'
    '{"type": "function", "function": {"name": "computer_use", '
    '"description": "Use a mouse and keyboard to interact with a computer, '
    'and take screenshots.\\n'
    '* This is an interface to a headless desktop GUI. There are no desktop icons.\\n'
    '* To start an application like a browser, you MUST use the `open app` action and provide the `app_name`.\\n'
    '* Some applications may take time to start or process actions, so you '
    'may need to wait and take successive screenshots to see the results of '
    'your actions.\\n'
    '* The screen\'s resolution is 1000x1000.\\n'
    '* Make sure to click any buttons, links, icons, etc with the cursor tip '
    'in the center of the element.", '
    '"parameters": {"properties": {"action": {"description": '
    '"The action to perform. The available actions are:\\n'
    '* `key`, `type`, `mouse_move`, `left_click`, `left_click_drag`, '
    '`right_click`, `middle_click`, `double_click`, `triple_click`, `scroll`, '
    '`hscroll`, `wait`, `terminate`, `answer`, `interact`, `open app`", '
    '"type": "string"}, '
    '"app_name": {"type": "string", "description": "The name of the application to open, e.g., google-chrome"}, '
    '"keys": {"type": "array"}, '
    '"text": {"type": "string"}, '
    '"coordinate": {"type": "array"}, '
    '"pixels": {"type": "number"}, '
    '"time": {"type": "number"}, '
    '"status": {"type": "string"}}, '
    '"required": ["action"], "type": "object"}}}\n'
    '</tools>\n\n'
    'For each function call, return a json object with function name and '
    'arguments within <tool_call></tool_call> XML tags:\n'
    '<tool_call>\n'
    '{"name": <function-name>, "arguments": <args-json-object>}\n'
    '</tool_call>\n'
)

def build_messages(image_path, instruction, history_output, model_name, history_n=4, output_format=""):
    current_step = len(history_output)
    history_start_idx = max(0, current_step - history_n)
    previous_actions = []
    for i in range(history_start_idx):
        if i < len(history_output):
            text = history_output[i]["output"]
            if "Action:" in text and "<tool_call>" in text:
                text = text.split("Action:")[1].split("<tool_call>")[0].strip()
            previous_actions.append(f"Step {i + 1}: {text}")

    previous_actions_str = "\n".join(previous_actions) if previous_actions else "None"

    instruction_prompt = (
        "Please generate the next move according to the UI screenshot, "
        "instruction and previous actions.\n\n"
        f"Instruction: {instruction}\n"
    )

    if output_format and output_format.strip():
        instruction_prompt += (
            f"\n【CRITICAL REQUIREMENT】\n"
            f"When you have completed the task, you MUST use the 'answer' action to output the final result. "
            f"Do NOT use 'terminate', 'stop', or 'done'.\n"
            f"Your 'text' parameter in the 'answer' action MUST be a strictly formatted JSON string.\n"
            f"The following JSON template defines the exact keys you must use, and the values describe the expected content and DATA TYPE for each key.\n"
            f"You must replace the descriptive values with the actual extracted data, keeping the requested data types:\n"
            f"{output_format}\n"
        )

    instruction_prompt += f"\nPrevious actions:\n{previous_actions_str}"

    messages = [{"role": "system", "content": [{"text": SYSTEM_PROMPT}]}]
    history_len = min(history_n, len(history_output))
    if history_len > 0:
        for idx, item in enumerate(history_output[-history_n:]):
            if idx == 0:
                messages.append({"role": "user", "content": [{"text": instruction_prompt}, {"image": "file://" + item["image"]}]})
            else:
                messages.append({"role": "user", "content": [{"image": "file://" + item["image"]}]})
            messages.append({"role": "assistant", "content": [{"text": item["output"]}]})
        messages.append({"role": "user", "content": [{"image": "file://" + image_path}]})
    else:
        messages.append({"role": "user", "content": [{"text": instruction_prompt}, {"image": "file://" + image_path}]})
    return messages

def extract_tool_calls(text):
    # 1. 标准格式 <tool_call...{json}...</tool_call
    pattern = re.compile(r"<tool_call\b(.*?)</tool_call\b", re.DOTALL | re.IGNORECASE)
    blocks = pattern.findall(text)

    # 2. 兜底：匹配任意 XML 标签包裹的内容（如 qwen3.5 的 <tool_call_response 等）
    if not blocks:
        fallback = re.compile(r"<(\w+)>(.*?)</\1>", re.DOTALL)
        matches = fallback.findall(text)
        blocks = [blk for _, blk in matches]

    # 3. 再兜底：直接找 JSON 对象 {"name": ..., "arguments": {...}}
    if not blocks:
        json_fallback = re.compile(r'\{[^\{\}]*"name"\s*:\s*"[^"]*"[^\{\}]*"arguments"\s*:\s*\{[^\{\}]*\}[^\{\}]*\}', re.DOTALL)
        blocks = json_fallback.findall(text)

    # 4. 最后兜底：纯 JSON {"action": ...}，没有 name/arguments 包装
    if not blocks:
        try:
            raw = json.loads(text.strip())
            if "action" in raw:
                blocks = [json.dumps({"name": "computer_use", "arguments": raw})]
        except (json.JSONDecodeError, ValueError):
            pass

    actions = []
    for blk in blocks:
        blk = blk.strip()
        try:
            actions.append(ast.literal_eval(blk))
        except (ValueError, SyntaxError):
            try:
                actions.append(json.loads(blk))
            except (json.JSONDecodeError, ValueError):
                pass
    return actions

def get_output_dir(task_id="default"):
    """
    获取任务专属的输出目录。
    目录结构：/app/anno/{task_id}/
    
    Args:
        task_id: 任务ID，用于创建任务专属子目录
    """
    out_dir = os.path.join("/app/anno", task_id)
    os.makedirs(out_dir, exist_ok=True)
    return out_dir

def sanitize_filename(name):
    return "".join(c if c.isalnum() or c in (" ", "_", "-") else "_" for c in name).strip()

def pil_to_base64(image):
    buffer = BytesIO()
    image.save(buffer, format="PNG") 
    return base64.b64encode(buffer.getvalue()).decode("utf-8")

def image_to_base64(image_path):
    # 1. 安全处理：如果是 file:// 协议，剔除它转换成纯本地路径
    if isinstance(image_path, str) and image_path.startswith('file://'):
        image_path = image_path.replace('file://', '', 1)
    dummy_image = Image.open(image_path)
    resized_height, resized_width = smart_resize(dummy_image.height, dummy_image.width, factor=28, min_pixels=3136, max_pixels=10035200)
    dummy_image = dummy_image.resize((resized_width, resized_height))
    return f"data:image/png;base64,{pil_to_base64(dummy_image)}"

class LlmWrapper(abc.ABC):
    @abc.abstractmethod
    def predict(self, text_prompt: str) -> tuple[str, Optional[bool], Any]: pass

class MultimodalLlmWrapper(abc.ABC):
    @abc.abstractmethod
    def predict_mm(self, text_prompt: str, images: list[np.ndarray]) -> tuple[str, Optional[bool], Any]: pass

class GUIOwlWrapper(LlmWrapper, MultimodalLlmWrapper):
    RETRY_WAITING_SECONDS = 20
    def __init__(self, api_key: str, base_url: str, model_name: str, max_retry: int = 10, temperature: float = 0.0):
        self.max_retry = min(max_retry, 10)
        self.temperature = temperature
        self.model = model_name
        self.bot = OpenAI(api_key=api_key, base_url=base_url, timeout=30)

    def convert_messages_format_to_openaiurl(self, messages):
        converted_messages = []
        for message in messages:
            new_content = []
            for item in message['content']:
                if list(item.keys())[0] == 'text':
                    new_content.append({'type': 'text', 'text': item['text']})
                elif list(item.keys())[0] == 'image':
                    new_content.append({'type': 'image_url', 'image_url': {'url': image_to_base64(item['image'])}})
            converted_messages.append({'role': message['role'], 'content': new_content})
        return converted_messages
    
    def predict(self, text_prompt: str) -> tuple[str, Optional[bool], Any]:
        return self.predict_mm(text_prompt, [])

    def predict_mm(self, messages = None) -> tuple[str, Optional[bool], Any]:
        payload = self.convert_messages_format_to_openaiurl(messages)
        counter = self.max_retry
        wait_seconds = self.RETRY_WAITING_SECONDS
        while counter > 0:
            try:
              chat_completion_from_url = self.bot.chat.completions.create(model=self.model, messages=payload, **{})
              return (chat_completion_from_url.choices[0].message.content, payload, chat_completion_from_url)
            except Exception as e:
                time.sleep(wait_seconds)
                wait_seconds *= 1
                counter -= 1
                print('Error calling LLM, will retry soon...')
        return 'Error calling LLM', None, None