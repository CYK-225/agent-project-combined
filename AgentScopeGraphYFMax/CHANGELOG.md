# 更新日志

本文件记录 AgentScopeGraphYFMax 项目的所有重要更改。

## [2.0.0] - 2026-05-11

### 重构 — GraphBuilder 充血模型

**删除的类：**
- `NodeFactory` → 由 `SimpleNodeAction` 基类替代
- `EdgeFactory` → 由 `SimpleEdgeAction` 基类替代
- `SubgraphNodeBuilder` → 子图用 `AbstractGraphTemplate` + `buildStateGraph()` 创建
- `ParallelNodeBuilder` → 由 `GraphBuilder.addParallelBranches()` 替代
- `NodeHandle` → 由 `GraphBuilder` 直接承担所有链式调用
- `GraphWorkflowConfig` → Spring Boot 自动发现 `@Component`
- `StreamingNodeBuilder` → 死代码，删除
- `CompileConfigBuilder` → 死代码，删除

**新增：**
- `SimpleNodeAction` — 节点动作抽象基类（`execute(OverAllState) → Map`）
- `SimpleEdgeAction` — 边动作抽象基类（`execute(OverAllState) → String`）
- `AbstractGraphTemplate.buildStateGraph()` — 构建未编译的 StateGraph（用于子图）
- `GraphBuilder.addParallelBranches()` — 内置并行扇出拓扑
- `GraphEngine.listCheckpoints()` — 时光旅行检查点列表
- `GraphEngine.getCheckpoint()` — 获取指定检查点
- `GraphEngine.branchFromCheckpoint()` — 从历史状态分支执行
- `GraphEngine.updateAndResume()` — 修改历史状态并恢复执行

**GraphBuilder 充血模型：**
- `addNode()` 返回 `GraphBuilder`（非 NodeHandle）
- `addEdge(target)` 使用隐式源节点（lastNodeName）
- `addConditionalEdges(action)` 使用隐式源节点
- 全程无需 `then()`

**并行执行改用虚拟线程：**
- `GraphEngine` 使用 `Executors.newVirtualThreadPerTaskExecutor()`
- 需在 `application.yml` 中启用 `spring.threads.virtual.enabled: true`
- 移除 `parallel-pool-size` 配置

### 兼容矩阵（上游 1.1.2.2 完全覆盖）

| 上游能力 | 框架映射 |
|---------|---------|
| `addNode(AsyncNodeAction)` | `GraphBuilder.addNode()` |
| `addNode(StateGraph 子图)` | `GraphBuilder.addSubgraphNode()` |
| `addNode(CompiledGraph 子图)` | `GraphBuilder.addSubgraphNode()` |
| `addEdge` / `addConditionalEdges` | `GraphBuilder.addEdge()` / `addConditionalEdges()` |
| `compile` / `compile(CompileConfig)` | `AbstractGraphTemplate.compileGraph()` |
| `invoke` / `stream` | `GraphEngine.invoke()` / `GraphEngine.stream()` |
| `getStateHistory` / `updateState` / `getState` | `GraphEngine` 完整时光旅行 API |
| `KeyStrategyFactory` | `StateKeyFactory` |
| `BaseCheckpointSaver` | `CheckpointFactory` + `MyBatisFlexCheckpointSaver` |
| `AsyncNodeAction` / `AsyncEdgeAction` | `SimpleNodeAction` / `SimpleEdgeAction` 基类 |

---

## [1.0.0] - 2026-05-07

### 新增
- 初始版本，基于 Spring AI Alibaba Graph 构建
- `@GraphDefinition` 注解驱动
- `AbstractGraphTemplate` 模板方法基类
- 检查点持久化（Memory / PostgreSQL）
- 4 种工作流模式（Sequential / Conditional / FanOut / MultiAgent）
- 4 个示例工作流
