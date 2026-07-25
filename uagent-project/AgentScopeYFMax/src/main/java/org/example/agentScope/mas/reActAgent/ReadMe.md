
---

# ReAct Agent 构建工厂与配置文档

## 1. 概述

本模块提供了一套基于 **AgentScope** 框架的 ReAct 智能体构建方案。核心由 `ReActAgentFactory`（工厂类）与 `AgentConfigPo`（配置对象）组成。

该设计采用了 **延迟构建 (Lazy Build)** 与 **配置驱动 (Configuration Driven)** 的策略，旨在实现配置数据（如 YAML/JSON 反序列化对象）与运行时构建逻辑的解耦，同时提供链式调用的灵活性，以便在最终实例化前动态覆盖参数。

### 核心特性

* **配置解耦**：通过 POJO 承载纯数据配置。
* **链式覆盖**：支持在构建阶段覆盖 POJO 中的初始值。
* **纯净构建**：仅负责 `ReActAgent` 实例的生产，不涉及 AgentPool 的生命周期管理。

---

## 2. 使用规范

### 2.1 典型构建流程

构建过程通常分为三个阶段：

1. **定义配置**：实例化 `AgentConfigPo` 并填充基础元数据（通常来自配置文件）。
2. **工厂封装**：调用 `ReActAgentFactory.create()` 获取构建包装器。
3. **动态调整与构建**：使用链式方法补充或覆盖组件（如 Model、Memory），最后调用 `build()`。

### 2.2 代码示例

```java
// 1. 准备基础配置 (模拟从数据库或 YAML 读取)
AgentConfigPo configPo = new AgentConfigPo();
configPo.setName("Assistant_01");
configPo.setDescription("A helpful assistant");
configPo.setSysPrompt("You are a helpful AI assistant.");

// 2. 使用工厂创建，并注入运行时组件 (如 Model 和 Memory)
ReActAgent agent = ReActAgentFactory.create(configPo)
        .model(myLlmModelInstance)      // 覆盖/注入 Model
        .memory(myMemoryInstance)       // 覆盖/注入 Memory
        .maxIters(15)                   // 动态调整最大迭代次数
        .build();                       // 3. 生成最终实例

```

### 2.3 约束与注意事项

* **必填项校验**：`name` 属性必须在调用 `build()` 之前设置，否则将抛出 `IllegalStateException`。
* **非空校验**：传入 `create()` 方法的 `AgentConfigPo` 不能为 `null`。
* **副本机制**：`AgentBuilderWrapper` 在初始化时会深拷贝配置对象的引用或值，后续的链式修改不会影响原始传入的 POJO 对象。

---

## 3. 配置对象详解 (AgentConfigPo)

`AgentConfigPo` 是一个纯数据类（POJO），用于封装创建 ReActAgent 所需的所有参数。

| 字段分类 | 字段名 | 类型 | 描述 |
| --- | --- | --- | --- |
| **元数据** | `name` | String | **(必填)** 智能体的唯一名称。 |
|  | `description` | String | 智能体的描述信息，用于多智能体协作时的自我介绍。 |
|  | `sysPrompt` | String | 系统提示词，定义人设和行为准则。 |
| **核心组件** | `model` | Model | LLM 模型实例。 |
|  | `toolkit` | Toolkit | 工具箱实例。 |
|  | `memory` | Memory | 记忆组件，用于存储对话历史。 |
| **执行控制** | `maxIters` | Integer | 最大思考/行动迭代次数 (null 则使用框架默认值)。 |
|  | `checkRunning` | Boolean | 是否检查并发运行状态 (默认为 true)。 |
|  | `modelExecutionConfig` | ExecutionConfig | 模型调用的超时、重试等配置。 |
|  | `toolExecutionConfig` | ExecutionConfig | 工具调用的超时、重试等配置。 |
| **高级功能** | `planNotebook` | PlanNotebook | 用于长程任务规划的计划本。 |
|  | `toolExecutionContext` | ToolExecutionContext | 工具执行上下文，用于传递业务数据。 |

---

## 4. API 参考

### 4.1 ReActAgentFactory

工厂入口类，提供静态方法用于初始化构建流程。

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `create(config)` | `AgentBuilderWrapper` | 基于传入的 `AgentConfigPo` 初始化构建器包装类。 |

### 4.2 AgentBuilderWrapper

内部构建器包装类，提供链式 API 以修改配置副本 (`effectiveConfig`)，并最终生成智能体。

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `model(model)` | `AgentBuilderWrapper` | 修改或设置模型 (Model)。 |
| `toolkit(toolkit)` | `AgentBuilderWrapper` | 修改或设置工具箱 (Toolkit)。 |
| `memory(memory)` | `AgentBuilderWrapper` | 修改或设置记忆组件 (Memory)。 |
| `maxIters(maxIters)` | `AgentBuilderWrapper` | 修改或设置最大迭代次数。 |
| `checkRunning(checkRunning)` | `AgentBuilderWrapper` | 修改或设置检查运行状态标志。 |
| `modelExecutionConfig(config)` | `AgentBuilderWrapper` | 修改或设置模型执行配置。 |
| `toolExecutionConfig(config)` | `AgentBuilderWrapper` | 修改或设置工具执行配置。 |
| `planNotebook(planNotebook)` | `AgentBuilderWrapper` | 修改或设置计划本。 |
| `toolExecutionContext(context)` | `AgentBuilderWrapper` | 修改或设置工具执行上下文。 |
| `build()` | `ReActAgent` | **终结操作**。根据最终配置构建并返回 Agent 实例。 |