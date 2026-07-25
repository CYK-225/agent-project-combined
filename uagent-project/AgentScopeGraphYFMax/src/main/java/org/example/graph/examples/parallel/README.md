# parallel — 并行扇出与汇聚

本包介绍并行执行拓扑：一个扇出节点同时触发多个分支，分支完成后汇聚到合并节点。

前置知识：[basics](../basics/README.md) 的图基础、[pool](../pool/README.md) 的池化引用。

---

## 1. 并行拓扑概念

```
                  ┌→ [branch-1] ─┐
[fanout] ──────┤→ [branch-2] ─┤→ [merge] → END
                  └→ [branch-3] ─┘
```

- **fanout（扇出节点）**：准备数据，触发所有分支并行执行
- **branches（分支节点）**：各自独立执行，互不等待
- **merge（合并节点）**：所有分支完成后执行，汇总结果

所有分支共享同一个 `OverAllState`，各自写入不同的键，合并节点读取所有键生成最终结果。

---

## 2. addParallelBranches — GraphBuilder 并行 API

### 2.1 四参数版（便捷版）

```java
builder.addParallelBranches(
    "fanout",                    // 扇出节点名称（自动创建，透传状态）
    Map.of(                      // 分支：名称 → 动作
        "financial", finAction,
        "technical", techAction
    ),
    "merge",                     // 合并节点名称
    mergeAction                  // 合并动作
);
```

内部自动创建一个**默认扇出节点**（状态透传，不做任何处理）。

### 2.2 五参数版（完全控制）

```java
builder.addParallelBranches(
    "fanout",                    // 扇出节点名称
    fanoutAction,                // 扇出动作（null = 节点已存在，不重复创建）
    Map.of("a", aAction, "b", bAction),
    "merge",                     // 合并节点名称
    mergeAction                  // 合并动作（null = 节点已存在）
);
```

**null 含义**：当 `fanoutAction` 或 `mergeAction` 为 null 时，表示该节点已通过之前的
`addNode` 创建过，`addParallelBranches` 仅负责连边，不会重复注册。

### 2.3 对比示例

本包中四个并行图展示了不同的构建方式：

| 文件 | 构建方式 | 分支动作来源 |
|------|----------|-------------|
| `ParallelAnalysisGraph` | `addParallelBranches`（4参数） | 内联 lambda |
| `PoolParallelAnalysisGraph` | `addNode` + `addParallelBranches`（5参数，null） | 池 `pool.get()` |
| `PoolParallelReviewGraph` | `addParallelBranches`（4参数） | 池 `pool.get()` |
| `PoolFanOutPatternGraph` | `FanOutGraphPattern` 池感知 | 池（Pattern 自动解析） |

---

## 3. FanOutGraphPattern — 预制拓扑模式

`FanOutGraphPattern` 封装了扇出拓扑的构建逻辑，支持两种模式：

### 3.1 直接传入动作

```java
FanOutGraphPattern.builder()
    .name("analysis")
    .fanoutNodeName("fanout")
    .branchActions(Map.of("a", actionA, "b", actionB))
    .mergeNodeName("merge")
    .mergeAction(mergeAction)
    .build()
    .apply(builder);
```

### 3.2 池感知（推荐）

```java
FanOutGraphPattern.builder()
    .name("analysis")
    .fanoutNodeName("fanout")
    .branchNames(List.of("financial-analysis", "technical-analysis", "market-analysis"))
    .mergeNodeName("report-synthesis")
    .nodeActionPool(components.nodeActions())
    .build()
    .apply(builder);
```

提供 `branchNames` + `nodeActionPool` 后，Pattern 自动从池中按名称解析每个分支的动作。

---

## 4. PatternResult — 模式组合

每个 Pattern 的 `apply()` 返回 `PatternResult`，包含：
- `entryNode` — 模式创建的第一个节点（上游连接点）
- `terminalNodes` — 需要连接到下游的节点列表

Pattern **不再自动连接到 END**，由调用方通过 `PatternResult` 决定去向：

```java
// 简单场景：终端全部到 END
PatternResult r = fanOutPattern.apply(builder);
r.connectToEnd(builder);

// 组合场景：Pattern A 的终端 → Pattern B 的入口
PatternResult r1 = fanOutPattern.apply(builder);
PatternResult r2 = conditionalPattern.apply(builder);
r1.connectTo(r2.getEntryNode(), builder);  // A 的 merge → B 的 source
r2.connectToEnd(builder);                   // B 的分支 → END
```

### 组合示例

```
validate → fanout → [code/security/perf-review] → review-summary
                                                            ↓
                                              judge → [finalize | draft] → END
```

`ComposedPatternGraph` 将 `FanOutGraphPattern`（并行审查）和 `ConditionalGraphPattern`（审批决策）
串联为完整工作流，两个 Pattern 各自独立构建，通过 `PatternResult.connectTo()` 拼接。

---

## 4. 与池化结合

并行分支的节点动作可以从池获取，和 [pool 包](../pool/README.md) 的 `addNode(name, pool)` 同理：

```java
// 方式一：手动从池获取，传入 addParallelBranches
Map<String, AsyncNodeAction> branches = new LinkedHashMap<>();
branches.put("financial-analysis", components.nodeActions().get("financial-analysis"));
branches.put("technical-analysis", components.nodeActions().get("technical-analysis"));

// 方式二：先 addNode(name, pool) 创建节点，再 addParallelBranches(null)
builder.addNode("data-dispatcher", components.nodeActions())   // 预创建
       .addNode("report-synthesis", components.nodeActions())  // 预创建
       .addParallelBranches("data-dispatcher", null, branches, "report-synthesis", null);
```

两种方式效果相同。方式二适合需要精细控制节点创建顺序的场景（如扇出节点需要特殊处理）。

---

## 5. 本包文件

### 图定义

| 文件 | 说明 |
|------|------|
| `ParallelAnalysisGraph` | 基础版：内联 lambda 并行分析 |
| `PoolParallelAnalysisGraph` | 池版：预创建 + null 模式 |
| `PoolParallelReviewGraph` | 池版：4 参数便捷模式，并行审查 |
| `PoolFanOutPatternGraph` | Pattern 版：FanOutGraphPattern + 池感知 |
| `ComposedPatternGraph` | 组合版：FanOut + Conditional 串联 |

### 池注册节点

| @NodeAction 名称 | 文件 | 用途 |
|------------------|------|------|
| `data-dispatcher` | `DataDispatcherNode` | 扇出分发 |
| `financial-analysis` | `FinancialAnalysisNode` | 财务分析分支 |
| `technical-analysis` | `TechnicalAnalysisNode` | 技术分析分支 |
| `market-analysis` | `MarketAnalysisNode` | 市场分析分支 |
| `report-synthesis` | `ReportSynthesisNode` | 报告合并 |
| `code-review` | `CodeReviewNode` | 代码审查分支 |
| `security-review` | `SecurityReviewNode` | 安全审查分支 |
| `performance-review` | `PerformanceReviewNode` | 性能审查分支 |
| `review-summary` | `ReviewSummaryNode` | 审查汇总 |

---

## 下一步

- 子图嵌套 → [subgraph 包](../subgraph/README.md)
- 全部配置速查 → [config 包](../config/README.md)
