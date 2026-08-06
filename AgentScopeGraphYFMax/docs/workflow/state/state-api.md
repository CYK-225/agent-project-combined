# 状态键策略 API

## 概述

定义图状态中每个键的合并策略。

## 类

```
graph.workflow.state
└── StateKeyFactory
```

## StateKeyFactory

```java
@Component
public class StateKeyFactory {
    // 默认工厂（所有键使用 ReplaceStrategy）
    public KeyStrategyFactory defaultFactory();

    // 返回上游 KeyStrategyFactoryBuilder
    public KeyStrategyFactoryBuilder builder();
}
```

## KeyStrategyFactoryBuilder（上游）

```java
KeyStrategyFactoryBuilder addStrategy(String key, KeyStrategy strategy);
KeyStrategyFactoryBuilder addStrategy(String key);            // 使用默认策略
KeyStrategyFactoryBuilder defaultStrategy(KeyStrategy strategy);
KeyStrategyFactoryBuilder addStrategies(Map<String, KeyStrategy> strategies);
KeyStrategyFactoryBuilder build();                             // → KeyStrategyFactory
```

## 上游 KeyStrategyFactory 签名

```java
// 无参数，返回 Map<String, KeyStrategy>
Map<String, KeyStrategy> apply();
```

## 内置策略

| 策略 | 类 | 行为 |
|------|-----|------|
| ReplaceStrategy | `com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy` | 替换（默认） |
| AppendStrategy | `com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy` | 追加 |

## 使用示例

```java
@Override
protected KeyStrategyFactory setupStateKeyFactory() {
    return components.stateKey().builder()
            .addStrategy("messages", new AppendStrategy())
            .addStrategy("input")
            .defaultStrategy(new ReplaceStrategy())
            .build();
}
```
