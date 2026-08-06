# 示例工作流

## 概述

14 个示例文件，覆盖所有功能领域。

## 文件清单

### 图定义示例（6 个）

| 文件 | 说明 | 拓扑 |
|------|------|------|
| `SequentialQaGraph.java` | 顺序流水线 | `analyze -> answer -> END` |
| `ParallelAnalysisGraph.java` | 并行扇出 | `fanout -> [fin, tech, mkt] -> merge -> END` |
| `HumanApprovalGraph.java` | 人类反馈 | `draft -> [interrupt] -> finalize / re-draft -> END` |
| `SubgraphWorkflowGraph.java` | 子图引用 | `preprocess -> [subgraph] -> postprocess -> END` |
| `ResearchSubgraph.java` | 子图定义 | `search -> synthesize -> END` |
| `AnnotatedNodeExample.java` | @NodeAction 池引用 | `validate -> process -> output -> END` |
| `AnnotatedEdgeExample.java` | @EdgeCondition 池引用 | `dispatcher -> [router] -> handlers -> result -> END` |

### 节点类（6 个）

| 文件 | @NodeAction 名称 | 说明 |
|------|-----------------|------|
| `ValidateNode.java` | `validate` | 输入验证 |
| `ProcessNode.java` | `process` | 数据处理 |
| `OutputNode.java` | `output` | 结果输出 |
| `QuestionHandlerNode.java` | `question-handler` | 问题处理 |
| `CommandHandlerNode.java` | `command-handler` | 命令处理 |
| `DefaultHandlerNode.java` | `default-handler` | 默认处理 |

### 边类（2 个）

| 文件 | @EdgeCondition 名称 | 说明 |
|------|-------------------|------|
| `TypeRouterEdge.java` | `type-router` | 输入类型路由 |
| `ApprovalRouterEdge.java` | `approval-router` | 审批结果路由 |

## 快速使用

### 顺序流水线

```java
// SequentialQaGraph.java
builder.addNode("analyze", state -> { ... })
       .addEdge("answer")
       .addNode("answer", state -> { ... })
       .addEdge(StateGraph.END);
```

### 并行扇出

```java
// ParallelAnalysisGraph.java
builder.addParallelBranches("fanout",
    Map.of("financial", finAction, "technical", techAction, "market", mktAction),
    "synthesizer", mergeAction
).addEdge(StateGraph.END);
```

### 池引用节点/边

```java
// AnnotatedNodeExample.java
builder.addNode("validate", components.nodeActions())
       .addEdge("process")
       .addNode("process", components.nodeActions())
       .addEdge("output")
       .addNode("output", components.nodeActions())
       .addEdge(StateGraph.END);
```

### 子图

```java
// SubgraphWorkflowGraph.java
StateGraph sub = new ResearchSubgraph(components)
    .buildStateGraph(new ResearchSubgraph().getDefinition());
builder.addNode("preprocess", action)
       .addEdge("research")
       .addSubgraphNode("research", sub)
       .addEdge("postprocess")
       .addNode("postprocess", action)
       .addEdge(StateGraph.END);
```
