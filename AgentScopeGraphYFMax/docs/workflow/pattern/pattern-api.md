# 工作流模式 API

## 概述

4 种预定义拓扑模式，通过 `GraphPattern` 接口复用。

## 类

```
graph.workflow.pattern
├── GraphPattern              ← 策略接口
├── SequentialGraphPattern    ← 顺序流水线
├── ConditionalGraphPattern   ← 条件分支
├── FanOutGraphPattern        ← 扇出并行
└── MultiAgentGraphPattern    ← 多智能体协作
```

## GraphPattern 接口

```java
public interface GraphPattern {
    String getName();
    String getDescription();
    void apply(GraphBuilder builder) throws GraphStateException;
}
```

## SequentialGraphPattern

顺序流水线：`node1 -> node2 -> ... -> nodeN -> END`

```java
SequentialGraphPattern.builder()
    .name("pipeline")
    .description("三步流水线")
    .nodeNames(List.of("step1", "step2", "step3"))
    .nodeActions(List.of(action1, action2, action3))
    .build()
    .apply(builder);
```

## ConditionalGraphPattern

条件分支：`source -> [condition] -> branchA | branchB -> END`

```java
ConditionalGraphPattern.builder()
    .name("branching")
    .sourceNode("router")
    .sourceAction(routerAction)
    .routingAction(routingAction)
    .routeMap(Map.of("a", "handlerA", "b", "handlerB"))
    .branchActions(Map.of("handlerA", actionA, "handlerB", actionB))
    .build()
    .apply(builder);
```

## FanOutGraphPattern

扇出并行：`fanout -> [branch1, branch2, ...] -> merge -> END`

```java
FanOutGraphPattern.builder()
    .name("parallel")
    .fanoutNodeName("fanout")
    .mergeNodeName("merge")
    .branchActions(Map.of("fin", finAction, "tech", techAction))
    .mergeAction(mergeAction)
    .build()
    .apply(builder);
```

## MultiAgentGraphPattern

多智能体协作：`agent1 -> agent2 -> ... -> judge -> [continue | end]`

```java
MultiAgentGraphPattern.builder()
    .name("debate")
    .agentActions(Map.of("agent1", action1, "agent2", action2))
    .judgeNode("judge")
    .judgeAction(judgeAction)
    .judgeRoutingAction(routingAction)
    .firstAgent("agent1")
    .build()
    .apply(builder);
```
