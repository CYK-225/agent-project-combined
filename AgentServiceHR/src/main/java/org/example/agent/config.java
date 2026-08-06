package org.example.agent;

import io.agentscope.spring.boot.agui.common.AguiAgentId;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;
import jakarta.annotation.Resource;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentServiceHR 配置类
 * 负责将 HR 相关的 Agent 暴露给 AG-UI 前端
 */
@Configuration
public class config {

    @Resource
    private AgentPoolManager agentPoolManager;

    /**
     * HR 简历筛选 Agent - 暴露给 AG-UI
     */
    @Bean
    @AguiAgentId("hr-resume-screener")
    public AguiAgentRegistryCustomizer exposeHrResumeScreenerToAgui() {
        return registry -> {
            registry.registerFactory(
                    "hr-resume-screener",
                    () -> agentPoolManager.getAgent("hr-resume-screener")
            );
        };
    }

    /**
     * HR智能体 - 暴露给 AG-UI
     * <p>
     * 前端可以通过这个 ID 调用 HR 智能体。
     * </p>
     */
    @Bean
    @AguiAgentId("hr-agent")
    public AguiAgentRegistryCustomizer exposeHRAgentToAgui() {
        return registry -> {
            registry.registerFactory(
                    "hr-agent",
                    () -> agentPoolManager.getAgent("hr-agent")
            );
        };
    }

    // 可以在这里添加更多 Agent 配置
    // 例如：
    // @Bean
    // @AguiAgentId("another-agent")
    // public AguiAgentRegistryCustomizer exposeAnotherAgentToAgui() {
    //     return registry -> {
    //         registry.registerFactory(
    //                 "another-agent",
    //                 () -> agentPoolManager.getAgent("another-agent")
    //         );
    //     };
    // }
}
