# GraphEngine API

## 概述

图执行的中央运行时。提供执行、流式输出、状态管理、时光旅行完整 API。

并行执行使用 Java 21 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`）。

## 类

```
graph.createGraph.engine
├── GraphEngine             ← 图执行引擎
└── GraphEngineProperties   ← 配置属性
```

## GraphEngine

```java
@Component
@RequiredArgsConstructor
public class GraphEngine {
    private final GraphEngineProperties properties;
}
```

### 执行方法

```java
// 同步执行
OverAllState invoke(CompiledGraph graph, OverAllState state,
                    String threadId, String checkPointId) throws GraphStateException;

OverAllState invoke(CompiledGraph graph, OverAllState state,
                    RunnableConfig config) throws GraphStateException;

// 流式执行
Flux<NodeOutput> stream(CompiledGraph graph, OverAllState state, String threadId);
```

### 状态查询

```java
StateSnapshot getState(CompiledGraph graph, RunnableConfig config);
StateSnapshot getState(CompiledGraph graph, String threadId);
```

### 时光旅行

```java
// 获取状态历史
Collection<StateSnapshot> getStateHistory(CompiledGraph graph, RunnableConfig config);
Collection<StateSnapshot> getStateHistory(CompiledGraph graph, String threadId);

// 列出检查点摘要
List<CheckpointSummary> listCheckpoints(CompiledGraph graph, String threadId);

// 获取指定检查点
Optional<StateSnapshot> getCheckpoint(CompiledGraph graph, String threadId, String checkpointId);

// 从历史检查点分支执行
OverAllState branchFromCheckpoint(CompiledGraph graph, StateSnapshot snapshot)
        throws GraphStateException;

// 更新历史状态并恢复执行
OverAllState updateAndResume(CompiledGraph graph, StateSnapshot snapshot,
                             Map<String, Object> updatedState) throws Exception;

// 更新状态（人类反馈注入）
RunnableConfig updateState(CompiledGraph graph, RunnableConfig config,
                           Map<String, Object> updatedState) throws Exception;
```

### CheckpointSummary

```java
public record CheckpointSummary(
    String node,
    String checkpointId,
    String nextNode,
    OverAllState state
) {}
```

## GraphEngineProperties

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

### 配置

```yaml
spring:
  threads:
    virtual:
      enabled: true    # 启用虚拟线程（并行执行必需）

graph:
  workflow:
    engine:
      default-checkpoint-strategy: memory
      parallel-executor-enabled: true   # 使用虚拟线程
      default-recursion-limit: 25
```

## 上游 CompiledGraph 真实签名对照

| 方法 | 参数 | 返回值 |
|------|------|--------|
| `invoke` | `(OverAllState, RunnableConfig)` | `Optional<OverAllState>` |
| `stream` | `(Map, RunnableConfig)` | `Flux<NodeOutput>` |
| `getStateHistory` | `(RunnableConfig)` | `Collection<StateSnapshot>` |
| `getState` | `(RunnableConfig)` | `StateSnapshot` |
| `updateState` | `(RunnableConfig, Map)` | `RunnableConfig` |

## 使用示例

```java
@Autowired
private GraphEngine engine;

// 执行
OverAllState result = engine.invoke(graph, state, "thread-1", null);

// 时光旅行
List<CheckpointSummary> cps = engine.listCheckpoints(graph, "thread-1");
StateSnapshot target = engine.getCheckpoint(graph, "thread-1", "cp-id").orElseThrow();
OverAllState branchResult = engine.branchFromCheckpoint(graph, target);

// 修改历史并恢复
OverAllState newResult = engine.updateAndResume(graph, target, Map.of("key", "newValue"));
```
