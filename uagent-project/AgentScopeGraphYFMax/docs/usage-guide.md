# AgentScopeGraphYFMax 功能模块使用文档

本文档按 11 个功能领域详细说明框架的使用方法。所有 API 签名已通过 `javap` 对上游 1.1.2.2 jar 验证。

---

## 模块总览

```
┌─────────────────────────────────────────────────────────┐
│                      图执行 (GraphEngine)                │
│          invoke / stream / time-travel / updateState     │
├─────────────────────────────────────────────────────────┤
│                   图组装 (AbstractGraphTemplate)          │
│         compileGraph() / buildStateGraph() 模板方法编排   │
├──────────┬──────────┬───────────┬────────────────────────┤
│ 图创建    │ 节点创建  │ 边创建     │ 子图操作               │
│StateGraph│SimpleNode│SimpleEdge │AbstractGraphTemplate   │
│GraphBuild│Action    │Action     │.buildStateGraph()      │
│er        │(Lambda)  │(Lambda)   │addSubgraphNode()       │
│          │@NodeAct. │@EdgeCond. │                        │
│          │Pool引用   │Pool引用    │                        │
├──────────┴──────────┴───────────┴────────────────────────┤
│         并行操作 (GraphBuilder.addParallelBranches)       │
│         fanout -> [branches] -> merge                    │
├─────────────────────────────────────────────────────────┤
│  组件池管理                    │  持久化执行               │
│  NodeActionPool               │  CheckpointFactory       │
│  EdgeConditionPool            │  MyBatisFlexSaver        │
├───────────────────────────────┼─────────────────────────┤
│  人类反馈         │  时光旅行                              │
│  interruptBefore  │  listCheckpoints / branchFromCP      │
│  updateState()    │  updateAndResume                     │
└───────────────────┴──────────────────────────────────────┘
```

---

## 1. 图创建

**涉及文件：** `core/AbstractGraphTemplate.java`

图创建发生在 `AbstractGraphTemplate.compileGraph()` 内部，由框架自动完成：

```java
// 框架内部流程（开发者无需直接调用）
KeyStrategyFactory keyStrategyFactory = setupStateKeyFactory();
StateGraph stateGraph = new StateGraph(name, keyStrategyFactory);
GraphBuilder builder = new GraphBuilder(stateGraph, checkpointSaver);
buildGraph(builder);  // 委托子类定义拓扑
CompiledGraph compiledGraph = stateGraph.compile(compileConfigBuilder.build());
```

**开发者需要做的：** 继承 `AbstractGraphTemplate`，实现 `buildGraph()` 和 `initialState()`：

```java
@GraphDefinition(name = "my-graph", group = "demo")
public class MyGraph extends AbstractGraphTemplate {

    // 1. 注入组件门面
    public MyGraph(GraphComponentFacade components) {
        super(components);
    }

    // 2. 定义初始状态
    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    // 3. 构建图拓扑（此方法内完成节点和边的定义）
    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 在此添加节点和边
    }
}
```

**可选重写点：**

| 方法 | 用途 | 默认行为 |
|------|------|----------|
| `setupStateKeyFactory()` | 自定义键策略 | 所有键使用 ReplaceStrategy |
| `setupCheckpointSaver(def)` | 自定义检查点 | 从注解读取 checkpointStrategy |
| `setupCompileConfig(def)` | 自定义编译配置 | 应用注解的递归限制和中断配置 |
| `afterGraphCompiled(graph)` | 编译后钩子 | 空操作 |
| `init()` | 构造后钩子 | 空操作 |

### 1.2 图拓扑写法全集

`buildGraph()` 中有 5 种写法，可自由组合。`GraphBuilder` 支持直接调用 `addNode()` 和 `addEdge()`。

**写法 A：连续链式（推荐）**

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("a", actionA)
           .addEdge("b")              // a -> b
           .addNode("b", actionB)     // 注册 b，返回 GraphBuilder
           .addEdge("c")              // b -> c
           .addNode("c", actionC)     // 注册 c
           .addEdge(StateGraph.END);  // c -> END
}
```

**写法 B：一个节点连多条边**

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("router", routerAction)
           .addEdge("targetA")        // router -> targetA
           .addEdge("targetB")        // router -> targetB
           .addEdge("targetC");       // router -> targetC

    builder.addNode("targetA", actionA).addEdge(StateGraph.END);
    builder.addNode("targetB", actionB).addEdge(StateGraph.END);
    builder.addNode("targetC", actionC).addEdge(StateGraph.END);
}
```

**写法 C：批量注册节点 + 批量注册边**

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 1. 批量注册节点
    builder.addNode("draft", draftAction);
    builder.addNode("review", reviewAction);
    builder.addNode("approve", approveAction);
    builder.addNode("reject", rejectAction);

    // 2. 批量注册边
    builder.addEdge("draft", "review");
    builder.addEdge("approve", StateGraph.END);
    builder.addEdge("reject", StateGraph.END);

    // 3. 条件边单独处理
    builder.addConditionalEdges("review", routerAction)
           .route("approved", "approve")
           .route("rejected", "reject")
           .done();
}
```

**写法 D：通过组件池引用（推荐用于大型项目）**

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 通过 NodeActionPool 按名称引用独立节点类
    builder.addNode("validate", components.nodeActions())   // @NodeAction(value="validate")
            .addEdge("process")
            .addNode("process", components.nodeActions())   // @NodeAction(value="process")
            .addEdge("output")
            .addNode("output", components.nodeActions())    // @NodeAction(value="output")
            .addEdge(StateGraph.END);
}
```

**写法 E：混合使用**

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // Lambda 节点
    builder.addNode("input", state -> {
                Map<String, Object> update = new HashMap<>();
                update.put("processed", true);
                return CompletableFuture.completedFuture(update);
            })
            .addEdge("validate");

    // 池引用节点 + 池引用边
    builder.addNode("validate", components.nodeActions())
            .addConditionalEdges("validation-router", components.edgeConditions())
            .route("pass", "output")
            .route("fail", "error")
            .done();

    builder.addNode("output", components.nodeActions())
            .addEdge(StateGraph.END);
    builder.addNode("error", components.nodeActions())
            .addEdge(StateGraph.END);
}
```

### GraphBuilder 支持的方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `addNode(name, AsyncNodeAction)` | `GraphBuilder` | 添加节点 |
| `addNode(name, NodeActionPool)` | `GraphBuilder` | 从池获取节点实例并添加 |
| `addEdge(target)` | `GraphBuilder` | 隐式源节点（lastNodeName） |
| `addEdge(source, target)` | `GraphBuilder` | 显式指定源和目标 |
| `addConditionalEdges(action)` | `ConditionalEdgeBuilder` | 隐式源节点的条件边 |
| `addConditionalEdges(source, action)` | `ConditionalEdgeBuilder` | 显式源节点的条件边 |
| `addConditionalEdges(name, EdgeConditionPool)` | `ConditionalEdgeBuilder` | 从池获取边实例的条件边 |
| `addSubgraphNode(name, StateGraph)` | `GraphBuilder` | 添加 StateGraph 子图 |
| `addSubgraphNode(name, CompiledGraph)` | `GraphBuilder` | 添加 CompiledGraph 子图 |
| `addParallelBranches(fanout, branches, merge, action)` | `GraphBuilder` | 并行扇出拓扑 |

---

## 2. 节点创建

**涉及文件：** `builder/GraphBuilder.java`、`core/NodeActionPool.java`

### 2.1 Lambda 方式（最简洁，适合一次性逻辑）

```java
// 在 buildGraph() 中使用
builder.addNode("节点名", state -> {
    // state 是 OverAllState（单参数，非 state + config）
    String input = (String) state.value("input").orElse("");
    Map<String, Object> update = new HashMap<>();
    update.put("output", "处理: " + input);
    // 返回 CompletableFuture<Map<String, Object>>
    return CompletableFuture.completedFuture(update);
});
```

**关键点：**
- `addNode(String, AsyncNodeAction)` 返回 `GraphBuilder`
- `AsyncNodeAction` 签名：`CompletableFuture<Map<String, Object>> apply(OverAllState)` -- **单参数**
- 此方法 `throws GraphStateException`

### 2.2 SimpleNodeAction 抽象类（推荐用于可复用节点）

继承 `SimpleNodeAction`，只需实现 `execute(OverAllState)` 方法，返回 `Map<String, Object>` 即可，无需处理 `CompletableFuture`。

```java
/**
 * 分析节点 -- 独立类实现
 * 子类只需实现 execute()，框架自动包装为 CompletableFuture
 */
public class AnalyzeNodeAction extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("analysis", "已分析: " + input);
    }
}

// 在 buildGraph() 中使用
builder.addNode("analyze", new AnalyzeNodeAction());
```

**SimpleNodeAction 源码：**

```java
public abstract class SimpleNodeAction implements AsyncNodeAction {

    @Override
    public final CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        try {
            return CompletableFuture.completedFuture(execute(state));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // 子类实现此方法
    protected abstract Map<String, Object> execute(OverAllState state) throws Exception;
}
```

### 2.3 @NodeAction 注解 + 池引用（推荐用于大型项目）

将节点定义为独立类，通过 `@NodeAction` 注解注册到 `NodeActionPool`，在图定义中按名称引用：

```java
// 定义独立节点类
@NodeAction(value = "validate", description = "输入验证节点")
public class ValidateNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.isBlank()) throw new IllegalArgumentException("输入不能为空");
        return Map.of("validated", true, "cleanInput", input.trim());
    }
}

// 在 buildGraph() 中通过池按名称引用
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("validate", components.nodeActions())  // 从 NodeActionPool 获取
            .addEdge(StateGraph.END);
}
```

**@NodeAction 注解属性：**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | 类简单名 | 节点唯一名称（池中的 key） |
| `description` | String | "" | 描述 |
| `scope` | String | "prototype" | "prototype"（每次新建）/ "singleton"（缓存） |

**NodeActionPool API：**

```java
// 获取节点实例
AsyncNodeAction action = components.nodeActions().get("validate");

// 检查是否存在
boolean exists = components.nodeActions().exists("validate");

// 列出所有已注册的节点（名称 -> 描述）
Map<String, String> all = components.nodeActions().listAll();
```

### 2.4 直接实现 AsyncNodeAction 接口（异步场景）

```java
/**
 * 异步分析节点
 * AsyncNodeAction 签名: CompletableFuture<Map<String, Object>> apply(OverAllState)
 */
public class AsyncAnalyzeNode implements AsyncNodeAction {

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String input = (String) state.value("input").orElse("");
            return Map.of("analysis", "异步分析: " + input);
        });
    }
}

// 使用
builder.addNode("analyze", new AsyncAnalyzeNode());
```

### 节点创建方式对比

| 方式 | 基类/接口 | 返回类型 | 适用场景 |
|------|-----------|---------|---------|
| `SimpleNodeAction` 类 | 继承抽象类 | `Map<String, Object>` | **推荐**，简洁直接 |
| `@NodeAction` + 池引用 | `SimpleNodeAction` + 注解 | `Map<String, Object>` | **大型项目**，独立复用、按名称引用 |
| Lambda | `AsyncNodeAction` | `CompletableFuture<Map<String, Object>>` | 一次性简单逻辑 |
| `AsyncNodeAction` 接口 | 实现接口 | `CompletableFuture<Map<String, Object>>` | 需要异步/并行逻辑 |

---

## 3. 边创建

**涉及文件：** `builder/GraphBuilder.java`、`builder/ConditionalEdgeBuilder.java`、`core/EdgeConditionPool.java`

### 3.1 无条件边

```java
builder.addNode("a", actionA)
       .addEdge("b")           // a -> b（隐式源节点）
       .addNode("b", actionB)
       .addEdge(StateGraph.END); // b -> END

// 或使用 GraphBuilder 直接添加
builder.addEdge("a", "b");    // a -> b（显式指定）
```

### 3.2 Lambda 条件边

```java
builder.addNode("router", routerAction)
       // 添加条件边路由（lambda）
       .addConditionalEdges(state ->
               (String) state.value("type").orElse("default"))
       // 映射路由结果 -> 目标节点
       .route("question", "answerNode")
       .route("statement", "ackNode")
       .route("default", "fallbackNode")
       .done()                 // 完成条件边，返回 GraphBuilder
       .addNode("answerNode", answerAction)
       .addEdge(StateGraph.END)
       .addNode("ackNode", ackAction)
       .addEdge(StateGraph.END);
```

### 3.3 SimpleEdgeAction 抽象类（推荐用于可复用边）

继承 `SimpleEdgeAction`，只需实现 `execute(OverAllState)` 方法，返回目标节点名称即可。

```java
/**
 * 类型路由边 -- 独立类实现
 * 子类只需实现 execute()，框架自动包装为 CompletableFuture
 */
public class TypeRouterEdge extends SimpleEdgeAction {

    @Override
    protected String execute(OverAllState state) throws Exception {
        String type = (String) state.value("type").orElse("default");
        return switch (type) {
            case "question" -> "answerNode";
            case "command"  -> "executeNode";
            default         -> "fallbackNode";
        };
    }
}

// 使用
builder.addNode("router", routerAction)
       .addConditionalEdges(new TypeRouterEdge())
       .route("answerNode", "answerNode")
       .route("executeNode", "executeNode")
       .route("fallbackNode", "fallbackNode")
       .done();
```

**SimpleEdgeAction 源码：**

```java
public abstract class SimpleEdgeAction implements AsyncEdgeAction {

    @Override
    public final CompletableFuture<String> apply(OverAllState state) {
        try {
            return CompletableFuture.completedFuture(execute(state));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // 子类实现此方法
    protected abstract String execute(OverAllState state) throws Exception;
}
```

### 3.4 @EdgeCondition 注解 + 池引用（推荐用于大型项目）

将边路由定义为独立类，通过 `@EdgeCondition` 注解注册到 `EdgeConditionPool`，在图定义中按名称引用：

```java
// 定义独立边类
@EdgeCondition(value = "type-router", description = "输入类型路由：根据输入特征路由到不同处理器")
public class TypeRouterEdge extends SimpleEdgeAction {

    @Override
    protected String execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.contains("?")) {
            return "question-handler";
        } else if (input.startsWith("!")) {
            return "command-handler";
        } else {
            return "default-handler";
        }
    }
}

// 在 buildGraph() 中通过池按名称引用
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("dispatcher", dispatchAction)
            .addConditionalEdges("type-router", components.edgeConditions())
            .route("question-handler", "question-handler")
            .route("command-handler", "command-handler")
            .route("default-handler", "default-handler")
            .done();
}
```

**@EdgeCondition 注解属性：**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | 类简单名 | 边唯一名称（池中的 key） |
| `description` | String | "" | 描述 |
| `scope` | String | "prototype" | "prototype"（每次新建）/ "singleton"（缓存） |

**EdgeConditionPool API：**

```java
// 获取边实例
AsyncEdgeAction action = components.edgeConditions().get("type-router");

// 检查是否存在
boolean exists = components.edgeConditions().exists("type-router");

// 列出所有已注册的边（名称 -> 描述）
Map<String, String> all = components.edgeConditions().listAll();
```

### 3.5 直接实现 AsyncEdgeAction 接口（异步场景）

```java
public class AsyncTypeRouter implements AsyncEdgeAction {

    @Override
    public CompletableFuture<String> apply(OverAllState state) {
        String type = (String) state.value("type").orElse("default");
        return CompletableFuture.completedFuture(
                "question".equals(type) ? "answerNode" : "fallbackNode");
    }
}
```

### 边创建方式对比

| 方式 | 基类/接口 | 返回类型 | 适用场景 |
|------|-----------|---------|---------|
| `SimpleEdgeAction` 类 | 继承抽象类 | `String` | **推荐**，简洁直接 |
| `@EdgeCondition` + 池引用 | `SimpleEdgeAction` + 注解 | `String` | **大型项目**，独立复用、按名称引用 |
| Lambda | `AsyncEdgeAction` | `CompletableFuture<String>` | 一次性简单逻辑 |
| `AsyncEdgeAction` 接口 | 实现接口 | `CompletableFuture<String>` | 需要异步逻辑 |

**关键点：**
- `AsyncEdgeAction` 签名：`CompletableFuture<String> apply(OverAllState)` -- **单参数**
- `ConditionalEdgeBuilder.route(String resultValue, String targetNode)` 逐一映射
- `ConditionalEdgeBuilder.done()` 完成配置并返回 `GraphBuilder`

---

## 4. 子图操作

**涉及文件：** `core/AbstractGraphTemplate.java`、`builder/GraphBuilder.java`

子图通过 `@GraphDefinition` 注解的类 + 继承 `AbstractGraphTemplate` 来定义。有三种使用方式：

### 4.1 方式 A：StateGraph 子图（父图编译）

子图构建为 `StateGraph`，由父图统一编译。子图不独立持有检查点。

```java
// 1. 定义子图类
@GraphDefinition(name = "research-sub")
public class ResearchSubgraph extends AbstractGraphTemplate {

    public ResearchSubgraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() { return new OverAllState(); }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addNode("search", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("raw", "搜索: " + state.value("query").orElse(""));
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge("synthesize")
                .addNode("synthesize", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("result", "综合: " + state.value("raw").orElse(""));
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }
}

// 2. 父图中使用子图（StateGraph 方式）
@GraphDefinition(name = "parent-graph")
public class ParentGraph extends AbstractGraphTemplate {

    public ParentGraph(GraphComponentFacade components) { super(components); }

    @Override
    protected OverAllState initialState() { return new OverAllState(); }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 构建子图的 StateGraph（不编译）
        StateGraph sub = new ResearchSubgraph(components).buildStateGraph();

        builder.addNode("preprocess", preprocessAction)
                .addEdge("research")
                .addSubgraphNode("research", sub)   // StateGraph 子图
                .addEdge("postprocess")
                .addNode("postprocess", postprocessAction)
                .addEdge(StateGraph.END);
    }
}
```

### 4.2 方式 B：CompiledGraph 子图（独立编译）

子图通过 `GraphPoolManager` 获取已编译的 `CompiledGraph`，独立于父图运行。

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 从图池获取已编译的子图
    CompiledGraph sub = components.pool().getGraph("research-sub");

    builder.addNode("preprocess", preprocessAction)
            .addEdge("research")
            .addSubgraphNode("research", sub)   // CompiledGraph 子图
            .addEdge("postprocess")
            .addNode("postprocess", postprocessAction)
            .addEdge(StateGraph.END);
}
```

### 4.3 方式 C：StateGraph -> CompiledGraph 转换

先构建 `StateGraph`，再手动编译为 `CompiledGraph`，获得独立编译的灵活性。

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 构建 StateGraph 后手动编译
    StateGraph subStateGraph = new ResearchSubgraph(components).buildStateGraph();
    CompiledGraph sub = subStateGraph.compile(CompileConfig.builder().build());

    builder.addNode("preprocess", preprocessAction)
            .addEdge("research")
            .addSubgraphNode("research", sub)   // CompiledGraph 子图
            .addEdge("postprocess")
            .addNode("postprocess", postprocessAction)
            .addEdge(StateGraph.END);
}
```

### AbstractGraphTemplate 子图相关 API

```java
// 构建 StateGraph（不编译）-- 接收显式 GraphDefinition
public final StateGraph buildStateGraph(GraphDefinition definition) throws GraphStateException;

// 构建 StateGraph（不编译）-- 使用类上的 @GraphDefinition 注解
public final StateGraph buildStateGraph() throws GraphStateException;

// 编译图 -- 返回 CompiledGraph
public final CompiledGraph compileGraph(GraphDefinition definition) throws GraphStateException;
public final CompiledGraph compileGraph() throws GraphStateException;
```

### 子图方式对比

| 方式 | 输入类型 | 编译时机 | 适用场景 |
|------|---------|---------|---------|
| 方式 A：StateGraph 子图 | `StateGraph` | 由父图统一编译 | 子图与父图共享检查点 |
| 方式 B：CompiledGraph 子图 | `CompiledGraph` | 已在图池中编译 | 复用已注册的独立图 |
| 方式 C：手动编译 | `CompiledGraph` | 手动 `compile()` | 需要自定义编译配置 |

**关键点：**
- 子图定义为 `@GraphDefinition` 类，继承 `AbstractGraphTemplate`
- `buildStateGraph()` 返回未编译的 `StateGraph`
- `compileGraph()` 返回已编译的 `CompiledGraph`
- 父图通过 `addSubgraphNode(name, StateGraph)` 或 `addSubgraphNode(name, CompiledGraph)` 引入子图

---

## 5. 并行操作

**涉及文件：** `builder/GraphBuilder.java`

### 5.1 创建并行拓扑

使用 `GraphBuilder.addParallelBranches()` 一行代码构建扇出并行拓扑：

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addParallelBranches("fanout",
            Map.of(
                "financial", (AsyncNodeAction) state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("fin", "财务分析");
                    return CompletableFuture.completedFuture(update);
                },
                "technical", (AsyncNodeAction) state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("tech", "技术分析");
                    return CompletableFuture.completedFuture(update);
                },
                "market", (AsyncNodeAction) state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("mkt", "市场分析");
                    return CompletableFuture.completedFuture(update);
                }
            ),
            "synthesizer",
            (AsyncNodeAction) state -> {
                Map<String, Object> update = new HashMap<>();
                update.put("report", state.value("fin") + " | " +
                                     state.value("tech") + " | " + state.value("mkt"));
                return CompletableFuture.completedFuture(update);
            }
    ).addEdge(StateGraph.END);
}
```

**生成的拓扑：**
```
fanout -> financial -> synthesizer -> END
fanout -> technical -> synthesizer
fanout -> market    -> synthesizer
```

### 5.2 配置并行执行

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true    # 必须启用，用于并行执行

graph:
  workflow:
    engine:
      parallel-executor-enabled: true   # 使用虚拟线程，无需配置线程池大小
```

> **虚拟线程：** 并行执行使用 Java 21 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`），无需配置线程池大小。需在 `application.yml` 中启用 `spring.threads.virtual.enabled: true`。

**addParallelBranches API：**

```java
public class GraphBuilder {
    /**
     * 添加并行扇出分支
     * @param fanoutName   扇出源节点名称
     * @param branches     分支名 -> 动作映射
     * @param mergeName    合并节点名称
     * @param mergeAction  合并节点动作
     * @return 合并节点的 GraphBuilder
     */
    public GraphBuilder addParallelBranches(
            String fanoutName,
            Map<String, AsyncNodeAction> branches,
            String mergeName,
            AsyncNodeAction mergeAction) throws GraphStateException;
}
```

---

## 6. 组件池管理

**涉及文件：** `core/NodeActionPool.java`、`core/EdgeConditionPool.java`

### 6.1 @NodeAction 注册与使用

定义独立节点类并通过 `@NodeAction` 注解注册：

```java
@NodeAction(value = "validate", description = "输入验证节点")
public class ValidateNode extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.isBlank()) throw new IllegalArgumentException("输入不能为空");
        return Map.of("validated", true, "cleanInput", input.trim());
    }
}

@NodeAction(value = "process", description = "数据处理节点")
public class ProcessNode extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("cleanInput").orElse("");
        return Map.of("result", "已处理: " + input);
    }
}
```

在图定义中按名称引用：

```java
@GraphDefinition(name = "pool-demo", group = "examples")
public class PoolDemoGraph extends AbstractGraphTemplate {

    public PoolDemoGraph(GraphComponentFacade components) { super(components); }

    @Override
    protected OverAllState initialState() { return new OverAllState(); }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addNode("validate", components.nodeActions())  // 从 NodeActionPool 获取 ValidateNode
                .addEdge("process")
                .addNode("process", components.nodeActions())   // 从 NodeActionPool 获取 ProcessNode
                .addEdge(StateGraph.END);
    }
}
```

### 6.2 @EdgeCondition 注册与使用

定义独立边类并通过 `@EdgeCondition` 注解注册：

```java
@EdgeCondition(value = "type-router", description = "输入类型路由")
public class TypeRouterEdge extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.contains("?")) return "question-handler";
        if (input.startsWith("!")) return "command-handler";
        return "default-handler";
    }
}
```

在图定义中按名称引用：

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("dispatcher", dispatchAction)
            // 从 EdgeConditionPool 获取 TypeRouterEdge 实例
            .addConditionalEdges("type-router", components.edgeConditions())
            .route("question-handler", "question-handler")
            .route("command-handler", "command-handler")
            .route("default-handler", "default-handler")
            .done();
}
```

### 6.3 实例化模式

`@NodeAction` 和 `@EdgeCondition` 都支持 `scope` 属性控制实例化行为：

| scope | 行为 | 适用场景 |
|-------|------|----------|
| `"prototype"`（默认） | 每次 `get()` 创建新实例 | 有状态的节点/边，避免并发污染 |
| `"singleton"` | 首次 `get()` 后缓存，后续共享 | 无状态的节点/边，性能更优 |

### 6.4 实例化优先级

池在创建实例时，优先通过 Spring `ApplicationContext` 获取（支持构造器注入），失败时回退到无参构造：

```java
// 内部逻辑
try {
    return applicationContext.getBean(clazz);  // 优先 Spring 容器
} catch (Exception e) {
    return clazz.getDeclaredConstructor().newInstance();  // 回退无参构造
}
```

---

## 7. 持久化执行

**涉及文件：** `checkpoint/CheckpointFactory.java`、`checkpoint/MyBatisFlexCheckpointSaver.java`、`dal/checkpoint/`（实体和 Mapper）

### 7.1 检查点策略

通过 `@GraphDefinition(checkpointStrategy = "...")` 配置：

| 策略 | 存储 | 适用场景 |
|------|------|----------|
| `"memory"` | 进程内 MemorySaver（单例） | 开发测试 |
| `"postgres"` | PostgreSQL 双表持久化 | 生产环境 |
| `""` | 无检查点 | 无需状态恢复 |

### 7.2 PostgreSQL 持久化

**建表：**
```sql
-- src/main/resources/db/checkpoint-postgresql.sql
CREATE TABLE graph_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    thread_id VARCHAR(255) NOT NULL,
    checkpoint_id VARCHAR(255) NOT NULL,
    parent_checkpoint_id VARCHAR(255),
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_checkpoint_thread ON graph_checkpoint(thread_id);
CREATE INDEX idx_checkpoint_id ON graph_checkpoint(checkpoint_id);

CREATE TABLE graph_checkpoint_blob (
    checkpoint_id VARCHAR(255) PRIMARY KEY,
    state_data TEXT
);
```

**配置：**
```yaml
graph:
  workflow:
    engine:
      default-checkpoint-strategy: postgres
```

### 7.3 CheckpointFactory

```java
@Component
public class CheckpointFactory {
    // postgresSaver 通过 @Autowired(required = false) 注入
    // 无 PG 环境时不会阻止启动
    public CheckpointFactory(@Autowired(required = false) MyBatisFlexCheckpointSaver postgresSaver);

    public BaseCheckpointSaver create(String strategy);
}
```

### 7.4 MyBatisFlexCheckpointSaver

```java
@Component
public class MyBatisFlexCheckpointSaver implements BaseCheckpointSaver {
    public Collection<Checkpoint> list(RunnableConfig config);  // 列出所有检查点
    public Optional<Checkpoint> get(RunnableConfig config);     // 获取最新或指定检查点
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint); // 保存
    public Tag release(RunnableConfig config);                  // 释放（删除）
}
```

**双表设计：** `graph_checkpoint`（元数据）+ `graph_checkpoint_blob`（状态数据 TEXT），分离大状态负载。

---

## 8. 人类反馈（中断与恢复）

**涉及文件：** `examples/HumanApprovalGraph.java`、`engine/GraphEngine.java`

### 8.1 定义中断点

```java
@GraphDefinition(
    name = "human-approval",
    checkpointStrategy = "postgres",         // 必须持久化
    interruptBefore = {"humanReview"}        // 在 humanReview 节点前中断
)
```

### 8.2 完整示例

```java
@GraphDefinition(
    name = "human-approval",
    group = "examples",
    checkpointStrategy = "postgres",
    interruptBefore = {"humanReview"}
)
public class HumanApprovalGraph extends AbstractGraphTemplate {

    public HumanApprovalGraph(GraphComponentFacade components) { super(components); }

    @Override
    protected OverAllState initialState() { return new OverAllState(); }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addNode("draft", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("draft", "AI 生成的提案");
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge("humanReview")
                .addNode("humanReview", state -> {
                    // 此节点在执行前被中断
                    String feedback = (String) state.value("humanFeedback").orElse("approved");
                    Map<String, Object> update = new HashMap<>();
                    update.put("approved", "approved".equals(feedback));
                    return CompletableFuture.completedFuture(update);
                })
                .addConditionalEdges(state ->
                        Boolean.TRUE.equals(state.value("approved").orElse(false))
                                ? "finalize" : "draft")
                .route("finalize", "finalize")
                .route("draft", "draft")
                .done()
                .addNode("finalize", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("result", "已定稿");
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }
}
```

### 8.3 执行与恢复流程

```java
@Autowired private GraphPoolManager poolManager;
@Autowired private GraphEngine engine;

// 第一步：首次执行 -- 在 humanReview 前中断，保存检查点
OverAllState state = new OverAllState();
state = state.input(Map.of("input", "提案内容"));
OverAllState result = poolManager.invokeGraph("human-approval", state, "thread-1");

// 第二步：注入人类反馈
CompiledGraph graph = poolManager.getGraph("human-approval");
RunnableConfig config = RunnableConfigBuilder.create().threadId("thread-1").build();
Map<String, Object> feedback = Map.of("humanFeedback", "approved");
RunnableConfig updatedConfig = engine.updateState(graph, config, feedback);
// updateState 返回更新后的 RunnableConfig（含新检查点 ID）

// 第三步：恢复执行
OverAllState finalResult = engine.invoke(graph, state, updatedConfig);
```

**GraphEngine.updateState 签名：**
```java
public RunnableConfig updateState(CompiledGraph graph, RunnableConfig config,
                                  Map<String, Object> updatedState) throws Exception;
```

---

## 9. 时光旅行（状态历史查询与分支恢复）

**涉及文件：** `engine/GraphEngine.java`

### 9.1 列出所有检查点

```java
CompiledGraph graph = poolManager.getGraph("my-graph");

// 获取所有检查点摘要
List<CheckpointSummary> checkpoints = engine.listCheckpoints(graph, "thread-1");
for (CheckpointSummary cp : checkpoints) {
    System.out.println("节点: " + cp.node());
    System.out.println("检查点ID: " + cp.checkpointId());
    System.out.println("下一节点: " + cp.nextNode());
}
```

### 9.2 获取指定检查点的完整状态

```java
// 获取指定检查点的完整状态快照
Optional<StateSnapshot> snapshot = engine.getCheckpoint(graph, "thread-1", "cp-id");
if (snapshot.isPresent()) {
    System.out.println("状态数据: " + snapshot.get().state());
}
```

### 9.3 从历史检查点分支恢复

```java
// 从历史状态快照恢复执行（创建新分支，不修改原历史）
List<CheckpointSummary> cps = engine.listCheckpoints(graph, "thread-1");
Optional<StateSnapshot> ss = engine.getCheckpoint(graph, "thread-1", cps.get(0).checkpointId());
OverAllState result = engine.branchFromCheckpoint(graph, ss.get());
```

### 9.4 修改历史状态并重执行

```java
// 修改历史状态并从该点重新执行
Optional<StateSnapshot> ss = engine.getCheckpoint(graph, "thread-1", "cp-id");
OverAllState result = engine.updateAndResume(graph, ss.get(), Map.of("key", "新值"));
```

### 9.5 获取状态历史（传统方式）

```java
CompiledGraph graph = poolManager.getGraph("my-graph");

// 方式一：通过 threadId
Collection<StateSnapshot> history = engine.getStateHistory(graph, "thread-1");

// 方式二：通过 RunnableConfig
RunnableConfig config = RunnableConfigBuilder.create().threadId("thread-1").build();
Collection<StateSnapshot> history2 = engine.getStateHistory(graph, config);

for (StateSnapshot snapshot : history) {
    System.out.println("检查点ID: " + snapshot.config().checkPointId().orElse(""));
    System.out.println("状态数据: " + snapshot.state());
}
```

### 9.6 获取当前状态

```java
// 方式一：通过 threadId
StateSnapshot current = engine.getState(graph, "thread-1");

// 方式二：通过 RunnableConfig
StateSnapshot current2 = engine.getState(graph, config);
```

**GraphEngine 时光旅行完整 API：**

```java
// 检查点管理
public List<CheckpointSummary> listCheckpoints(CompiledGraph graph, String threadId);
public Optional<StateSnapshot> getCheckpoint(CompiledGraph graph, String threadId, String checkpointId);

// 分支恢复
public OverAllState branchFromCheckpoint(CompiledGraph graph, StateSnapshot snapshot);
public OverAllState updateAndResume(CompiledGraph graph, StateSnapshot snapshot,
                                     Map<String, Object> updatedState);

// 状态历史查询
public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, String threadId);
public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, RunnableConfig config);
public StateSnapshot getState(CompiledGraph graph, String threadId);
public StateSnapshot getState(CompiledGraph graph, RunnableConfig config);
```

---

## 10. 图组装（编排流程）

**涉及文件：** `core/AbstractGraphTemplate.java`、`core/GraphComponentFacade.java`

### 10.1 AbstractGraphTemplate.compileGraph() 编排流程

```
1. init()                              <- 构造后钩子
2. setupStateKeyFactory()              -> KeyStrategyFactory
3. setupCheckpointSaver(def)           -> BaseCheckpointSaver
4. new StateGraph(name, keyFactory)    -> StateGraph（图创建）
5. new GraphBuilder(sg, saver)         -> GraphBuilder 包装
6. buildGraph(builder)                 <- 子类定义节点和边（节点创建 + 边创建 + 子图 + 并行）
7. setupCompileConfig(def)             -> CompileConfig.Builder
8. SaverConfig 注入检查点到编译配置     （持久化执行）
9. stateGraph.compile(config)          -> CompiledGraph（编译）
10. afterGraphCompiled(graph)          <- 编译后钩子
```

### 10.2 AbstractGraphTemplate.buildStateGraph() 编排流程

`buildStateGraph()` 仅构建 `StateGraph` 而不编译，用于子图场景：

```
1. init()                              <- 构造后钩子
2. setupStateKeyFactory()              -> KeyStrategyFactory
3. new StateGraph(name, keyFactory)    -> StateGraph（图创建）
4. new GraphBuilder(sg, null)          -> GraphBuilder 包装（无检查点）
5. buildGraph(builder)                 <- 子类定义节点和边
6. 返回 StateGraph（不编译）
```

### 10.3 GraphComponentFacade 统一入口

```java
// 6 个组件通过 components 统一访问
components.stateKey()        // StateKeyFactory -- 键策略
components.checkpoint()      // CheckpointFactory -- 检查点
components.nodeActions()     // NodeActionPool -- 节点动作池
components.edgeConditions()  // EdgeConditionPool -- 边条件池
components.pool()            // GraphPoolManager -- 图注册与执行
components.engine()          // GraphEngine -- 底层执行引擎
```

---

## 11. 图执行

**涉及文件：** `engine/GraphEngine.java`、`core/GraphPoolManager.java`

### 11.1 通过 GraphPoolManager 执行（推荐）

```java
@Autowired private GraphPoolManager poolManager;

// 同步执行
OverAllState state = new OverAllState();
state = state.input(Map.of("input", "问题"));
OverAllState result = poolManager.invokeGraph("my-graph", state, "thread-1");

// 从指定检查点恢复执行
OverAllState result2 = poolManager.invokeGraph("my-graph", state, "thread-1", "checkpoint-id");

// 流式执行
Flux<?> output = poolManager.streamGraph("my-graph", state, "thread-1");
```

### 11.2 通过 GraphEngine 直接执行

```java
@Autowired private GraphEngine engine;

CompiledGraph graph = poolManager.getGraph("my-graph");
OverAllState state = new OverAllState();
state = state.input(Map.of("input", "问题"));

// 方式一：指定 threadId 和 checkPointId
OverAllState result = engine.invoke(graph, state, "thread-1", null);

// 方式二：自定义 RunnableConfig
RunnableConfig config = RunnableConfigBuilder.create()
        .threadId("thread-1")
        .metadata(Map.of("traceId", "abc-123"))
        .build();
OverAllState result2 = engine.invoke(graph, state, config);

// 流式执行
Flux<NodeOutput> stream = engine.stream(graph, state, "thread-1");
stream.subscribe(output -> {
    System.out.println("节点: " + output.node());
    System.out.println("数据: " + output.state().data());
});
```

**GraphEngine 完整 API：**

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphEngine {
    private final GraphEngineProperties properties;

    // 构建 RunnableConfig（内部方法）-- 使用虚拟线程
    private RunnableConfig buildRunnableConfig(String threadId, String checkPointId);

    // 同步执行
    public OverAllState invoke(CompiledGraph graph, OverAllState state,
                               String threadId, String checkPointId)
            throws GraphStateException;

    public OverAllState invoke(CompiledGraph graph, OverAllState state,
                               RunnableConfig config)
            throws GraphStateException;

    // 流式执行
    public Flux<NodeOutput> stream(CompiledGraph graph, OverAllState state, String threadId);

    // 时光旅行
    public List<CheckpointSummary> listCheckpoints(CompiledGraph graph, String threadId);
    public Optional<StateSnapshot> getCheckpoint(CompiledGraph graph, String threadId, String checkpointId);
    public OverAllState branchFromCheckpoint(CompiledGraph graph, StateSnapshot snapshot);
    public OverAllState updateAndResume(CompiledGraph graph, StateSnapshot snapshot,
                                         Map<String, Object> updatedState);

    // 状态管理
    public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, String threadId);
    public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, RunnableConfig config);
    public StateSnapshot getState(CompiledGraph graph, String threadId);
    public StateSnapshot getState(CompiledGraph graph, RunnableConfig config);
    public RunnableConfig updateState(CompiledGraph graph, RunnableConfig config,
                                      Map<String, Object> updatedState) throws Exception;
}
```

### 11.3 GraphPoolManager 图管理

```java
@Component
public class GraphPoolManager {
    // 获取编译后的图（懒加载）
    public CompiledGraph getGraph(String name);

    // 按分组获取图列表
    public List<CompiledGraph> getGraphsByGroup(String group);

    // 获取元数据（不实例化）
    public Optional<GraphMetadata> getMetadata(String name);

    // 便捷执行方法
    public OverAllState invokeGraph(String name, OverAllState state, String threadId)
            throws GraphStateException;
    public OverAllState invokeGraph(String name, OverAllState state,
                                    String threadId, String checkPointId)
            throws GraphStateException;
    public Flux<?> streamGraph(String name, OverAllState state, String threadId);

    // 缓存管理
    public void evictCache(String name);
    public void evictAllCache();
}
```

---

## 上游 API 签名对照表

本框架所有类型签名均与上游 `spring-ai-alibaba-graph-core:1.1.2.2` 一致：

| 接口/类 | 方法签名 | 参数 |
|---------|---------|------|
| `AsyncNodeAction` | `CompletableFuture<Map<String,Object>> apply(OverAllState)` | **单参数** |
| `AsyncEdgeAction` | `CompletableFuture<String> apply(OverAllState)` | **单参数** |
| `NodeAction` | `Map<String,Object> apply(OverAllState) throws Exception` | **单参数** |
| `EdgeAction` | `String apply(OverAllState) throws Exception` | **单参数** |
| `KeyStrategyFactory` | `Map<String,KeyStrategy> apply()` | **无参数** |
| `CompiledGraph.invoke` | `Optional<OverAllState> invoke(OverAllState, RunnableConfig)` | |
| `CompiledGraph.stream` | `Flux<NodeOutput> stream(Map, RunnableConfig)` | |
| `CompiledGraph.getState` | `StateSnapshot getState(RunnableConfig)` | |
| `CompiledGraph.getStateHistory` | `Collection<StateSnapshot> getStateHistory(RunnableConfig)` | |
| `CompiledGraph.updateState` | `RunnableConfig updateState(RunnableConfig, Map)` | |
| `StateGraph.addNode` | `throws GraphStateException` | |
| `StateGraph.addEdge` | `throws GraphStateException` | |
| `StateGraph.compile` | `throws GraphStateException` | |
