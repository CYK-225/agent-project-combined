"""
V3 GUI API 测试脚本 - 模拟 AgentScope 调用 GUI 工具接口。
验证每个接口返回格式正确（含宿主机截图路径）。
"""

import requests
import json
import sys

# 测试目标容器地址（根据实际容器 IP/端口修改）
CONTAINER_URL = "http://127.0.0.1:9301"

# 模拟参数
TASK_ID = "v3_test_task_001"
OUTPUT_DIR = "/usr/local/server/ai/AutoGUI/V3/profiles/test_user/OutPut"


def print_result(name, resp):
    """打印测试结果"""
    print(f"\n{'='*60}")
    print(f"接口：{name}")
    print(f"状态码：{resp.status_code}")
    try:
        data = resp.json()
        print(f"返回数据：")
        # 截图 base64 太长，只显示前100字符
        display_data = dict(data)
        if 'screenshot' in display_data and display_data['screenshot']:
            display_data['screenshot'] = display_data['screenshot'][:100] + "..."
        print(json.dumps(display_data, ensure_ascii=False, indent=2))

        # 验证关键字段
        assert 'success' in data, "缺少 success 字段"
        assert 'screenshot_path' in data, "缺少 screenshot_path 字段"
        assert 'screenshot' in data, "缺少 screenshot 字段"

        if data['success']:
            print(f"✅ 成功 | 截图路径：{data['screenshot_path']}")
        else:
            print(f"❌ 失败 | 错误：{data.get('error', '未知')}")

    except Exception as e:
        print(f"❌ 解析响应失败：{e}")
        print(f"原始响应：{resp.text[:500]}")


def test_health():
    """测试健康检查接口"""
    print("\n" + "="*60)
    print("接口：GET /health")
    resp = requests.get(f"{CONTAINER_URL}/health")
    print(f"状态码：{resp.status_code}")
    print(json.dumps(resp.json(), ensure_ascii=False, indent=2))


def test_mouse_move():
    """测试鼠标移动"""
    resp = requests.post(f"{CONTAINER_URL}/gui/mouse_move", json={
        "task_id": TASK_ID,
        "step": 1,
        "output_dir": OUTPUT_DIR,
        "x": 500,
        "y": 500
    })
    print_result("POST /gui/mouse_move", resp)


def test_left_click():
    """测试左键单击"""
    resp = requests.post(f"{CONTAINER_URL}/gui/left_click", json={
        "task_id": TASK_ID,
        "step": 2,
        "output_dir": OUTPUT_DIR,
        "x": 500,
        "y": 500
    })
    print_result("POST /gui/left_click", resp)


def test_right_click():
    """测试右键单击"""
    resp = requests.post(f"{CONTAINER_URL}/gui/right_click", json={
        "task_id": TASK_ID,
        "step": 3,
        "output_dir": OUTPUT_DIR,
        "x": 500,
        "y": 500
    })
    print_result("POST /gui/right_click", resp)


def test_double_click():
    """测试双击"""
    resp = requests.post(f"{CONTAINER_URL}/gui/double_click", json={
        "task_id": TASK_ID,
        "step": 4,
        "output_dir": OUTPUT_DIR,
        "x": 500,
        "y": 500
    })
    print_result("POST /gui/double_click", resp)


def test_type():
    """测试文字输入"""
    resp = requests.post(f"{CONTAINER_URL}/gui/type", json={
        "task_id": TASK_ID,
        "step": 5,
        "output_dir": OUTPUT_DIR,
        "text": "测试文字"
    })
    print_result("POST /gui/type", resp)


def test_key():
    """测试按键"""
    resp = requests.post(f"{CONTAINER_URL}/gui/key", json={
        "task_id": TASK_ID,
        "step": 6,
        "output_dir": OUTPUT_DIR,
        "keys": ["enter"]
    })
    print_result("POST /gui/key", resp)


def test_scroll():
    """测试滚动"""
    resp = requests.post(f"{CONTAINER_URL}/gui/scroll", json={
        "task_id": TASK_ID,
        "step": 7,
        "output_dir": OUTPUT_DIR,
        "pixels": -300
    })
    print_result("POST /gui/scroll", resp)


def test_wait():
    """测试等待"""
    resp = requests.post(f"{CONTAINER_URL}/gui/wait", json={
        "task_id": TASK_ID,
        "step": 8,
        "output_dir": OUTPUT_DIR,
        "seconds": 1
    })
    print_result("POST /gui/wait", resp)


def test_reset():
    """测试桌面重置"""
    resp = requests.post(f"{CONTAINER_URL}/gui/reset", json={
        "task_id": TASK_ID,
        "step": 9,
        "output_dir": OUTPUT_DIR
    })
    print_result("POST /gui/reset", resp)


def test_callback_format():
    """模拟回调数据格式（打印示例）"""
    print("\n" + "="*60)
    print("模拟回调数据格式（AgentScope → Java）")
    callback_data = {
        "task_id": TASK_ID,
        "data": {
            "status": "processing",
            "step": 2,
            "action": "left_click",
            "result": "左键单击于：[500, 500]",
            "screenshot_path": f"{OUTPUT_DIR}/{TASK_ID}/step_002.png"
        }
    }
    print(json.dumps(callback_data, ensure_ascii=False, indent=2))
    print("回调地址：POST http://8.129.128.167:8081/api/v3/docker/callback")


if __name__ == '__main__':
    print("V3 GUI API 测试")
    print(f"目标：{CONTAINER_URL}")
    print(f"任务ID：{TASK_ID}")
    print(f"输出目录：{OUTPUT_DIR}")

    try:
        # 1. 健康检查
        test_health()

        # 2. 逐个测试 GUI 接口
        test_mouse_move()
        test_left_click()
        test_right_click()
        test_double_click()
        test_type()
        test_key()
        test_scroll()
        test_wait()
        test_reset()

        # 3. 打印回调格式
        test_callback_format()

        print(f"\n{'='*60}")
        print("所有测试完成！")

    except requests.exceptions.ConnectionError:
        print(f"\n❌ 无法连接到 {CONTAINER_URL}")
        print("请确保 V3 容器已启动并监听在正确端口")
    except Exception as e:
        print(f"\n❌ 测试异常：{e}")
