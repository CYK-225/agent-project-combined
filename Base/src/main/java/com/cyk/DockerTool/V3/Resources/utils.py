"""
V3 GUI 工具模块 - 纯 GUI 自动化工具函数。
提供计算机交互工具和图像处理功能。
LLM 功能已迁移至外部 AgentScope 服务。
"""

import os
import sys
import time
import base64
import logging
import subprocess
from io import BytesIO
from typing import Optional

import pyautogui
import pyperclip
from PIL import Image

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - [V3] - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 图像工具
# ---------------------------------------------------------------------------

def pil_to_base64(image: Image.Image) -> str:
    """将 PIL Image 转换为 base64 编码字符串"""
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


# ---------------------------------------------------------------------------
# 计算机交互工具
# ---------------------------------------------------------------------------

class ComputerTools:
    def __init__(self, output_dir: str = "/app/anno"):
        self.image_info = None
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)

    def _load_image_info(self, path: str) -> None:
        width, height = Image.open(path).size
        self.image_info = (width, height)

    def get_output_path(self, filename: str) -> str:
        return os.path.join(self.output_dir, filename)

    def get_screenshot(self, image_path: str, retry_times: int = 3) -> bool:
        if os.path.exists(image_path):
            os.remove(image_path)

        for attempt in range(retry_times):
            try:
                screenshot = pyautogui.screenshot()
                screenshot.save(image_path)
                if os.path.exists(image_path):
                    self._load_image_info(image_path)
                    logger.debug(f"截图已保存到 {image_path}")
                    return True
            except Exception as e:
                logger.warning(f"截图尝试 {attempt + 1} 失败：{e}")
            time.sleep(0.1)
        return False

    def reset(self) -> None:
        pyautogui.hotkey("win", "d")

    def press_key(self, keys) -> None:
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
        pyperclip.copy(text)
        pyautogui.keyDown("ctrl")
        pyautogui.keyDown("v")
        pyautogui.keyUp("v")
        pyautogui.keyUp("ctrl")

    def open_app(self, app_name: str, wait: float = 0.5) -> None:
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
            try:
                if "chrome" in app_name.lower() or "chromium" in app_name.lower():
                    import shutil
                    chrome_path = shutil.which("chromium") or shutil.which("chromium-browser") or shutil.which("google-chrome")

                    if not chrome_path:
                        raise FileNotFoundError("系统中未安装 Chromium/Chrome，或环境变量 PATH 异常！")

                    logger.info(f"探测到浏览器真实路径: {chrome_path}")

                    subprocess.Popen([
                        chrome_path,
                        "--no-sandbox",
                        "--window-position=0,0",
                        "--window-position=0,0",
                        "--window-size=1000,1000",
                        "--disable-gpu",
                        "--disable-dev-shm-usage",
                        "--disable-infobars",
                        "--force-device-scale-factor=1",
                        "--disable-dbus",
                        "--disable-features=dbus",
                        "--user-data-dir=/app/chrome_profile",
                        "--test-type",
                        "--disable-session-crashed-bubble",
                        "--no-first-run",
                        "--password-store=basic"
                    ])
                elif "firefox" in app_name.lower():
                    subprocess.Popen(["firefox", "--window-size=1000,1000"])
                else:
                    subprocess.Popen([app_name])
                time.sleep(max(wait, 2.0))
            except Exception as e:
                logger.error(f"启动 {app_name} 失败：{e}")

    def mouse_move(self, x: int, y: int) -> None:
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.moveTo(x, y)

    def left_click(self, x: int, y: int) -> None:
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.click()

    def left_click_drag(self, x: int, y: int) -> None:
        pyautogui.dragTo(x, y, duration=0.5)
        pyautogui.moveTo(x, y)

    def right_click(self, x: int, y: int) -> None:
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.rightClick()

    def middle_click(self, x: int, y: int) -> None:
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.middleClick()

    def double_click(self, x: int, y: int) -> None:
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.doubleClick()

    def triple_click(self, x: int, y: int) -> None:
        pyautogui.moveTo(x, y)
        time.sleep(0.1)
        pyautogui.tripleClick()

    def scroll(self, pixels: int) -> None:
        pyautogui.scroll(pixels)
