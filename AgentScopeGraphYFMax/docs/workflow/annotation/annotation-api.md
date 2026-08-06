# 注解 API

## 概述

框架提供 3 个注解，均为类级，继承 `@Component` 实现 Spring 自动注册。

## @GraphDefinition

图工作流声明注解。标记类为由 `GraphPoolManager` 管理的图模板。

```java
@Target(ElementType.TYPE) @Retention(RUNTIME) @Component
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

## @NodeAction

节点动作声明注解。标记类为可被 `NodeActionPool` 扫描注册的节点。

```java
@Target(ElementType.TYPE) @Retention(RUNTIME) @Component
public @interface NodeAction {
    String value() default "";         // 节点名称
    String description() default "";   // 描述
    String scope() default "prototype"; // prototype / singleton
}
```

## @EdgeCondition

边条件声明注解。标记类为可被 `EdgeConditionPool` 扫描注册的边动作。

```java
@Target(ElementType.TYPE) @Retention(RUNTIME) @Component
public @interface EdgeCondition {
    String value() default "";         // 边名称
    String description() default "";   // 描述
    String scope() default "prototype"; // prototype / singleton
}
```

## 实例化模式

| scope | 行为 | 适用场景 |
|-------|------|---------|
| `"prototype"`（默认） | 每次 `pool.get()` 创建新实例 | 有状态的节点/边 |
| `"singleton"` | 首次创建后缓存共享 | 无状态的节点/边 |
