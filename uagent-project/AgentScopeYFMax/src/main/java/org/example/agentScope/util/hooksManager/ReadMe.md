# HooksManager 模块技术文档

## 1. 模块概述

`HooksManager` 是基于 AgentScope 框架的生命周期管理模块，采用面向切面编程（AOP）思想与响应式编程（Reactive Programming）模式。该模块旨在不侵入核心业务逻辑的前提下，对智能体（Agent）的**调用（Call）**、**推理（Reasoning）**、**行动（Acting）**及**异常（Error）**四大阶段进行全链路监控、干预与增强。

此外，模块集成了 **Langfuse** 分布式追踪能力，支持对 Agent 运行过程的可观测性监控。

## 2. 核心组件架构

### 2.1 策略工厂 (`HookListFactory`)

通过工厂模式根据业务场景动态组装 Hook 列表。

* **组件路径**: `org.example.masfanplus.HooksManager.HookListFactory`
* **功能**: 提供预设的 Hook 组合策略。

| 策略标识 (hooksType) | 包含的 Hooks | 适用场景 |
| --- | --- | --- |
| **"Review"** | `PostReasoning` (x2), `PreActing`, `PostActing`, `PostCall` | 侧重于推理结果审计、动作拦截及最终结果修正。 |
| **"log"** | `PreCall`, `ReasoningChunk`, `ActingChunk` | 侧重于输入监控及流式输出（Token/Chunk）的实时日志。 |
| **"sp"** | `PreCall`, `ReasoningChunk`, `ActingChunk`, `spStateFeedbackHook`, `StudioMessage` | 供给计划决策链路。 |
| **"finance"** | `stateFeedbackHook`, `StudioMessage` | 财务分析链路。 |
| **"debug"** | `ErrorHook` | 仅捕获异常，用于调试与告警。 |

### 2.2 链路追踪 (`LangfuseSdkTracingHook`)

* **功能**: 实现 AgentScope 事件到 Langfuse Trace/Generation 的映射。
* **机制**:
* **Trace 上下文**: 使用 `traceIdMap` 维护 `AgentName -> TraceID` 映射。
* **Generation 上下文**: 使用 `generationIdMap` 维护 `AgentName -> GenerationID` 映射。
* **异步处理**: 利用 `subscribeOn(Schedulers.boundedElastic())` 确保网络请求不阻塞 Agent 主线程。



---

## 3. 开发与使用规范

在开发自定义 Hook 或修改现有 Hook 时，**必须**严格遵守以下规范以确保响应式流的稳定性。

### 3.1 响应式流规范

所有 `onEvent` 方法必须返回 `Mono<T>` 类型，且**严禁返回 `null**`。

* **正确示例**: `return Mono.just(event);`
* **异步操作**: 涉及 I/O 或网络请求时，应使用调度器切换线程。

### 3.2 事件类型检查

由于 `Hook` 接口的泛型设计，必须在逻辑入口处使用 `instanceof` 进行类型防御。

```java
if (event instanceof PreReasoningEvent) {
    PreReasoningEvent preReasonEvent = (PreReasoningEvent) event;
    // 业务逻辑...
}

```

### 3.3 优先级控制 (`priority`)

通过重写 `priority()` 方法控制执行顺序，数值越小优先级越高。

* **0**: 最高优先级（建议用于 `ErrorHook` 或核心追踪）。
* **5**: 默认优先级（普通业务逻辑）。
* **10**: 低优先级（建议用于审计、日志记录）。

---

## 4. API 方法参考

以下表格整理了在各个生命周期阶段（Event）中可用的核心操作方法。

### 4.1 全局控制与元数据 (HookEvent)

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `getAgent()` | `Agent` | 获取触发当前事件的 Agent 实例，可访问其 `name`、`id` 等属性。 |

### 4.2 推理阶段 API (Pre/Post/Chunk Reasoning)

**适用事件**: `PreReasoningEvent`, `PostReasoningEvent`, `ReasoningChunkEvent`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `getInputMessages()` | `List<Msg>` | (Pre) 获取即将发送给模型的历史消息上下文。 |
| `getEffectiveGenerateOptions()` | `GenerateOptions` | (Pre) 获取当前生效的模型生成参数（如 temperature, maxTokens）。 |
| `setGenerateOptions(opts)` | `void` | (Pre) **动态修改**本次推理的模型参数。 |
| `getReasoningMessage()` | `Msg` | (Post) 获取模型生成的完整原始消息（包含 Thought/Action）。 |
| `stopAgent()` | `void` | (Post) **熔断机制**，立即终止 Agent 的后续运行。 |
| `gotoReasoning(Msg hint)` | `void` | (Post) **自修正**，将 `hint` 提示词加入历史并强制模型重新推理。 |
| `getIncrementalChunk()` | `Msg` | (Chunk) 获取流式生成模式下的当前增量文本块。 |
| `getAccumulated()` | `Msg` | (Chunk) 获取流式生成模式下截至目前的累计文本。 |

### 4.3 行动阶段 API (Pre/Post/Chunk Acting)

**适用事件**: `PreActingEvent`, `PostActingEvent`, `ActingChunkEvent`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `getToolUse()` | `ToolUseBlock` | (Pre/Chunk) 获取工具调用的元数据（函数名 `getName()`，参数 `getInput()`）。 |
| `setToolUse(toolUse)` | `void` | (Pre) **重写**工具调用指令（如修正参数格式）。 |
| `getToolResult()` | `ToolResultBlock` | (Post) 获取工具执行后的返回结果。 |
| `setToolResult(result)` | `void` | (Post) **修改**工具的返回结果（如脱敏处理）。 |
| `getChunk()` | `ToolResultBlock` | (Chunk) 获取工具执行过程中的流式增量输出。 |

### 4.4 调用阶段 API (Pre/Post Call)

**适用事件**: `PreCallEvent`, `PostCallEvent`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `getInputMessages()` | `List<Msg>` | (Pre) 获取进入 Agent 的原始输入消息列表。 |
| `setInputMessages(msgs)` | `void` | (Pre) **修改**输入消息列表（如注入 System Prompt）。 |
| `getFinalMessage()` | `Msg` | (Post) 获取 Agent 执行结束后的最终回复消息。 |
| `setFinalMessage(msg)` | `void` | (Post) **修改**最终回复内容（如格式化、过滤敏感词）。 |

### 4.5 异常阶段 API (Error)

**适用事件**: `ErrorEvent`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `getError()` | `Throwable` | 获取捕获到的异常对象。 |

---

## 5. 规范示例代码

以下是一个标准的 Hook 实现示例，展示了类型检查、API 调用及响应式返回的标准写法。

```java
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.model.GenerateOptions;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;

@Component
public class StandardExampleHook implements Hook {

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 1. 类型安全检查
        if (event instanceof PreReasoningEvent) {
            PreReasoningEvent preEvent = (PreReasoningEvent) event;

            // 2. 获取上下文
            var agent = preEvent.getAgent();
            System.out.println("Hooks: Processing agent " + agent.getName());

            // 3. 调用 API 进行干预 (示例：动态调整 Temperature)
            GenerateOptions currentOpts = preEvent.getEffectiveGenerateOptions();
            if (currentOpts.getTemperature() > 0.7) {
                GenerateOptions safeOpts = GenerateOptions.builder()
                        .temperature(0.5) // 强制降温
                        .build();
                preEvent.setGenerateOptions(safeOpts);
            }
        }

        // 4. 必须返回 Mono.just 以维持链式调用
        return Mono.just(event);
    }

    @Override
    public int priority() {
        // 定义执行优先级
        return 5;
    }
}

```

---

## 6. SessionContext 与 HookBuilder

### 6.1 问题

业务 Hook 需要访问会话级 DTO（如 `SupplyPlanModelEvent`），但 `HookListFactory.buildHookList(String)` 参数列表不宜无限膨胀。

### 6.2 SessionContext

轻量级 DTO 容器，在 Hook 之间传递引用，支持通过 agentscope State API 持久化各个 DTO。

```java
public class SessionContext {
    <T> void add(T dto);                                           // 注入 DTO（按类型存储）
    <T> T get(Class<T> type);                                      // 按类型获取
    <T> T getOrDefault(Class<T> type, T defaultVal);               // 获取或返回默认值
    boolean contains(Class<?> type);                                // 是否存在
    Map<Class<?>, Object> getAll();                                 // 只读视图
    void saveAllTo(Session session, SessionKey sessionKey);        // 批量持久化
    void loadFrom(Session session, SessionKey sessionKey, Class<?>... types);  // 按类型恢复
}
```

**与 State 的关系**：`SessionContext` 本身不实现 `StateModule`，内部遍历各个 DTO，如果 DTO 实现了 `State` 则调用其 `saveTo()` / `loadFrom()`。

### 6.3 HookBuilder

跳过工厂，自由组装 Hook 列表的构建器。

```java
public class HookBuilder {
    static HookBuilder create();                                // 空 Builder
    static HookBuilder from(List<Hook> existing);               // 从已有列表追加

    <T> HookBuilder addDto(T dto);                              // 注入 DTO
    HookBuilder add(Hook hook);                                 // 直接添加实例
    HookBuilder addFactory(Function<SessionContext, Hook> fn);  // 工厂函数，延迟创建
    HookBuilder addOptional(Supplier<Hook> supplier);           // 可选，null 跳过

    List<Hook> build();                                         // 构建
    SessionContext getSessionContext();                          // 用于持久化
}
```

### 6.4 使用示例

```java
// 方式 1：工厂（固定策略）
SessionContext sessionContext = new SessionContext();
sessionContext.add(supplyPlanModelEvent);
sessionContext.add(decisionMarkEvent);
List<Hook> hooks = HookListFactory.buildHookList("sp", sessionContext);

// 方式 2：Builder（自由组装）
List<Hook> hooks = HookBuilder.create()
        .addDto(supplyPlanModelEvent)
        .addDto(decisionMarkEvent)
        .addFactory(sc -> new DecisionHook(sc))
        .addFactory(sc -> new MasterGuardrailHook(sc))
        .add(new SPStateFeedbackHook())
        .build();

// 持久化
sessionContext.saveAllTo(session, sessionKey);

// 恢复
SessionContext restoredContext = new SessionContext();
restoredContext.loadFrom(session, sessionKey, SupplyPlanModelEvent.class);
List<Hook> hooks2 = HookListFactory.buildHookList("sp", restoredContext);
```