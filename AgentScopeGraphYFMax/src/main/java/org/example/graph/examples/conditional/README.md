# conditional — 条件路由与人机交互

本包介绍条件边（根据状态动态选择下一节点）和人机交互（中断等待外部反馈）。

前置知识：[basics](../basics/README.md) 的图基础、[pool](../pool/README.md) 的池化引用。

---

## 1. 条件边概念

普通边（`addEdge`）是静态连接：A 永远指向 B。
条件边（`addConditionalEdges`）是动态路由：根据当前状态选择 A 的下一个节点。

```
              ┌→ [finalize]  ← approved
[draft] → [humanReview] ─┤
              └→ [draft]    ← rejected
```

路由动作（`AsyncEdgeAction`）接收当前状态，返回目标节点名称字符串。

---

## 2. SimpleEdgeAction — 边基类

和 [SimpleNodeAction](../basics/README.md) 类似，`SimpleEdgeAction` 封装了 `CompletableFuture`：

```java
public class ApprovalRouterEdge extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) {
        boolean approved = (boolean) state.value("approved").orElse(false);
        return approved ? "finalize" : "draft";
    }
}
```

返回值是**目标节点名称**，不是 true/false。

---

## 3. ConditionalEdgeBuilder

`addConditionalEdges` 返回 `ConditionalEdgeBuilder`，用 `route().route().done()` 链定义路由表：

```java
builder.addConditionalEdges(source, routingAction)
       .route("finalize", "finalize")   // 路由动作返回 "finalize" → 跳到 finalize 节点
       .route("draft", "draft")          // 路由动作返回 "draft"   → 跳到 draft 节点
       .done();                           // 结束条件边定义，返回 GraphBuilder
```

`route(key, targetNode)` 的含义：
- `key`：路由动作的返回值（字符串匹配）
- `targetNode`：匹配时要跳转到的节点名称

---

## 4. 人机交互（Human-in-the-Loop）

### 4.1 中断机制

通过 `@GraphDefinition` 的 `interruptBefore` / `interruptAfter` 在指定节点处暂停执行：

```java
@GraphDefinition(
    name = "human-approval",
    interruptBefore = {"humanReview"}   // 执行 humanReview 前暂停
)
```

- `interruptBefore`：在节点**执行前**暂停（节点还没运行）
- `interruptAfter`：在节点**执行后**暂停（节点已运行，可查看其输出）

### 4.2 交互流程

```
1. 调用 invoke() → 执行到 humanReview 前暂停，返回当前状态
2. 人工查看状态，决定是否批准
3. 调用 engine.updateState() 注入反馈
4. 调用 invoke() 继续执行
```

```java
// 步骤 1：首次执行，在中断点暂停
OverAllState state1 = engine.invoke(graph, initialState, "thread-1");

// 步骤 2-3：注入人工决策
engine.updateState(graph, config, Map.of("approved", true));

// 步骤 4：继续执行
OverAllState state2 = engine.invoke(graph, state1, "thread-1");
```

---

## 5. 本包文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `HumanApprovalGraph` | 图定义 | 人机交互审批：draft → [中断] → finalize/重做 |
| `ApprovalRouterEdge` | @EdgeCondition("approval-router") | 按审批结果路由 |

> `HumanApprovalGraph` 通过 `new ApprovalRouterEdge()` 直接实例化边动作。
> 池引用方式见 [pool 包](../pool/README.md) 的 `AnnotatedEdgeExample` — 同样的路由能力，
> 换一种获取动作的方式。

---

## 下一步

- 并行扇出拓扑 → [parallel 包](../parallel/README.md)
- 子图嵌套 → [subgraph 包](../subgraph/README.md)
- 全部配置速查 → [config 包](../config/README.md)
