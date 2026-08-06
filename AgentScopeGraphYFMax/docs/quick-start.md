# 快速开始

本文档帮助您在 5 分钟内创建并运行第一个图工作流。

---

## 前置条件

- Java 21
- Maven 3.8+
- Spring Boot 3.4.x

---

## 第一步：添加依赖

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>AgentScopeGraphYFMax</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 第二步：配置 application.yml

```yaml
graph:
  workflow:
    engine:
      default-checkpoint-strategy: memory   # 开发阶段用内存
      default-recursion-limit: 25
    scan-packages: org.example              # @GraphDefinition / @NodeAction / @EdgeCondition 扫描包
```

---

## 第三步：创建图定义类

创建一个简单的两节点顺序流水线：

```java
import com.alibaba.cloud.ai.graph.GraphStateException;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@GraphDefinition(
        name = "simple-qa",
        group = "examples",
        description = "简单的两节点流水线",
        checkpointStrategy = "memory"
)
public class SimpleQaGraph extends AbstractGraphTemplate {

    // 构造函数 -- 注入组件门面
    public SimpleQaGraph(GraphComponentFacade components) {
        super(components);
    }

    // 定义初始状态
    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    // 构建图拓扑
    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 节点 lambda 签名：state -> CompletableFuture<Map<String, Object>>（单参数）
        builder.addNode("analyze", state -> {
                    String input = (String) state.value("input").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("analysis", "已分析: " + input);
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge("answer")                              // analyze -> answer
                .addNode("answer", state -> {
                    String analysis = (String) state.value("analysis").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("result", "回答: " + analysis);
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);                       // answer -> END
    }
}
```

**要点：**
- `@GraphDefinition` 继承 `@Component`，Spring 自动扫描注册
- 节点 lambda 签名为 `state -> CompletableFuture<Map>`（单参数，非 `(state, config)`）
- lambda 直接返回 `Map<String, Object>`（无需调用 `state.updateState()`）
- `buildGraph()` 必须声明 `throws GraphStateException`
- `GraphBuilder` 支持链式调用：`addEdge()` 后可直接 `addNode()`

---

## 第四步：执行工作流

```java
@Autowired
private GraphPoolManager poolManager;

// 准备初始状态
OverAllState state = new OverAllState();
state = state.input(Map.of("input", "你好世界"));

// 方式一：通过 GraphPoolManager 统一入口（推荐）
OverAllState result = poolManager.invokeGraph("simple-qa", state, "thread-1");

// 方式二：手动获取图后调用 GraphEngine
@Autowired
private GraphEngine engine;

CompiledGraph graph = poolManager.getGraph("simple-qa");
OverAllState result2 = engine.invoke(graph, state, "thread-1", null);
```

---

## 第五步：查看结果

```java
// 从 OverAllState 中读取结果
System.out.println(result.value("analysis").orElse(""));  // "已分析: 你好世界"
System.out.println(result.value("result").orElse(""));    // "回答: 已分析: 你好世界"
```

---

## 使用 @NodeAction 池引用节点（可选）

对于可复用的节点逻辑，定义独立类并通过 `@NodeAction` 注解注册到 `NodeActionPool`：

```java
import org.example.graph.workflow.annotation.NodeAction;
import org.example.graph.createGraph.node.SimpleNodeAction;

/**
 * 分析节点 -- 独立类，通过 @NodeAction 注册到 NodeActionPool。
 * 子类只需实现 execute()，框架自动包装为 CompletableFuture。
 */
@NodeAction(value = "analyze", description = "输入分析节点")
public class AnalyzeNodeAction extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("analysis", "已分析: " + input);
    }
}
```

在 `buildGraph()` 中通过池按名称引用：

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 通过 NodeActionPool 按名称获取节点实例
    builder.addNode("analyze", components.nodeActions())
           .addEdge(StateGraph.END);
}
```

**要点：**
- `@NodeAction` 继承 `@Component`，Spring 自动扫描注册到 `NodeActionPool`
- 名称由 `@NodeAction(value = "analyze")` 定义，保证一致性
- `scope = "prototype"`（默认）每次 `get()` 创建新实例，避免状态污染
- 节点类可复用于多个图定义

---

## 使用 @EdgeCondition 池引用边（可选）

对于可复用的边路由逻辑，定义独立类并通过 `@EdgeCondition` 注解注册到 `EdgeConditionPool`：

```java
import org.example.graph.workflow.annotation.EdgeCondition;
import org.example.graph.createGraph.edge.SimpleEdgeAction;

/**
 * 类型路由边 -- 独立类，通过 @EdgeCondition 注册到 EdgeConditionPool。
 */
@EdgeCondition(value = "type-router", description = "输入类型路由")
public class TypeRouterEdge extends SimpleEdgeAction {

    @Override
    protected String execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return input.contains("?") ? "question-handler" : "default-handler";
    }
}
```

在 `buildGraph()` 中通过池按名称引用：

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("dispatch", dispatchAction)
            // 通过 EdgeConditionPool 按名称获取边实例
            .addConditionalEdges("type-router", components.edgeConditions())
            .route("question-handler", "question-handler")
            .route("default-handler", "default-handler")
            .done()
            .addNode("question-handler", components.nodeActions())
            .addEdge(StateGraph.END)
            .addNode("default-handler", components.nodeActions())
            .addEdge(StateGraph.END);
}
```

---

## 条件分支示例

```java
@GraphDefinition(name = "conditional-demo", group = "examples")
public class ConditionalGraph extends AbstractGraphTemplate {

    public ConditionalGraph(GraphComponentFacade components) { super(components); }

    @Override
    protected OverAllState initialState() { return new OverAllState(); }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addNode("router", state -> {
                    String input = (String) state.value("input").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("type", input.contains("?") ? "question" : "statement");
                    return CompletableFuture.completedFuture(update);
                })
                // 添加条件边路由
                .addConditionalEdges(state ->
                        (String) state.value("type").orElse("statement"))
                .route("question", "answer")
                .route("statement", "acknowledge")
                .done()
                .addNode("answer", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("response", "这是一个回答");
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END)
                .addNode("acknowledge", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("response", "已收到");
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }
}
```

**要点：**
- `addConditionalEdges(AsyncEdgeAction)` 返回 `ConditionalEdgeBuilder`
- `.route(resultValue, targetNode)` 逐一映射路由结果到目标节点
- `.done()` 完成条件边配置并返回 `GraphBuilder`
- 路由 action 签名为 `state -> String`（单参数）

---

## 并行扇出示例

```java
@GraphDefinition(name = "parallel-demo", group = "examples")
public class ParallelGraph extends AbstractGraphTemplate {

    public ParallelGraph(GraphComponentFacade components) { super(components); }

    @Override
    protected OverAllState initialState() { return new OverAllState(); }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addParallelBranches("fanout",
                Map.of(
                    "branch-a", (AsyncNodeAction) state -> {
                        Map<String, Object> update = new HashMap<>();
                        update.put("a", "分支A结果");
                        return CompletableFuture.completedFuture(update);
                    },
                    "branch-b", (AsyncNodeAction) state -> {
                        Map<String, Object> update = new HashMap<>();
                        update.put("b", "分支B结果");
                        return CompletableFuture.completedFuture(update);
                    }
                ),
                "merge",
                (AsyncNodeAction) state -> {
                    String a = (String) state.value("a").orElse("");
                    String b = (String) state.value("b").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("result", a + " + " + b);
                    return CompletableFuture.completedFuture(update);
                }
        ).addEdge(StateGraph.END);
    }
}
```

**拓扑：** `fanout -> [branch-a, branch-b] -> merge -> END`

**配置并行执行器：**
```yaml
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

---

## 下一步

| 主题 | 文档 |
|------|------|
| 完整使用指南 | [usage-guide.md](usage-guide.md) |
| API 参考 | [api-reference.md](api-reference.md) |
| 工作流模式 | [workflow-patterns.md](workflow-patterns.md) |
| 架构设计 | [architecture.md](architecture.md) |
| 最佳实践 | [best-practices.md](best-practices.md) |
| 故障排除 | [troubleshooting.md](troubleshooting.md) |
