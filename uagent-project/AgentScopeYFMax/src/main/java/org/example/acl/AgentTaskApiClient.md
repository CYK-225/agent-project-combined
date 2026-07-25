# ACL - Agent 中台 API 客户端

本模块提供 `AgentTaskApiClient`，封装了与 Agent 中台的所有 HTTP 通信。**工具平台**通过此客户端与 Agent 中台交互。

## AgentTaskApiClient

### 初始化

```java
AgentTaskApiClient client = new AgentTaskApiClient("http://localhost:8080");
```

传入 Agent 中台的地址和端口。

---

## 业务接口

### 1. 调用 Agent `invokeAgent`

调用 Agent（首次创建会话，非首次拿到历史记忆）。

```java
boolean success = client.invokeAgent(
    "hr-agent",                    // agentName
    "session_001",                 // sessionId（用于记忆隔离）
    "task_001",                    // taskId（可选，不传则自动生成）
    9301,                          // containerPort
    "打开浏览器搜索天气",            // instruction
    "http://tool-platform/callback" // callbackUrl
);
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `agentName` | String | Agent 名称，例如 "hr-agent" |
| `sessionId` | String | 会话 ID（用于记忆隔离，可选，默认=taskId） |
| `taskId` | String | 任务 ID（可选，不传则自动生成） |
| `containerPort` | int | 容器端口号 |
| `instruction` | String | 指令内容（Agent 要执行的任务） |
| `callbackUrl` | String | 工具平台回调地址（接收日志用） |

返回 `boolean`，`true` 表示调用成功。

**会话机制：**
- 首次调用：sessionId 不存在 → 创建新会话
- 非首次调用：sessionId 已存在 → 拿到历史记忆

---

## 完整调用示例

```java
// 初始化
AgentTaskApiClient client = new AgentTaskApiClient("http://localhost:8080");

// 调用 Agent（首次创建会话）
boolean invoked = client.invokeAgent(
    "hr-agent",                    // agentName
    "session_001",                 // sessionId
    "task_001",                    // taskId
    9301,                          // containerPort
    "打开浏览器搜索天气",            // instruction
    "http://tool-platform/callback" // callbackUrl
);
```

---

## 状态流转

```
PENDING → RUNNING → SUSPENDED → RESUMED → SUSPENDED → ... → COMPLETED
                    ↓
                  FAILED / CANCELLED
```

| 状态 | 说明 |
|------|------|
| `PENDING` | 待处理，任务已创建 |
| `RUNNING` | 执行中，Agent 正在推理 |
| `SUSPENDED` | 已挂起，等待容器执行 |
| `RESUMED` | 已恢复，Agent 继续推理 |
| `COMPLETED` | 已完成 |
| `FAILED` | 失败 |
| `CANCELLED` | 已取消 |

---

## 与 GuiApiClient 的关系

| 类 | 方向 | 用途 |
|----|------|------|
| `GuiApiClient` | Agent 中台 → 容器 | Agent 调用容器执行 GUI 操作 |
| `AgentTaskApiClient` | 工具平台 → Agent 中台 | 工具平台调用 Agent 中台管理任务 |

```
工具平台 ──AgentTaskApiClient──→ Agent 中台 ──GuiApiClient──→ 容器
                                    ↑                            │
                                    └──────── callback ──────────┘
```

---

## API 路径对照

| 接口 | 方法 | 路径 |
|------|------|------|
| 调用 Agent | POST | `/api/agent/invoke` |
