# 故障排除指南

本文档提供 AgentScopeGraphYFMax 框架常见问题的诊断和解决方案。

---

## 问题 1：PostgreSQL 检查点保存器不可用

### 错误信息

```
java.lang.IllegalStateException: PostgreSQL 检查点保存器不可用，请检查数据库配置。
```

### 原因

`CheckpointFactory.create("postgres")` 找不到 `MyBatisFlexCheckpointSaver` Bean。可能原因：

1. `MyBatisFlexCheckpointSaver` 未被 Spring 扫描到
2. 数据库连接配置错误
3. MyBatis-Flex Mapper 未注册

### 解决方案

1. **确保组件扫描覆盖 DAL 层**：

```java
@SpringBootApplication(scanBasePackages = {"org.example", "com.your.package"})
```

2. **检查 MyBatis-Flex Mapper 扫描路径**：

```java
@MapperScan("org.example.dal.checkpoint.mapper")
```

3. **确认 PostgreSQL 数据源配置**：

```yaml
spring:
  datasource:
    # 确保 MyBatis-Flex 使用的数据源可访问 PostgreSQL
```

4. **执行 DDL 脚本创建表**：

```bash
psql -d your_database -f src/main/resources/db/checkpoint-postgresql.sql
```

5. **临时方案：改用内存检查点**：

```java
@GraphDefinition(checkpointStrategy = "memory")
```

---

## 问题 2：ConditionalEdgeBuilder.route() 参数错误

### 错误信息

```
java.lang.IllegalStateException: No route matched for value: xxx
```

### 原因

`ConditionalEdgeBuilder` 的 `route()` 方法的第一个参数（路由值）必须与路由函数的返回值完全匹配。

### 解决方案

```java
// 正确：route() 的 key 与路由函数返回值一致
builder.addNode("router", routerAction)
        .addConditionalEdges(state -> {
            String type = (String) state.value("type").orElse("");
            // 返回值必须与 route() 注册的 key 完全匹配
            return "typeA".equals(type) ? "branchA" : "branchB";
        })
        .route("branchA", "nodeA")    // key = "branchA"
        .route("branchB", "nodeB")    // key = "branchB"
        .done();
```

```java
// 错误：大小写不一致
.route("BranchA", "nodeA")           // 大写 B
// 路由函数返回 "branchA"（小写 b），匹配失败
```

### 注意事项

- `route()` 方法可以链式调用多次
- `done()` 必须被调用以完成构建，否则条件边不会注册
- 路由函数必须返回 `String` 类型
- 路由 action 签名为 `state -> String`（单参数 `OverAllState`）

---

## 问题 3：并行节点未实际并行执行

### 症状

使用 `addParallelBranches` 创建了扇出拓扑，但节点仍然是串行执行。

### 原因

未启用并行执行器。`GraphEngine` 需要在 `RunnableConfig` 中配置 `Executor` 才能实现真正的并行。

### 解决方案

在 `application.yml` 中启用虚拟线程和并行执行器：

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

### 验证

查看日志中是否有以下输出：

```
GraphEngine: 已注入虚拟线程执行器到 RunnableConfig
```

如果没有，检查 `parallel-executor-enabled` 是否为 `true` 以及 `spring.threads.virtual.enabled` 是否已启用。

---

## 问题 4：@GraphDefinition 注解的类未被发现

### 错误信息

```
java.lang.IllegalArgumentException: 未找到图: my-graph
```

### 原因

`GraphPoolManager` 扫描包路径不包含图定义类所在包。

### 解决方案

1. **检查 `scan-packages` 配置**：

```yaml
graph:
  workflow:
    scan-packages: org.example,com.your.package    # 逗号分隔多个包
```

2. **确认图定义类在扫描路径内**：

```
# 如果图定义在 com.example.graph.workflow 包下
# scan-packages 至少应包含 com.example
```

3. **确认注解的 `active` 属性为 `true`**（默认为 `true`）：

```java
@GraphDefinition(name = "my-graph", active = true)
```

4. **确认类继承了 `AbstractGraphTemplate`**：

`GraphPoolManager` 只扫描同时满足以下条件的类：
- 带有 `@GraphDefinition` 注解
- `active = true`
- 继承 `AbstractGraphTemplate`

---

## 问题 5：配置属性不生效

### 症状

在 `application.yml` 中配置了 `graph.workflow.engine.*`，但运行时未生效。

### 原因

配置前缀不正确，或 `GraphEngineProperties` 未被 Spring 管理。

### 解决方案

1. **确认配置前缀**：必须是 `graph.workflow.engine`

```yaml
# 正确
graph:
  workflow:
    engine:
      parallel-executor-enabled: true

# 错误（前缀不对）
spring:
  ai:
    alibaba:
      graph:
        engine:
          parallel-executor-enabled: true
```

2. **确认 `GraphEngineProperties` Bean 存在**：

`GraphEngineProperties` 使用了 `@Component` + `@ConfigurationProperties(prefix = "graph.workflow.engine")`，需要被组件扫描覆盖。Spring Boot 会自动发现 `@Component` 和 `@ConfigurationProperties` 注解的类。

3. **确认扫描路径覆盖框架包**：

主应用类的 `scanBasePackages` 必须包含 `org.example.graph.workflow`。

---

## 问题 6：编译图失败 (GraphStateException)

### 错误信息

```
com.alibaba.cloud.ai.graph.exception.GraphStateException: ...
```

### 常见原因

1. **缺少 END 边**：图中至少有一个节点必须有到 `StateGraph.END` 的边

```java
// 正确：最后一个节点连接到 END
builder.addNode("lastNode", action)
        .addEdge(StateGraph.END);
```

2. **引用了不存在的节点**：`addEdge()` 的目标节点必须已经通过 `addNode()` 添加

```java
// 正确：先添加节点，再连接边
builder.addNode("a", actionA)
        .addEdge("b")
        .addNode("b", actionB)        // b 已存在
        .addEdge(StateGraph.END);
```

3. **条件边未调用 `done()`**：

```java
// 正确
builder.addNode("source", action)
        .addConditionalEdges(routingAction)
        .route("value1", "target1")
        .done();                      // 必须调用

// 错误：忘记调用 done()
builder.addNode("source", action)
        .addConditionalEdges(routingAction)
        .route("value1", "target1");
// 条件边未注册！
```

4. **并行分支名称冲突**：`addParallelBranches` 的扇出节点名和合并节点名不能与已有节点名冲突

```java
// 确保 fanoutName 和 mergeName 唯一
builder.addParallelBranches("my-fanout", branches, "my-merge", mergeAction);
```

---

## 问题 7：图编译成功但 invoke 返回 null

### 原因

`GraphEngine.invoke()` 内部调用 `graph.invoke(state, config).orElse(null)`，如果上游返回空 `Optional`，则返回 `null`。

### 解决方案

1. **检查初始状态是否为空**：

```java
// 正确：提供有意义的初始状态
OverAllState state = new OverAllState();
state = state.input(Map.of("question", "什么是 AI?"));

// 可能导致问题：空状态
OverAllState state = new OverAllState();
```

2. **检查节点函数是否正确返回 Map**：

```java
// 正确：返回 Map<String, Object>
return CompletableFuture.completedFuture(Map.of("result", "processed"));

// 错误：返回 state 对象而非 Map（上游期望 Map）
state.updateState(Map.of("result", "processed"));
return CompletableFuture.completedFuture(state);  // 类型不匹配
```

---

## 问题 8：子图节点执行失败

### 错误信息

```
java.lang.IllegalStateException: Subgraph node execution failed
```

### 原因

子图 `StateGraph` 缺少必要的键策略或子图内部节点引用了不存在的状态键。

### 解决方案

使用 `buildStateGraph()` 创建子图时，确保子图类的 `setupStateKeyFactory()` 正确配置：

```java
@GraphDefinition(name = "my-subgraph")
public class MySubgraph extends AbstractGraphTemplate {

    public MySubgraph(GraphComponentFacade components) { super(components); }

    @Override
    protected OverAllState initialState() { return new OverAllState(); }

    // 如果需要自定义键策略
    @Override
    protected KeyStrategyFactory setupStateKeyFactory() {
        return components.stateKey().builder()
                .addStrategy("messages", new AppendStrategy())
                .defaultStrategy(new ReplaceStrategy())
                .build();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 子图拓扑
    }
}
```

---

## 问题 9：GraphPoolManager.invokeGraph 抛出 IllegalStateException

### 错误信息

```
java.lang.IllegalStateException: GraphEngine 不可用，无法调用图。
```

### 原因

`GraphEngine` Bean 未被注入到 `GraphPoolManager` 中。`GraphEngine` 使用了 `@Autowired(required = false)`，如果 Bean 不存在则为 `null`。

### 解决方案

确认 `GraphEngine` 及其依赖 `GraphEngineProperties` 都在组件扫描路径内。Spring Boot 自动发现 `@Component` 注解的类，无需额外配置类。

---

## 问题 10：类路径冲突或依赖版本不匹配

### 错误信息

```
java.lang.NoSuchMethodError / java.lang.ClassNotFoundException
```

### 解决方案

1. 检查依赖树：

```bash
mvn dependency:tree | grep spring-ai-alibaba
```

2. 确保所有 `spring-ai-alibaba-graph` 相关依赖版本一致

3. 排除冲突依赖：

```xml
<dependency>
    <groupId>your.group</groupId>
    <artifactId>your-artifact</artifactId>
    <exclusions>
        <exclusion>
            <groupId>conflicting.group</groupId>
            <artifactId>conflicting-artifact</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## 问题 11：时光旅行 API 返回空结果

### 症状

`listCheckpoints()` 或 `getStateHistory()` 返回空列表。

### 原因

1. threadId 不匹配 — 确保传入的 threadId 与执行时使用的完全一致
2. 使用了内存检查点 — `MemorySaver` 在应用重启后丢失
3. 检查点已被清理

### 解决方案

```java
// 确保 threadId 一致
String threadId = "thread-1";  // 与执行时相同
List<CheckpointSummary> cps = engine.listCheckpoints(graph, threadId);

// 生产环境使用 PostgreSQL 检查点
@GraphDefinition(checkpointStrategy = "postgres")
```

---

## 调试技巧

### 启用详细日志

```yaml
logging:
  level:
    org.example.graph.workflow: DEBUG
    com.alibaba.cloud.ai.graph: DEBUG
```

### 查看已注册的图

```java
@Autowired
private GraphPoolManager poolManager;

@GetMapping("/debug/graphs")
public Map<String, Object> listGraphs() {
    Map<String, Object> result = new HashMap<>();
    poolManager.getMetadataRegistry().forEach((name, meta) -> {
        result.put(name, Map.of(
                "group", meta.getGroup(),
                "description", meta.getDescription(),
                "checkpoint", meta.getCheckpointStrategy(),
                "lazy", meta.isLazy(),
                "scope", meta.getScope()
        ));
    });
    return result;
}
```

### 使用时光旅行查看状态历史

```java
List<CheckpointSummary> cps = engine.listCheckpoints(graph, "thread-1");
for (CheckpointSummary cp : cps) {
    log.info("检查点: {}, 节点: {}", cp.id(), cp.nodeId());
    Optional<StateSnapshot> ss = engine.getCheckpoint(graph, "thread-1", cp.id());
    ss.ifPresent(snapshot -> log.info("状态: {}", snapshot.state()));
}
```

### 调试节点函数

```java
builder.addNode("debugNode", state -> {
    // 打印所有状态
    state.data().forEach((key, value) ->
            log.debug("状态键[{}] = {}", key, value));

    // 正常处理
    return CompletableFuture.completedFuture(Map.of("debugDone", true));
});
```

---

## 配置参考

### 完整配置

```yaml
spring:
  threads:
    virtual:
      enabled: true                               # 必须启用，用于并行执行

graph:
  workflow:
    scan-packages: org.example                    # 扫描包路径（逗号分隔）
    engine:
      default-recursion-limit: 25                 # 默认递归限制
      parallel-executor-enabled: true             # 使用虚拟线程，无需配置线程池大小
      default-checkpoint-strategy: memory         # 默认检查点策略
```

### 日志配置

```yaml
logging:
  level:
    org.example.graph.workflow: DEBUG
    com.alibaba.cloud.ai.graph: DEBUG
    org.springframework.web.client: DEBUG
```

---

## 相关文档

- [API 参考](api-reference.md) — 完整 API 文档
- [快速开始](quick-start.md) — 快速上手指南
- [最佳实践](best-practices.md) — 生产环境最佳实践
- [架构设计](architecture.md) — 框架架构设计
