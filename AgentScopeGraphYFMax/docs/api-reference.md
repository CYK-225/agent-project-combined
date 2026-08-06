# AgentScopeGraphYFMax API 参考文档

本文档提供框架全部公共 API 的完整签名和说明。所有方法签名均通过 `javap` 对上游 1.1.2.2 jar 验证。

---

## 包结构总览

```
org.example.graph.createGraph
├── builder         图构建器（流式 API）
├── edge            边动作抽象基类
├── engine          图执行引擎
└── node            节点动作抽象基类

org.example.graph.workflow
├── annotation      注解定义（@GraphDefinition / @NodeAction / @EdgeCondition）
├── checkpoint      检查点工厂与持久化
├── core            核心抽象（模板方法、组件门面、图池、动作池）
├── pattern         可复用拓扑模式
└── state           状态键策略工厂
```

---

## 1. annotation 包

### @GraphDefinition

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface GraphDefinition {
    String name() default "";
    String value() default "";
    String description() default "";
    String group() default "default";
    boolean lazy() default true;
    boolean active() default true;
    String scope() default "prototype";
    int priority() default 0;
    String checkpointStrategy() default "memory";
    int recursionLimit() default 25;
    String[] interruptBefore() default {};
    String[] interruptAfter() default {};
    boolean enableStreaming() default false;
    int parallelism() default 0;
}
```

继承 `@Component`，Spring 自动扫描注册。`GraphPoolManager` 在启动时扫描此类注解的类。

### @NodeAction

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface NodeAction {
    String value() default "";
    String description() default "";
    String scope() default "prototype";
}
```

类级注解，继承 `@Component`。将 `AsyncNodeAction` 实现类标记为可被 `NodeActionPool` 扫描注册的节点动作。

- `value` — 节点唯一名称，为空时默认使用类简单名
- `description` — 描述，用于可观测性和文档
- `scope` — 实例化模式：`"prototype"`（默认，每次新建）/ `"singleton"`（共享缓存）

**使用示例：**
```java
@NodeAction(value = "validate", description = "输入验证节点")
public class ValidateNode extends SimpleNodeAction { ... }
```

### @EdgeCondition

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface EdgeCondition {
    String value() default "";
    String description() default "";
    String scope() default "prototype";
}
```

类级注解，继承 `@Component`。将 `AsyncEdgeAction` 实现类标记为可被 `EdgeConditionPool` 扫描注册的边条件动作。

- `value` — 边唯一名称，为空时默认使用类简单名
- `description` — 描述，用于可观测性和文档
- `scope` — 实例化模式：`"prototype"`（默认，每次新建）/ `"singleton"`（共享缓存）

**使用示例：**
```java
@EdgeCondition(value = "type-router", description = "输入类型路由")
public class TypeRouterEdge extends SimpleEdgeAction { ... }
```

---

## 2. core 包

### AbstractGraphTemplate

模板方法基类。所有图工作流必须继承此类。

```java
public abstract class AbstractGraphTemplate {

    // 构造函数 -- 由 Spring 注入
    protected AbstractGraphTemplate(GraphComponentFacade components);

    // ========== 必须实现 ==========

    protected abstract void buildGraph(GraphBuilder builder) throws GraphStateException;
    protected abstract OverAllState initialState();

    // ========== 可选重写 ==========

    protected KeyStrategyFactory setupStateKeyFactory();
    protected BaseCheckpointSaver setupCheckpointSaver(GraphDefinition def);
    protected CompileConfig.Builder setupCompileConfig(GraphDefinition def);
    protected void afterGraphCompiled(CompiledGraph graph);
    protected void init();

    // ========== 模板方法（final） ==========

    public final CompiledGraph compileGraph(GraphDefinition definition) throws GraphStateException;
    public final CompiledGraph compileGraph() throws GraphStateException;
    public final StateGraph buildStateGraph(GraphDefinition definition) throws GraphStateException;
    public final StateGraph buildStateGraph() throws GraphStateException;

    // ========== 访问器 ==========

    public GraphDefinition getDefinition();
    protected GraphComponentFacade components;  // getter via @Getter
}
```

**`compileGraph()` 编排流程：**
1. 调用 `init()`
2. 解析 `setupStateKeyFactory()` -> `KeyStrategyFactory`
3. 解析 `setupCheckpointSaver(def)` -> `BaseCheckpointSaver`
4. 创建 `StateGraph(name, keyStrategyFactory)`
5. 包装为 `GraphBuilder`，委托子类 `buildGraph()`
6. 从注解构建 `CompileConfig`（递归限制、中断节点等）
7. 注入 `SaverConfig` 到编译配置
8. 调用 `stateGraph.compile(config)` 返回 `CompiledGraph`
9. 调用 `afterGraphCompiled()` 钩子

### GraphComponentFacade

6 个组件的统一入口。

```java
@Component
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class GraphComponentFacade {
    private final StateKeyFactory stateKey;
    private final CheckpointFactory checkpoint;
    private final NodeActionPool nodeActions;
    private final EdgeConditionPool edgeConditions;
    private final GraphPoolManager pool;
    private final GraphEngine engine;
}
```

**用法：**
```java
components.stateKey().defaultFactory()               // KeyStrategyFactory
components.checkpoint().create("postgres")            // BaseCheckpointSaver
components.nodeActions().get("validate")              // AsyncNodeAction
components.edgeConditions().get("type-router")        // AsyncEdgeAction
components.pool().invokeGraph("name", state, tid)     // OverAllState
components.engine().invoke(graph, state, config)      // OverAllState
```

### GraphPoolManager

自动发现 `@GraphDefinition` 类，懒加载编译，管理图生命周期。

```java
@Component
public class GraphPoolManager {

    // ========== 图获取 ==========

    public CompiledGraph getGraph(String name);
    public List<CompiledGraph> getGraphsByGroup(String group);
    public Optional<GraphMetadata> getMetadata(String name);

    // ========== 高层执行 API ==========

    public OverAllState invokeGraph(String name, OverAllState state, String threadId)
            throws GraphStateException;

    public OverAllState invokeGraph(String name, OverAllState state, String threadId, String checkPointId)
            throws GraphStateException;

    public Flux<?> streamGraph(String name, OverAllState state, String threadId);

    // ========== 缓存管理 ==========

    public void evictCache(String name);
    public void evictAllCache();

    // ========== 元数据 ==========

    public Map<String, GraphMetadata> getMetadataRegistry();
    public Map<String, Set<String>> getGroupIndex();
}
```

**GraphMetadata：**
```java
@Getter @Builder
public static class GraphMetadata {
    private String name;
    private String description;
    private String group;
    private boolean lazy;
    private String scope;               // "prototype" / "singleton"
    private String checkpointStrategy;
    private Class<? extends AbstractGraphTemplate> templateClass;
    private GraphDefinition definition;
    private int priority;
}
```

### NodeActionPool

自动发现 `@NodeAction` 注解的类，建立名称 -> 类/实例映射。

```java
@Component
public class NodeActionPool {

    // ========== 公共 API ==========

    // 根据名称获取节点动作实例（prototype 模式每次新建，singleton 模式共享缓存）
    public AsyncNodeAction get(String name);

    // 检查指定名称的节点动作是否已注册
    public boolean exists(String name);

    // 获取节点动作的描述
    public String getDescription(String name);

    // 列出所有已注册的节点动作（名称 -> 描述）
    public Map<String, String> listAll();

    // 获取已注册的节点名称集合
    public Set<String> getRegisteredNames();
}
```

### EdgeConditionPool

自动发现 `@EdgeCondition` 注解的类，建立名称 -> 类/实例映射。

```java
@Component
public class EdgeConditionPool {

    // ========== 公共 API ==========

    // 根据名称获取边动作实例（prototype 模式每次新建，singleton 模式共享缓存）
    public AsyncEdgeAction get(String name);

    // 检查指定名称的边动作是否已注册
    public boolean exists(String name);

    // 获取边动作的描述
    public String getDescription(String name);

    // 列出所有已注册的边动作（名称 -> 描述）
    public Map<String, String> listAll();

    // 获取已注册的边名称集合
    public Set<String> getRegisteredNames();
}
```

---

## 3. engine 包

### GraphEngine

图执行的中央运行时。提供 invoke、stream、时光旅行功能。

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphEngine {
    private final GraphEngineProperties properties;

    // 内部构建 RunnableConfig -- 使用虚拟线程（parallelExecutorEnabled 时）
    private RunnableConfig buildRunnableConfig(String threadId, String checkPointId);

    // ========== 执行 ==========

    // 调用图，返回最终状态（可能为 null）
    public OverAllState invoke(CompiledGraph graph, OverAllState state,
                               String threadId, String checkPointId)
            throws GraphStateException;

    // 使用自定义 RunnableConfig 调用
    public OverAllState invoke(CompiledGraph graph, OverAllState state,
                               RunnableConfig config)
            throws GraphStateException;

    // 流式执行，返回节点输出流
    public Flux<NodeOutput> stream(CompiledGraph graph, OverAllState state, String threadId);

    // ========== 时光旅行（状态历史与分支恢复） ==========

    // 列出所有检查点摘要
    public List<CheckpointSummary> listCheckpoints(CompiledGraph graph, String threadId);

    // 获取指定检查点的完整状态快照
    public Optional<StateSnapshot> getCheckpoint(CompiledGraph graph, String threadId, String checkpointId);

    // 从历史状态快照恢复执行（创建新分支）
    public OverAllState branchFromCheckpoint(CompiledGraph graph, StateSnapshot snapshot);

    // 修改历史状态并从该点重新执行
    public OverAllState updateAndResume(CompiledGraph graph, StateSnapshot snapshot,
                                         Map<String, Object> updatedState);

    // ========== 状态查询 ==========

    // 获取状态历史（通过 threadId）
    public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, String threadId);

    // 获取状态历史（通过 RunnableConfig）
    public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, RunnableConfig config);

    // 获取当前状态（通过 threadId）
    public StateSnapshot getState(CompiledGraph graph, String threadId);

    // 获取当前状态（通过 RunnableConfig）
    public StateSnapshot getState(CompiledGraph graph, RunnableConfig config);

    // ========== 状态更新（人类反馈注入） ==========

    // 更新状态（人类反馈注入）
    public RunnableConfig updateState(CompiledGraph graph, RunnableConfig config,
                                      Map<String, Object> updatedState)
            throws Exception;
}
```

**CheckpointSummary 记录类：**
```java
public record CheckpointSummary(
    String node,
    String checkpointId,
    String nextNode,
    OverAllState state
) {}
```

**上游 CompiledGraph 真实签名对照：**

| 方法 | 参数 | 返回值 |
|------|------|--------|
| `invoke` | `(OverAllState, RunnableConfig)` | `Optional<OverAllState>` |
| `stream` | `(Map<String, Object>, RunnableConfig)` | `Flux<NodeOutput>` |
| `getStateHistory` | `(RunnableConfig)` | `Collection<StateSnapshot>` |
| `getState` | `(RunnableConfig)` | `StateSnapshot` |
| `updateState` | `(RunnableConfig, Map<String, Object>)` | `RunnableConfig` |

### GraphEngineProperties

```java
@Data
@Component
@ConfigurationProperties(prefix = "graph.workflow.engine")
public class GraphEngineProperties {
    private int defaultRecursionLimit = 25;
    private boolean parallelExecutorEnabled = false;
    private String defaultCheckpointStrategy = "memory";
}
```

> **并行执行：** `parallelExecutorEnabled = true` 时，`GraphEngine` 使用 `Executors.newVirtualThreadPerTaskExecutor()` 创建虚拟线程执行器，无需配置线程池大小。需在 `application.yml` 中启用 `spring.threads.virtual.enabled: true`。

---

## 4. builder 包

### GraphBuilder

充血模型构建器，封装上游 `StateGraph`。提供 5 种图拓扑写法支持，支持池感知操作。

```java
public class GraphBuilder {

    // ========== 节点操作 ==========

    // 添加节点，返回 GraphBuilder
    public GraphBuilder addNode(String name, AsyncNodeAction action) throws GraphStateException;

    // 通过 NodeActionPool 添加节点（从池获取实例）
    public GraphBuilder addNode(String name, NodeActionPool pool) throws GraphStateException;

    // 添加子图节点（StateGraph）
    public GraphBuilder addSubgraphNode(String name, StateGraph subGraph) throws GraphStateException;

    // 添加子图节点（CompiledGraph）
    public GraphBuilder addSubgraphNode(String name, CompiledGraph subGraph) throws GraphStateException;

    // ========== 边操作 ==========

    // 添加无条件边（隐式源节点 lastNodeName）
    public GraphBuilder addEdge(String target) throws GraphStateException;

    // 添加无条件边（显式指定）
    public GraphBuilder addEdge(String source, String target) throws GraphStateException;

    // 添加条件边（隐式源节点），返回 ConditionalEdgeBuilder
    public ConditionalEdgeBuilder addConditionalEdges(AsyncEdgeAction routingAction);

    // 添加条件边（显式源节点），返回 ConditionalEdgeBuilder
    public ConditionalEdgeBuilder addConditionalEdges(String source, AsyncEdgeAction routingAction);

    // 通过 EdgeConditionPool 添加条件边
    public ConditionalEdgeBuilder addConditionalEdges(String name, EdgeConditionPool pool);

    // ========== 并行操作 ==========

    // 添加并行扇出分支
    public GraphBuilder addParallelBranches(
            String fanoutName,
            Map<String, AsyncNodeAction> branches,
            String mergeName,
            AsyncNodeAction mergeAction) throws GraphStateException;

    // ========== 访问 ==========

    public StateGraph getStateGraph();
    public BaseCheckpointSaver getCheckpointSaver();
}
```

### ConditionalEdgeBuilder

条件边路由构建器。

```java
public class ConditionalEdgeBuilder {

    // 映射路由结果到目标节点
    public ConditionalEdgeBuilder route(String resultValue, String targetNode);

    // 完成条件边配置，返回 GraphBuilder
    public GraphBuilder done() throws GraphStateException;
}
```

### RunnableConfigBuilder

`RunnableConfig.Builder` 的流式包装器。

```java
public class RunnableConfigBuilder {

    public static RunnableConfigBuilder create();

    public RunnableConfigBuilder threadId(String id);
    public RunnableConfigBuilder checkPointId(String id);
    public RunnableConfigBuilder metadata(Map<String, Object> meta);
    public RunnableConfigBuilder addParallelNodeExecutor(Executor executor);

    public RunnableConfig build();
}
```

**上游 RunnableConfig.Builder 真实签名：**
```java
Builder threadId(String)
Builder checkPointId(String)
Builder addMetadata(String, Object)
Builder defaultParallelExecutor(Executor)
RunnableConfig build()
```

---

## 5. node/edge 包

### SimpleNodeAction

节点动作抽象类，替代直接实现 `AsyncNodeAction`。

```java
public abstract class SimpleNodeAction implements AsyncNodeAction {

    @Override
    public final CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        try {
            return CompletableFuture.completedFuture(execute(state));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // 子类实现此方法
    protected abstract Map<String, Object> execute(OverAllState state) throws Exception;
}
```

**上游 AsyncNodeAction 签名：**
- `AsyncNodeAction`: `CompletableFuture<Map<String, Object>> apply(OverAllState)` -- 单参数
- `NodeAction`: `Map<String, Object> apply(OverAllState) throws Exception` -- 单参数

### SimpleEdgeAction

边动作抽象类，替代直接实现 `AsyncEdgeAction`。

```java
public abstract class SimpleEdgeAction implements AsyncEdgeAction {

    @Override
    public final CompletableFuture<String> apply(OverAllState state) {
        try {
            return CompletableFuture.completedFuture(execute(state));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // 子类实现此方法
    protected abstract String execute(OverAllState state) throws Exception;
}
```

**上游 AsyncEdgeAction 签名：**
- `AsyncEdgeAction`: `CompletableFuture<String> apply(OverAllState)` -- 单参数
- `EdgeAction`: `String apply(OverAllState) throws Exception` -- 单参数

---

## 6. state 包

### StateKeyFactory

```java
@Component
public class StateKeyFactory {
    // 默认工厂（所有键使用 ReplaceStrategy）
    public KeyStrategyFactory defaultFactory();

    // 返回上游 KeyStrategyFactoryBuilder
    public KeyStrategyFactoryBuilder builder();
}
```

**上游 KeyStrategyFactoryBuilder 方法：**
```java
KeyStrategyFactoryBuilder addStrategy(String key, KeyStrategy strategy);
KeyStrategyFactoryBuilder addStrategy(String key);          // 使用默认策略
KeyStrategyFactoryBuilder defaultStrategy(KeyStrategy strategy);
KeyStrategyFactoryBuilder addStrategies(Map<String, KeyStrategy> strategies);
KeyStrategyFactoryBuilder addSuffixStrategy(String suffix, KeyStrategy strategy);
KeyStrategyFactoryBuilder addPrefixStrategy(String prefix, KeyStrategy strategy);
KeyStrategyFactory build();
```

**上游 KeyStrategyFactory 签名：** `Map<String, KeyStrategy> apply()` -- 无参数

---

## 7. checkpoint 包

### CheckpointFactory

```java
@Component
public class CheckpointFactory {
    // postgresSaver 通过 @Autowired(required = false) 注入
    public CheckpointFactory(@Autowired(required = false) MyBatisFlexCheckpointSaver postgresSaver);

    // 创建检查点保存器
    public BaseCheckpointSaver create(String strategy);
}
```

| 策略 | 结果 |
|------|------|
| `"memory"` | `MemorySaver` 实例（单例缓存） |
| `"postgres"` | `MyBatisFlexCheckpointSaver`（IoC 注入） |
| `null` / `""` | `null`（无检查点） |

### MyBatisFlexCheckpointSaver

```java
@Component
public class MyBatisFlexCheckpointSaver implements BaseCheckpointSaver {
    public Collection<Checkpoint> list(RunnableConfig config);
    public Optional<Checkpoint> get(RunnableConfig config);
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint);
    public Tag release(RunnableConfig config);
}
```

基于 MyBatis-Flex + PostgreSQL 的双表设计（`graph_checkpoint` + `graph_checkpoint_blob`）。

---

## 8. pattern 包

### GraphPattern

```java
public interface GraphPattern {
    String getName();
    String getDescription();
    void apply(GraphBuilder builder) throws GraphStateException;
}
```

### SequentialGraphPattern

```java
@Getter @Builder
public class SequentialGraphPattern implements GraphPattern {
    private final String name;
    private final String description;
    private final List<String> nodeNames;
    private final List<AsyncNodeAction> nodeActions;
}
```

拓扑：`node1 -> node2 -> ... -> nodeN -> END`

### ConditionalGraphPattern

```java
@Getter @Builder
public class ConditionalGraphPattern implements GraphPattern {
    private final String name;
    private final String description;
    private final String sourceNode;
    private final AsyncNodeAction sourceAction;
    private final AsyncEdgeAction routingAction;
    private final Map<String, String> routeMap;
    private final Map<String, AsyncNodeAction> branchActions;
}
```

拓扑：`source -> [condition] -> branchA | branchB | ... -> END`

### FanOutGraphPattern

```java
@Getter @Builder
public class FanOutGraphPattern implements GraphPattern {
    private final String name;
    private final String description;
    private final String fanoutNodeName;
    private final String mergeNodeName;
    private final Map<String, AsyncNodeAction> branchActions;
    private final AsyncNodeAction mergeAction;
}
```

拓扑：`fanout -> [branch1, branch2, ...] -> merge -> END`

### MultiAgentGraphPattern

```java
@Getter @Builder
public class MultiAgentGraphPattern implements GraphPattern {
    private final String name;
    private final String description;
    private final Map<String, AsyncNodeAction> agentActions;
    private final String judgeNode;
    private final AsyncNodeAction judgeAction;
    private final AsyncEdgeAction judgeRoutingAction;
    private final String firstAgent;
}
```

拓扑：`agent1 -> agent2 -> ... -> judge -> [continue -> agent1 | end -> END]`

---

## 9. dal/checkpoint 包

| 类 | 说明 |
|----|------|
| `GraphCheckpointEntity` | `graph_checkpoint` 表实体（thread_id, checkpoint_id, node_id 等） |
| `GraphCheckpointBlobEntity` | `graph_checkpoint_blob` 表实体（checkpoint_id, state_data TEXT） |
| `GraphCheckpointMapper` | `BaseMapper<GraphCheckpointEntity>` |
| `GraphCheckpointBlobMapper` | `BaseMapper<GraphCheckpointBlobEntity>` |
