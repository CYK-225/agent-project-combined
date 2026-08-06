# 边动作 API

## 概述

边动作定义图中节点之间的路由逻辑。

## 类

```
graph.createGraph.edge
└── SimpleEdgeAction       ← 抽象基类（推荐）

graph.workflow.core
└── EdgeConditionPool      ← 边动作池（扫描 @EdgeCondition）
```

## SimpleEdgeAction

抽象基类，自动包装为 `AsyncEdgeAction`。子类只需实现 `execute()`。

```java
public abstract class SimpleEdgeAction implements AsyncEdgeAction {
    // 子类实现：返回目标节点名称（String）
    protected abstract String execute(OverAllState state) throws Exception;

    // 框架自动包装为 CompletableFuture<String>
    @Override
    public final CompletableFuture<String> apply(OverAllState state) { ... }
}
```

## @EdgeCondition 注解

类级注解，继承 `@Component`。标记类为可被 `EdgeConditionPool` 扫描注册的边动作。

```java
@Target(ElementType.TYPE) @Retention(RUNTIME) @Component
public @interface EdgeCondition {
    String value() default "";         // 边名称（默认类简单名）
    String description() default "";   // 描述
    String scope() default "prototype"; // prototype / singleton
}
```

## EdgeConditionPool

自动扫描 `@EdgeCondition` 注解类，建立 name → class 映射。

```java
@Component
public class EdgeConditionPool {
    public AsyncEdgeAction get(String name);     // 获取实例
    public boolean exists(String name);           // 检查是否注册
    public String getDescription(String name);   // 获取描述
    public Map<String, String> listAll();         // 列出全部
    public Set<String> getRegisteredNames();      // 获取名称集合
}
```

## 使用方式

### 定义边类

```java
@EdgeCondition(value = "type-router", description = "输入类型路由")
public class TypeRouterEdge extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.contains("?")) return "question-handler";
        if (input.startsWith("!")) return "command-handler";
        return "default-handler";
    }
}
```

### 通过池引用

```java
builder.addNode("dispatcher", dispatchAction)
       .addConditionalEdges("type-router", components.edgeConditions())
       .route("question-handler", "question-handler")
       .route("command-handler", "command-handler")
       .route("default-handler", "default-handler")
       .done();
```

### 直接 lambda

```java
builder.addNode("router", routerAction)
       .addConditionalEdges(state ->
           CompletableFuture.completedFuture(
               (String) state.value("type").orElse("default")))
       .route("question", "answer")
       .route("statement", "ack")
       .done();
```
