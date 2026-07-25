# 图模式详解

本文档介绍 AgentScopeGraphYFMax 框架支持的 4 种图模式及其实现方式。

---

## 模式概览

| 模式 | 实现类 | 说明 | 适用场景 |
|------|--------|------|----------|
| 顺序流水线 | `SequentialGraphPattern` | 节点按顺序依次执行 | 数据预处理、ETL |
| 条件分支 | `ConditionalGraphPattern` | 根据条件选择不同路径 | 路由、决策、验证 |
| 扇出并行 | `FanOutGraphPattern` | 同时执行多个独立分支 | 多源分析、性能优化 |
| 多智能体 | `MultiAgentGraphPattern` | 多个智能体协作 + 裁判循环 | 辩论、审查、迭代优化 |

---

## 1. 顺序流水线 (Sequential)

最简单的模式：节点按顺序依次执行，无分支和循环。

### 流程图

```
START --> node1 --> node2 --> ... --> nodeN --> END
```

### 使用 GraphPattern 实现

```java
import org.example.graph.workflow.pattern.SequentialGraphPattern;

// 使用 SequentialGraphPattern 模式对象
SequentialGraphPattern pattern = SequentialGraphPattern.builder()
        .name("data-pipeline")
        .description("数据处理流水线")
        .nodeNames(List.of("extract", "transform", "load"))
        .nodeActions(List.of(extractAction, transformAction, loadAction))
        .build();

// 在 buildGraph() 中应用
pattern.apply(builder);
```

### 手动实现（连续链式）

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("step1", step1Action)
            .addEdge("step2")
            .addNode("step2", step2Action)
            .addEdge("step3")
            .addNode("step3", step3Action)
            .addEdge(StateGraph.END);
}
```

### 使用池引用实现

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 通过 NodeActionPool 按名称引用独立节点类
    builder.addNode("extract", components.nodeActions())
            .addEdge("transform")
            .addNode("transform", components.nodeActions())
            .addEdge("load")
            .addNode("load", components.nodeActions())
            .addEdge(StateGraph.END);
}
```

### 完整示例：问答流水线

```java
@GraphDefinition(
        name = "sequential-qa",
        description = "顺序问答流水线",
        group = "examples",
        checkpointStrategy = "memory"
)
public class SequentialQaGraph extends AbstractGraphTemplate {

    public SequentialQaGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addNode("analyze", state -> {
                    String question = (String) state.value("question").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("analysis", "已分析: " + question);
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge("answer")
                .addNode("answer", state -> {
                    String analysis = (String) state.value("analysis").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("answer", "回答: " + analysis);
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }
}
```

### 使用 SimpleNodeAction 实现

```java
public class AnalyzeAction extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String question = (String) state.value("question").orElse("");
        return Map.of("analysis", "已分析: " + question);
    }
}

public class AnswerAction extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String analysis = (String) state.value("analysis").orElse("");
        return Map.of("answer", "回答: " + analysis);
    }
}

// 在 buildGraph() 中使用
builder.addNode("analyze", new AnalyzeAction())
       .addEdge("answer")
       .addNode("answer", new AnswerAction())
       .addEdge(StateGraph.END);
```

### 适用场景

- 数据预处理流水线
- 文件转换管道
- 简单的 ETL 任务
- 问答链（分析 -> 检索 -> 生成）

---

## 2. 条件分支 (Conditional)

根据条件判断选择不同的执行路径。

### 流程图

```
START --> source --> [路由函数] --routeA--> branchA --> END
                      |
                      +--routeB--> branchB --> END
```

### 使用 GraphPattern 实现

```java
import org.example.graph.workflow.pattern.ConditionalGraphPattern;

ConditionalGraphPattern pattern = ConditionalGraphPattern.builder()
        .name("request-router")
        .description("请求路由")
        .sourceNode("classifier")
        .sourceAction(classifierAction)
        .routingAction(routingAction)       // AsyncEdgeAction，返回路由字符串
        .routeMap(Map.of(                   // 路由字符串 -> 目标节点
                "technical", "techHandler",
                "business", "bizHandler"))
        .branchActions(Map.of(              // 分支节点名 -> 动作
                "techHandler", techAction,
                "bizHandler", bizAction))
        .build();

pattern.apply(builder);
```

### 手动实现（推荐）

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("validate", validateAction)
            // 路由 action 签名：state -> String（单参数）
            .addConditionalEdges(state -> {
                String result = (String) state.value("validationResult").orElse("");
                return switch (result) {
                    case "VALID" -> "process";
                    case "PARTIAL" -> "review";
                    default -> "reject";
                };
            })
            .route("process", "process")
            .route("review", "review")
            .route("reject", "reject")
            .done()
            .addNode("process", processAction)
            .addEdge(StateGraph.END)
            .addNode("review", reviewAction)
            .addEdge(StateGraph.END)
            .addNode("reject", rejectAction)
            .addEdge(StateGraph.END);
}
```

### 使用 @EdgeCondition 池引用实现

```java
// 定义独立边类
@EdgeCondition(value = "validation-router", description = "验证结果路由")
public class ValidationRouter extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) throws Exception {
        String result = (String) state.value("validationResult").orElse("");
        return switch (result) {
            case "VALID" -> "process";
            case "PARTIAL" -> "review";
            default -> "reject";
        };
    }
}

// 在 buildGraph() 中通过池引用
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("validate", components.nodeActions())
            .addConditionalEdges("validation-router", components.edgeConditions())
            .route("process", "process")
            .route("review", "review")
            .route("reject", "reject")
            .done()
            .addNode("process", components.nodeActions())
            .addEdge(StateGraph.END)
            .addNode("review", components.nodeActions())
            .addEdge(StateGraph.END)
            .addNode("reject", components.nodeActions())
            .addEdge(StateGraph.END);
}
```

### 条件边 API 详解

```java
// 方式 1：隐式源节点（lastNodeName）
builder.addNode("source", sourceAction)
        .addConditionalEdges(routingAction)
        .route("value1", "target1")
        .route("value2", "target2")
        .done();

// 方式 2：显式源节点
builder.addConditionalEdges("sourceNode", routingAction)
        .route("value1", "target1")
        .route("value2", "target2")
        .done();

// 方式 3：从 EdgeConditionPool 获取
builder.addConditionalEdges("router-name", components.edgeConditions())
        .route("value1", "target1")
        .route("value2", "target2")
        .done();

// routingAction 签名：state -> String（单参数）
AsyncEdgeAction routingAction = state -> {
    // 返回的字符串必须与 route() 注册的 key 匹配
    return "value1";
};
```

### 适用场景

- 请求路由和分类
- 数据验证和错误处理
- 业务规则引擎
- 内容审核（通过 / 拒绝 / 需修改）

---

## 3. 扇出并行 (FanOut / Parallel)

同时执行多个独立分支，然后合并结果。

### 流程图

```
START --> fanout --> branch1 --> merge --> END
                 --> branch2 -->
                 --> branch3 -->
```

### 使用 GraphPattern 实现

```java
import org.example.graph.workflow.pattern.FanOutGraphPattern;

FanOutGraphPattern pattern = FanOutGraphPattern.builder()
        .name("parallel-analysis")
        .description("并行分析")
        .fanoutNodeName("fanout")
        .mergeNodeName("synthesizer")
        .branchActions(Map.of(
                "financial", financialAction,
                "technical", technicalAction,
                "market", marketAction))
        .mergeAction(synthesizerAction)
        .build();

pattern.apply(builder);
```

### 手动实现（使用 addParallelBranches）

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 构建扇出并行拓扑
    builder.addParallelBranches("fanout",
            Map.of(
                "financial", (AsyncNodeAction) state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("financialResult", "财务分析: " + state.value("input").orElse(""));
                    return CompletableFuture.completedFuture(update);
                },
                "technical", (AsyncNodeAction) state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("technicalResult", "技术分析: " + state.value("input").orElse(""));
                    return CompletableFuture.completedFuture(update);
                },
                "market", (AsyncNodeAction) state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("marketResult", "市场分析: " + state.value("input").orElse(""));
                    return CompletableFuture.completedFuture(update);
                }
            ),
            "synthesizer",
            (AsyncNodeAction) state -> {
                String financial = (String) state.value("financialResult").orElse("");
                String technical = (String) state.value("technicalResult").orElse("");
                String market = (String) state.value("marketResult").orElse("");
                Map<String, Object> update = new HashMap<>();
                update.put("synthesis", financial + " | " + technical + " | " + market);
                return CompletableFuture.completedFuture(update);
            }
    ).addEdge(StateGraph.END);
}
```

### addParallelBranches 生成的拓扑

```
fanout -> financial  -> synthesizer -> END
fanout -> technical  -> synthesizer
fanout -> market     -> synthesizer
```

### 重要提示

并行执行需要在 `application.yml` 中配置虚拟线程：

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

### 适用场景

- 多源数据聚合（财务 + 技术 + 市场分析）
- 并行数据处理
- 批量任务执行
- 性能优化（独立任务并行化）

---

## 4. 多智能体协作 (MultiAgent)

多个智能体依次执行后，由裁判节点决定是继续循环还是结束。

### 流程图

```
START --> agent1 --> agent2 --> ... --> judge --> [continue --> agent1]
                                              --> [end --> END]
```

### 使用 GraphPattern 实现

```java
import org.example.graph.workflow.pattern.MultiAgentGraphPattern;

MultiAgentGraphPattern pattern = MultiAgentGraphPattern.builder()
        .name("debate")
        .description("多智能体辩论")
        .agentActions(Map.of(
                "proposer", proposerAction,
                "critic", criticAction))
        .judgeNode("judge")
        .judgeAction(judgeAction)
        .judgeRoutingAction(judgeRoutingAction)  // 返回 "continue" 或 "end"
        .firstAgent("proposer")                  // 回环目标
        .build();

pattern.apply(builder);
```

### 手动实现

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 智能体节点链
    builder.addNode("proposer", proposerAction)
            .addEdge("critic")
            .addNode("critic", criticAction)
            .addEdge("judge")
            // 裁判节点
            .addNode("judge", judgeAction)
            .addConditionalEdges(state -> {
                int round = (int) state.value("round").orElse(0);
                boolean consensus = (boolean) state.value("consensus").orElse(false);
                if (consensus || round >= 3) {
                    return "end";
                }
                return "continue";
            })
            .route("continue", "proposer")   // 继续循环
            .route("end", StateGraph.END)    // 结束
            .done();
}
```

### 使用 @NodeAction + @EdgeCondition 池引用实现

```java
// 定义裁判路由边
@EdgeCondition(value = "judge-router", description = "裁判路由：判断是否继续循环")
public class JudgeRouter extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) throws Exception {
        int round = (int) state.value("round").orElse(0);
        boolean consensus = Boolean.TRUE.equals(state.value("consensus").orElse(false));

        if (consensus) return "end";
        if (round >= 5) return "end";
        return "continue";
    }
}

// 在 buildGraph() 中使用
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("proposer", components.nodeActions())
            .addEdge("critic")
            .addNode("critic", components.nodeActions())
            .addEdge("judge")
            .addNode("judge", components.nodeActions())
            .addConditionalEdges("judge-router", components.edgeConditions())
            .route("continue", "proposer")
            .route("end", StateGraph.END)
            .done();
}
```

### 适用场景

- 多智能体辩论（提出 -> 批评 -> 判断）
- 迭代式审查（编写 -> 审查 -> 修改循环）
- 协作式问题解决
- 监督者模式（Supervisor Pattern）
- 移交模式（Handoff Pattern）

---

## 混合模式

实际应用中，常常需要组合多种模式。

### 示例：并行分析 + 条件路由 + 池引用 + 人类审批

```java
@GraphDefinition(
        name = "complex-workflow",
        description = "混合模式示例",
        group = "examples",
        checkpointStrategy = "memory",
        interruptBefore = {"humanReview"}
)
public class ComplexWorkflow extends AbstractGraphTemplate {

    public ComplexWorkflow(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 阶段 1：并行分析
        builder.addParallelBranches("fanout",
                Map.of(
                    "analysis1", analysis1Action,
                    "analysis2", analysis2Action
                ),
                "merge",
                mergeAction
        );

        // 阶段 2：合并后路由（使用池引用边）
        builder.addConditionalEdges("score-router", components.edgeConditions())
                .route("auto-approve", "finalize")
                .route("needs-review", "humanReview")
                .done();

        // 阶段 3：人类审批节点（通过 interruptBefore 中断）
        builder.addNode("humanReview", reviewAction)
                .addConditionalEdges(state -> {
                    String feedback = (String) state.value("humanFeedback").orElse("reject");
                    return "approved".equals(feedback) ? "finalize" : "revise";
                })
                .route("finalize", "finalize")
                .route("revise", "fanout")   // 重新分析
                .done();

        // 最终节点（使用池引用节点）
        builder.addNode("finalize", components.nodeActions())
                .addEdge(StateGraph.END);
    }
}
```

---

## 选择合适模式的建议

| 场景 | 推荐模式 |
|------|----------|
| 简单的线性处理流程 | 顺序流水线 |
| 需要根据条件选择不同路径 | 条件分支 |
| 多个独立任务可同时执行 | 扇出并行 |
| 需要多轮迭代和判断 | 多智能体协作 |
| 需要人类介入审批 | 条件分支 + interruptBefore |
| 复杂业务流程 | 混合模式 |

---

## 相关文档

- [API 参考](api-reference.md) -- 完整 API 文档
- [快速开始](quick-start.md) -- 快速上手指南
- [最佳实践](best-practices.md) -- 生产环境最佳实践
- [架构设计](architecture.md) -- 框架架构设计
