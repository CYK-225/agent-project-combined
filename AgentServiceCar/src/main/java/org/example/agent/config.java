

package org.example.agent;

import io.agentscope.spring.boot.agui.common.AguiAgentId;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;
import jakarta.annotation.Resource;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class config {
    @Resource
    private AgentPoolManager agentPoolManager;



    //    @Bean
//    @AguiAgentId("MasterUserAgents") // 暴露给前端的 ID 为 support-agent
//    public Agent MasterUserAgents() {
//        return agentPoolManager.getAgent("MasterUserAgents");
//    }
    @Bean
    @AguiAgentId("MasterUserAgents")
    public AguiAgentRegistryCustomizer exposeAgentToAgui() {
        return registry -> {
            // 当 AG-UI 收到针对 "MasterUserAgents" 的请求时，
            // 它会执行后面这个 Lambda 表达式 (即每次都去你的连接池里拿)
            registry.registerFactory(
                    "MasterUserAgents",
                    () -> agentPoolManager.getAgent("MasterUserAgents")
            );
        };
    }

    @Bean
    @AguiAgentId("FinanceForecastAgent")
    public AguiAgentRegistryCustomizer exposeFinanceForecastAgentToAgui() {
        return registry -> {
            // 当 AG-UI 收到针对 "FinanceForecastAgents" 的请求时，
            // 它会执行后面这个 Lambda 表达式 (即每次都去你的连接池里拿)
            registry.registerFactory(
                    "FinanceForecastAgent",
                    () -> agentPoolManager.getAgent("FinanceForecastAgent")
            );
        };
    }

    @Bean
    @AguiAgentId("MasterSupplyPlanAgent")
    public AguiAgentRegistryCustomizer exposeMasterSupplyPlanAgentToAgui() {
        return registry -> {
            registry.registerFactory(
                    "MasterSupplyPlanAgent",
                    () -> agentPoolManager.getAgent("MasterSupplyPlanAgent")
            );
        };
    }

    @Bean
    @AguiAgentId("MasterSPAgents")
    public AguiAgentRegistryCustomizer MasterSPAgentsToAgui() {
        return registry -> {
            // 当 AG-UI 收到针对 "MasterUserAgents" 的请求时，
            // 它会执行后面这个 Lambda 表达式 (即每次都去你的连接池里拿)
            registry.registerFactory(
                    "MasterSPAgents",
                    () -> agentPoolManager.getAgent("MasterSPAgents")
            );
        };
    }

    @Bean
    @AguiAgentId("DecisionMarkAgent")
    public AguiAgentRegistryCustomizer DecisionMarkAgentToAgui() {
        return registry -> {
            registry.registerFactory(
                    "DecisionMarkAgent",
                    () -> agentPoolManager.getAgent("DecisionMarkAgent")
            );
        };
    }

    @Bean
    @AguiAgentId("RecommendMenuAgent")
    public AguiAgentRegistryCustomizer RecommendMenuAgent() {
        return registry -> {
            registry.registerFactory(
                    "RecommendMenuAgent",
                    () -> agentPoolManager.getAgent("RecommendMenuAgent")
            );
        };
    }


}