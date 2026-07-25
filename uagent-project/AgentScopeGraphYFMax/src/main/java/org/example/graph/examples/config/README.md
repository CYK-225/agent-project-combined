# config — 全配置参考

本包是图框架所有可配置项的速查手册。
各配置项的使用场景已在前置包中分散讲解，此处汇总不重复。

---

## 1. @GraphDefinition 注解（14 个属性）

在类上声明，将类注册为由 `GraphPoolManager` 管理的图模板。

| 属性 | 类型 | 默认值 | 说明 | 详见 |
|------|------|--------|------|------|
| `name` | String | 类名 | 图的唯一标识，`pool.getGraph(name)` 使用 | — |
| `value` | String | `""` | `name` 的简写 | — |
| `description` | String | `""` | 可观测性描述，日志和 listAll() 显示 | — |
| `group` | String | `"default"` | 分组，`getGraphsByGroup()` 按此筛选 | — |
| `lazy` | boolean | `true` | `true`=首次调用时编译，`false`=启动时编译 | — |
| `active` | boolean | `true` | `false`=启动时跳过此图 | — |
| `scope` | String | `"prototype"` | `"prototype"`=每次新建，`"singleton"`=共享 | [pool](../pool/README.md) |
| `priority` | int | `0` | 分组内排序（值越小优先级越高） | — |
| `checkpointStrategy` | String | `"memory"` | `"memory"`/`"postgres"`/`""`（无） | [conditional](../conditional/README.md) |
| `recursionLimit` | int | `25` | 条件循环最大递归深度 | — |
| `interruptBefore` | String[] | `{}` | 执行前中断的节点列表 | [conditional](../conditional/README.md) |
| `interruptAfter` | String[] | `{}` | 执行后中断的节点列表 | [conditional](../conditional/README.md) |
| `enableStreaming` | boolean | `false` | 是否支持 `stream()` 返回 Flux | — |
| `parallelism` | int | `0` | 并行线程池大小（0=系统默认） | [parallel](../parallel/README.md) |

---

## 2. AbstractGraphTemplate 可重写方法（5 个）

子类继承 `AbstractGraphTemplate`，可重写以下方法自定义行为。

| 方法 | 默认行为 | 说明 |
|------|----------|------|
| `setupStateKeyFactory()` | 所有键 ReplaceStrategy | 自定义状态键策略（如某些键用 AppendStrategy） |
| `setupCheckpointSaver(def)` | 从注解 checkpointStrategy 解析 | 注入自定义 BaseCheckpointSaver |
| `setupCompileConfig(def)` | 应用 recursionLimit/interrupt | 添加额外的 CompileConfig 选项 |
| `afterGraphCompiled(graph)` | 空实现 | 编译后钩子（注册监控、打印拓扑） |
| `init()` | 空实现 | 构造后初始化（每次编译调用一次） |

---

## 3. GraphBuilder 操作速查

### 节点操作

| 方法 | 说明 | 详见 |
|------|------|------|
| `addNode(name, action)` | 直接添加节点动作 | [basics](../basics/README.md) |
| `addNode(name, pool)` | 从 NodeActionPool 按名称获取 | [pool](../pool/README.md) |
| `addSubgraphNode(name, StateGraph)` | 嵌入未编译子图 | [subgraph](../subgraph/README.md) |
| `addSubgraphNode(name, CompiledGraph)` | 嵌入已编译子图 | [subgraph](../subgraph/README.md) |

### 边操作

| 方法 | 说明 | 详见 |
|------|------|------|
| `addEdge(target)` | 隐式源节点（lastNodeName → target） | [basics](../basics/README.md) |
| `addEdge(source, target)` | 显式指定源→目标 | [basics](../basics/README.md) |

### 条件边操作

| 方法 | 说明 | 详见 |
|------|------|------|
| `addConditionalEdges(action)` | 隐式源 + 路由动作 | [conditional](../conditional/README.md) |
| `addConditionalEdges(source, action)` | 显式源 + 路由动作 | [conditional](../conditional/README.md) |
| `addConditionalEdges(name, pool)` | 池获取路由动作，name 做源 | [pool](../pool/README.md) |
| `addConditionalEdges(name, pool, useLast)` | 池获取 + 可选隐式源 | [pool](../pool/README.md) |

### 并行操作

| 方法 | 说明 | 详见 |
|------|------|------|
| `addParallelBranches(fanout, branches, merge, mergeAction)` | 4 参数便捷版 | [parallel](../parallel/README.md) |
| `addParallelBranches(fanout, fanoutAction, branches, merge, mergeAction)` | 5 参数完全版 | [parallel](../parallel/README.md) |

---

## 4. RunnableConfigBuilder — 运行时配置

在调用 `GraphEngine` 执行时传入的运行时配置。

| 方法 | 说明 |
|------|------|
| `threadId(id)` | 检查点线程标识 |
| `checkPointId(id)` | 从指定检查点恢复（时间旅行） |
| `metadata(map)` | 附带元数据到执行上下文 |
| `addParallelNodeExecutor(executor)` | 自定义并行线程池 |
| `build()` | 构建 RunnableConfig |

```java
RunnableConfig config = RunnableConfigBuilder.create()
    .threadId("session-001")
    .checkPointId("cp-abc123")
    .metadata(Map.of("userId", "user-42"))
    .addParallelNodeExecutor(Executors.newFixedThreadPool(8))
    .build();
```

---

## 5. GraphEngine — 执行与状态查询

| 方法 | 说明 |
|------|------|
| `invoke(graph, state, threadId)` | 同步执行 |
| `invoke(graph, state, config)` | 带 RunnableConfig 执行 |
| `stream(graph, state, threadId)` | 流式执行（返回 Flux） |
| `getState(graph, threadId)` | 获取当前状态快照 |
| `listCheckpoints(graph, threadId)` | 列出所有检查点摘要 |
| `getCheckpoint(graph, threadId, cpId)` | 获取指定检查点 |
| `branchFromCheckpoint(graph, snapshot)` | 从历史快照分支 |
| `updateState(graph, config, updates)` | 注入状态更新（人工反馈） |
| `updateAndResume(graph, snapshot, updates)` | 修改历史状态并重新执行 |

---

## 6. 本包文件

| 文件 | 说明 |
|------|------|
| `GraphConfigShowcase` | 全配置展示：@GraphDefinition 全属性 + 可重写方法 + 运行时配置示例 |
