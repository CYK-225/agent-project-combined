# AgentScopeGraphYFMax 架构设计文档

本文档介绍 AgentScopeGraphYFMax 图工作流框架的整体架构设计。

---

## 设计理念

### 核心原则

1. **声明式优先** — 使用 `@GraphDefinition` 注解定义图工作流，无需手动管理 Bean 注册
2. **组件池化** — `@NodeAction` / `@EdgeCondition` 注册独立节点/边类到动作池，按名称引用，可复用、可测试
3. **模板方法模式** — `AbstractGraphTemplate` 统一编排编译流程，子类只需关注业务拓扑
4. **充血模型构建器** — `GraphBuilder` 流式 API，支持 5 种拓扑写法，池感知操作
5. **IoC 驱动** — 所有工厂和服务通过 Spring 依赖注入，零孤立死代码
6. **生产就绪** — 内置检查点持久化（内存 / PostgreSQL）、并行执行（虚拟线程）、人类反馈中断、时光旅行

### 设计目标

- 基于 Spring AI Alibaba Graph 上游库，不重复造轮子
- 屏蔽 `StateGraph` / `CompileConfig` / `KeyStrategyFactory` 等底层组装复杂度
- 支持 4 种图模式（顺序、条件、扇出并行、多智能体）
- 提供 `GraphPoolManager` 统一管理所有图实例的注册、缓存和调用
- 提供 `NodeActionPool` / `EdgeConditionPool` 统一管理独立节点/边类的注册和实例化
- 提供完整的时光旅行 API（检查点列表、分支恢复、修改历史重执行）

---

## 六层架构

```
+=========================================================================+
|  Layer 6  支撑层 (Support)                                               |
|  StateKeyFactory / CheckpointFactory                                     |
|  SimpleNodeAction / SimpleEdgeAction                                     |
|  GraphPattern (4 种模式实现)                                              |
+=========================================================================+
|  Layer 5  组件池层 (Component Pool)                                       |
|  NodeActionPool (@NodeAction 扫描)                                       |
|  EdgeConditionPool (@EdgeCondition 扫描)                                 |
|  prototype / singleton 实例化模式                                         |
+=========================================================================+
|  Layer 4  运行时层 (Runtime)                                             |
|  GraphEngine / GraphPoolManager                                          |
|  @Component 扫描 + 懒加载 + 分组索引 + 缓存池                             |
+=========================================================================+
|  Layer 3  模板层 (Template)                                               |
|  AbstractGraphTemplate + @GraphDefinition                                |
|  模板方法编排: init -> keyFactory -> checkpoint -> buildGraph -> compile  |
+=========================================================================+
|  Layer 2  构建层 (Builder)                                                |
|  GraphBuilder（充血模型 + 池感知）                                         |
|  ConditionalEdgeBuilder / RunnableConfigBuilder                          |
+=========================================================================+
|  Layer 1  上游层 (Upstream)                                               |
|  StateGraph / CompiledGraph / OverAllState                               |
|  CompileConfig / RunnableConfig / Checkpoint / BaseCheckpointSaver       |
|  KeyStrategyFactory / AsyncNodeAction / AsyncEdgeAction                  |
+=========================================================================+
```

### 层次职责

| 层次 | 职责 | 关键类 |
|------|------|--------|
| Layer 1 上游层 | Spring AI Alibaba Graph 核心 API，提供图编译和执行引擎 | `StateGraph`, `CompiledGraph`, `OverAllState` |
| Layer 2 构建层 | 流式构建器，封装上游 API 的组装细节，支持池感知操作 | `GraphBuilder`, `ConditionalEdgeBuilder` |
| Layer 3 模板层 | 模板方法基类 + 注解驱动，统一编排编译流程 | `AbstractGraphTemplate`, `@GraphDefinition` |
| Layer 4 运行时层 | 图实例的自动发现、缓存池和执行引擎 | `GraphPoolManager`, `GraphEngine` |
| Layer 5 组件池层 | 独立节点/边类的自动发现、注册和实例化管理 | `NodeActionPool`, `EdgeConditionPool` |
| Layer 6 支撑层 | 工厂类、动作基类和可复用图模式 | `StateKeyFactory`, `CheckpointFactory`, `SimpleNodeAction`, `SimpleEdgeAction` |

---

## 包结构

```
org.example.graph.createGraph
├── builder/
│   ├── GraphBuilder.java              # 充血模型构建器（节点/边/子图/并行/池引用全部内置）
│   ├── ConditionalEdgeBuilder.java    # 条件边路由映射构建器
│   └── RunnableConfigBuilder.java     # 运行配置构建器
├── edge/
│   └── SimpleEdgeAction.java          # 边动作抽象基类
├── engine/
│   ├── GraphEngine.java               # 中央运行时（invoke / stream / 时光旅行）
│   └── GraphEngineProperties.java     # 配置属性
└── node/
    ├── SimpleNodeAction.java          # 节点动作抽象基类
    └── StreamingNodeAction.java       # 流式节点动作

org.example.graph.workflow
├── annotation/
│   ├── GraphDefinition.java           # 图声明注解（继承 @Component）
│   ├── NodeAction.java               # 节点动作类注解（继承 @Component，注册到 NodeActionPool）
│   └── EdgeCondition.java            # 条件边路由类注解（继承 @Component，注册到 EdgeConditionPool）
├── core/
│   ├── AbstractGraphTemplate.java     # 模板方法基类
│   ├── GraphComponentFacade.java      # 统一组件门面（6 个组件）
│   ├── GraphPoolManager.java          # 图池管理器（@GraphDefinition 自动发现 + 缓存 + 执行）
│   ├── NodeActionPool.java            # 节点动作池（@NodeAction 自动发现 + 实例化）
│   └── EdgeConditionPool.java         # 边条件池（@EdgeCondition 自动发现 + 实例化）
├── state/
│   └── StateKeyFactory.java           # 策略工厂（Replace / Append）
├── checkpoint/
│   ├── CheckpointFactory.java         # 检查点工厂（memory / postgres）
│   └── MyBatisFlexCheckpointSaver.java  # PostgreSQL 持久化实现
├── pattern/
│   ├── GraphPattern.java              # 模式接口
│   ├── SequentialGraphPattern.java    # 顺序流水线
│   ├── ConditionalGraphPattern.java   # 条件分支
│   ├── FanOutGraphPattern.java        # 扇出并行
│   └── MultiAgentGraphPattern.java    # 多智能体协作
└── examples/
    ├── SequentialQaGraph.java         # 顺序流水线示例
    ├── ParallelAnalysisGraph.java     # 并行扇出示例
    ├── HumanApprovalGraph.java        # 人类反馈示例
    ├── SubgraphWorkflowGraph.java     # 子图引用示例
    ├── ResearchSubgraph.java          # 子图定义
    ├── AnnotatedNodeExample.java      # @NodeAction 池引用示例
    ├── AnnotatedEdgeExample.java      # @EdgeCondition 池引用示例
    ├── ValidateNode.java              # @NodeAction 独立节点
    ├── ProcessNode.java               # @NodeAction 独立节点
    ├── OutputNode.java                # @NodeAction 独立节点
    ├── QuestionHandlerNode.java       # @NodeAction 独立节点
    ├── CommandHandlerNode.java        # @NodeAction 独立节点
    ├── DefaultHandlerNode.java        # @NodeAction 独立节点
    ├── TypeRouterEdge.java            # @EdgeCondition 独立边
    └── ApprovalRouterEdge.java        # @EdgeCondition 独立边
```

---

## 核心组件

### 1. AbstractGraphTemplate -- 模板方法基类

所有图定义类都应继承此基类。它通过模板方法 `compileGraph()` 统一编排编译流程。

```
compileGraph(GraphDefinition) 的编排流程:
  1. init()                          -- 构造后钩子
  2. setupStateKeyFactory()          -- 创建 KeyStrategyFactory
  3. setupCheckpointSaver(def)       -- 创建检查点保存器
  4. new StateGraph(name, ksf)       -- 创建上游 StateGraph
  5. buildGraph(builder)             -- [子类实现] 定义节点和边
  6. setupCompileConfig(def)         -- 生成 CompileConfig（含 interruptBefore/After）
  7. stateGraph.compile(config)      -- 编译
  8. afterGraphCompiled(graph)       -- 编译后钩子
```

**必须实现的方法：**
- `buildGraph(GraphBuilder)` -- 定义图拓扑（节点 + 边）
- `initialState()` -- 提供初始状态

**可选重写点：**
- `setupStateKeyFactory()` -- 自定义状态键策略
- `setupCheckpointSaver(GraphDefinition)` -- 自定义检查点保存器
- `setupCompileConfig(GraphDefinition)` -- 自定义编译配置
- `afterGraphCompiled(CompiledGraph)` -- 编译后回调
- `init()` -- 初始化钩子

### 2. GraphBuilder -- 充血模型构建器

封装上游 `StateGraph`，提供流式 API，支持 5 种拓扑写法和池感知操作。

```
GraphBuilder 的核心方法:
  -- 节点 --
  addNode(name, AsyncNodeAction)                    -> GraphBuilder
  addNode(name, NodeActionPool)                     -> GraphBuilder（从池获取）

  -- 子图 --
  addSubgraphNode(name, StateGraph)                 -> GraphBuilder
  addSubgraphNode(name, CompiledGraph)              -> GraphBuilder

  -- 边 --
  addEdge(target)                                   -> GraphBuilder（隐式源）
  addEdge(source, target)                           -> GraphBuilder
  addConditionalEdges(AsyncEdgeAction)              -> ConditionalEdgeBuilder（隐式源）
  addConditionalEdges(source, AsyncEdgeAction)      -> ConditionalEdgeBuilder
  addConditionalEdges(name, EdgeConditionPool)      -> ConditionalEdgeBuilder（从池获取）

  -- 并行 --
  addParallelBranches(fanout, branches, merge, action) -> GraphBuilder
```

### 3. ConditionalEdgeBuilder -- 条件边构建器

将路由动作的输出字符串映射到目标节点：

```
ConditionalEdgeBuilder 的核心方法:
  route(resultValue, targetNode)          -> ConditionalEdgeBuilder
  done()                                  -> GraphBuilder
```

### 4. GraphComponentFacade -- 组件门面

聚合 6 个组件的统一入口：

```
GraphComponentFacade 的访问器:
  stateKey()        -> StateKeyFactory
  checkpoint()      -> CheckpointFactory
  nodeActions()     -> NodeActionPool
  edgeConditions()  -> EdgeConditionPool
  pool()            -> GraphPoolManager
  engine()          -> GraphEngine
```

### 5. GraphPoolManager -- 图池管理器

自动发现 `@GraphDefinition` 注解类，注册元数据，延迟实例化 `CompiledGraph`。

```
GraphPoolManager 的核心方法:
  getGraph(name)                                  -> CompiledGraph
  invokeGraph(name, state, threadId)              -> OverAllState
  invokeGraph(name, state, threadId, checkpointId) -> OverAllState
  streamGraph(name, state, threadId)              -> Flux<?>
  getGraphsByGroup(group)                         -> List<CompiledGraph>
  getMetadata(name)                               -> Optional<GraphMetadata>
```

### 6. NodeActionPool -- 节点动作池

自动发现 `@NodeAction` 注解的类，建立名称 -> 类/实例映射。

```
NodeActionPool 的核心方法:
  get(name)               -> AsyncNodeAction
  exists(name)            -> boolean
  getDescription(name)    -> String
  listAll()               -> Map<name, description>
  getRegisteredNames()    -> Set<String>
```

支持两种实例化模式：
- `prototype`（默认）-- 每次 `get()` 创建新实例，避免状态污染
- `singleton` -- 首次 `get()` 后缓存，后续共享

### 7. EdgeConditionPool -- 边条件池

自动发现 `@EdgeCondition` 注解的类，建立名称 -> 类/实例映射。

```
EdgeConditionPool 的核心方法:
  get(name)               -> AsyncEdgeAction
  exists(name)            -> boolean
  getDescription(name)    -> String
  listAll()               -> Map<name, description>
  getRegisteredNames()    -> Set<String>
```

### 8. GraphEngine -- 执行引擎

中央运行时，正确连接检查点、线程ID和并行执行器支持（使用 Java 21 虚拟线程）。提供完整的时光旅行 API。

```
GraphEngine 的核心方法:
  -- 执行 --
  invoke(graph, state, threadId, checkpointId)    -> OverAllState
  invoke(graph, state, config)                    -> OverAllState
  stream(graph, state, threadId)                  -> Flux<NodeOutput>

  -- 时光旅行 --
  listCheckpoints(graph, threadId)                -> List<CheckpointSummary>
  getCheckpoint(graph, threadId, checkpointId)    -> Optional<StateSnapshot>
  branchFromCheckpoint(graph, snapshot)           -> OverAllState
  updateAndResume(graph, snapshot, updatedState)  -> OverAllState

  -- 状态查询 --
  getStateHistory(graph, threadId)                -> Collection<StateSnapshot>
  getStateHistory(graph, config)                  -> Collection<StateSnapshot>
  getState(graph, threadId)                       -> StateSnapshot
  getState(graph, config)                         -> StateSnapshot

  -- 状态更新 --
  updateState(graph, config, updatedState)        -> RunnableConfig
```

### 9. SimpleNodeAction / SimpleEdgeAction -- 动作基类

轻量抽象类，替代直接实现 `AsyncNodeAction` / `AsyncEdgeAction`。子类只需实现同步的 `execute()` 方法，框架自动包装为 `CompletableFuture`。

```
SimpleNodeAction:
  execute(OverAllState) -> Map<String, Object>    // 子类实现
  apply(OverAllState)   -> CompletableFuture<Map> // 框架自动包装

SimpleEdgeAction:
  execute(OverAllState) -> String                  // 子类实现
  apply(OverAllState)   -> CompletableFuture<String> // 框架自动包装
```

---

## 组件池机制

### @NodeAction 注册流程

```
应用启动
  |
  v
NodeActionPool.init()
  |
  +-- ClassPathScanningCandidateComponentProvider
  |     扫描 graph.workflow.scan-packages 配置的包路径
  |     过滤 @NodeAction 注解的类
  |
  +-- 对每个候选类：
        1. 验证实现了 AsyncNodeAction
        2. 解析名称（@NodeAction.value 或类简单名）
        3. 记录到 classMap / descriptionMap / scopeMap
        4. 日志: "已注册节点动作: xxx -> Yyy（scope=prototype）"
```

### @EdgeCondition 注册流程

```
应用启动
  |
  v
EdgeConditionPool.init()
  |
  +-- ClassPathScanningCandidateComponentProvider
  |     扫描 graph.workflow.scan-packages 配置的包路径
  |     过滤 @EdgeCondition 注解的类
  |
  +-- 对每个候选类：
        1. 验证实现了 AsyncEdgeAction
        2. 解析名称（@EdgeCondition.value 或类简单名）
        3. 记录到 classMap / descriptionMap / scopeMap
        4. 日志: "已注册边动作: xxx -> Yyy（scope=prototype）"
```

### 实例化优先级

池在创建实例时，优先通过 Spring `ApplicationContext` 获取（支持构造器注入），失败时回退到无参构造：

```
1. applicationContext.getBean(clazz)     -- 优先 Spring 容器（支持 @Autowired 注入）
2. clazz.getDeclaredConstructor().newInstance()  -- 回退无参构造
```

### GraphBuilder 池感知调用流程

```
builder.addNode("validate", components.nodeActions())
  |
  v
GraphBuilder.addNode(String name, NodeActionPool pool)
  |
  +-- pool.get("validate")
  |     |
  |     +-- classMap.get("validate") -> ValidateNode.class
  |     +-- scopeMap.get("validate") -> "prototype"
  |     +-- createInstance(ValidateNode.class)
  |           |
  |           +-- applicationContext.getBean(ValidateNode.class)  -- 优先
  |           +-- 或 ValidateNode.class.getDeclaredConstructor().newInstance()  -- 回退
  |
  +-- addNode("validate", action)   -- 委托标准 addNode 方法
```

---

## 状态管理

### StateKeyFactory

状态键策略工厂，控制 `OverAllState` 中每个键的更新行为。

- `defaultFactory()` -- 所有键使用 `ReplaceStrategy`（后写覆盖先写）
- `builder()` -- 返回 `KeyStrategyFactoryBuilder`，支持 `addStrategy(key, strategy)` 精确控制

### OverAllState

上游库提供的状态容器，节点间通过 `OverAllState` 传递数据：
- `state.value(key)` -- 读取键值（返回 `Optional`）
- `state.updateState(map)` -- 批量更新键值
- `state.data()` -- 获取全部数据（`Map<String, Object>`）
- `state.input(map)` -- 设置初始输入

---

## 检查点系统

### CheckpointFactory

根据策略名称生产 `BaseCheckpointSaver` 实例：

| 策略 | 实现 | 说明 |
|------|------|------|
| `"memory"` | `MemorySaver`（上游库） | 进程内，无持久化，适合开发测试 |
| `"postgres"` | `MyBatisFlexCheckpointSaver` | PostgreSQL 持久化，IoC 注入 |
| `""` / `null` | `null` | 无检查点 |

检查点在 `compileGraph()` 时自动注入到 `CompileConfig.SaverConfig` 中。

### 双表设计

PostgreSQL 模式使用双表设计：
- `graph_checkpoint` -- 检查点元数据（threadId, checkpointId, nodeId 等）
- `graph_checkpoint_blob` -- 状态数据（JSON 序列化 TEXT）

---

## @GraphDefinition 注解属性

```java
@GraphDefinition(
    name = "my-graph",              // 唯一图名称（默认为类简单名称）
    description = "...",            // 描述
    group = "default",              // 分组（用于 getGraphsByGroup）
    lazy = true,                    // 延迟实例化
    active = true,                  // 启用/禁用
    scope = "prototype",            // prototype / singleton
    priority = 0,                   // 分组内优先级
    checkpointStrategy = "memory",  // memory / postgres / ""
    recursionLimit = 25,            // 递归限制
    interruptBefore = {},           // 执行前中断节点
    interruptAfter = {},            // 执行后中断节点
    enableStreaming = false,        // 流式输出支持
    parallelism = 0                 // 并行线程池大小
)
```

---

## 执行流程

### 图调用流程

```
用户代码
  |
  v
GraphPoolManager.invokeGraph("my-graph", state, "thread-1")
  |
  +-- getGraph("my-graph")
  |     |
  |     +-- 从缓存获取或创建 AbstractGraphTemplate 实例
  |     +-- template.compileGraph(definition)  [首次调用]
  |     +-- 返回 CompiledGraph
  |
  +-- graphEngine.invoke(graph, state, "thread-1", null)
        |
        +-- buildRunnableConfig(threadId, checkpointId)
        +-- graph.invoke(state, config)
        +-- 返回 OverAllState
```

### 人类反馈流程

```
1. @GraphDefinition(interruptBefore = {"humanReview"})
2. GraphEngine.invoke(graph, state, "thread-1", null)
   -- 执行到 humanReview 节点前中断，保存检查点
3. 人类检查 state，提供反馈
4. GraphEngine.updateState(graph, config, updatedState)
   -- 注入人类反馈到状态
5. GraphEngine.invoke(graph, state, updatedConfig)
   -- 从中断点继续执行
```

### 时光旅行流程

```
1. listCheckpoints(graph, "thread-1")
   -- 获取所有检查点摘要列表
2. getCheckpoint(graph, "thread-1", "cp-id")
   -- 获取指定检查点的完整状态快照
3. branchFromCheckpoint(graph, snapshot)
   -- 从历史状态恢复执行（创建新分支，不修改原历史）
4. updateAndResume(graph, snapshot, Map.of("key", "val"))
   -- 修改历史状态并从该点重新执行
```

---

## 配置

配置前缀：`graph.workflow.engine.*`

```yaml
spring:
  threads:
    virtual:
      enabled: true    # 必须启用，用于并行执行

graph:
  workflow:
    engine:
      default-recursion-limit: 25      # 默认递归限制
      parallel-executor-enabled: true   # 使用虚拟线程，无需配置线程池大小
      default-checkpoint-strategy: memory  # 默认检查点策略
    scan-packages: org.example          # 扫描包路径（逗号分隔），用于 @GraphDefinition / @NodeAction / @EdgeCondition
```

> **虚拟线程：** 并行执行使用 Java 21 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`），无需配置线程池大小。需在 `application.yml` 中启用 `spring.threads.virtual.enabled: true`。

---

## 相关文档

- [快速开始](quick-start.md) -- 快速上手指南
- [API 参考](api-reference.md) -- 详细 API 文档
- [图模式](workflow-patterns.md) -- 4 种图模式详解
- [最佳实践](best-practices.md) -- 生产环境最佳实践
- [故障排除](troubleshooting.md) -- 常见问题解答
