package org.example.agentScope.util.tool;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.session.Session;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import lombok.Data;
import lombok.Getter;

@Data
public class SubAgent {

    private ReActAgent agent;
    private SubConfig config;

    private SubAgent() {
        this.config = new SubConfig(this);
    }

    public static SubAgent create() {
        return new SubAgent();
    }

    public SubAgent agent(ReActAgent agent) {
        this.agent = agent;
        return this;
    }

    public SubConfig config() {
        return this.config;
    }

    /**
     * 【修改点】使用三元表达式
     * 逻辑封装在 config.isAllNull() 中，代码更干净
     */
    public SubAgentConfig toImplementationConfig() {
        if (config.isAllNull()) return null;

        var builder = SubAgentConfig.builder();

        // 仅当值存在时才调用 builder 方法
        if (config.toolName != null) builder.toolName(config.toolName);
        if (config.description != null) builder.description(config.description);
        if (config.forwardEvents != null) builder.forwardEvents(config.forwardEvents);
        if (config.session != null) builder.session(config.session);
        if(config.streamOptions!=null) builder.streamOptions(config.streamOptions);

        return builder.build();
    }

    @Getter
    public static class SubConfig {
        private final SubAgent parent;

        private String toolName;
        private String description;
        private Boolean forwardEvents;
        private Session session;
        private StreamOptions streamOptions;

        public SubConfig(SubAgent parent) {
            this.parent = parent;
        }

        /**
         * 辅助方法：判断配置是否全空
         */
        public boolean isAllNull() {
            return toolName == null
                    && description == null
                    && forwardEvents == null
                    && session == null;
        }

        public SubConfig toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public SubConfig description(String description) {
            this.description = description;
            return this;
        }

        public SubConfig forwardEvents(Boolean forwardEvents) {
            this.forwardEvents = forwardEvents;
            return this;
        }

        public SubConfig session(Session session) {
            this.session = session;
            return this;
        }
        public SubConfig streamOptions( StreamOptions streamOptions) {
            this.streamOptions = streamOptions;
            return this;
        }

        public SubAgent end() {
            return this.parent;
        }
    }
}