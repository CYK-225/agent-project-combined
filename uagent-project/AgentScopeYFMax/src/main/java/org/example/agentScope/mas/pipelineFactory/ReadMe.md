
---

# PipelineFactory 技术文档

## 1. 概述

`PipelineFactory` 是 `org.example.masfanplus.PIpelineFactory` 包下的核心组件。该类采用 **注册表模式 (Registry Pattern)** 与 **工厂模式 (Factory Pattern)**，旨在简化 `AgentScope` 框架中智能体（Agent）的管理与管道（Pipeline）的构建过程。

通过内部维护一个 `HashMap<String, AgentBase>` 注册池，该类解耦了智能体的实例化与管道的组装逻辑，允许开发者通过字符串标识符（ID）快速编排顺序（Sequential）或扇出（Fanout）执行流。

## 2. 使用规范

在集成 `PipelineFactory` 时，请遵循以下开发规范：

### 2.1 注册机制

* **预注册原则**：在使用任何管道方法（创建或执行）之前，必须先将 `AgentBase` 实例注册到工厂中。
* **唯一标识**：`agentPool` 使用 `String` 类型的名称作为键（Key）。若重复注册相同的名称，后注册的实例将覆盖前值。

### 2.2 异常处理

* **空值检查**：所有涉及 `agentNames` 的方法均会进行非空校验。如果传入空数组或 `null`，将抛出 `IllegalArgumentException`。
* **解析失败**：如果在构建管道时引用了未注册的智能体名称，方法将抛出 `IllegalArgumentException`，并指明未找到的 Agent 名称。

### 2.3 执行模式

* **同步阻塞**：该类提供的“立即执行方法”（如 `runSequential`, `runFanout`）在内部调用了 AgentScope 的 `.block()` 方法。这意味着这些方法是**同步阻塞**的，会等待底层 `Mono` 任务完成后直接返回 `Msg` 或 `List<Msg>` 结果，而非响应式流。

---

## 3. API 参考手册

本节详细列出了 `PipelineFactory` 提供的公共方法。

### 3.1 注册与管理 (Registration & Management)

负责维护智能体实例池。

| 方法签名 | 返回类型 | 描述 |
| --- | --- | --- |
| `register(String name, AgentBase agent)` | `PipelineFactory` | 注册单个智能体，返回当前工厂实例以支持链式调用。 |
| `registerAll(HashMap<String, AgentBase> agents)` | `PipelineFactory` | 批量注册智能体，将传入 Map 中的所有键值对并入当前池。 |
| `getAgent(String name)` | `AgentBase` | 根据名称获取智能体实例。若不存在则抛出异常。 |
| `getAgents()` | `HashMap<String, AgentBase>` | 获取当前注册池中所有智能体的集合引用。 |

### 3.2 管道对象构建 (Pipeline Builder)

创建可复用的管道对象，**不立即执行**。适用于需要多次运行或进一步组合的场景。

| 方法签名 | 返回类型 | 描述 |
| --- | --- | --- |
| `createSequential(String... agentNames)` | `SequentialPipeline` | 根据名称列表创建一个顺序执行管道对象。 |
| `createFanout(boolean concurrent, String... agentNames)` | `FanoutPipeline` | 根据名称列表创建扇出管道对象。<br>

<br>`concurrent=true`：并发执行；<br>

<br>`concurrent=false`：顺序执行（作为 Fanout 结构）。 |

### 3.3 立即执行方法 (Immediate Execution Wrappers)

直接构建并运行管道，**阻塞等待结果**。适用于一次性任务。

**顺序执行 (Sequential)**

| 方法签名 | 返回类型 | 描述 |
| --- | --- | --- |
| `runSequential(Msg input, String... agentNames)` | `Msg` | 传入初始消息 `input`，按顺序执行指定的一组智能体。 |
| `runSequential(String... agentNames)` | `Msg` | 无初始输入（使用默认空消息），按顺序执行指定的一组智能体。 |
| `runSequential(Msg input, Class<T> outputClass, String... agentNames)` | `Msg` | 带结构化输出校验的顺序执行，尝试将结果解析为 `outputClass` 类型。 |

**扇出执行 (Fanout)**

| 方法签名 | 返回类型 | 描述 |
| --- | --- | --- |
| `runFanout(Msg input, String... agentNames)` | `List<Msg>` | 传入初始消息 `input`，**并发**执行指定的一组智能体，聚合所有结果。 |
| `runFanout(String... agentNames)` | `List<Msg>` | 无初始输入，**并发**执行指定的一组智能体。 |
| `runFanoutSequential(Msg input, String... agentNames)` | `List<Msg>` | 传入初始消息 `input`，**顺序**执行扇出结构（即所有 Agent 接收相同输入，但串行处理）。 |

### 3.4 管道组合 (Composition)

| 方法签名 | 返回类型 | 描述 |
| --- | --- | --- |
| `compose(SequentialPipeline p1, SequentialPipeline p2)` | `Pipeline<Msg>` | 将两个顺序管道连接组合成一个新的管道流。 |

---

## 4. 代码示例

以下示例展示了如何使用 `PipelineFactory` 进行注册、构建管道及执行任务。

```java
// 1. 初始化工厂
PipelineFactory factory = new PipelineFactory();

// 2. 注册智能体 (假设 userAgent 和 assistantAgent 已创建)
factory.register("user", userAgent)
       .register("assistant", assistantAgent)
       .register("reviewer", reviewerAgent);

// 3. 场景 A: 立即执行顺序对话 (User -> Assistant)
Msg startMsg = new Msg().setContent("Hello");
Msg result = factory.runSequential(startMsg, "user", "assistant");

// 4. 场景 B: 构建可复用的并发评审管道 (同时让 assistant 和 reviewer 处理)
FanoutPipeline reviewPipeline = factory.createFanout(true, "assistant", "reviewer");

// 执行管道
Pipeline<List<Msg>> pipeline = reviewPipeline; // 向上转型示例
List<Msg> reviews = reviewPipeline.act(startMsg).block();

```

---

## 4. 进阶场景代码示例

### 场景背景：自动化代码优化工作流

我们模拟一个包含四个智能体角色的系统：

1. **`architect` (架构师)**：分析需求，拆解任务。
2. **`coder_py` (Python专家)**：负责编写 Python 实现。
3. **`coder_java` (Java专家)**：负责编写 Java 实现。
4. **`reviewer` (评审员)**：评估代码并选出最佳方案。

### 4.1 初始化与角色注册

首先，实例化工厂并注册具备不同能力的智能体。

```java
public class WorkflowDemo {
    public static void main(String[] args) {
        // 1. 创建工厂实例
        PipelineFactory factory = new PipelineFactory();

        // 2. 模拟智能体注册 (实际场景中 AgentBase 通常由 Spring 注入或配置加载)
        // 假设这些 Agent 已经根据各自的 Prompt 初始化完成
        factory.register("architect", new ArchitectAgent())
               .register("coder_py", new PythonCoderAgent())
               .register("coder_java", new JavaCoderAgent())
               .register("reviewer", new CodeReviewerAgent());

        System.out.println(">>> 智能体池初始化完成，准备执行任务...");
        
        // 执行复杂逻辑...
        executeIntelligentWorkflow(factory);
    }
}

```

### 4.2 智能工作流编排

本示例展示了如何混合使用**顺序执行**进行分析，**并发执行**进行多版本生成，最后人工干预或逻辑聚合数据。

```java
    private static void executeIntelligentWorkflow(PipelineFactory factory) {
        // 定义初始需求
        Msg requirement = new Msg();
        requirement.setContent("实现一个高性能的斐波那契数列计算服务");

        // ---------------------------------------------------------
        // 阶段一：架构分析 (Sequential - 链式思维)
        // ---------------------------------------------------------
        // 逻辑：输入需求 -> 架构师分析 -> 生成技术规范
        // 使用 runSequential 立即获取分析结果
        System.out.println(">>> 阶段一：架构师分析中...");
        Msg techSpec = factory.runSequential(requirement, "architect");
        
        System.out.println("技术规范已生成: " + techSpec.getContent());

        // ---------------------------------------------------------
        // 阶段二：多语言并发实现 (Fanout - 竞争/并行模式)
        // ---------------------------------------------------------
        // 逻辑：基于同一份技术规范，让 Python 和 Java 专家同时编写代码
        // concurrent = true 开启并发，提高效率
        System.out.println(">>> 阶段二：多语言专家并发编码中...");
        List<Msg> codeProposals = factory.runFanout(techSpec, "coder_py", "coder_java");

        // 简单处理：将并发结果合并为一条消息供评审使用
        Msg combinedProposals = aggregateProposals(codeProposals);

        // ---------------------------------------------------------
        // 阶段三：最终评审 (Sequential - 决策环节)
        // ---------------------------------------------------------
        // 逻辑：合并的代码方案 -> 评审员打分选优
        System.out.println(">>> 阶段三：评审员进行最终评估...");
        Msg finalDecision = factory.runSequential(combinedProposals, "reviewer");

        System.out.println(">>> 最终优选方案: \n" + finalDecision.getContent());
    }

    /**
     * 辅助方法：将 Fanout 返回的列表合并为单条 Msg 供后续 Sequential 节点使用
     */
    private static Msg aggregateProposals(List<Msg> proposals) {
        StringBuilder sb = new StringBuilder("以下是各专家提交的代码方案：\n");
        for (Msg msg : proposals) {
            sb.append("--- 方案 ---\n").append(msg.getContent()).append("\n");
        }
        Msg aggMsg = new Msg();
        aggMsg.setContent(sb.toString());
        return aggMsg;
    }

```

### 4.3 管道复用与组合 (Builder 模式)

此示例展示如何利用 `create` 和 `compose` 方法预定义标准化的流水线（Standard Operating Procedure, SOP），以便在系统中多次复用。

```java
    private static void buildReusablePipeline(PipelineFactory factory) {
        // 定义子管道 A：需求预处理 (用户输入 -> 架构师优化)
        SequentialPipeline analysisPipe = factory.createSequential("architect");

        // 定义子管道 B：Java 开发流程 (Java编码 -> 评审)
        // 注意：这里我们构建了一个特定的顺序流
        SequentialPipeline javaDevPipe = factory.createSequential("coder_java", "reviewer");

        // 组合管道：将 A 和 B 串联
        // 效果：用户输入 -> [架构师] -> (输出作为输入) -> [Java编码] -> [评审]
        Pipeline<Msg> fullPipeline = factory.compose(analysisPipe, javaDevPipe);

        // 此时管道尚未执行，可以被存储或传递给 Web Controller
        // ... 
        
        // 实际调用
        Msg task = new Msg().setContent("重构登录模块");
        Msg result = fullPipeline.act(task).block(); // 手动触发执行
    }

```

## 5. 关键设计模式解析

上述代码体现了以下设计思想，通过 `PipelineFactory` 这里的 API 能够轻松实现：

1. **Map-Reduce 模式**：
* 在阶段二使用了 `runFanout` (Map/Scatter)，将任务分发给多个 Agent。
* 在 `aggregateProposals` 进行了简单的 (Reduce/Gather)，将结果聚合。


2. **解耦编排**：
* 业务逻辑（Main方法）不知道 Agent 的具体实现类，只通过 String ID (`coder_py`) 引用。
* 如果需要替换 Python 专家为 C++ 专家，只需在 `register` 处修改一行代码，无需改动核心业务流。


3. **同步/异步灵活切换**：
* 对于依赖上一步结果的（如架构分析），使用 `Sequential`。
* 对于互不依赖的耗时任务（如多语言编码），使用 `Fanout Concurrent` 以减少总耗时。