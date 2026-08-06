# Agent 任务系统 - 接口文档

## 📁 文件结构

```
AgentScopeYFMax/
├── src/main/java/org/example/repository/dal/
│   ├── config/
│   │   └── RestTemplateConfig.java          # RestTemplate配置
│   ├── controller/agentTask/
│   │   └── AgentController.java             # Agent 控制器
│   ├── entity/
│   │   └── AgentTaskEntity.java             # 任务实体
│   ├── mapper/
│   │   └── AgentTaskMapper.java             # Mapper接口
│   └── service/
│       ├── IAgentTaskService.java           # Service接口
│       ├── AgentTaskExecutorService.java    # 执行服务
│       └── impl/
│           └── AgentTaskServiceImpl.java    # Service实现
└── src/main/resources/masfanplus/Session/
    └── AgentTaskMapper.xml                  # MyBatis XML

AgentServiceHR/
└── src/main/java/org/example/agent/HR/
    ├── HRAgent.java                         # HR智能体
    └── tools/
        └── HrTools.java                     # HR工具集（挂起机制）
```

---

## 🔌 提供给工具平台的接口

### 1. 调用 Agent（创建或恢复会话）

**POST** `/api/agent/invoke`

**请求体：**
```json
{
    "agentName": "hr-agent",
    "sessionId": "session_001",
    "taskId": "task_001",
    "containerPort": 9301,
    "instruction": "打开浏览器搜索天气",
    "callbackUrl": "http://tool-platform.com/callback"
}
```

**参数说明：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentName | String | 是 | Agent 名称 |
| sessionId | String | 否 | 会话 ID（默认=taskId） |
| taskId | String | 否 | 任务 ID（不传则自动生成） |
| containerPort | Integer | 是 | 容器端口号 |
| instruction | String | 是 | 用户指令 |
| callbackUrl | String | 否 | 回调地址 |

**响应：**
```json
{
    "success": true,
    "message": "Agent 调用成功",
    "taskId": "task_001",
    "sessionId": "session_001"
}
```

**会话机制：**
- 首次调用：sessionId 不存在 → 创建新会话
- 非首次调用：sessionId 已存在 → 拿到历史记忆

---

### 2. 恢复 Agent 执行

**POST** `/api/agent/resume`

**请求体：**
```json
{
    "taskId": "task_001",
    "executionResult": "点击成功",
    "success": true,
    "result": "左键单击于：[500, 300]",
    "screenshot": null,
    "screenshotPath": "/path/to/step_001.png",
    "step": 1
}
```

**参数说明：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| taskId | String | 是 | 任务 ID |
| executionResult | String | 否 | 容器执行结果 |
| success | Boolean | 否 | 工具执行是否成功 |
| result | String | 否 | 成功时的结果描述 |
| screenshot | String | 否 | base64 截图 |
| screenshotPath | String | 否 | 截图路径 |
| step | Integer | 否 | 步骤编号 |

**响应：**
```json
{
    "success": true,
    "message": "任务恢复成功"
}
```

---

## 🔄 完整流程

```
1. 工具平台 → POST /api/agent/invoke
   ↓
2. Agent 中台创建任务，返回成功
   ↓
3. Agent 开始执行，调用工具
   ↓
4. 工具抛出 ToolSuspendException（挂起）
   ↓
5. 工具平台调用容器执行
   ↓
6. 容器执行完成
   ↓
7. 容器回调 → POST /api/phone/callback
   ↓
8. Agent 恢复执行，继续推理
   ↓
9. 循环直到任务完成
   ↓
10. Agent 中台发送日志给工具平台（回调地址）
```

---

## 🛠️ 工具挂起机制

### HrTools 示例

```java
@Tool(name = "gui_left_click", description = "在指定坐标执行左键单击")
public ToolResultBlock guiLeftClick(
        @ToolParam(name = "step", description = "步骤编号") Integer step,
        @ToolParam(name = "x", description = "X坐标") Integer x,
        @ToolParam(name = "y", description = "Y坐标") Integer y) {
    
    // 异步发送请求到容器
    guiClient.leftClick(step, x, y);
    
    // 挂起，等待容器执行完成回调
    throw new ToolSuspendException("等待容器执行左键单击");
}
```

### GUI 工具列表

| 工具名 | 说明 | 参数 |
|--------|------|------|
| `gui_left_click` | 左键单击 | step, x, y |
| `gui_right_click` | 右键单击 | step, x, y |
| `gui_double_click` | 双击 | step, x, y |
| `gui_triple_click` | 三击 | step, x, y |
| `gui_middle_click` | 中键单击 | step, x, y |
| `gui_mouse_move` | 鼠标移动 | step, x, y |
| `gui_drag` | 拖拽 | step, x, y |
| `gui_type` | 输入文字 | step, text |
| `gui_key` | 按键 | step, keys |
| `gui_scroll` | 滚动 | step, amount |
| `gui_open_app` | 打开应用 | step, appName |
| `gui_wait` | 等待 | step, seconds |
| `gui_reset` | 重置 | step |

---

## 📊 数据库表

**表名：** `agent_task`

**数据源：** `postgresql-session`

**字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| agent_name | VARCHAR | Agent 名称 |
| task_id | VARCHAR | 任务 ID（唯一） |
| session_id | VARCHAR | 会话 ID |
| container_port | INTEGER | 容器端口 |
| instruction | TEXT | 指令 |
| log | TEXT | 日志 |
| model_output | TEXT | 模型输出 |
| callback_url | VARCHAR | 回调地址 |
| status | VARCHAR | 状态 |
| error_message | TEXT | 错误信息 |
| created_by | VARCHAR | 创建者 |
| updated_by | VARCHAR | 修改者 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| started_at | TIMESTAMP | 开始时间 |
| completed_at | TIMESTAMP | 完成时间 |
| success | BOOLEAN | 工具执行是否成功 |
| result | TEXT | 工具执行结果 |
| screenshot | TEXT | base64 截图 |
| screenshot_path | VARCHAR | 截图路径 |
| step | INTEGER | 步骤编号 |

**状态流转：**
```
PENDING → RUNNING → SUSPENDED → RESUMED → SUSPENDED → ... → COMPLETED
                    ↓
                  FAILED / CANCELLED
```

---

## 📝 ACL 客户端

工具平台可以使用 `AgentTaskApiClient` 调用 Agent 中台接口：

```java
AgentTaskApiClient client = new AgentTaskApiClient("http://localhost:8080");

// 调用 Agent
client.invokeAgent("hr-agent", "session_001", "task_001", 9301, "打开浏览器", "http://callback");

// 查询状态
TaskStatus status = client.getTaskStatus("task_001");

// 恢复任务
client.resumeTask("task_001", "点击成功", true, "左键单击", null, "/path/screenshot.png", 1);
```

---

*更新时间：2026-05-27*
