# subgraph — 子图嵌套

本包介绍如何将一个完整的图作为节点嵌入到另一个图中。

前置知识：[basics](../basics/README.md) 的图基础。

---

## 1. 子图概念

子图（Subgraph）是将一个独立的图拓扑嵌入到父图中的某个节点位置。
该节点执行时，会运行子图的全部流程，子图的结果作为该节点的输出。

```
父图：preprocess → [research 子图] → postprocess → END

子图：search → synthesize → END
```

等效于：

```
preprocess → search → synthesize → postprocess → END
```

但子图的节点和边是**封装的** — 父图不需要知道子图内部结构。

---

## 2. 两种嵌入方式

### 方式 A：StateGraph 子图（父图编译）

```java
// 构建子图但不编译
StateGraph subGraph = new ResearchSubgraph(components).buildStateGraph();

// 嵌入到父图
builder.addSubgraphNode("research", subGraph);
```

- 子图以 `StateGraph` 形式嵌入，**由父图统一编译**
- 适合子图与父图共享检查点配置的场景
- 通过 `AbstractGraphTemplate.buildStateGraph()` 获取

### 方式 B：CompiledGraph 子图（独立编译）

```java
// 从 GraphPoolManager 获取已编译的子图
CompiledGraph subGraph = components.pool().getGraph("research-sub");

// 嵌入到父图
builder.addSubgraphNode("research", subGraph);
```

- 子图已独立编译，拥有自己的检查点和编译配置
- 适合子图需要独立配置（如不同的 recursionLimit）的场景
- 通过 `GraphPoolManager.getGraph(name)` 获取

### 选择依据

| | StateGraph 子图 | CompiledGraph 子图 |
|---|---|---|
| 编译时机 | 父图编译时一起编译 | 提前独立编译 |
| 检查点 | 继承父图配置 | 独立配置 |
| 适用场景 | 简单封装、共享配置 | 独立配置、跨图复用 |

---

## 3. addSubgraphNode 两种重载

```java
// 重载一：嵌入 StateGraph
builder.addSubgraphNode("research", stateGraph);

// 重载二：嵌入 CompiledGraph
builder.addSubgraphNode("research", compiledGraph);
```

两个重载的行为相同 — GraphBuilder 内部处理类型差异。
选择哪种取决于子图的编译时机和配置需求。

---

## 4. 本包文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `SubgraphWorkflowGraph` | 图定义 | 展示 StateGraph 和 CompiledGraph 两种嵌入方式 |
| `ResearchSubgraph` | 子图定义 | 可复用的研究子图：search → synthesize → END |

`ResearchSubgraph` 注册为 `@GraphDefinition(name = "research-sub", group = "subgraphs")`，
可被方式 B 通过 `components.pool().getGraph("research-sub")` 获取。

---

## 下一步

- 全部配置速查 → [config 包](../config/README.md)
