# V3 Bridge 解耦重构技术文档

## 📋 目录

- [1. 项目概述](#1-项目概述)
- [2. 架构设计](#2-架构设计)
- [3. ACL 层建设](#3-acl-层建设)
- [4. 工作流引擎](#4-工作流引擎)
- [5. 数据库操作](#5-数据库操作)
- [6. API 接口](#6-api-接口)
- [7. 配置说明](#7-配置说明)
- [8. 部署与验证](#8-部署与验证)
- [9. 常见问题](#9-常见问题)

---

## 1. 项目概述

### 1.1 背景

V3 Bridge 子系统原存在以下问题：
- **耦合严重**：`AgentBridgeManager` 同时处理 Docker 容器管理和 AI 中台通信
- **内存存储**：任务状态存储在 `ConcurrentHashMap`，服务重启后丢失
- **硬编码泛滥**：agentName、API Key、超时时间等分散在代码中
- **缺少工作流**：无法根据步骤成功/失败自动推进提示词

### 1.2 重构目标

1. **解耦架构**：将 Docker 容器管理与 AI 中台通信分离为独立关注点
2. **职责明确**：工具平台不落盘过程数据，只负责查询下一步提示词并发送给 AI 中台
3. **配置化**：所有硬编码值提取到配置文件
4. **工作流引擎**：基于配置文件的工作流管理系统

### 1.3 技术栈

- **框架**：Spring Boot 3.2.4 + Java 21
- **ORM**：MyBatis-Flex
- **HTTP 客户端**：Hutool HttpRequest
- **JSON 处理**：Fastjson2
- **数据库**：PostgreSQL (postgresql-session)

---

## 2. 架构设计

### 2.1 三层解耦架构

```
┌──────────────────────────────────────────────┐
│  编排层 (Orchestration)                       │
│  AgentBridgeManager                           │
│  - 协调容器 + 中台调用                         │
│  - 管理工作流生命周期                          │
│  - 内存维护最小任务上下文                      │
├──────────────────────────────────────────────┤
│  Docker 层 (已有)          │  AI 中台通信层    │
│  DockerPoolManagerV3       │  AgentPlatformClient
│  - 容器生命周期             │  ├─ invokeAgent()
│  - TaskContext              │  ├─ resumeAgent()
│  - getOrCreatePod()         │  ├─ getStatus()
│  （不改动）                  │  ├─ cancelTask()
│                             │  └─ healthCheck()
│                             │  实现：HutoolAgentPlatformClient
└──────────────────────────────────────────────┘
```

### 2.2 核心组件关系

```
AgentControllerV3
    ↓ 调用
AgentBridgeManager (编排层)
    ↓ 委托
├─ AgentPlatformClient (AI 中台通信)
│   └─ HutoolAgentPlatformClient
├─ ConcurrentHashMap (内存任务上下文)
│   └─ taskId → AgentTaskContext
└─ WorkflowEngine (工作流引擎)
    └─ ConfigWorkflowEngine → WorkflowConfigLoader
```

---

## 3. ACL 层建设

### 3.1 包结构

```
com.cyk.acl.agent/
├── AgentBridgeManager.java             # 编排层核心（含 AgentTaskContext 内部类）
├── dto/                                # 统一通信 DTO
│   └── AgentTaskNotifyDTO.java         # 覆盖 invoke/callback/resume 三种场景
├── client/                             # HTTP 客户端
│   ├── AgentPlatformClient.java (接口)
│   └── impl/HutoolAgentPlatformClient.java
└── controller/                         # REST 控制器
    ├── AgentCallbackController.java
    ├── ContainerBridgeController.java
    └── AgentTestController.java
```

> **注意**：工具平台不落盘过程数据。`entity/`、`mapper/`、`service/` 子包已移除，任务上下文通过 `ConcurrentHashMap` 在内存中维护。过程数据的持久化由 AI 中台负责。

### 3.2 核心类说明

#### AgentTaskContext（内部类）
**存储方式**: `ConcurrentHashMap<String, AgentTaskContext>`（内存）

| 字段 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务 ID |
| agentName | String | Agent 名称 |
| sessionId | String | 会话 ID |
| containerPort | Integer | 容器端口 |
| instruction | String | 用户指令 |
| currentStep | int | 当前步骤 |
| status | String | 状态：RUNNING/SUSPENDED/COMPLETED/FAILED |
| modelOutput | String | 模型输出 |
| errorMessage | String | 错误信息 |

#### AgentPlatformClient 接口

```java
public interface AgentPlatformClient {
    boolean invokeAgent(InvokeAgentRequest request);
    boolean resumeAgent(ResumeAgentRequest request);
    JSONObject getAgentTaskStatus(String taskId);
    boolean cancelAgentTask(String taskId);
    boolean healthCheck();
}
```

#### HutoolAgentPlatformClient 实现

**配置项**（从 application.yml 读取）：
- `agent.bridge.agent-platform-url`: AI 中台地址
- `agent.bridge.self-base-url`: 本服务地址
- `agent.bridge.callback-path`: 回调路径
- `agent.bridge.timeout`: HTTP 超时时间

---

## 4. 工作流引擎

### 4.1 包结构

```
com.cyk.DockerTool.V3.workflow/
├── model/                     # 工作流模型
│   ├── WorkflowDefinition.java
│   ├── WorkflowStep.java
│   ├── WorkflowInstance.java
│   └── StepResult.java
├── config/                    # 配置加载
│   ├── WorkflowConfigProperties.java
│   └── WorkflowConfigLoader.java
├── engine/                    # 引擎核心
│   ├── WorkflowEngine.java (接口)
│   └── impl/ConfigWorkflowEngine.java
├── service/                   # 业务服务
│   ├── WorkflowService.java + Impl
└── controller/                # REST API
    └── WorkflowController.java
```

### 4.2 工作流定义格式

**配置文件路径**: `start/src/main/resources/workflows/hr-agent-workflow.json`

```json
{
  "workflowId": "hr-agent-workflow",
  "workflowName": "HR智能体工作流",
  "description": "自动化处理HR相关任务的智能体工作流",
  "steps": [
    {
      "stepNo": 1,
      "stepName": "简历筛选",
      "promptId": 1001,
      "promptTitle": "简历分析提示词",
      "nextStepOnSuccess": 2,
      "nextStepOnFailure": null,
      "failureStrategy": "TERMINATE",
      "maxRetries": 0
    },
    {
      "stepNo": 2,
      "stepName": "候选人评估",
      "promptId": 1002,
      "promptTitle": "候选人评估提示词",
      "nextStepOnSuccess": 3,
      "nextStepOnFailure": 1,
      "failureStrategy": "RETRY",
      "maxRetries": 2
    }
  ]
}
```

**字段说明**：
- `promptId`: 关联 `prompts.prompt_id`，从数据库查询提示词内容
- `nextStepOnSuccess`: 成功时的下一步骤序号（null 表示工作流结束）
- `nextStepOnFailure`: 失败时的下一步骤序号（null 表示终止）
- `failureStrategy`: 
  - `TERMINATE`: 立即终止
  - `RETRY`: 重试当前步骤（≤ maxRetries）
  - `SKIP`: 跳过当前步骤

### 4.3 状态机逻辑

```
[INIT] → RUNNING
    ↓
[RUNNING] → 执行步骤 N
    ├─ 步骤成功 → 查询 nextStepOnSuccess
    │   ├─ 有下一步 → [RUNNING] (继续执行)
    │   └─ 无下一步 → [COMPLETED] (工作流结束)
    │
    └─ 步骤失败 → 查询 failureStrategy
        ├─ TERMINATE → [FAILED] (立即终止)
        ├─ RETRY → 检查 retry_count < max_retries
        │   ├─ 是 → [RUNNING] (重试当前步骤)
        │   └─ 否 → [FAILED] (达到最大重试次数)
        └─ SKIP → 查询 nextStepOnFailure
            ├─ 有下一步 → [RUNNING] (跳过当前步骤)
            └─ 无下一步 → [COMPLETED] (跳过剩余步骤)
```

### 4.4 核心方法

#### ConfigWorkflowEngine.startWorkflow()
启动工作流，创建运行时实例，初始化当前步骤为 1。

#### ConfigWorkflowEngine.advanceStep()
根据步骤成功/失败状态推进工作流，返回 `StepResult`。

#### ConfigWorkflowEngine.getCurrentPrompt()
从数据库查询当前步骤的提示词内容：
```java
QueryWrapper queryWrapper = QueryWrapper.create()
    .from(PROMPTS_ENTITY)
    .where(PROMPTS_ENTITY.PROMPT_ID.eq(currentStep.getPromptId()))
    .and(PROMPTS_ENTITY.STEP.eq(1))
    .limit(1);
PromptsEntity prompt = promptsService.getOne(queryWrapper);
return prompt.getContent();
```

---

## 5. 数据存储策略

### 5.1 设计原则

工具平台不落盘任务过程数据，职责划分如下：

| 职责 | 负责方 |
|------|--------|
| 任务进度、执行明细、状态变更持久化 | AI 中台 |
| 查询下一步提示词并发送给 AI 中台 | 工具平台（AgentBridgeManager） |
| 容器生命周期管理 | 工具平台（DockerPoolManagerV3） |
| 工作流步骤推进 | 工具平台（WorkflowEngine） |
| SSE 实时推送 | 工具平台（V3EmitterManager） |

### 5.2 内存任务上下文

`AgentBridgeManager` 使用 `ConcurrentHashMap<String, AgentTaskContext>` 在内存中维护最小上下文，仅保存回调时必需的信息：

- `agentName`、`sessionId`、`containerPort`：用于工作流推进时重新 invoke Agent
- `instruction`：用作工作流提示词为空时的降级
- `currentStep`、`status`、`modelOutput`、`errorMessage`：用于 SSE 推送和状态查询

任务完成或终止后自动从 Map 中移除。

### 5.3 工作流提示词查询

`ConfigWorkflowEngine.getCurrentPrompt()` 从 `prompts` 表查询提示词内容：

```java
QueryWrapper queryWrapper = QueryWrapper.create()
    .from(PROMPTS_ENTITY)
    .where(PROMPTS_ENTITY.PROMPT_ID.eq(currentStep.getPromptId()))
    .and(PROMPTS_ENTITY.STEP.eq(1))
    .limit(1);
PromptsEntity prompt = promptsService.getOne(queryWrapper);
return prompt.getContent();
```

### 5.4 已移除的数据库落盘代码

以下文件已在重构中移除（过程数据由 AI 中台负责）：

- `entity/AgentTaskEntity.java`、`entity/AgentExecutionDetailEntity.java`
- `mapper/AgentTaskMapper.java`、`mapper/AgentExecutionDetailMapper.java` 及对应 XML
- `service/AgentTaskService.java`、`service/AgentExecutionService.java` 及 Impl

对应的数据库表 `agent_task`、`agent_execution_detail` 不再需要由工具平台创建和维护。

---

## 6. API 接口

### 6.1 原有 API（保持不变）

| 路径 | 方法 | 说明 |
|------|------|------|
| `/api/v3/agent/task` | POST | 提交 Agent 任务 |
| `/api/v3/agent/callback` | POST | 接收 AI 中台回调 |
| `/api/v3/container/callback/{containerId}` | POST | 接收容器回调 |

### 6.2 新增 API

#### 启动工作流
```
POST /api/v3/workflow/start
参数:
  - workflowId: String (工作流定义 ID)
  - taskId: String (关联的任务 ID)
返回:
  {
    "code": 200,
    "data": {
      "instanceId": "uuid...",
      "workflowId": "hr-agent-workflow",
      "taskId": "task_123",
      "currentStepNo": 1,
      "status": "RUNNING",
      "startedAt": "2026-06-05T10:00:00"
    }
  }
```

#### 查询工作流状态
```
GET /api/v3/workflow/status/{instanceId}
返回:
  {
    "code": 200,
    "data": {
      "instanceId": "uuid...",
      "status": "RUNNING",
      "currentStepNo": 2,
      ...
    }
  }
```

---

## 7. 配置说明

### 7.1 application.yml 新增配置

```yaml
agent:
  bridge:
    # AI 中台地址
    agent-platform-url: http://localhost:8089
    # 本服务地址（用于回调）
    self-base-url: http://localhost:8081
    # 回调路径
    callback-path: /api/v3/agent/callback
    # HTTP 超时时间（毫秒）
    timeout: 30000
  
  workflow:
    # 配置文件目录
    config-dir: classpath:workflows/
    # 默认工作流 ID
    default-workflow-id: hr-agent-workflow
```

### 7.2 工作流配置文件

**位置**: `start/src/main/resources/workflows/*.json`

**示例**: `hr-agent-workflow.json`（见 4.2 节）

---

## 8. 部署与验证

### 8.1 编译验证

```bash
cd C:\Users\27575\.qoder-cn\worktree\工具平台\vb7r40
mvn clean compile -pl Base
```

### 8.2 数据库准备

1. **创建提示词数据**（工作流所需）：
   ```sql
   INSERT INTO prompts (prompt_id, step, prompt_title, content, ...) 
   VALUES 
     (1001, 1, '简历分析提示词', '请分析以下简历...'),
     (1002, 1, '候选人评估提示词', '请评估以下候选人...'),
     (1003, 1, '报告生成提示词', '请生成评估报告...');
   ```

### 8.3 功能验证

1. **测试 ACL 层通信**：
   - 调用 `/api/v3/agent/task` 提交任务
   - 确认 AI 中台收到 invokeAgent 请求
   - 等待 AI 中台回调，验证 SUSPENDED/COMPLETED 处理正常

2. **测试工作流引擎**：
   - 调用 `/api/v3/workflow/start?workflowId=hr-agent-workflow&taskId=task_123`
   - 检查工作流实例是否创建
   - 验证第一步提示词是否正确获取

3. **测试完整链路**：
   - 提交带工作流的任务
   - 观察步骤自动推进
   - 验证工作流完成后回调任务系统

---

## 9. 常见问题

### Q1: 为什么使用 jakarta 而非 javax？

**A**: Spring Boot 3.x 基于 Jakarta EE 9+，所有 `javax.*` 包已迁移到 `jakarta.*`。

### Q2: 工作流实例存储在内存中，重启会丢失吗？

**A**: 是的，当前实现使用 `ConcurrentHashMap` 存储工作流实例。生产环境建议持久化到 Redis 或数据库。

### Q3: 如何添加新的工作流？

**A**: 
1. 在 `start/src/main/resources/workflows/` 下创建新的 JSON 文件
2. 定义步骤和提示词关联
3. 在 `prompts` 表中添加对应的提示词记录
4. 重启应用，`WorkflowConfigLoader` 会自动加载

### Q4: AgentBridgeManager 改造后，原有调用方需要修改吗？

**A**: 不需要。AgentBridgeManager 的公共方法签名保持不变，内部实现改为委托给 ACL 层，对外透明。

### Q5: 如何监控任务执行状态？

**A**: 
- 调用 `/api/v3/agent/status/{taskId}` 查询实时状态
- 调用 `/api/v3/workflow/status/{instanceId}` 查询工作流状态
- 查看日志中的工作流推进记录
- 任务过程数据由 AI 中台持久化，可从 AI 中台查询详细执行历史

---

## 附录

### A. 文件清单

**新建文件**（14 个）：
- ACL 层：3 个文件（dto × 1 + client × 2 + controller × 3 = 6，含 AgentTaskNotifyDTO）
- 工作流引擎：11 个文件（model/config/engine/service/controller）

**已移除文件**（13 个）：
- Entity：`AgentTaskEntity.java`、`AgentExecutionDetailEntity.java`
- Mapper：`AgentTaskMapper.java`、`AgentExecutionDetailMapper.java`
- Mapper XML：`AgentTaskMapper.xml`、`AgentExecutionDetailMapper.xml`
- Service：`AgentTaskService.java`、`AgentExecutionService.java`
- ServiceImpl：`AgentTaskServiceImpl.java`、`AgentExecutionServiceImpl.java`
- 旧 DTO：`InvokeAgentRequest.java`、`ResumeAgentRequest.java`、`AgentCallbackPayload.java`

**修改文件**（4 个）：
- `AgentBridgeManager.java`（去除数据库操作，改用 ConcurrentHashMap）
- `AgentCallbackController.java`（去除 getLastLog 引用）
- `AgentTestController.java`（去除 getLastLog 引用）
- `AgentControllerV3.java`

### B. 关键代码路径

- [ACL 层入口（AgentPlatformClient）](file:///C:/Users/27575/.qoder-cn/worktree/工具平台/vb7r40/Base/src/main/java/com/cyk/acl/agent/client/AgentPlatformClient.java)
- [AgentBridgeManager（编排层）](file:///C:/Users/27575/.qoder-cn/worktree/工具平台/vb7r40/Base/src/main/java/com/cyk/acl/agent/AgentBridgeManager.java)
- [AgentCallbackController](file:///C:/Users/27575/.qoder-cn/worktree/工具平台/vb7r40/Base/src/main/java/com/cyk/acl/agent/controller/AgentCallbackController.java)
- [ContainerBridgeController](file:///C:/Users/27575/.qoder-cn/worktree/工具平台/vb7r40/Base/src/main/java/com/cyk/acl/agent/controller/ContainerBridgeController.java)
- [工作流引擎实现](file:///C:/Users/27575/.qoder-cn/worktree/工具平台/vb7r40/Base/src/main/java/com/cyk/DockerTool/V3/workflow/engine/impl/ConfigWorkflowEngine.java)
- [示例工作流配置](file:///C:/Users/27575/.qoder-cn/worktree/工具平台/vb7r40/start/src/main/resources/workflows/hr-agent-workflow.json)

### C. Git 分支

- 分支名：`v3-bridge-refactor`
- 提交数：2 个
- 变更统计：+1905 / -142 行

---

**文档版本**: v1.1  
**最后更新**: 2026-06-05  
**维护者**: V3 Bridge 重构团队

---

## 附录 D. 重构进度记录

### D.1 已完成项

| # | 修改项 | 涉及文件 | 完成状态 |
|---|--------|---------|---------|
| 1 | Entity 添加 MyBatis-Flex 注解 (`@Table`/`@Id`) | `acl/agent/entity/`（后已移除） | 已完成（后移除） |
| 2 | 删除旧版重复 Entity | `DockerTool/V3/AgentTaskEntity.java`, `DockerTool/V3/AgentExecutionDetailEntity.java` | 已完成 |
| 3 | 实现 `getInstanceByTaskId` | `WorkflowEngine.java`, `ConfigWorkflowEngine.java`, `WorkflowServiceImpl.java` | 已完成 |
| 4 | `DockerPoolManagerV3.executeToolAction()` | `DockerPoolManagerV3.java` | 已完成 |
| 5 | `AgentBridgeManager` 核心集成 | `AgentBridgeManager.java` | 已完成 |
| 6 | `AgentControllerV3` 联动修改 | `AgentControllerV3.java` | 已完成 |
| 7 | 移除数据库落盘代码 | `entity/`、`mapper/`、`service/` 共 10 个文件 | 已完成 |

### D.2 核心集成说明（修改项 5）

`AgentBridgeManager` 已完成以下集成：

1. **`handleAgentSuspended()` 实现**：收到 AI 中台 SUSPENDED 回调后，推送 SSE 事件 → 查找容器 Pod → 构造回调 URL → 调用 `executeToolAction()` 向容器发送 GUI 操作指令。容器执行完成后通过 `ContainerBridgeController` 回调触发 `resumeAgent()`。

2. **工作流引擎集成**：`invokeAgent()` 支持 6 参数重载，可选传入 `workflowId` 启动工作流。`handleAgentCompleted()` 中推进工作流（成功分支），获取下一步提示词再次 invoke。`handleAgentFailed()` 中推进工作流（失败分支），根据策略决定重试/跳过/终止。

3. **SSE 推送集成**：在 `handleAgentSuspended`、`handleAgentCompleted`、`handleAgentFailed`、`resumeAgent` 中均推送 SSE 事件。`AgentControllerV3.submitTask()` 中添加了 `emitterManager.bindTask()` 绑定。

4. **AI 中台回调工具调用参数格式**：结构化字段模式，payload 中包含 `toolName` 和 `toolInput` 独立字段。

### D.3 待后续优化项

- 工作流实例持久化（当前使用 ConcurrentHashMap 内存存储，重启丢失）
- `DockerControllerV3` 旧模式清理（与 `ContainerBridgeController` 并存）

### D.4 Bridge 迁移到 ACL 包（已完成）

将 `com.cyk.DockerTool.V3.bridge` 包整体迁移到 `com.cyk.acl.agent` 包：

| 文件 | 原位置 | 新位置 |
|------|--------|--------|
| `AgentBridgeManager.java` | `DockerTool.V3.bridge` | `acl.agent`（包根目录） |
| `AgentCallbackController.java` | `DockerTool.V3.bridge` | `acl.agent.controller` |
| `ContainerBridgeController.java` | `DockerTool.V3.bridge` | `acl.agent.controller` |
| `AgentTestController.java` | `DockerTool.V3.bridge` | `acl.agent.controller` |

旧 `DockerTool.V3.bridge` 包已删除。`AgentControllerV3` 的 import 已更新为 `com.cyk.acl.agent.AgentBridgeManager`。

### D.5 移除数据库落盘代码（已完成）

**设计决策**：工作流的过程数据落盘由 AI 中台负责，工具平台只负责查询下一步提示词并发送给 AI 中台，不需要知道进度也不需要本地持久化。

**已删除文件**（10 个）：

| 类别 | 文件 |
|------|------|
| Entity | `acl/agent/entity/AgentTaskEntity.java`、`acl/agent/entity/AgentExecutionDetailEntity.java` |
| Mapper | `acl/agent/mapper/AgentTaskMapper.java`、`acl/agent/mapper/AgentExecutionDetailMapper.java` |
| Mapper XML | `resources/com/cyk/acl/agent/mapper/AgentTaskMapper.xml`、`AgentExecutionDetailMapper.xml` |
| Service | `acl/agent/service/AgentTaskService.java`、`acl/agent/service/AgentExecutionService.java` |
| ServiceImpl | `acl/agent/service/impl/AgentTaskServiceImpl.java`、`AgentExecutionServiceImpl.java` |

**AgentBridgeManager 改造**：
- 移除 `AgentTaskService`、`AgentExecutionService` 依赖注入
- 移除所有 `taskService`/`executionService` 调用
- 新增 `ConcurrentHashMap<String, AgentTaskContext>` 维护最小内存上下文
- `invokeAgent()` 中 `taskContextMap.put()` 保存上下文
- `handleAgentCallback()` 中直接更新内存 `AgentTaskContext`
- `handleAgentCompleted()`/`handleAgentFailed()` 完成后 `taskContextMap.remove()` 清理
- `AgentTaskContext` 去除 `lastLog` 字段，新增 `modelOutput`、`errorMessage`

**Controller 联动修改**：
- `AgentCallbackController`、`AgentTestController` 中的 `getLastLog()` 调用改为 `getErrorMessage()`

### D.6 统一通信 DTO 为 AgentTaskNotifyDTO（已完成）

**设计决策**：将 3 个独立 DTO（`InvokeAgentRequest`、`ResumeAgentRequest`、`AgentCallbackPayload`）+ Controller 层的 `Map<String, Object>` 统一为单一的 `AgentTaskNotifyDTO`，覆盖 invoke/callback/resume 三种通信场景。

**已删除文件**（3 个）：
- `dto/InvokeAgentRequest.java`
- `dto/ResumeAgentRequest.java`
- `dto/AgentCallbackPayload.java`

**新增文件**（1 个）：
- `dto/AgentTaskNotifyDTO.java` — 统一通信 DTO，不同场景使用对应字段

**AgentTaskNotifyDTO 字段与场景对应关系**：

| 字段 | invoke | callback | resume |
|------|--------|----------|--------|
| taskId | Y | Y | Y |
| agentName | Y | | |
| sessionId | Y | | |
| containerPort | Y | | |
| instruction | Y | | |
| callbackUrl | Y | | |
| status | | Y | |
| step | | Y | Y |
| toolName | | Y | |
| toolInput | | Y | |
| modelOutput | | Y | |
| errorMessage | | Y | |
| executionResult | | | Y |
| success | | | Y |
| result | | | Y |
| screenshotPath | | | Y |

**改造范围**：
- `AgentPlatformClient` 接口：方法参数统一为 `AgentTaskNotifyDTO`
- `HutoolAgentPlatformClient`：从 DTO 中按场景提取字段构造 HTTP body
- `AgentBridgeManager`：`handleAgentCallback` 接收 `AgentTaskNotifyDTO`，`invokeAgent`/`resumeAgent` 内部构造 DTO
- 3 个 Controller 全部改为 `@RequestBody AgentTaskNotifyDTO`，删除内部请求类
