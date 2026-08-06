
---

#  MsgHub 框架技术文档

## 1. 概述

`MsgHub` 是一个基于 `AgentScope` 的多智能体协作框架扩展。该框架提供了一套统一的智能体池管理机制（`AgentPool`）和流式 Hub 构建工具（`HubBuilder`），并定义了标准化的多智能体协作模式（`HubPattern`）。通过 `HubEngine`，开发者可以在受控的上下文中执行复杂的对话逻辑，如辩论（Debate）或综合研讨（Synthesis）。

## 2. 核心组件架构

### 2.1 AgentPool (智能体池)

`AgentPool` 是框架的核心存储容器，负责统一管理 `AgentBase` 实例。它提供了静态工厂方法进行初始化，支持动态注册与注销智能体，并作为创建 `MsgHub` 的入口。

### 2.2 HubBuilder (Hub 构建器)

`HubBuilder` 采用 Fluent API 设计，允许开发者以链式调用的方式配置 `MsgHub`。它支持通过名称或实例加入参与者、预设公告消息（Announcement）以及控制自动广播（AutoBroadcast）行为。

### 2.3 HubPattern & HubEngine (模式与引擎)

* **HubPattern`<T>**`: 定义了多智能体协作的标准接口。任何协作逻辑（如辩论、投票、研讨）均需实现此接口的 `run` 方法。
* **HubEngine**: 无状态执行引擎，负责将特定的 `HubPattern` 应用于给定的 `AgentPool` 上，并返回执行结果。

## 3. 使用规范

### 3.1 初始化与管理

建议通过静态方法快速构建智能体池，随后通过链式调用进行动态管理。

```java
// 初始化
AgentPool pool = AgentPool.of(agentA, agentB);

// 动态注册与获取
pool.register(agentC)
    .remove("agentD"); // 支持链式调用

AgentBase agent = pool.get("agentA");

```

### 3.2 构建临时的 MsgHub

在执行具体任务时，应从 `AgentPool` 创建临时的 `MsgHub` 上下文。

```java
// 创建并构建 Hub
MsgHub hub = pool.hub("Topic-Discussion")
    .join("agentA", "agentB") // 通过名称加入
    .announce(startMsg)       // 发布公告
    .quiet()                  // 关闭自动广播（可选）
    .build();

```

### 3.3 执行协作模式

使用 `HubEngine` 执行预定义的协作模式，而非手动管理复杂的交互循环。

```java
// 构建辩论模式
DebatePattern debate = DebatePattern.build()
    .topic("AI Safety")
    .debaters("Proponent", "Opponent")
    .moderator("Judge")
    .maxRounds(3);

// 执行并获取结果
DebateResult result = HubEngine.execute(pool, debate);

```

## 4. 内置模式详解

### 4.1 DebatePattern (辩论模式)

* **描述**: 多名辩手循环发言，主持人（Moderator）在每一轮结束后进行裁决。
* **流程**:
1. 初始化：设置辩题、辩手、主持人和最大轮次。
2. 循环：每轮创建一个新的 `MsgHub` 上下文，辩手依次发言。
3. 裁决：每轮结束后，主持人根据上下文判断是否达成结论。
4. 结束：若达成结论或达到最大轮次，返回 `DebateResult`。



### 4.2 SynthesisPattern (综合模式)

* **描述**: 参与者进行多轮自由讨论，最后由指定的综合者（Synthesizer）进行总结。
* **流程**:
1. 自由讨论：所有参与者（含综合者）在 `MsgHub` 中交互指定轮次。
2. 总结：综合者根据历史消息生成最终摘要。



---

## 5. API 参考

### 5.1 AgentPool

提供智能体的生命周期管理和 Hub 构建入口。

#### 静态工厂方法

| 方法 | 描述 |
| --- | --- |
| `of(AgentBase... agents)` | 通过变长参数初始化池，并注册传入的智能体。 |
| `of(Map<String, AgentBase> agentsMap)` | 通过已有的 Map 初始化池。 |

#### 实例方法

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `register(AgentBase... agents)` | `AgentPool` | 注册一个或多个智能体，支持链式调用。 |
| `remove(String agentName)` | `AgentPool` | 根据名称移除智能体。 |
| `get(String name)` | `AgentBase` | 获取指定名称的智能体实例。 |
| `hub()` | `HubBuilder` | 创建一个具有随机 UUID 名称的 Hub 构建器。 |
| `hub(String name)` | `HubBuilder` | 创建指定名称的 Hub 构建器。 |

### 5.2 AgentPool.HubBuilder

用于配置和生成 `MsgHub` 实例的流式构建器。

| 方法 | 描述 |
| --- | --- |
| `join(String... agentNames)` | **核心方法**。通过名称从池中查找并添加参与者。若名称不存在抛出异常。 |
| `join(AgentBase... agents)` | 直接添加智能体实例作为参与者。 |
| `announce(Msg... msgs)` | 添加一条或多条公告消息。 |
| `announce(List<Msg> msgs)` | 从列表添加公告消息。 |
| `quiet()` | 关闭自动广播（默认为开启）。 |
| `build()` | 构建并返回 `MsgHub` 实例。若无参与者将抛出异常。 |

### 5.3 HubEngine

模式执行入口。

| 方法 | 描述 |
| --- | --- |
| `execute(AgentPool pool, HubPattern<T> pattern)` | 在指定的智能体池上运行模式，并返回类型为 `T` 的结果。 |

### 5.4 DebatePattern (Builder)

`DebatePattern` 采用流式配置方法。

| 方法 | 参数 | 描述 |
| --- | --- | --- |
| `build()` | - | **静态方法**。创建一个新的 DebatePattern 实例。 |
| `topic(String)` | `String` | 设置辩论主题。 |
| `debaters(String...)` | `String...` | 添加辩手名称列表。 |
| `moderator(String)` | `String` | 设置主持人（裁判）名称。 |
| `maxRounds(int)` | `int` | 设置最大辩论轮次（默认 5）。 |
| `setLogger(Consumer<String>)` | `Consumer` | 设置日志输出处理器（默认 `System.out::println`）。 |

### 5.5 SynthesisPattern (Builder)

`SynthesisPattern` 使用 Lombok `@Builder` 构建。

| 字段 | 类型 | 描述 |
| --- | --- | --- |
| `topic` | `String` | 讨论主题。 |
| `synthesizerName` | `String` | 负责总结的智能体名称。 |
| `contributors` | `List<String>` | 参与讨论的智能体名称列表。 |
| `discussionRounds` | `int` | 自由讨论的轮次数量。 |