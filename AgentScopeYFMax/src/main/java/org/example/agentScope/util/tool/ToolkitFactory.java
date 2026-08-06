package org.example.agentScope.util.tool;

import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
@Getter
@Setter
//TODO 这里是不是应该增加一个text-to-sql的工具，并用mybatisflex来做安全连接只允许特定的表和select语句，
@Slf4j
public class ToolkitFactory {

    // --- 注入的默认配置 ---
    @Value("${agentscope.toolkit.parallel}")
    private boolean parallel=false;
    @Value("${agentscope.toolkit.allow-tool-deletion}")
    private boolean allowToolDeletion = false;
    @Value("${agentscope.toolkit.timeout-seconds}")
    private long timeoutSeconds = 300;
    @Value("${agentscope.toolkit.enableMetaTool}")
    private boolean enableMetaTool = false;

    // ==========================================
    //  核心入口：重载的 create 方法
    // ==========================================

    /**
     * 重载 1: 使用 application.yml 中的默认配置
     */
    public ToolkitChain create() {
        log.info("parllel"+parallel);
        ToolkitConfig config = ToolkitConfig.builder()
                .parallel(parallel)
                .allowToolDeletion(allowToolDeletion)
                .executionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .build())
                .build();

        // 使用默认配置 + 默认 enableMetaTool 开关
        return initChain(new Toolkit(config), this.enableMetaTool);
    }

    /**
     * 重载 2: 传入自定义 Config，但沿用 application.yml 的 MetaTool 开关
     */
    public ToolkitChain create(ToolkitConfig customConfig) {
        return initChain(new Toolkit(customConfig), this.enableMetaTool);
    }

    /**
     * 重载 3: 传入自定义 Config 和 自定义 MetaTool 开关 (全手动控制)
     */
    public ToolkitChain create(ToolkitConfig customConfig, boolean enableMetaTool) {
        return initChain(new Toolkit(customConfig), enableMetaTool);
    }

    // ==========================================
    //  私有辅助方法：统一初始化逻辑
    // ==========================================
    private ToolkitChain initChain(Toolkit toolkit, boolean enableMeta) {
        // 1. 根据开关注册元工具
        if (enableMeta) {
            toolkit.registerMetaTool();
        }
        // 2. 创建默认组
        toolkit.createToolGroup("default", "默认工具组", true);

        // 3. 包装并返回
        return new ToolkitChain(toolkit);
    }

    /**
     * 入口方法：基于已有的 Toolkit 进行链式修改
     */
    public ToolkitChain modify(Toolkit toolkit) {
        return new ToolkitChain(toolkit);
    }

    // ==========================================
    //  内部 Builder 类 (保持不变)
    // ==========================================
    @RequiredArgsConstructor
    public class ToolkitChain {
        private final Toolkit toolkit;

        /**
         * 获取最终构建的 Toolkit 实例
         * @return
         */
        public Toolkit build() { return toolkit; }



        // --- 链式操作方法 ---

        /**
         * 添加工具到默认组
         * @param tools
         * @return
         */
        public ToolkitChain addTools(Object... tools) {
            return addTools("default", tools);
        }

        /**
         * 创建新的工具组
         * @param groupName
         * @param desc
         * @param isActive
         * @return
         */
        public ToolkitChain createToolGroup(String groupName, String desc, boolean isActive) {
            toolkit.createToolGroup(groupName, desc, isActive);
            return this;
        }

        /**
         * 添加工具到指定组
         * @param groupName
         * @param tools
         * @return
         */
        public ToolkitChain addTools(String groupName, Object... tools) {
            Arrays.stream(tools).forEach(tool ->
                    toolkit.registration().tool(tool).group(groupName).apply()
            );
            return this;
        }
        /**
         * 添加Agent到默认组
         * @param agents
         * @return
         */
        public ToolkitChain addAgent(SubAgent... agents) {
            return addAgent("default", agents);
        }


        /**
         * 添加Agent到组
         * @param groupName
         * @param agents
         * @return
         */
        public ToolkitChain addAgent(String groupName, SubAgent... agents) {
            Arrays.stream(agents).forEach(agent ->
                        toolkit.registration()
                                .subAgent(agent::getAgent, agent.toImplementationConfig())
                                .group(groupName).apply()
            );
            return this;
        }

        /**
         * 添加MCP工具到默认组
         * @param mcps
         * @return
         */
        public ToolkitChain addMCPTools(McpClientWrapper... mcps) {
            return addMCPTools("default", mcps);
        }

        /**
         * 添加MCP工具到指定组
         * @param groupName
         * @param mcps
         * @return
         */
        public ToolkitChain addMCPTools(String groupName, McpClientWrapper... mcps) {
            if(mcps==null || mcps.length==0) return this;
            Arrays.stream(mcps).forEach(mcp ->
                    toolkit.registration().mcpClient(mcp).group(groupName).apply()
            );
            return this;
        }

        /**
         * 移除工具
         * @param toolNames
         * @return
         */
        public ToolkitChain removeTools(String... toolNames) {
            if(toolNames == null) return this;
            for (String name : toolNames) {
                toolkit.removeTool(name);
            }
            return this;
        }
        /**
         * 移除工具组
         */
        public ToolkitChain removeToolGroup(List<String> groupNames) {
            if(groupNames == null) return this;
            toolkit.removeToolGroups(groupNames);
            return this;}

        /**
         * 添加Schema工具
         * @param tools
         * @return
         */
        public ToolkitChain addSchemaTools(List<ToolSchema> tools) {
            if(tools==null || tools.isEmpty()) return this;
            toolkit.registerSchemas(tools);
            return this;
        }

        /**
         * 批量注册MCP连接和工具
         * @param groupName
         * @param tools
         * @param mcps
         * @return
         */
        // 如果需要批量注册复杂逻辑
        public ToolkitChain registerAll(String groupName,String groupDescription, List<Object> tools, McpClientWrapper... mcps) {

            createToolGroup(groupName, groupDescription, true);
            if (tools != null && !tools.isEmpty()) addTools(groupName, tools.toArray());
            if (mcps != null && mcps.length>0) addMCPTools(groupName, mcps);
            return this;
        }
        /**
         * 收集普通工具（skill专用）
         */
        public static Object[] getToolsForSkill(Object... tools) {
            return tools == null ? new Object[0] : tools;
        }

        /**
         * 收集Mcp工具（skill专用）
         */
        public static Object[] getMCPToolsForSkill(McpClientWrapper... mcps) {
            return mcps == null ? new Object[0] : mcps;
        }

        /**
         * 收集Schema工具（skill专用）
         */
        public static Object[] getSchemaToolsForSkill(List<ToolSchema> tools) {
            return (tools == null || tools.isEmpty()) ? new Object[0] : tools.toArray();
        }

        /**
         * 收集Agent（skill专用）
         */
        public static Object[] getAgentForSkill(SubAgent... agents) {
            return agents == null ? new Object[0] : agents;
        }

    }

}