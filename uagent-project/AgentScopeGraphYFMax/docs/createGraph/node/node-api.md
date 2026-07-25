# 节点动作 API

## 概述

节点动作是图中每个处理步骤的逻辑单元。

## 类

```
graph.createGraph.node
├── SimpleNodeAction      ← 抽象基类（推荐）
└── StreamingNodeAction   ← 流式节点

graph.workflow.core
└── NodeActionPool        ← 节点动作池（扫描 @NodeAction）
```

## SimpleNodeAction

抽象基类，自动包装为 `AsyncNodeAction`。子类只需实现 `execute()`。

```java
public abstract class SimpleNodeAction implements AsyncNodeAction {
    // 子类实现：返回 Map<String, Object>
    protected abstract Map<String, Object> execute(OverAllState state) throws Exception;

    // 框架自动包装为 CompletableFuture<Map>（无需手动处理）
    @Override
    public final CompletableFuture<Map<String, Object>> apply(OverAllState state) { ... }
}
```

## @NodeAction 注解

类级注解，继承 `@Component`。标记类为可被 `NodeActionPool` 扫描注册的节点。

```java
@Target(ElementType.TYPE) @Retention(RUNTIME) @Component
public @interface NodeAction {
    String value() default "";         // 节点名称（默认类简单名）
    String description() default "";   // 描述
    String scope() default "prototype"; // prototype / singleton
}
```

## NodeActionPool

自动扫描 `@NodeAction` 注解类，建立 name → class 映射。

```java
@Component
public class NodeActionPool {
    public AsyncNodeAction get(String name);     // 获取实例
    public boolean exists(String name);           // 检查是否注册
    public String getDescription(String name);   // 获取描述
    public Map<String, String> listAll();         // 列出全部
    public Set<String> getRegisteredNames();      // 获取名称集合
}
```

## 使用方式

### 定义节点类

```java
@NodeAction(value = "validate", description = "输入验证节点")
public class ValidateNode extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("validated", true, "cleanInput", input.trim());
    }
}
```

### 通过池引用

```java
// 在 buildGraph() 中
builder.addNode("validate", components.nodeActions())  // 从 NodeActionPool 获取
       .addEdge("process")
       .addNode("process", components.nodeActions());
```

### 直接 lambda

```java
builder.addNode("analyze", state -> {
    Map<String, Object> update = new HashMap<>();
    update.put("result", "分析完成");
    return CompletableFuture.completedFuture(update);
});
```
