package org.example.AgentFan;

import io.agentscope.spring.boot.agui.common.AguiAgentId;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;
import jakarta.annotation.Resource;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentTestConfig {
    @Resource
    private AgentPoolManager agentPoolManager;

    /**
     * 注册 PermissionInjectionAgent 到 AG-UI 前端
     * <p>
     * 该 Agent 负责根据用户 token 获取公司权限和商户权限，
     * 并在执行 SQL 查询时注入权限过滤条件。
     */
    @Bean
    @AguiAgentId("PermissionInjectionAgent")
    public AguiAgentRegistryCustomizer exposePermissionAgentToAgui() {
        return registry -> {
            registry.registerFactory(
                    "PermissionInjectionAgent",
                    () -> agentPoolManager.getAgent("PermissionInjectionAgent")
            );
        };
    }

    //    @Bean
//    @AguiAgentId("MasterUserAgents") // 暴露给前端的 ID 为 support-agent
//    public Agent MasterUserAgents() {
//        return agentPoolManager.getAgent("MasterUserAgents");
//    }

//    @Bean
//    @AguiAgentId("MasterUserAgents")
//    public AguiAgentRegistryCustomizer exposeAgentToAgui() {
//        return registry -> {
//            // 当 AG-UI 收到针对 "MasterUserAgents" 的请求时，
//            // 它会执行后面这个 Lambda 表达式 (即每次都去你的连接池里拿)
//            registry.registerFactory(
//                    "MasterUserAgents",
//                    () -> agentPoolManager.getAgent("MasterUserAgents")
//            );
//        };
//    }

}