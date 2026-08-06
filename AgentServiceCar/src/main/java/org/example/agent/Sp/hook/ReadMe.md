# Hook 业务层技术文档

> 本文档描述 AgentServiceCar 业务 Hook 的架构设计、开发规范与使用方式。
> 底层框架能力见 `AgentScopeYFMax/hooksManager/ReadMe.md`。

---

## 1. 架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        框架层 (AgentScopeYFMax)                      │
│                                                                     │
│  AgentHookToolkit (接口, 22 个默认方法)                               │
│       ↑                                                             │
│  AbstractAgentHook (骨架类, 12 个生命周期锚点)                         │
│       ↑                                                             │
│  SessionContext (会话级 DTO 容器)                                     │
│  HookListFactory (策略工厂, 内部委托 HookBuilder)                     │
│  HookBuilder (自由组装构建器)                                         │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ implements / extends
┌───────────────────────────────┴─────────────────────────────────────┐
│                        业务层 (AgentServiceCar)                      │
│                                                                     │
│  decisionMarkAgentHook/                                             │
│    ├── DecisionHook           extends AbstractAgentHook              │
│    ├── DynamicPickDishPromptHook  implements Hook + AgentHookToolkit │
│    ├── SPStateFeedbackHook    implements Hook + AgentHookToolkit     │
│    └── SPStateTracker         状态追踪器（无 Hook 依赖）               │
│                                                                     │
│  masterSPAgentHook/                                                 │
│    └── MasterGuardrailHook    extends AbstractAgentHook              │
└─────────────────────────────────────────────────────────────────────┘
```

### 两种 Hook 写法

| 写法 | 适用场景 | 示例 |
|---|---|---|
| `extends AbstractAgentHook` | 只需处理 1~3 个生命周期事件 | `DecisionHook`、`MasterGuardrailHook` |
| `implements Hook + AgentHookToolkit` | 需要自定义事件路由、或已有 `@Component` 注解 | `DynamicPickDishPromptHook`、`SPStateFeedbackHook` |

**选择建议**：优先用 `AbstractAgentHook`（代码更少），已有 `@Component` 的 Spring Bean 用 `implements` 方式。

---

## 2. AgentHookToolkit 工具方法速查

所有方法均为 `default`，`implements AgentHookToolkit` 即可直接调用。

### System Prompt 注入

```java
// 推理阶段注入（最常用）
injectSystemPrompt(event, "当前时间是 2026-04-29");

// 带标签包装（便于 LLM 识别来源）
injectSystemPrompt(event, timeText, "system_notification");
// → <system_notification>\n当前时间...\n</system_notification>

// 调用阶段注入（更早，适合全局上下文）
injectSystemPrompt(callEvent, "用户偏好：素食");
```

### 文本清洗（阅后即焚）

```java
// 一键清除 thinking/analysis 标签
cleanThinkingTags(event);

// 清除指定标签
stripTagContent(event, "menu_expert_rule");

// 清除中文括号（兜底）
cleanChineseParentheses(event);

// 自定义清洗函数
cleanResponseText(event, text -> text.replaceAll("内部代号\\w+", "[已脱敏]"));
```

### ToolResult 操作

```java
// 提取纯文本
String text = extractResultText(event.getToolResult());

// 追加系统提示（异常注入）
event.setToolResult(enrichToolResult(event.getToolResult(), "请尝试其他方式"));
```

### 校验与流程控制

```java
// 条件熔断
stopIf(event, content.contains("危险指令"), "检测到不安全内容");

// 引导重试
retryWith(event, "格式不对，请用 JSON 格式重试一次。");
```

### 模型参数调整

```java
// 单独调温度
adjustTemperature(event, 0.1);

// 单独调 Token
adjustMaxTokens(event, 2000);

// 组合调（null 表示不修改）
adjustModelParams(event, 0.3, 4000);
```

### 日志

```java
// 统一格式：>>> [Hook 阶段名] 智能体: xxx
logPhase(event, "推理前准备");
```

---

## 3. SessionContext —— 会话级 DTO 传递与持久化

### 问题

业务 Hook 需要访问会话级 DTO（如 `SupplyPlanModelEvent`），但 `HookListFactory` 的参数列表不宜无限膨胀。

### 方案

`SessionContext` 作为 DTO 容器，在 Hook 之间传递引用，支持通过 agentscope State API 持久化。

### 使用方式

```java
// === 1. 创建容器并注入 DTO ===
SessionContext sessionContext = new SessionContext();
sessionContext.add(supplyPlanModelEvent);
sessionContext.add(decisionMarkEvent);

// === 2. 构建 Hook 列表 ===
List<Hook> hooks = HookListFactory.buildHookList("sp", sessionContext);

// === 3. 会话结束时持久化各个 DTO ===
// DTO 必须 implements State 才能被持久化
sessionContext.saveAllTo(session, sessionKey);

// === 4. 下次会话按类型恢复 ===
SessionContext restoredContext = new SessionContext();
restoredContext.loadFrom(session, sessionKey, SupplyPlanModelEvent.class);
restoredContext.loadFrom(session, sessionKey, DecisionMarkEvent.class);
List<Hook> hooks2 = HookListFactory.buildHookList("sp", restoredContext);
```

### DTO 要求

```java
// DTO 需要实现 State 接口才能被 SessionContext 持久化
@Data
public class SupplyPlanModelEvent implements State {
    private String planId;
    private List<String> dishList;
    // ...
}
```

### API 一览

| 方法 | 说明 |
|---|---|
| `add(T dto)` | 注入 DTO（按类型存储，后者覆盖） |
| `get(Class<T> type)` | 按类型获取 |
| `getOrDefault(Class<T>, T)` | 获取或返回默认值 |
| `contains(Class<?>)` | 是否存在 |
| `getAll()` | 只读视图 |
| `saveAllTo(Session, SessionKey)` | 批量持久化（跳过未实现 State 的） |
| `loadFrom(Session, SessionKey, Class<T>)` | 按类型恢复 |

---

## 4. 各 Hook 说明

### DecisionHook

| 属性 | 值 |
|---|---|
| 继承 | `AbstractAgentHook` |
| 生命周期 | `PreReasoning` |
| 所需 DTO | `SupplyPlanModelEvent` |

**职责**：在推理前注入菜单管理工具的结构化报告。

```java
new DecisionHook(sessionContext);                          // 推荐：通过 SessionContext
new DecisionHook(supplyPlanModelEvent);                    // 兼容：直接传 DTO
```

### DynamicPickDishPromptHook

| 属性 | 值 |
|---|---|
| 实现 | `Hook` + `AgentHookToolkit` |
| 生命周期 | `PreActing` → `PostActing` → `PreReasoning` → `PostReasoning` |
| 所需 DTO | `SupplyPlanModelEvent`、`DecisionMarkEvent` |
| 线程安全 | `ThreadLocal<Mode>` + `ThreadLocal<String>` |

**职责**：根据工具调用动态切换 MENU / SEARCH 专家模式，注入对应规则，推理后清洗标签。

```java
new DynamicPickDishPromptHook(sessionContext);             // 推荐：通过 SessionContext
DynamicPickDishPromptHook.builder()                        // 兼容：手写 Builder
    .supplyPlanModelEvent(e1).decisionMarkEvent(e2).build();
```

### SPStateFeedbackHook

| 属性 | 值 |
|---|---|
| 实现 | `Hook` + `AgentHookToolkit` |
| Spring | `@Component("spStateFeedbackHook")` |
| 生命周期 | `PreCall` → `PostActing` → `PostReasoning` |
| 线程安全 | `ThreadLocal<SPStateTracker>` |

**职责**：监听工具执行结果，检测异常状态，注入干预提示或强制纠正。

### MasterGuardrailHook

| 属性 | 值 |
|---|---|
| 继承 | `AbstractAgentHook` |
| 生命周期 | `PreReasoning` → `PostReasoning` → `PostActing` |
| 所需 DTO | `SupplyPlanModelEvent`、`DecisionMarkEvent` |

**职责**：注入动态时间感知，清洗推理后的 thinking 标签。

```java
new MasterGuardrailHook(sessionContext);                   // 推荐：通过 SessionContext
new MasterGuardrailHook(supplyEvent, decisionEvent);       // 兼容：直接传 DTO
```

### SPStateTracker

| 属性 | 值 |
|---|---|
| 类型 | 纯 Java 类，无框架依赖 |
| 线程安全 | 由 `SPStateFeedbackHook` 的 `ThreadLocal` 保证 |

**职责**：维护一次供给计划执行过程中的结构化状态（步骤、工具状态、重试计数、干预建议）。

---

## 5. 开发新 Hook 的规范

### 5.1 选择基类

```
需要处理的生命周期事件 ≤ 3 个？
├── 是 → extends AbstractAgentHook（重写对应 handleXxx 方法）
└── 否 → implements Hook + AgentHookToolkit（自行路由 onEvent）
```

### 5.2 模板

**方式 A：继承 AbstractAgentHook**

```java
@Slf4j
public class MyHook extends AbstractAgentHook {

    private final SessionContext sessionContext;

    public MyHook(SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    @Override
    protected void handlePreReasoning(PreReasoningEvent event) {
        MyDto dto = sessionContext.get(MyDto.class);
        // 业务逻辑
        injectSystemPrompt(event, somePrompt);    // toolkit 默认方法
        logPhase(event, "已注入 xxx");             // toolkit 默认方法
    }

    @Override
    protected void handlePostReasoning(PostReasoningEvent event) {
        cleanThinkingTags(event);                  // toolkit 默认方法
    }
}
```

**方式 B：实现 Hook 接口**

```java
@Slf4j
public class MyHook implements Hook, AgentHookToolkit {

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent e) {
            logPhase(e, "推理前");
            injectSystemPrompt(e, somePrompt);
        } else if (event instanceof PostReasoningEvent e) {
            cleanThinkingTags(e);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() { return 5; }
}
```

### 5.3 线程安全

Hook 在多线程环境下运行（ReAct 循环、线程池复用），**可变状态必须用 `ThreadLocal`**：

```java
private final ThreadLocal<MyState> state = ThreadLocal.withInitial(MyState::new);

// 在最后一个生命周期阶段清理
@Override
protected void handlePostReasoning(PostReasoningEvent event) {
    try {
        // 业务逻辑...
    } finally {
        state.remove();   // 防止线程池复用时的状态污染
    }
}
```

### 5.4 响应式规范

- `onEvent` 必须返回 `Mono.just(event)`，**严禁返回 null**
- 异步 I/O 用 `subscribeOn(Schedulers.boundedElastic())`
- 优先级：`0` = 最高（错误捕获），`5` = 默认，`10` = 最低（审计日志）

---

## 6. Hook 组装方式

有两种组装 Hook 列表的方式，按需选择。

### 方式 1：HookListFactory（固定策略）

适合有固定组合的场景，通过策略标识一行调用。

```java
SessionContext sessionContext = new SessionContext();
sessionContext.add(supplyEvent);
sessionContext.add(decisionEvent);
List<Hook> hooks = HookListFactory.buildHookList("sp", sessionContext);
```

新增策略时在 `HookListFactory.buildHookList` 的 switch 中添加 case：

```java
case "myStrategy":
    return buildMyStrategyHooks(sessionContext);

// ...

private static List<Hook> buildMyStrategyHooks(SessionContext sessionContext) {
    return HookBuilder.create()
            .add(new MyHook(sessionContext))
            .add(new SPStateFeedbackHook())
            .add(new StudioMessageHook(StudioManager.getClient()))
            .build();
}
```

### 方式 2：HookBuilder（自由组装）

适合临时组合、实验性调试、或不想改工厂代码的场景。

**基础用法：**

```java
List<Hook> hooks = HookBuilder.create()
        .addDto(supplyEvent)                                  // 注入会话级 DTO
        .addDto(decisionEvent)
        .addFactory(sc -> new DecisionHook(sc))               // 工厂：需要 DTO，延迟创建
        .addFactory(sc -> new MasterGuardrailHook(sc))        // 工厂：需要 DTO，延迟创建
        .addFactory(sc -> new DynamicPickDishPromptHook(sc))  // 工厂：需要 DTO，延迟创建
        .add(new SPStateFeedbackHook())                       // 直接：不需要 DTO
        .add(new StudioMessageHook(StudioManager.getClient()))
        .addOptional(() -> tryGetBean("optionalBean"))        // 可选：null 自动跳过
        .build();
```

**追加模式（工厂结果 + 自定义 Hook）：**

```java
List<Hook> hooks = HookBuilder.from(HookListFactory.buildHookList("log"))
        .addDto(myDto)
        .addFactory(sc -> new MyCustomHook(sc))
        .build();
```

**获取 SessionContext 做持久化：**

```java
HookBuilder builder = HookBuilder.create()
        .addDto(supplyEvent)
        .add(sc -> new DecisionHook(sc));

List<Hook> hooks = builder.build();

// 会话结束后，从 builder 取出 sessionContext 做持久化
builder.getSessionContext().saveAllTo(session, sessionKey);
```

### 两种方式对比

| 维度 | HookListFactory | HookBuilder |
|---|---|---|
| 调用方式 | `buildHookList("sp", sessionContext)` | `create().addDto(...).add(...).build()` |
| 适用场景 | 固定策略、团队统一 | 临时组合、实验调试 |
| 新增策略 | 改工厂代码 + 加 case | 不改任何代码 |
| 类型安全 | 编译期（策略标识是字符串） | 编译期（Function 泛型） |
| Spring Bean | ✅ 支持（通过 applicationContext） | ❌ 需手动传入 |
| 可读性 | 简洁一行 | 链式清晰 |
