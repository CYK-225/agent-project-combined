# GraphBuilder API

## 概述

图构建器（充血模型），统一负责节点创建、边创建、子图操作、并行操作。

所有 `add*` 方法返回 `GraphBuilder`（`this`），支持流式链式调用。

## 类

```
graph.createGraph.builder
├── GraphBuilder            ← 充血模型构建器
├── ConditionalEdgeBuilder  ← 条件边路由配置
└── RunnableConfigBuilder   ← 运行配置构建
```

## GraphBuilder 方法

### 节点操作

| 方法 | 说明 | 返回 |
|------|------|------|
| `addNode(name, AsyncNodeAction)` | 添加节点 | `GraphBuilder` |
| `addNode(name, NodeActionPool)` | 从池获取节点 | `GraphBuilder` |
| `addSubgraphNode(name, StateGraph)` | 添加子图（StateGraph） | `GraphBuilder` |
| `addSubgraphNode(name, CompiledGraph)` | 添加子图（CompiledGraph） | `GraphBuilder` |

### 边操作

| 方法 | 说明 | 返回 |
|------|------|------|
| `addEdge(target)` | 隐式源节点 → target | `GraphBuilder` |
| `addEdge(source, target)` | source → target | `GraphBuilder` |
| `addConditionalEdges(AsyncEdgeAction)` | 从隐式源节点添加条件边 | `ConditionalEdgeBuilder` |
| `addConditionalEdges(name, EdgeConditionPool)` | 从池获取条件边 | `ConditionalEdgeBuilder` |
| `addConditionalEdges(source, AsyncEdgeAction)` | 从指定源添加条件边 | `ConditionalEdgeBuilder` |

### 并行操作

| 方法 | 说明 | 返回 |
|------|------|------|
| `addParallelBranches(fanout, branches, merge, mergeAction)` | 扇出并行拓扑 | `GraphBuilder` |

### 属性

| 方法 | 说明 |
|------|------|
| `getStateGraph()` | 获取底层 StateGraph |
| `getCheckpointSaver()` | 获取检查点保存器 |

## ConditionalEdgeBuilder 方法

| 方法 | 说明 | 返回 |
|------|------|------|
| `route(resultValue, targetNode)` | 映射路由结果到目标节点 | `ConditionalEdgeBuilder` |
| `done()` | 完成条件边配置 | `GraphBuilder` |

## RunnableConfigBuilder 方法

| 方法 | 说明 | 返回 |
|------|------|------|
| `create()` | 创建新实例 | `RunnableConfigBuilder` |
| `threadId(id)` | 设置线程ID | `RunnableConfigBuilder` |
| `checkPointId(id)` | 设置检查点ID | `RunnableConfigBuilder` |
| `metadata(Map)` | 设置元数据 | `RunnableConfigBuilder` |
| `addParallelNodeExecutor(Executor)` | 设置并行执行器 | `RunnableConfigBuilder` |
| `build()` | 构建 RunnableConfig | `RunnableConfig` |

## 使用示例

### 连续链式（无需 then）

```java
builder.addNode("a", action)
       .addEdge("b")              // a → b（隐式）
       .addNode("b", action)
       .addEdge("c")              // b → c（隐式）
       .addNode("c", action)
       .addEdge(StateGraph.END);  // c → END
```

### 从池引用

```java
builder.addNode("validate", components.nodeActions())
       .addEdge("process")
       .addConditionalEdges("type-router", components.edgeConditions())
       .route("q", "question").route("d", "default")
       .done();
```

### 并行扇出

```java
builder.addParallelBranches("fanout",
    Map.of("fin", finAction, "tech", techAction),
    "merge", mergeAction
).addEdge(StateGraph.END);
```

### 条件分支

```java
builder.addNode("router", routerAction)
       .addConditionalEdges(state -> (String) state.value("type").orElse("default"))
       .route("question", "answer")
       .route("statement", "ack")
       .done();
```
