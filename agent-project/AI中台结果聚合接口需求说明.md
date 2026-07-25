# AI中台接口需求：多步骤任务结果聚合查询

## 1. 背景

当前工具平台与 AI 中台采用 V3 Bridge 模式协作，一个工作流（Workflow）包含多个步骤（Step），每个步骤独立调用 `POST /api/agent/invoke`，AI 中台在步骤完成时通过回调返回 `modelOutput`。

### 现有调用流程

```
工具平台 invokeAgent(step1) → AI中台推理 → COMPLETED回调(modelOutput=结果1)
工具平台 invokeAgent(step2) → AI中台推理 → COMPLETED回调(modelOutput=结果2)
工具平台 invokeAgent(step3) → AI中台推理 → COMPLETED回调(modelOutput=结果3)
```

关键点：**多次 invoke 使用同一个 `taskId`**，AI 中台按 `taskId` 关联了同一次工作流的所有步骤记录。每一步的结果在 AI 中台侧均有落盘。

### 现有接口一览

| 方法 | 路径 | 方向 | 说明 |
|------|------|------|------|
| POST | `/api/agent/invoke` | 工具平台 → AI中台 | 启动 Agent |
| POST | 工具平台回调地址 | AI中台 → 工具平台 | 状态回调（SUSPENDED/COMPLETED/FAILED） |
| POST | `/api/agent/resume` | 工具平台 → AI中台 | 恢复 Agent |
| GET | `/api/agent/status/{taskId}` | 工具平台 → AI中台 | 查询单次任务状态 |
| POST | `/api/agent/cancel/{taskId}` | 工具平台 → AI中台 | 取消任务 |

## 2. 需求描述

工具平台需要在工作流全部步骤完成后，一次性获取该 `taskId` 下所有步骤的执行结果（即所有 `modelOutput` 的聚合），用于：

- 前端展示完整的多步骤执行报告
- 后续业务数据落盘（将采集结果写入业务数据表）

当前 `GET /api/agent/status/{taskId}` 仅返回最新一次 invoke 的状态，无法获取历史步骤的输出，需要新增一个聚合查询接口。

## 3. 接口定义

### 请求

```
GET /api/agent/results/{taskId}
```

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| taskId | path | String | 是 | 工作流关联的任务 ID（多次 invoke 共用同一个） |

### 成功响应

```json
{
  "success": true,
  "taskId": "v3_task_xxx",
  "totalSteps": 3,
  "status": "COMPLETED",
  "results": [
    {
      "step": 1,
      "status": "COMPLETED",
      "modelOutput": "步骤1的输出内容...",
      "startedAt": "2026-06-05T10:00:00",
      "completedAt": "2026-06-05T10:02:30"
    },
    {
      "step": 2,
      "status": "COMPLETED",
      "modelOutput": "步骤2的输出内容...",
      "startedAt": "2026-06-05T10:02:35",
      "completedAt": "2026-06-05T10:05:10"
    },
    {
      "step": 3,
      "status": "COMPLETED",
      "modelOutput": "步骤3的输出内容...",
      "startedAt": "2026-06-05T10:05:15",
      "completedAt": "2026-06-05T10:08:00"
    }
  ]
}
```

### 异常响应

**任务不存在：**

```json
{
  "success": false,
  "message": "任务不存在: xxx"
}
```

**任务仍在执行中：**

```json
{
  "success": false,
  "message": "任务仍在执行中，当前步骤: 2"
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | 请求是否成功 |
| taskId | String | 任务 ID |
| totalSteps | Integer | 该 taskId 下的总步骤数 |
| status | String | 整体状态（见下方状态定义） |
| results | Array | 按执行顺序排列的各步骤结果 |
| results[].step | Integer | 步骤序号（对应 invoke 时的执行顺序，从 1 开始） |
| results[].status | String | 单步状态：`COMPLETED` / `FAILED` |
| results[].modelOutput | String | 该步骤 Agent 的输出（即 COMPLETED 回调时的 modelOutput） |
| results[].startedAt | String | 该步骤开始时间（ISO 8601 格式） |
| results[].completedAt | String | 该步骤完成时间（ISO 8601 格式） |

### 整体状态定义

| 状态值 | 含义 |
|--------|------|
| `COMPLETED` | 所有步骤均已完成（含全部成功或全部失败） |
| `RUNNING` | 仍有步骤正在执行中 |
| `PARTIAL` | 部分步骤成功，部分失败，且均非执行中 |

## 4. AI 中台侧实现要点

### 4.1 数据来源

AI 中台在每次收到 `/api/agent/invoke` 时已有落盘记录，只需按 `taskId` 查询所有关联记录并按时间排序即可。

### 4.2 步骤序号

如果 AI 中台现有数据模型中没有 `step` 序号字段，可按 `createdAt` 升序排序，工具平台侧会根据数组下标映射到工作流的步骤编号。

### 4.3 状态判断逻辑

```
IF 存在 status = RUNNING 的记录 THEN
    整体状态 = "RUNNING"
ELSE IF 所有记录 status = COMPLETED THEN
    整体状态 = "COMPLETED"
ELSE
    整体状态 = "PARTIAL"
```

### 4.4 性能

一个工作流通常 3~5 个步骤，数据量很小，直接全量查询无需分页。

## 5. 工具平台侧配套改动

AI 中台提供上述接口后，工具平台侧会做以下配套改动：

### 5.1 改动清单

| 文件 | 改动说明 |
|------|----------|
| `AgentPlatformClient.java` | 接口新增 `JSONObject getAgentTaskResults(String taskId)` 方法 |
| `HutoolAgentPlatformClient.java` | 实现，调用 `GET {agentPlatformUrl}/api/agent/results/{taskId}` |
| `AgentBridgeManager.java` | 新增 `JSONObject getAccumulatedResults(String taskId)` 方法，委托给 client |
| `AgentCallbackController.java` | 新增 `GET /api/v3/agent/results/{taskId}` 端点，暴露给前端调用 |

### 5.2 工具平台侧调用时序

```
前端 → GET /api/v3/agent/results/{taskId}
         ↓
      AgentCallbackController
         ↓
      AgentBridgeManager.getAccumulatedResults(taskId)
         ↓
      HutoolAgentPlatformClient → GET /api/agent/results/{taskId} → AI中台
         ↓
      返回聚合结果 → 前端展示 / 工具平台落盘
```

## 6. 典型工作流示例

以 HR 智能体工作流为例（3 个步骤）：

| 步骤 | stepNo | 名称 | 说明 |
|------|--------|------|------|
| 1 | 1 | 简历筛选 | 分析候选人简历 |
| 2 | 2 | 候选人评估 | 综合评估候选人能力 |
| 3 | 3 | 生成报告 | 生成评估报告 |

全部步骤完成后，前端调用 `GET /api/v3/agent/results/v3_task_xxx`，工具平台透传到 AI 中台的 `GET /api/agent/results/v3_task_xxx`，拿到三个步骤的 `modelOutput` 聚合，前端可以展示完整的 HR 评估报告。
