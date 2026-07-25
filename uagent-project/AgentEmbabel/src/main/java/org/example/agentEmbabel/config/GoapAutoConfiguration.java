package org.example.agentEmbabel.config;

import org.example.agentEmbabel.core.GoapOrchestrator;
import org.example.agentEmbabel.core.GoapPlanner;
import org.example.agentEmbabel.core.GraphFragmentBuilder;
import org.example.agentEmbabel.core.StateAdapter;
import org.example.agentEmbabel.pool.GoapActionPool;
import org.example.agentEmbabel.pool.GoapGoalPool;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * GOAP 自动配置。
 * 扫描 org.example.agentEmbabel 包下的所有组件。
 */
@Configuration
@ComponentScan(basePackages = "org.example.agentEmbabel")
public class GoapAutoConfiguration {
    // 组件扫描会自动注册以下 Bean：
    // - GoapActionPool
    // - GoapGoalPool
    // - StateAdapter
    // - GraphFragmentBuilder
    // - GoapPlanner
    // - GoapOrchestrator
}
