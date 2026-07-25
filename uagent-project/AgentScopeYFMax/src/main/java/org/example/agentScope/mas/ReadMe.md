
---

# MAS-FanPlus 框架扩展技术文档

## 1. 概述

本项目基于 AgentScope 框架进行了深度扩展，提供了一套模块化的多智能体系统（MAS）构建方案。主要包含四大核心模块：

1. **ReActAgent 模块**：实现配置与构建逻辑解耦的智能体工厂。
2. **PipelineFactory 模块**：提供基于注册机制的流水线编排与快速执行能力。
3. **MsgHub 模块**：提供多智能体协作池管理及标准化协作模式（如辩论、研讨）。
4. **AgentHub 模块**：基于 Spring 与虚拟线程的高并发工具执行门面。

---

## 2. 功能模块详解

### 2.1 ReAct Agent 构建模块

该模块采用**配置驱动（Configuration Driven）**与**延迟构建（Lazy Build）**策略，旨在解耦配置数据与运行时对象。

#### 功能罗列

* **POJO 配置封装**：使用 `AgentConfigPo` 承载纯数据配置，便于从 YAML/JSON 或数据库加载。
* **工厂链式构建**：`ReActAgentFactory` 提供构建器包装类，支持在最终实例化前覆盖配置（如注入运行时对象）。
* **生命周期管理**：仅负责 Agent 实例的创建，不涉及 AgentPool 的管理。

#### 使用规范

1. **必填项**：`AgentConfigPo` 中的 `name` 属性为必填项，否则在 `build()` 时会抛出异常。
2. **对象复用**：工厂方法会创建配置对象的副本，对构建器的修改不会影响原始 POJO。
3. **非空校验**：传入工厂的配置对象不能为 `null`。

#### API 参考 (`ReActAgentFactory`)

| 方法 | 描述 |
| --- | --- |
| `create(AgentConfigPo config)` | 静态入口，返回 `AgentBuilderWrapper`。 |
| `model(Model model)` | 覆盖或设置 LLM 模型实例。 |
| `memory(Memory memory)` | 覆盖或设置记忆组件。 |
| `toolkit(Toolkit toolkit)` | 覆盖或设置工具箱。 |
| `build()` | **终结操作**，构建并返回 `ReActAgent` 实例。 |

#### 代码示例

```java
// 1. 定义基础配置
AgentConfigPo config = new AgentConfigPo();
config.setName("Assistant_Bot");
config.setSysPrompt("You are a helpful assistant.");

// 2. 注入运行时组件并构建
ReActAgent agent = ReActAgentFactory.create(config)
    .model(myLLM)          // 注入模型实例
    .maxIters(20)          // 动态调整参数
    .build();

```

---

### 2.2 Pipeline 编排模块

`PipelineFactory` 结合了注册表模式与工厂模式，用于快速编排顺序（Sequential）或扇出（Fanout）的执行流。

#### 功能罗列

* **智能体注册表**：内部维护 `HashMap<String, AgentBase>`，通过字符串 ID 引用智能体。
* **同步阻塞执行**：提供 `runSequential` 和 `runFanout` 系列方法，直接阻塞等待结果，简化异步流程。
* **管道组合**：支持将多个 `SequentialPipeline` 组合成新的管道。

#### 使用规范

1. **预注册原则**：执行任何管道操作前，必须先将 Agent 注册到工厂中。
2. **名称引用**：所有 API 均通过 String 类型的名称引用 Agent，若名称不存在将抛出 `IllegalArgumentException`。
3. **并发控制**：`createFanout` 方法通过布尔值参数控制并发（Concurrent）或顺序（Sequential）执行。

#### API 参考 (`PipelineFactory`)

| 方法 | 描述 |
| --- | --- |
| `register(String name, AgentBase agent)` | 注册智能体。 |
| `runSequential(Msg input, String... names)` | 按顺序执行一组智能体，返回最终 `Msg`。 |
| `runFanout(Msg input, String... names)` | 并发执行一组智能体，返回 `List<Msg>`。 |
| `createSequential(String... names)` | 创建顺序管道对象（不立即执行）。 |

#### 代码示例

```java
PipelineFactory factory = new PipelineFactory();
factory.register("planner", plannerAgent)
       .register("coder", coderAgent)
       .register("reviewer", reviewerAgent);

// 立即执行：规划 -> 编码
Msg codeResult = factory.runSequential(requirementMsg, "planner", "coder");

// 并发执行：多角色评审
List<Msg> reviews = factory.runFanout(codeResult, "reviewer", "security_check");

```

---

### 2.3 MsgHub 协作模块

该模块专注于多智能体环境下的上下文管理与复杂交互模式（Pattern）的封装。

#### 功能罗列

* **MsgAgentPool**：统一的智能体池管理，支持链式注册与获取。
* **HubBuilder**：Fluent API 用于构建临时的 `MsgHub` 上下文，支持自动广播与公告消息。
* **协作模式 (HubPattern)**：
* **DebatePattern**：多轮辩论，含主持人裁决机制。
* **SynthesisPattern**：自由讨论后由特定角色进行总结。


* **HubEngine**：无状态引擎，用于在 AgentPool 上执行特定的 Pattern。

#### 使用规范

1. **上下文隔离**：建议为每个独立的协作任务创建临时的 Hub，任务结束后自动释放资源。
2. **模式验证**：`DebatePattern` 要求至少 2 名辩手和 1 名主持人，否则无法启动。

#### API 参考 (`MsgAgentPool` & `HubEngine`)

| 类/接口 | 方法 | 描述 |
| --- | --- | --- |
| `MsgAgentPool` | `of(AgentBase... agents)` | 静态工厂，创建并初始化池。 |
| `MsgAgentPool` | `hub(String name)` | 获取 HubBuilder 以构建交互环境。 |
| `HubEngine` | `execute(pool, pattern)` | 在指定池上执行协作模式。 |
| `HubBuilder` | `join(String... names)` | 将池中指定名称的智能体加入 Hub。 |

#### 代码示例

```java
// 1. 初始化池
MsgAgentPool pool = MsgAgentPool.of(proponentAgent, opponentAgent, judgeAgent);

// 2. 构建辩论模式
DebatePattern debate = DebatePattern.build()
    .topic("AGI Safety")
    .debaters("proponent", "opponent")
    .moderator("judge")
    .maxRounds(5);

// 3. 执行
DebateResult result = HubEngine.execute(pool, debate);

```

---

### 2.4 AgentHub 工具执行模块

基于 Spring 容器的轻量级工具执行门面，利用 Java 21+ 虚拟线程实现高并发调用。

#### 功能罗列

* **Spring 集成**：`ToolDispatcher` 自动扫描并注册所有继承自 `BaseTool` 的 Bean。
* **虚拟线程调度**：`AgentRunner` 使用 `newVirtualThreadPerTaskExecutor` 并行处理工具调用，适合 I/O 密集型任务。
* **容错分发**：支持通过 `toolName` 精确查找，或从参数 `name` 字段进行降级查找。

#### 使用规范

1. **环境要求**：必须在 Spring Boot 环境下运行，且 JDK 版本需支持虚拟线程（JDK 21+）。
2. **工具定义**：自定义工具需继承 `BaseTool` 并添加 `@Component` 注解。
3. **命名唯一**：`getToolName()` 返回值必须全局唯一，重复会导致容器启动失败。
4. **初始化**：严禁在 Spring 容器完全启动前调用 `AgentHub.from()`。

#### API 参考 (`AgentHub`)

| 方法 | 描述 |
| --- | --- |
| `AgentHub.from(List<ToolUseBlock> blocks)` | 静态入口，创建 Runner 实例。 |
| `AgentRunner.execute()` | 并行执行所有工具块，聚合结果并返回 `Msg` (Role=TOOL)。 |

#### 代码示例

**定义工具：**

```java
@Component
public class WeatherTool extends BaseTool {
    @Override
    public String getToolName() { return "weather_query"; }
    
    @Override
    public String execute(Map<String, Object> input) {
        return "Weather info for " + input.get("location");
    }
}

```

**调用工具：**

```java
// 假设 response 包含 ToolUseBlock
if (response.getGenerateReason() == GenerateReason.TOOL_SUSPENDED) {
    Msg toolResult = AgentHub.from(response.getContentBlocks(ToolUseBlock.class))
                             .execute();
}

```

---

