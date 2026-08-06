
---

# MAS-FanPlus 核心组件开发者技术手册

## 1. 概述

MAS-FanPlus 是一套基于 AgentScope 框架的增强型开发套件。本手册旨在指导开发者如何利用项目中的 `util` 包下的各类 Factory 和 Builder 组件，快速构建高可维护性、高可观测性的智能体应用。

---

## 2. ToolkitFactory：工具箱构建系统

`ToolkitFactory` 提供了一套基于链式调用（Fluent API）的工具箱构建方案，解决了复杂 Agent 场景下本地工具、MCP 服务及子智能体（SubAgent）的混合编排问题。

### 2.1 核心特性

* **链式构建**：通过 `ToolkitChain` 实现语义化的工具注册。
* **多源集成**：统一支持 POJO 本地工具、远程 MCP 协议工具及 AgentScope 子智能体。
* **配置驱动**：支持通过 Spring 配置文件注入全局默认行为。
* **逻辑分组**：原生支持工具分组（Tool Grouping），便于权限隔离。

### 2.2 配置参数

以下配置位于 `application.yml` 中：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `agentscope.toolkit.parallel` | 是否并行执行多个工具 | `true` |
| `agentscope.toolkit.allow-tool-deletion` | 是否允许动态删除工具 | `true` |
| `agentscope.toolkit.timeout-seconds` | 工具执行超时时间（秒） | `300` |
| `agentscope.toolkit.enableMetaTool` | 是否开启元工具（MetaTool） | `false` |

### 2.3 使用示例

```java
@Autowired
private ToolkitFactory toolkitFactory;

public void initToolkit() {
    // 1. 基础构建
    Toolkit toolkit = toolkitFactory.create()
        .addTools(new WeatherService(), new Calculator()) // 添加到 default 组
        .addMCPTools(mcpClientWrapper)                    // 添加 MCP 工具
        .build();

    // 2. 复杂分组与 SubAgent 集成
    Toolkit complexToolkit = toolkitFactory.create()
        .createToolGroup("admin", "管理组", true)
        .addTools("admin", new SystemTool())
        .addAgent("admin", SubAgent.create()
            .agent(myReActAgent)
            .config().description("订单助手").end())
        .build();
}

```

### 2.4 API 参考 (ToolkitChain)

| 方法 | 描述 |
| --- | --- |
| `addTools(Object...)` | 注册本地工具到默认组。 |
| `addMCPTools(McpClientWrapper...)` | 注册 MCP 客户端工具。 |
| `addAgent(SubAgent...)` | 注册子智能体（SubAgent）作为工具。 |
| `createToolGroup(name, desc, active)` | 创建新的工具逻辑组。 |
| `addTools(groupName, Object...)` | 注册工具到指定组。 |
| `registerAll(...)` | 批量注册工具、MCP 等到指定组。 |

---

## 3. BaseSkillBoxFactory：技能装配工厂

该模块利用 Builder 模式简化 `SkillBox` 的创建，将 `Toolkit`（工具）与 `AgentSkill`（Prompt/技能描述）进行绑定。

### 3.1 核心功能

* **流式装配**：通过链式调用将工具与技能描述绑定。
* **Markdown 支持**：支持直接解析 Markdown 格式的技能文档。

### 3.2 使用示例

```java
// 定义技能 Prompt
String codeReviewSkillMd = Skill.create("CodeReview", "Java代码审计")
    .skillSubSection("Rules", "检查空指针、SQL注入")
    .render();

// 装配 SkillBox
SkillBox skillBox = BaseSkillBoxFactory.create()
    .registerSkillLoadTool(new Toolkit(), 
        BaseSkillBoxFactory.createBaseAgentSkillFromMd(codeReviewSkillMd))
    .build();

```

---

## 4. Session：会话持久化模块

基于 PostgreSQL 和 MyBatis-Flex 实现的会话存储方案，支持高效的列表增量更新。

### 4.1 核心特性

* **Hash 校验更新**：针对列表型状态（如历史对话），计算 Hash 值。若数据一致则跳过写入，若不一致且无法追加则全量重写，大幅优化数据库 I/O。
* **类型安全**：使用 `QueryColumn` 和 `QueryWrapper` 防止 SQL 注入。
* **严格校验**：内置 SessionID 和 Key 的格式（长度、字符）校验。

### 4.2 实体结构 (`agentscope_sessions`)

| 字段 | 说明 |
| --- | --- |
| `session_id` | 会话唯一标识（联合主键） |
| `state_key` | 状态键名，如 `history`（联合主键） |
| `item_index` | 列表索引，单对象为 0（联合主键） |
| `state_data` | JSON 序列化数据 |

### 4.3 API 参考 (PostgresSession)

| 方法 | 描述 |
| --- | --- |
| `save(sessionKey, key, value)` | 保存单个对象。 |
| `save(sessionKey, key, List values)` | 保存列表（带 Hash 优化）。 |
| `get(sessionKey, key, type)` | 获取并反序列化单个对象。 |
| `getList(sessionKey, key, itemType)` | 获取并反序列化列表。 |

---

## 5. PlanNotebookFactory：规划本构建器

用于构建 `PlanNotebook`，支持流式任务注入和自动状态管理。

### 5.1 核心功能

* **自动初始化**：构建时自动将第一个子任务状态置为 `IN_PROGRESS`，无需手动激活。
* **多模式注入**：支持 Stream API 添加、List 批量添加和嵌套 Builder 添加任务。

### 5.2 使用示例

```java
@Autowired
private PlanNotebookFactory planFactory;

public PlanNotebook createPlan() {
    return planFactory.builder()
        .maxSubtasks(5)
        .initPlanInfo("重构计划", "系统重构", "完成模块解耦")
        .addTask("任务1", "分析依赖", "依赖图") // 快速添加
        .task()                               // 嵌套构建
            .name("任务2").desc("接口设计").output("Swagger")
            .add()
        .build();
}

```

---

## 6. ModelFactory：模型构建工厂

统一管理 DashScope (阿里云) 和 Ollama (本地) 模型的构建，并提供场景化参数预设。

### 6.1 场景化预设 (OptionName)

通过传递字符串参数，自动应用最佳实践配置：

| 模式名称 | 适用场景 | 配置策略 (DashScope/Ollama) |
| --- | --- | --- |
| **"思考"** | 复杂推理、规划 | Temperature: 0.5 (适中) |
| **"工具"** | Function Calling | Temperature: 0.1 (极低), Ollama Context: 4096 |
| **"聊天"** | 开放域对话 | Temperature: 0.7/0.8 (较高) |

### 6.2 使用示例

```java
// 构建严谨的推理模型
Model reasonModel = DashScopeModelBuilder.buildDashScopeModel("思考");

// 构建本地工具调用模型
Model toolModel = OllamaModelBuilder.buildOllamaModel("工具");

```

---

## 7. HooksManager：全链路生命周期管理

基于 AOP 思想对 Agent 的 `Call`, `Reasoning`, `Acting`, `Error` 四大阶段进行拦截和增强。

### 7.1 Hook 策略组

`HookListFactory` 提供了预设的 Hook 组合：

* **"Review"**: 包含 `PostReasoning` (结果审计), `PreActing` (动作拦截) 等，用于风控。
* **"log"**: 包含 `ReasoningChunk`, `ActingChunk`，用于流式日志。
* **"debug"**: 仅包含 `ErrorHook`。

### 7.2 核心 Hook 组件

* **LangfuseSdkTracingHook**: 集成 Langfuse，自动将 Agent 事件映射为 Trace 和 Generation 进行分布式追踪。
* **TaskIdSseHook<T>**: 支持泛型的 SSE 推送钩子，可实时向前端推送思考过程、工具执行进度及结构化结果。

### 7.3 SSE Hook 使用示例

```java
// 泛型 T 指定工具结果期望转换的类型
TaskIdSseHook<UserEntity> sseHook = new TaskIdSseHook<>(taskId, UserEntity.class);

// 在 Hook 中会自动处理：
// 1. ThinkingBlock -> 推送 "思考中..."
// 2. ToolUse -> 推送 "正在执行工具..."
// 3. ToolResult -> 自动反序列化 JSON 为 UserEntity List

```

---

## 8. Memory：记忆模块

提供 `AutoContextMemory` 和 `ReMeLongTermMemory` 的构建封装。

### 8.1 AutoContextMemoryFactory

支持通过 Builder 模式覆盖 `application.yml` 中的默认配置（如 `maxToken`, `msgThreshold`）。

### 8.2 使用示例

```java
@Autowired
private AutoContextMemoryFactory memoryFactory;

// 创建自定义配置的内存
AutoContextMemory mem = memoryFactory.builder()
    .maxToken(8192)      // 覆盖默认值
    .msgThreshold(20)    // 覆盖默认值
    .build();

```