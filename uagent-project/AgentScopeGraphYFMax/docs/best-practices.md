# 最佳实践指南

本文档提供 AgentScopeGraphYFMax 框架在生产环境中的最佳实践。

---

## 1. 依赖注入：使用 GraphComponentFacade

所有图定义类应通过 `GraphComponentFacade` 获取工厂和服务，而非自行创建实例。

```java
// 正确：通过构造函数注入 GraphComponentFacade
@GraphDefinition(name = "my-graph")
public class MyGraph extends AbstractGraphTemplate {

    public MyGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 使用 facade 获取工厂和池
        KeyStrategyFactory ksf = components.stateKey().defaultFactory();
        BaseCheckpointSaver saver = components.checkpoint().create("postgres");
        AsyncNodeAction action = components.nodeActions().get("validate");
        AsyncEdgeAction edge = components.edgeConditions().get("type-router");
    }
}
```

### 为什么？

- `GraphComponentFacade` 是 Spring 管理的单例，所有工厂和池共享同一实例
- `CheckpointFactory` 内部缓存 `MemorySaver` 单例，避免重复创建
- `NodeActionPool` / `EdgeConditionPool` 在启动时扫描注册，运行时按需实例化
- `GraphEngine` 持有 `GraphEngineProperties` 配置，手动创建会丢失并行执行器设置

---

## 2. 节点和边动作的最佳实践

### 小型项目：推荐使用 SimpleNodeAction / SimpleEdgeAction

对于可复用的节点和边逻辑，使用 `SimpleNodeAction` / `SimpleEdgeAction` 抽象类：

```java
// 可复用节点
public class AnalyzeAction extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("analysis", "已分析: " + input);
    }
}

// 可复用边
public class TypeRouter extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) throws Exception {
        String type = (String) state.value("type").orElse("default");
        return switch (type) {
            case "question" -> "answerNode";
            case "command"  -> "executeNode";
            default         -> "fallbackNode";
        };
    }
}
```

### 大型项目：推荐使用 @NodeAction / @EdgeCondition 池引用

将节点和边定义为独立类，通过注解注册到动作池，在图定义中按名称引用：

```java
// 独立节点类
@NodeAction(value = "validate", description = "输入验证节点")
public class ValidateNode extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.isBlank()) throw new IllegalArgumentException("输入不能为空");
        return Map.of("validated", true, "cleanInput", input.trim());
    }
}

// 独立边类
@EdgeCondition(value = "type-router", description = "输入类型路由")
public class TypeRouterEdge extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return input.contains("?") ? "question-handler" : "default-handler";
    }
}

// 在 buildGraph() 中通过池引用
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("validate", components.nodeActions())
            .addEdge("dispatch")
            .addNode("dispatch", dispatchAction)
            .addConditionalEdges("type-router", components.edgeConditions())
            .route("question-handler", "question-handler")
            .route("default-handler", "default-handler")
            .done();
}
```

**池引用的优点：**
- 节点/边类可独立测试、可复用于多个图
- 名称由注解定义，保证一致性
- `prototype` 模式每次新建，避免状态污染
- 引用不存在的名称时启动报错，快速发现问题

### 一次性逻辑使用 Lambda

```java
// 一次性简单逻辑
builder.addNode("greet", state -> {
    String name = (String) state.value("name").orElse("World");
    return CompletableFuture.completedFuture(Map.of("greeting", "Hello, " + name));
});
```

---

## 3. 检查点策略选择

| 环境 | 策略 | 说明 |
|------|------|------|
| 开发/测试 | `checkpointStrategy = "memory"` | 进程内，无持久化，重启丢失 |
| 生产环境 | `checkpointStrategy = "postgres"` | PostgreSQL 持久化，支持恢复 |
| 无需检查点 | `checkpointStrategy = ""` | 无检查点，最轻量 |

### 内存策略注意事项

```java
// 内存策略适合单元测试和快速原型
@GraphDefinition(
        name = "test-graph",
        checkpointStrategy = "memory"
)
```

- `MemorySaver` 是进程内的，应用重启后所有检查点丢失
- `CheckpointFactory` 内部对 `MemorySaver` 做了单例缓存，同一策略名称返回同一实例
- 不要在生产环境使用内存策略，否则人类反馈中断后无法恢复

### PostgreSQL 策略注意事项

```java
// 生产环境使用 PostgreSQL
@GraphDefinition(
        name = "production-graph",
        checkpointStrategy = "postgres"
)
```

- 必须先执行 `checkpoint-postgresql.sql` 创建 `graph_checkpoint` 和 `graph_checkpoint_blob` 双表
- 确保 MyBatis-Flex 数据源配置正确
- 如果 `MyBatisFlexCheckpointSaver` 未被 Spring 扫描到，`CheckpointFactory.create("postgres")` 会抛出 `IllegalStateException`
- 建议定期清理过期检查点数据

### 自定义检查点保存器

如果需要其他后端（如 Redis、S3），重写 `setupCheckpointSaver()`：

```java
@Override
protected BaseCheckpointSaver setupCheckpointSaver(GraphDefinition def) {
    // 返回自定义实现
    return new MyCustomCheckpointSaver();
}
```

---

## 4. 并行执行配置

### 启用并行执行器

并行图（`FanOutGraphPattern`、`addParallelBranches`）需要在 `RunnableConfig` 中配置 `Executor`。框架通过 `GraphEngine` 自动注入虚拟线程执行器：

```yaml
spring:
  threads:
    virtual:
      enabled: true    # 必须启用，用于并行执行

graph:
  workflow:
    engine:
      parallel-executor-enabled: true   # 使用虚拟线程，无需配置线程池大小
```

> **虚拟线程：** 并行执行使用 Java 21 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`），无需配置线程池大小。需在 `application.yml` 中启用 `spring.threads.virtual.enabled: true`。

### 虚拟线程的优势

| 特性 | 说明 |
|------|------|
| 无需配置线程池大小 | 虚拟线程由 JVM 自动管理，按需创建和销毁 |
| 适合 IO 密集型任务 | IO 阻塞时虚拟线程自动挂起，不占用平台线程 |
| 低内存开销 | 每个虚拟线程仅占用少量内存 |
| Java 21 原生支持 | 无需引入额外依赖 |

### 禁用并行执行器

如果不需要并行执行（所有图都是顺序的），可以禁用以节省资源：

```yaml
graph:
  workflow:
    engine:
      parallel-executor-enabled: false  # 不创建虚拟线程执行器
```

---

## 5. 命名规范

### 图名称

```java
// 推荐：使用 kebab-case，简洁明了
@GraphDefinition(name = "order-processing")
@GraphDefinition(name = "human-approval")
@GraphDefinition(name = "parallel-analysis")

// 不推荐：使用驼峰或下划线
@GraphDefinition(name = "OrderProcessing")
@GraphDefinition(name = "order_processing")
```

### 节点名称

```java
// 推荐：使用 camelCase，描述动作
builder.addNode("validateInput", validateAction);
builder.addNode("processOrder", processAction);
builder.addNode("sendNotification", notifyAction);

// 不推荐：使用无意义的名称
builder.addNode("node1", action1);
builder.addNode("step_a", actionA);
```

### @NodeAction / @EdgeCondition 名称

```java
// 推荐：使用 kebab-case，与图定义中的引用名一致
@NodeAction(value = "validate-input", description = "输入验证")
@EdgeCondition(value = "type-router", description = "类型路由")

// 不推荐：使用驼峰或无描述
@NodeAction(value = "ValidateInput")
@EdgeCondition(value = "router")
```

### 状态键

```java
// 推荐：使用 camelCase，语义化
state.value("orderData")
state.value("validationResult")
state.value("humanFeedback")

// 不推荐：使用模糊的名称
state.value("data")
state.value("result")
state.value("flag")
```

### 分组

```java
// 推荐：按业务域或环境分组
@GraphDefinition(name = "validate-order", group = "order-domain")
@GraphDefinition(name = "send-email", group = "notification")

// 用于批量获取
List<CompiledGraph> orderGraphs = poolManager.getGraphsByGroup("order-domain");
```

---

## 6. 错误处理

### 节点函数异常处理

```java
// 推荐：继承 SimpleNodeAction，异常自动包装
public class RiskyOperationAction extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        String result = doRiskyWork(input);
        return Map.of("result", result, "status", "success");
        // 异常会被框架自动捕获并包装为 CompletableFuture.failedFuture(e)
    }
}
```

```java
// Lambda 方式：手动捕获异常
builder.addNode("riskyOperation", state -> {
    try {
        String input = (String) state.value("input").orElse("");
        String result = doRiskyWork(input);
        return CompletableFuture.completedFuture(Map.of("result", result, "status", "success"));
    } catch (Exception e) {
        return CompletableFuture.completedFuture(Map.of("error", e.getMessage(), "status", "failed"));
    }
});
```

### 条件分支处理错误状态

```java
builder.addNode("process", processAction)
        .addConditionalEdges(state -> {
            String status = (String) state.value("status").orElse("unknown");
            return switch (status) {
                case "success" -> "next";
                case "failed" -> "error-handler";
                default -> "error-handler";
            };
        })
        .route("next", "nextStep")
        .route("error-handler", "handleError")
        .done();
```

### 重试模式

```java
// 使用 SimpleNodeAction 跟踪重试次数
public class RetryableAction extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        int retryCount = (int) state.value("retryCount").orElse(0);
        try {
            String result = doWork();
            return Map.of("result", result, "retryCount", 0);
        } catch (Exception e) {
            return Map.of("retryCount", retryCount + 1, "lastError", e.getMessage());
        }
    }
}

// 在 buildGraph() 中使用
builder.addNode("retryableStep", new RetryableAction())
        .addConditionalEdges(state -> {
            int retryCount = (int) state.value("retryCount").orElse(0);
            String result = (String) state.value("result").orElse(null);
            if (result != null) return "success";
            if (retryCount < 3) return "retry";
            return "failed";
        })
        .route("success", "nextStep")
        .route("retry", "retryableStep")   // 自环重试
        .route("failed", "errorHandler")
        .done();
```

---

## 7. 人类反馈（Human-in-the-Loop）

### 基本模式

```java
@GraphDefinition(
        name = "approval-flow",
        checkpointStrategy = "postgres",         // 必须使用持久化检查点
        interruptBefore = {"humanReview"}        // 在指定节点前中断
)
```

### 关键要点

1. **必须使用持久化检查点**（`"postgres"`） -- 中断后应用可能重启，内存检查点会丢失
2. **使用语义化的 threadId** -- 方便按业务实体（如订单ID）恢复执行
3. **通过 `GraphEngine.updateState()` 注入反馈** -- 而非直接修改状态

```java
// 1. 首次执行（中断）
String threadId = "order-" + orderId;
poolManager.invokeGraph("approval-flow", state, threadId);

// 2. 人类审核后注入反馈
CompiledGraph graph = poolManager.getGraph("approval-flow");
RunnableConfig config = RunnableConfigBuilder.create().threadId(threadId).build();
Map<String, Object> feedback = Map.of("humanFeedback", "approved");
RunnableConfig updatedConfig = engine.updateState(graph, config, feedback);

// 3. 恢复执行
engine.invoke(graph, state, updatedConfig);
```

### 多级审批

```java
@GraphDefinition(
        name = "multi-level-approval",
        interruptBefore = {"managerReview", "directorReview"},
        checkpointStrategy = "postgres"
)
```

---

## 8. 子图设计

### 使用 buildStateGraph() 创建子图

```java
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    // 创建子图 StateGraph
    StateGraph researchSubgraph = new ResearchSubgraph(components).buildStateGraph();

    // 将子图作为节点添加到父图
    builder.addNode("preprocess", preprocessAction)
            .addEdge("research")
            .addSubgraphNode("research", researchSubgraph)
            .addEdge("postprocess")
            .addNode("postprocess", postprocessAction)
            .addEdge(StateGraph.END);
}
```

### 子图设计原则

- 子图应有明确的输入/输出约定（通过状态键）
- 子图内部状态键与父图共享 `OverAllState`，注意键名不要冲突
- 子图可以嵌套（子图中再包含子图）
- 三种子图创建方式：`buildStateGraph()`、`pool().getGraph()`、手动 `compile()`

---

## 9. 状态管理

### StateKeyFactory 策略选择

```java
// 默认：所有键使用 ReplaceStrategy（后写覆盖先写）
KeyStrategyFactory ksf = components.stateKey().defaultFactory();

// 自定义：部分键使用 AppendStrategy（追加到列表）
KeyStrategyFactory ksf = components.stateKey().builder()
        .addStrategy("question", new ReplaceStrategy())
        .addStrategy("answer", new ReplaceStrategy())
        .addStrategy("history", new AppendStrategy())
        .addStrategy("messages", new AppendStrategy())
        .defaultStrategy(new ReplaceStrategy())
        .build();
```

### 状态键设计原则

- 使用语义化名称，避免歧义
- 读取时使用 `state.value(key).orElse(default)` 提供默认值
- 更新时使用 `state.updateState(Map)` 批量更新
- 避免在状态中存储大型对象（如图片、文件内容），使用引用（URL、ID）

---

## 10. 配置管理

### 完整配置示例

```yaml
spring:
  threads:
    virtual:
      enabled: true                             # 必须启用，用于并行执行

graph:
  workflow:
    scan-packages: com.example.graph,org.example  # 扫描包路径（逗号分隔）
    engine:
      default-recursion-limit: 25                 # 递归限制
      parallel-executor-enabled: true             # 使用虚拟线程，无需配置线程池大小
      default-checkpoint-strategy: postgres       # 默认检查点策略
```

### 多环境配置

```yaml
# application-dev.yml
graph:
  workflow:
    engine:
      default-checkpoint-strategy: memory         # 开发用内存

# application-prod.yml
spring:
  threads:
    virtual:
      enabled: true

graph:
  workflow:
    engine:
      default-checkpoint-strategy: postgres       # 生产用 PostgreSQL
      parallel-executor-enabled: true
```

---

## 11. 日志与调试

### 启用框架日志

```yaml
logging:
  level:
    org.example.graph.workflow: DEBUG              # 框架日志
    com.alibaba.cloud.ai.graph: DEBUG              # 上游库日志
```

### 使用时光旅行调试

```java
// 获取检查点列表
List<CheckpointSummary> cps = engine.listCheckpoints(graph, "thread-1");
for (CheckpointSummary cp : cps) {
    System.out.println("节点: " + cp.node() + ", 检查点ID: " + cp.checkpointId());
}

// 获取状态历史
Collection<StateSnapshot> history = engine.getStateHistory(graph, "thread-1");
for (StateSnapshot snapshot : history) {
    System.out.println("检查点ID: " + snapshot.config().checkPointId().orElse(""));
    System.out.println("状态: " + snapshot.state());
}
```

### 查看已注册的动作池

```java
@Autowired
private NodeActionPool nodeActionPool;
@Autowired
private EdgeConditionPool edgeConditionPool;

@GetMapping("/debug/pools")
public Map<String, Object> listPools() {
    Map<String, Object> result = new HashMap<>();
    result.put("nodeActions", nodeActionPool.listAll());
    result.put("edgeConditions", edgeConditionPool.listAll());
    return result;
}
```

---

## 检查清单

### 开发阶段

- [ ] 图定义类继承 `AbstractGraphTemplate`
- [ ] 使用 `@GraphDefinition` 注解声明
- [ ] 通过 `GraphComponentFacade` 注入工厂
- [ ] 状态键使用语义化名称
- [ ] 可复用节点使用 `SimpleNodeAction` 或 `@NodeAction` 注解
- [ ] 可复用边使用 `SimpleEdgeAction` 或 `@EdgeCondition` 注解
- [ ] 节点函数包含异常处理

### 测试阶段

- [ ] 使用 `checkpointStrategy = "memory"` 进行测试
- [ ] 测试条件分支的所有路径
- [ ] 测试并行执行（启用 `parallel-executor-enabled`）
- [ ] 测试人类反馈中断和恢复流程
- [ ] 测试 `@NodeAction` / `@EdgeCondition` 池引用的正确性

### 部署阶段

- [ ] 生产环境使用 `checkpointStrategy = "postgres"`
- [ ] 已执行 PostgreSQL DDL 脚本
- [ ] 配置正确的 `scan-packages`（覆盖 `@GraphDefinition`、`@NodeAction`、`@EdgeCondition` 所在包）
- [ ] 启用适当的日志级别
- [ ] 并行执行已启用 `spring.threads.virtual.enabled: true`

---

## 相关文档

- [故障排除](troubleshooting.md) -- 常见问题解答
- [API 参考](api-reference.md) -- 完整 API 文档
- [图模式](workflow-patterns.md) -- 4 种图模式详解
- [快速开始](quick-start.md) -- 快速上手指南
