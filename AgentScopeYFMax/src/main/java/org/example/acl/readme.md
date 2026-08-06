# ACL - 容器与 Agent 的通信层

本模块封装了与 V3 GUI 容器的所有 HTTP 通信。Agent 通过 `GuiApiClient` 调用容器内的 GUI 操作接口。

## GuiApiClient

### 初始化

```java
GuiApiClient gui = new GuiApiClient("http://172.17.0.5:9301");
```

传入 V3 容器的地址和端口即可。

### 统一返回值

所有 GUI 操作方法都返回 `GuiResponse`，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 操作是否成功 |
| `result` | String | 操作结果描述（如 "左键单击于：[500, 300]"） |
| `screenshot` | String | 操作后的屏幕截图，base64 编码（`data:image/png;base64,...`），传给 LLM 做视觉分析 |
| `screenshotPath` | String | 截图在宿主机上的绝对路径（如 `/usr/local/server/ai/AutoGUI/V3/profiles/user1/OutPut/test_001/step_001.png`），用于回调 Java 后端 |

---

## GUI 操作方法

### 1. 鼠标移动 `mouseMove`

将鼠标移动到指定坐标。

```java
GuiResponse resp = gui.mouseMove(step, x, y);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号（决定截图文件名 step_001.png） |
| `x` | int | 目标 X 坐标（0-999，屏幕分辨率 1000x1000） |
| `y` | int | 目标 Y 坐标（0-999） |

---

### 2. 左键单击 `leftClick`

将鼠标移动到指定坐标并单击左键。

```java
GuiResponse resp = gui.leftClick(step, x, y);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `x` | int | 目标 X 坐标 |
| `y` | int | 目标 Y 坐标 |

---

### 3. 右键单击 `rightClick`

将鼠标移动到指定坐标并单击右键，通常用于打开右键菜单。

```java
GuiResponse resp = gui.rightClick(step, x, y);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `x` | int | 目标 X 坐标 |
| `y` | int | 目标 Y 坐标 |

---

### 4. 双击 `doubleClick`

将鼠标移动到指定坐标并双击，通常用于打开文件或选中文字。

```java
GuiResponse resp = gui.doubleClick(step, x, y);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `x` | int | 目标 X 坐标 |
| `y` | int | 目标 Y 坐标 |

---

### 5. 三击 `tripleClick`

将鼠标移动到指定坐标并三击，通常用于选中整行文字。

```java
GuiResponse resp = gui.tripleClick(step, x, y);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `x` | int | 目标 X 坐标 |
| `y` | int | 目标 Y 坐标 |

---

### 6. 中键单击 `middleClick`

将鼠标移动到指定坐标并单击中键，通常用于在新标签页打开链接。

```java
GuiResponse resp = gui.middleClick(step, x, y);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `x` | int | 目标 X 坐标 |
| `y` | int | 目标 Y 坐标 |

---

### 7. 拖动 `drag`

从鼠标当前位置拖动到目标坐标。

```java
GuiResponse resp = gui.drag(step, x, y);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `x` | int | 目标 X 坐标（拖动终点） |
| `y` | int | 目标 Y 坐标（拖动终点） |

---

### 8. 输入文字 `type`

通过剪贴板粘贴方式输入文字。会自动执行 Ctrl+V，无需先点击输入框（需先用 leftClick 点击输入框）。

```java
GuiResponse resp = gui.type(step, "你好世界");
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `text` | String | 要输入的文字内容，支持中文 |

---

### 9. 按键 `key`

按下键盘按键，支持单键和组合键。

```java
// 单键
GuiResponse resp = gui.key(step, "enter");

// 组合键
GuiResponse resp = gui.key(step, List.of("ctrl", "a"));
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `keys` | String / List\<String\> | 按键名称，单个字符串或列表 |

**常用按键名**：

| 按键 | 名称 |
|------|------|
| 回车 | `enter` |
| 退格 | `backspace` |
| 删除 | `delete` |
| Tab | `tab` |
| Escape | `escape` |
| 上/下/左/右 | `up` / `down` / `left` / `right` |
| Ctrl+A（全选） | `List.of("ctrl", "a")` |
| Ctrl+C（复制） | `List.of("ctrl", "c")` |
| Ctrl+V（粘贴） | `List.of("ctrl", "v")` |

---

### 10. 滚动 `scroll`

垂直滚动鼠标滚轮。

```java
GuiResponse resp = gui.scroll(step, -300);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `pixels` | int | 滚动像素值，**负数向下**，正数向上 |

---

### 11. 打开应用 `openApp`

启动一个应用程序。容器内已安装 Chromium 浏览器。

```java
GuiResponse resp = gui.openApp(step, "google-chrome");
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `appName` | String | 应用程序名称 |

**常用应用名**：

| 应用 | 名称 |
|------|------|
| Chrome 浏览器 | `google-chrome` |
| Chromium | `chromium` |
| Firefox | `firefox` |

---

### 12. 等待 `wait`

等待指定秒数。用于等待页面加载、动画完成等场景。

```java
GuiResponse resp = gui.wait(step, 2);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |
| `seconds` | int | 等待秒数 |

---

### 13. 重置桌面 `reset`

执行 Win+D 快捷键，回到桌面。

```java
GuiResponse resp = gui.reset(step);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `step` | int | 当前步骤编号 |

---

## 系统接口

### 健康检查 `healthCheck`

```java
boolean healthy = gui.healthCheck();
```

返回 `true` 表示容器服务正常运行。

### 关闭容器 `shutdown`



向容器发送关闭指令。

---

## 截图路径说明

- 容器内保存路径（固定）：`/app/anno/{TASK_ID}/step_{step:03d}.png`
- 宿主机路径（通过 volume 挂载映射）：`/usr/local/server/ai/AutoGUI/V3/profiles/{username}/OutPut/{TASK_ID}/step_{step:03d}.png`
- `TASK_ID` 和 `OUTPUT_DIR` 在容器启动时通过环境变量传入
- 每步截图独立保存，不覆盖（step_001.png, step_002.png, ...）

## 完整调用示例

```java
// 初始化
GuiApiClient gui = new GuiApiClient("http://172.17.0.5:9301");

// Step 1: 打开浏览器
GuiResponse r1 = gui.openApp(1, "google-chrome");
String screenshot1 = r1.getScreenshot(); // 传给 LLM 分析

// Step 2: 点击搜索框（LLM 分析截图后决定的坐标）
GuiResponse r2 = gui.leftClick(2, 500, 300);

// Step 3: 输入搜索内容
GuiResponse r3 = gui.type(3, "你好世界");

// Step 4: 按回车搜索
GuiResponse r4 = gui.key(4, "enter");

// Step 5: 等待页面加载
GuiResponse r5 = gui.wait(5, 2);

// Step 6: 截图给 LLM 看结果（可以调个空操作来截图）
GuiResponse r6 = gui.mouseMove(6, 0, 0);
String screenshot6 = r6.getScreenshot(); // 传给 LLM 判断任务是否完成

// 回调 Java 后端时使用
String path = r4.getScreenshotPath(); // "/usr/.../OutPut/test_001/step_004.png"
```
