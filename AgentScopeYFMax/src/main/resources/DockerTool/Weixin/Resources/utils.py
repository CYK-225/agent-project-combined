"""
Weixin 模块工具模块 - 微信 Linux 客户端 GUI 自动化工具。
与现有的 Chrome 实现完全隔离。
"""

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
import logging
import numpy as np
from io import BytesIO
from openai import OpenAI
from typing import Any, Optional, Dict, List, Tuple
from dataclasses import dataclass, field

import pyautogui
import pyperclip
from PIL import Image, ImageDraw

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 数据类
# ---------------------------------------------------------------------------

@dataclass
class TaskRequest:
    """表示来自 Java 后端的任务请求。"""
    task_id: str
    instruction: str
    api_key: str
    base_url: str
    model: str
    max_steps: int = 30
    callback_url: str = ""
    is_update_profile: bool = False
    profile_name: str = "default"
    app_type: str = "wechat"  # 默认为微信任务
    extra_params: Dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'TaskRequest':
        """从字典创建 TaskRequest。"""
        return cls(
            task_id=data.get('task_id', ''),
            instruction=data.get('instruction', ''),
            api_key=data.get('api_key', ''),
            base_url=data.get('base_url', ''),
            model=data.get('model', 'qwen-vl-max'),
            max_steps=data.get('max_steps', 30),
            callback_url=data.get('callback_url', ''),
            is_update_profile=data.get('is_update_profile', False),
            profile_name=data.get('profile_name', 'default'),
            app_type=data.get('app_type', 'wechat'),
            extra_params=data.get('extra_params', {})
        )


@dataclass
class TaskResult:
    """表示任务执行的结果。"""
    task_id: str
    status: str
    step: int = 0
    action: str = ""
    image_url: str = ""
    result: str = ""
    extra_data: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典用于 JSON 序列化。"""
        return {
            'task_id': self.task_id,
            'data': {
                'status': self.status,
                'step': self.step,
                'action': self.action,
                'image_url': self.image_url,
                'result': self.result,
                **self.extra_data
            }
        }


# ---------------------------------------------------------------------------
# 计算机交互工具
# ---------------------------------------------------------------------------

class ComputerTools:
    """通过 pyautogui 进行跨平台桌面 GUI 自动化的包装器。"""

    def __init__(self, output_dir: str = "/app/anno"):
        self.image_info = None
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)

    def _load_image_info(self, path: str) -> None:
        """缓存最新截图的宽度和高度。"""
        width, height = Image.open(path).size
        self.image_info = (width, height)

    def get_output_path(self, filename: str) -> str:
        """获取输出文件的完整路径。"""
        return os.path.join(self.output_dir, filename)

    # -- 截图 -------------------------------------------------------

    def get_screenshot(self, image_path: str, retry_times: int = 3) -> bool:
        """捕获桌面截图并保存到 image_path。"""
        logger.info(f"[截图] 开始截图，目标路径: {image_path}")

        if os.path.exists(image_path):
            os.remove(image_path)

        for attempt in range(retry_times):
            try:
                logger.info(f"[截图] 尝试 {attempt + 1}/{retry_times}，调用 pyautogui.screenshot()...")
                screenshot = pyautogui.screenshot()
                logger.info(f"[截图] 截图获取成功，尺寸: {screenshot.size}")

                screenshot.save(image_path)
                if os.path.exists(image_path):
                    self._load_image_info(image_path)
                    logger.info(f"[截图] 截图已保存到 {image_path}，大小: {os.path.getsize(image_path)} bytes")
                    return True
            except Exception as e:
                logger.warning(f"[截图] 尝试 {attempt + 1} 失败：{e}")
            time.sleep(0.1)

        logger.error(f"[截图] 所有尝试均失败")
        return False

    # -- 窗口管理 ------------------------------------------------

    def reset(self) -> None:
        """最小化所有窗口并显示桌面。"""
        pyautogui.hotkey("win", "d")

    # -- 键盘操作 -------------------------------------------------

    def press_key(self, keys) -> None:
        """按下一个或多个键。"""
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

    def type_text(self, text: str) -> None:
        """通过复制到剪贴板并粘贴来输入文本。"""
        pyperclip.copy(text)
        pyautogui.keyDown("ctrl")
        pyautogui.keyDown("v")
        pyautogui.keyUp("v")
        pyautogui.keyUp("ctrl")

    # -- 应用启动 ----------------------------------------------------

    def open_app(self, app_name: str, wait: float = 0.5) -> None:
        """
        通过名称启动应用程序。
        
        微信专用优化：
        - 支持 'wechat'、'微信' 等名称
        - 延长等待时间（微信启动较慢）
        - 使用 subprocess.Popen 启动
        """
        # 标准化应用名称
        app_name_lower = app_name.lower()
        
        # 微信相关名称映射
        wechat_names = ['wechat', '微信', 'weixin', 'xwechat', 'wechat-linux']
        
        if any(name in app_name_lower for name in wechat_names):
            # 启动微信 Linux 客户端
            logger.info(f"正在启动微信 Linux 客户端...")
            try:
                # 使用 subprocess.Popen 启动微信
                subprocess.Popen(
                    ["wechat"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    start_new_session=True
                )
                # 微信启动需要较长时间
                extended_wait = max(wait, 5.0)  # 至少等待 5 秒
                logger.info(f"微信启动中，等待 {extended_wait} 秒...")
                time.sleep(extended_wait)
                logger.info("微信客户端已启动")
                return
                
            except FileNotFoundError:
                logger.warning("wechat 命令未找到，尝试备用启动方式...")
                # 尝试备用启动方式
                try:
                    # 尝试通过 which 查找微信可执行文件
                    result = subprocess.run(
                        ["which", "wechat"],
                        capture_output=True,
                        text=True
                    )
                    if result.returncode == 0:
                        wechat_path = result.stdout.strip()
                        subprocess.Popen(
                            [wechat_path],
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,
                            start_new_session=True
                        )
                        time.sleep(max(wait, 5.0))
                        return
                except Exception as e:
                    logger.error(f"备用启动方式失败：{e}")
                    
            except Exception as e:
                logger.error(f"启动微信失败：{e}")
                return

        # 其他应用程序的启动逻辑
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
            # Linux 环境
            try:
                if "chrome" in app_name_lower:
                    subprocess.Popen([
                        "google-chrome",
                        "--no-sandbox",
                        "--window-position=0,0",
                        "--window-size=1000,1000",
                        "--disable-gpu",
                        "--disable-dev-shm-usage"
                    ])
                elif "firefox" in app_name_lower:
                    subprocess.Popen(["firefox", "--window-size=1000,1000"])
                else:
                    subprocess.Popen(
                        [app_name],
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.DEVNULL
                    )
                time.sleep(max(wait, 2.0))
            except Exception as e:
                logger.error(f"启动 {app_name} 失败：{e}")

    # -- 鼠标操作 ----------------------------------------------------

    def mouse_move(self, x: int, y: int) -> None:
        """移动鼠标到指定坐标。"""
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.moveTo(x, y)

    def left_click(self, x: int, y: int) -> None:
        """在指定坐标左键单击。"""
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.click()

    def left_click_drag(self, x: int, y: int) -> None:
        """拖动到指定坐标。"""
        pyautogui.dragTo(x, y, duration=0.5)
        pyautogui.moveTo(x, y)

    def right_click(self, x: int, y: int) -> None:
        """在指定坐标右键单击。"""
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.rightClick()

    def middle_click(self, x: int, y: int) -> None:
        """在指定坐标中键单击。"""
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.middleClick()

    def double_click(self, x: int, y: int) -> None:
        """在指定坐标双击。"""
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.doubleClick()

    def triple_click(self, x: int, y: int) -> None:
        """在指定坐标三击。"""
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.tripleClick()

    def scroll(self, pixels: int) -> None:
        """滚动指定像素。"""
        pyautogui.scroll(pixels)


# ---------------------------------------------------------------------------
# 文本格式化
# ---------------------------------------------------------------------------

def format_step_text(thought: str, action_list: List, explanation: str, max_width: int = 88) -> str:
    """格式化步骤文本用于显示。"""
    def wrap(s):
        if isinstance(s, str):
            return "\n".join(textwrap.wrap(s, width=max_width))
        return str(s)

    parts = [f"思考：\n{wrap(thought or '')}"]
    parts.append("\n动作：")
    if isinstance(action_list, list):
        for i, a in enumerate(action_list, 1):
            parts.append(f"  {i}. {json.dumps(a, ensure_ascii=False)}")
    else:
        parts.append(f"  {wrap(str(action_list))}")
    parts.append(f"\n解释：\n{wrap(explanation or '')}")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# 智能图像缩放和标注
# ---------------------------------------------------------------------------

def smart_resize(height: int, width: int, factor: int = 28,
                 min_pixels: int = 56 * 56, max_pixels: int = 14 * 14 * 4 * 1280,
                 max_long_side: int = 8192) -> Tuple[int, int]:
    """智能缩放尺寸以适应约束条件。"""
    def _round(n): return round(n / factor) * factor
    def _floor(n): return math.floor(n / factor) * factor
    def _ceil(n): return math.ceil(n / factor) * factor

    if height < 2 or width < 2:
        raise ValueError(f"高度({height})和宽度({width})必须 >= 2")
    if max(height, width) / min(height, width) > 200:
        raise ValueError(f"宽高比必须 < 200")

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


def annotate_screenshot(image_path: str, action_parameter: Dict,
                        save_path: str = "screenshot_anno.png") -> Optional[str]:
    """用动作标记标注截图。"""
    image = Image.open(image_path)
    draw = ImageDraw.Draw(image)

    if "coordinate" in action_parameter:
        radius = 15
        cx, cy = action_parameter["coordinate"]
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius),
                     fill="red", outline="red")
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
# VLM 消息构建
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = (
    '# 工具\n\n'
    '你可以调用一个或多个函数来辅助回答用户问题。\n\n'
    '你将获得<tools></tools> XML标签中的函数签名：\n'
    '<tools>\n'
    '{"type": "function", "function": {"name": "computer_use", '
    '"description": "使用鼠标和键盘与计算机交互，并截取屏幕截图。\\n'
    '* 这是一个运行 **微信 Linux 客户端** 的无头桌面 GUI 环境。\\n'
    '* 微信已经安装在系统中，启动命令是 `wechat`。\\n'
    '* 要启动微信，请使用 `open app` 动作，app_name 设为 `wechat`。\\n'
    '* 微信启动需要约 5 秒，启动后请等待并截图确认。\\n'
    '* 屏幕分辨率为 1000x1000。\\n'
    '* 微信窗口会自动最大化，无需手动调整。\\n'
    '* 如果需要登录微信，请使用二维码扫描登录。\\n'
    '* 确保点击任何按钮、链接、图标等时，光标尖端位于元素中心。", '
    '"parameters": {"properties": {"action": {"description": '
    '"要执行的动作。可用动作有：\\n'
    '* `key`（按键）, `type`（输入）, `mouse_move`（移动鼠标）, `left_click`（左键单击）, `left_click_drag`（左键拖动）, '
    '`right_click`（右键单击）, `middle_click`（中键单击）, `double_click`（双击）, `triple_click`（三击）, `scroll`（滚动）, '
    '`hscroll`（水平滚动）, `wait`（等待）, `terminate`（终止）, `answer`（回答）, `interact`（交互）, `open app`（打开应用）", '
    '"type": "string"}, '
    '"app_name": {"type": "string", "description": "要打开的应用程序名称，例如 wechat（微信）"}, '
    '"keys": {"type": "array"}, '
    '"text": {"type": "string"}, '
    '"coordinate": {"type": "array"}, '
    '"pixels": {"type": "number"}, '
    '"time": {"type": "number"}, '
    '"status": {"type": "string"}}, '
    '"required": ["action"], "type": "object"}}}\n'
    '</tools>\n\n'
    '# 微信操作指南\n\n'
    '## 启动微信\n'
    '使用 `open app` 动作，app_name 设为 `wechat`：\n'
    '<tool_call >\n'
    '{"name": "computer_use", "arguments": {"action": "open app", "app_name": "wechat"}}\n'
    '</tool_call >\n\n'
    '## 常用操作\n'
    '- 发送消息：点击联系人 → 输入消息 → 按回车发送\n'
    '- 查看聊天记录：滚动鼠标查看历史消息\n'
    '- 发送文件：点击文件传输助手 → 拖拽或使用发送文件功能\n\n'
    '# 输出格式要求\n\n'
    '【🛑 严格输出格式要求 🛑】\n'
    '1. 你必须且只能返回包含函数名和参数的 JSON 对象。\n'
    '2. 必须将 JSON 对象完全包裹在 <tool_call > 和 </tool_call > 标签中。注意：左标签的右括号前必须保留一个空格！\n'
    '3. 绝对禁止使用 Markdown 语法（如 ```json 或 ```）。\n'
    '4. 绝对禁止输出任何多余的思考过程、解释性文本、确认语或聊天回复。\n\n'
    '正确的输出示例：\n'
    '<tool_call >\n'
    '{"name": "computer_use", "arguments": {"action": "mouse_move", "coordinate": [500, 500]}}\n'
    '</tool_call >\n'
)


def build_messages(image_path: str, instruction: str, history_output: List[Dict],
                   model_name: str, history_n: int = 4) -> List[Dict]:
    """为 VLM API 调用构建消息。"""
    current_step = len(history_output)
    history_start_idx = max(0, current_step - history_n)
    previous_actions = []

    for i in range(history_start_idx):
        if i < len(history_output):
            text = history_output[i]["output"]
            if "Action:" in text and "<tool_call " in text:
                text = text.split("Action:")[1].split("<tool_call ")[0].strip()
            previous_actions.append(f"步骤 {i + 1}: {text}")

    previous_actions_str = "\n".join(previous_actions) if previous_actions else "无"
    instruction_prompt = (
        "请根据 UI 截图、指令和之前的动作生成下一步操作。\n\n"
        f"指令：{instruction}\n\n"
        f"之前的动作：\n{previous_actions_str}"
    )

    messages = [{"role": "system", "content": [{"text": SYSTEM_PROMPT}]}]
    history_len = min(history_n, len(history_output))

    if history_len > 0:
        for idx, item in enumerate(history_output[-history_n:]):
            if idx == 0:
                messages.append({
                    "role": "user",
                    "content": [{"text": instruction_prompt}, {"image": "file://" + item["image"]}]
                })
            else:
                messages.append({
                    "role": "user",
                    "content": [{"image": "file://" + item["image"]}]
                })
            messages.append({"role": "assistant", "content": [{"text": item["output"]}]})
        messages.append({"role": "user", "content": [{"image": "file://" + image_path}]})
    else:
        messages.append({
            "role": "user",
            "content": [{"text": instruction_prompt}, {"image": "file://" + image_path}]
        })

    return messages


def extract_tool_calls(text: str) -> List[Dict]:
    """从模型输出中提取工具调用。"""
    pattern = re.compile(r"<tool_call >(.*?)</tool_call >", re.DOTALL | re.IGNORECASE)
    blocks = pattern.findall(text)
    actions = []
    for blk in blocks:
        blk = blk.strip()
        try:
            actions.append(ast.literal_eval(blk))
        except (ValueError, SyntaxError) as e:
            logger.warning(f"解析工具调用失败：{blk}，错误：{e}")
    return actions


# ---------------------------------------------------------------------------
# 图像工具
# ---------------------------------------------------------------------------

def sanitize_filename(name: str) -> str:
    """通过移除特殊字符来清理文件名。"""
    return "".join(c if c.isalnum() or c in (" ", "_", "-") else "_" for c in name).strip()


def pil_to_base64(image: Image.Image) -> str:
    """将 PIL 图像转换为 base64 字符串。"""
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def image_to_base64(image_path: str) -> str:
    """将图像文件转换为 base64 数据 URL。"""
    if isinstance(image_path, str) and image_path.startswith('file://'):
        image_path = image_path.replace('file://', '', 1)

    dummy_image = Image.open(image_path)
    resized_height, resized_width = smart_resize(
        dummy_image.height, dummy_image.width,
        factor=28, min_pixels=3136, max_pixels=10035200
    )
    dummy_image = dummy_image.resize((resized_width, resized_height))
    return f"data:image/png;base64,{pil_to_base64(dummy_image)}"


# ---------------------------------------------------------------------------
# LLM 包装器
# ---------------------------------------------------------------------------

class LlmWrapper(abc.ABC):
    """LLM 包装器的抽象基类。"""

    @abc.abstractmethod
    def predict(self, text_prompt: str) -> Tuple[str, Optional[bool], Any]:
        pass


class MultimodalLlmWrapper(abc.ABC):
    """多模态 LLM 包装器的抽象基类。"""

    @abc.abstractmethod
    def predict_mm(self, text_prompt: str, images: List[np.ndarray]) -> Tuple[str, Optional[bool], Any]:
        pass


class GUIOwlWrapper(LlmWrapper, MultimodalLlmWrapper):
    """GUI-Owl 多模态模型的包装器。"""

    RETRY_WAITING_SECONDS = 20

    def __init__(self, api_key: str, base_url: str, model_name: str,
                 max_retry: int = 10, temperature: float = 0.0):
        self.max_retry = min(max_retry, 10)
        self.temperature = temperature
        self.model = model_name
        self.bot = OpenAI(api_key=api_key, base_url=base_url, timeout=30)

    def convert_messages_format_to_openaiurl(self, messages: List[Dict]) -> List[Dict]:
        """将内部消息格式转换为 OpenAI API 格式。"""
        converted_messages = []
        for message in messages:
            new_content = []
            for item in message['content']:
                if list(item.keys())[0] == 'text':
                    new_content.append({'type': 'text', 'text': item['text']})
                elif list(item.keys())[0] == 'image':
                    logger.info(f"[LLM] 正在转换图片为 base64: {item['image'][:50]}...")
                    image_url = image_to_base64(item['image'])
                    logger.info(f"[LLM] 图片转换完成，base64 长度: {len(image_url)}")
                    new_content.append({
                        'type': 'image_url',
                        'image_url': {'url': image_url}
                    })
            converted_messages.append({'role': message['role'], 'content': new_content})
        return converted_messages

    def predict(self, text_prompt: str) -> Tuple[str, Optional[bool], Any]:
        return self.predict_mm(text_prompt, [])

    def predict_mm(self, messages: List[Dict] = None) -> Tuple[str, Optional[bool], Any]:
        """使用多模态消息进行预测。"""
        logger.info(f"[LLM] 开始转换消息格式...")
        payload = self.convert_messages_format_to_openaiurl(messages)
        logger.info(f"[LLM] 消息格式转换完成，消息数量: {len(payload)}")

        counter = self.max_retry
        wait_seconds = self.RETRY_WAITING_SECONDS

        while counter > 0:
            try:
                logger.info(f"[LLM] 调用 API (尝试 {self.max_retry - counter + 1}/{self.max_retry})，模型: {self.model}")
                chat_completion = self.bot.chat.completions.create(
                    model=self.model,
                    messages=payload,
                    **{}
                )
                content = chat_completion.choices[0].message.content
                logger.info(f"[LLM] API 调用成功，响应长度: {len(content) if content else 0}")
                return (content, payload, chat_completion)
            except Exception as e:
                logger.error(f"[LLM] 调用出错：{e}，{wait_seconds} 秒后重试...")
                time.sleep(wait_seconds)
                wait_seconds *= 1
                counter -= 1

        return '调用 LLM 出错', None, None


# ---------------------------------------------------------------------------
# HTTP 回调工具
# ---------------------------------------------------------------------------

def send_callback(callback_url: str, result: TaskResult) -> bool:
    """向 Java 后端发送任务结果回调。"""
    import requests

    try:
        response = requests.post(
            callback_url,
            json=result.to_dict(),
            timeout=10
        )
        if response.status_code == 200:
            logger.info(f"任务 {result.task_id} 的回调发送成功")
            return True
        else:
            logger.warning(f"回调失败，状态码 {response.status_code}")
            return False
    except Exception as e:
        logger.error(f"发送回调失败：{e}")
        return False
