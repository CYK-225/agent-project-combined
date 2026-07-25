# pool — 池化注册机制

本包介绍 `@NodeAction` / `@EdgeCondition` 注解如何将节点和边注册到全局池，
以及 `GraphBuilder` 如何通过池按名称引用。

前置知识：[basics 包](../basics/README.md)的节点、边、GraphBuilder 基础 API。

---

## 1. 为什么需要池

在 [basics](../basics/README.md) 中，节点动作直接写在图定义里：

```java
// 直接写 lambda — 逻辑耦合在图定义中
builder.addNode("analyze", state -> {
    return CompletableFuture.completedFuture(Map.of(...));
});
```

**问题**：
- 同一逻辑在多个图中要重复写
- 节点名称拼错不会在编译期发现
- 无法对单个节点做单元测试

**池化方案**：将节点/边声明为独立类，通过注解注册，图定义按名称引用。

---

## 2. 两个注解

### @NodeAction — 注册节点动作

```java
@NodeAction(value = "validate", description = "输入验证", scope = "prototype")
public class ValidateNode extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) { ... }
}
```

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `value` | 节点唯一名称（池中的 key） | 类名 |
| `description` | 人工可读描述 | `""` |
| `scope` | `"prototype"` = 每次获取新建实例<br>`"singleton"` = 共享单例 | `"prototype"` |

> `scope = "prototype"` 适合有状态的节点（如计数器）；
> `scope = "singleton"` 适合无状态的纯函数节点。

### @EdgeCondition — 注册条件边路由

```java
@EdgeCondition(value = "type-router", description = "输入类型路由")
public class TypeRouterEdge extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) {
        return state.value("input").orElse("").contains("?")
            ? "question-handler" : "default-handler";
    }
}
```

属性与 `@NodeAction` 一致（value / description / scope）。

---

## 3. GraphBuilder 池感知 API

### 3.1 池引用添加节点

```java
// basics 中的方式：直接传入动作
builder.addNode("validate", action);

// 池引用：从 NodeActionPool 按名称获取
builder.addNode("validate", components.nodeActions());
```

`addNode(name, pool)` 内部等价于 `addNode(name, pool.get(name))`。
名称在池中不存在时，应用启动即报错 — 比运行时拼错更安全。

### 3.2 池引用添加条件边（两种重载）

```java
// 重载一：edgeName 同时作为池名称和源节点名
builder.addConditionalEdges("type-router", components.edgeConditions())
       .route("question-handler", "question-handler")
       .route("default-handler", "default-handler")
       .done();

// 重载二：useLastNode=true 时使用隐式源节点（lastNodeName）
builder.addNode("source", components.nodeActions())        // lastNodeName = "source"
       .addConditionalEdges("type-router", components.edgeConditions(), true)
       .route("question-handler", "question-handler")
       .done();
```

两个重载的区别仅在**源节点的确定方式**：
- 重载一：`edgeName` 既是池查找 key，也是源节点名
- 重载二：`edgeName` 仅做池查找，源节点由 `useLastNode` 控制

### 3.3 GraphComponentFacade — 池的访问入口

通过构造函数注入的 `components` 提供所有池的访问：

```java
components.nodeActions()     // NodeActionPool
components.edgeConditions()  // EdgeConditionPool
components.checkpoint()      // CheckpointFactory
components.stateKey()        // StateKeyFactory
components.pool()            // GraphPoolManager
components.engine()          // GraphEngine
```

---

## 4. 跨包复用

本包的 `AnnotatedNodeExample` 引用了 [basics](../basics/README.md) 中的三个节点：
`ValidateNode("validate")` → `ProcessNode("process")` → `OutputNode("output")`。

这些节点定义在 basics 包，注册到全局池，本包的图按名称引用 — 正是池化机制的价值：
**节点类只需定义一次，可被任意数量的图复用**。

---

## 5. 本包文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `AnnotatedNodeExample` | 图定义 | 顺序图全部节点从池引用（对比 basics/SequentialQaGraph） |
| `AnnotatedEdgeExample` | 图定义 | 条件路由从 EdgeConditionPool 引用 |
| `TypeRouterEdge` | @EdgeCondition("type-router") | 输入类型路由 |
| `QuestionHandlerNode` | @NodeAction("question-handler") | 问题处理器 |
| `CommandHandlerNode` | @NodeAction("command-handler") | 命令处理器 |
| `DefaultHandlerNode` | @NodeAction("default-handler") | 默认处理器 |

---

## 下一步

- 条件分支 + 人机交互 → [conditional 包](../conditional/README.md)
- 并行扇出 + 池引用 → [parallel 包](../parallel/README.md)
- 全部配置选项速查 → [config 包](../config/README.md)
