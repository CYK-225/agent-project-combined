package org.example.graph.workflow.state;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.stereotype.Component;

/**
 * KeyStrategyFactory 实例的工厂。
 * 提供默认工厂和基于上游 KeyStrategyFactoryBuilder 的自定义构建器。
 *
 * <p>KeyStrategyFactory 接口签名：{@code Map<String, KeyStrategy> apply()}（无参数），
 * 返回键名到策略的映射表。未映射的键使用 defaultStrategy 处理。</p>
 */
@Component
public class StateKeyFactory {

    /**
     * 默认工厂：所有键使用 ReplaceStrategy。
     * 返回空映射表，上游框架对未注册的键使用默认策略。
     */
    public KeyStrategyFactory defaultFactory() {
        return new KeyStrategyFactoryBuilder()
                .defaultStrategy(new ReplaceStrategy())
                .build();
    }

    /**
     * 创建上游 KeyStrategyFactoryBuilder 的自定义构建器。
     * 支持 addStrategy、defaultStrategy 等完整配置。
     *
     * <p>用法：
     * <pre>
     *   KeyStrategyFactory factory = stateKey.builder()
     *       .addStrategy("messages", new AppendStrategy())
     *       .addStrategy("input")
     *       .defaultStrategy(new ReplaceStrategy())
     *       .build();
     * </pre>
     */
    public KeyStrategyFactoryBuilder builder() {
        return new KeyStrategyFactoryBuilder()
                .defaultStrategy(new ReplaceStrategy());
    }
}
