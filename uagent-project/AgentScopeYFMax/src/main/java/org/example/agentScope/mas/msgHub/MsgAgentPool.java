package org.example.agentScope.mas.msgHub;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.Msg;
import io.agentscope.core.pipeline.MsgHub;

import java.util.*;

/**
 * 智能体池 (Agent Pool)
 * 提供统一的智能体管理和快速组建 MsgHub 的能力
 */
public class MsgAgentPool {

    // 核心存储
    private final Map<String, AgentBase> pool = new HashMap<>();

    // 私有构造
    private MsgAgentPool() {}

    // --- 1. 静态工厂方法 (入口) ---

    /**
     * 通过变长参数初始化
     */
    public static MsgAgentPool of(AgentBase... agents) {
        MsgAgentPool ap = new MsgAgentPool();
        ap.register(agents);
        return ap;
    }

    /**
     * 通过已有的 Map 初始化
     */
    public static MsgAgentPool of(Map<String, AgentBase> agentsMap) {
        MsgAgentPool ap = new MsgAgentPool();
        if (agentsMap != null) {
            ap.pool.putAll(agentsMap);
        }
        return ap;
    }

    // --- 2. 动态管理 (支持链式调用) ---

    /**
     * 注册单个或多个智能体
     */
    public MsgAgentPool register(AgentBase... agents) {
        for (AgentBase agent : agents) {
            if (agent != null && agent.getName() != null) {
                this.pool.put(agent.getName(), agent);
            }
        }
        return this; // 返回自身以支持链式调用
    }

    /**
     * 移除智能体
     */
    public MsgAgentPool remove(String agentName) {
        this.pool.remove(agentName);
        return this;
    }

    /**
     * 获取智能体实例
     */
    public AgentBase get(String name) {
        return this.pool.get(name);
    }

    // --- 3. Hub 构建入口 ---

    /**
     * 创建默认名称的 Hub 构建器
     */
    public HubBuilder hub() {
        return new HubBuilder(this, UUID.randomUUID().toString());
    }

    /**
     * 创建指定名称的 Hub 构建器
     */
    public HubBuilder hub(String name) {
        return new HubBuilder(this, name);
    }

    // --- 内部构建器 (Fluent API) ---

    public static class HubBuilder {
        private final MsgAgentPool pool;
        private final String name;
        private final List<AgentBase> participants = new ArrayList<>();
        private final List<Msg> announcements = new ArrayList<>();
        private boolean autoBroadcast = true;

        public HubBuilder(MsgAgentPool pool, String name) {
            this.pool = pool;
            this.name = name;
        }

        /**
         * 核心：通过名字加入 (支持变长参数)
         */
        public HubBuilder join(String... agentNames) {
            for (String agentName : agentNames) {
                AgentBase agent = pool.get(agentName);
                if (agent == null) {
                    throw new IllegalArgumentException(STR."Agent [\{agentName}] not found in pool.");
                }
                this.participants.add(agent);
            }
            return this;
        }

        /**
         * 允许直接加入实体对象 (混合使用)
         */
        public HubBuilder join(AgentBase... agents) {
            Collections.addAll(this.participants, agents);
            return this;
        }

        /**
         * 设置公告消息
         */
        public HubBuilder announce(Msg... msgs) {
            Collections.addAll(this.announcements, msgs);
            return this;
        }

        public HubBuilder announce(List<Msg> msgs) {
            this.announcements.addAll(msgs);
            return this;
        }

        /**
         * 关闭自动广播 (静默模式)
         */
        public HubBuilder quiet() {
            this.autoBroadcast = false;
            return this;
        }

        /**
         * 构建 MsgHub
         */
        public MsgHub build() {
            if (participants.isEmpty()) {
                throw new IllegalStateException("Cannot build MsgHub without participants.");
            }
            return MsgHub.builder()
                    .name(name)
                    .participants(participants)
                    .announcement(announcements)
                    .enableAutoBroadcast(autoBroadcast)
                    .build();
        }
    }
}