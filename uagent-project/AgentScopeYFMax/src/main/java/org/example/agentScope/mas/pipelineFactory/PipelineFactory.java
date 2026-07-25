package org.example.agentScope.mas.pipelineFactory;



import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.Msg;
import io.agentscope.core.pipeline.FanoutPipeline;
import io.agentscope.core.pipeline.Pipeline;
import io.agentscope.core.pipeline.Pipelines;
import io.agentscope.core.pipeline.SequentialPipeline;


import java.util.*;
import java.util.stream.Collectors;

/**
 * PipelineFactory
 * 提供基于注册池的智能体管道构建与快速执行工厂。
 */
public class PipelineFactory {

    private final HashMap<String, AgentBase> agentPool;

    public PipelineFactory() {
        this.agentPool = new HashMap<>();
    }

    public PipelineFactory(HashMap<String, AgentBase> agents) {
        this.agentPool = new HashMap<>(agents);
    }

    // ==========================================
    //              1. 注册管理
    // ==========================================

    /**
     * 注册单个智能体
     * @param name
     * @param agent
     * @return
     */
    public PipelineFactory register(String name, AgentBase agent) {
        this.agentPool.put(name, agent);
        return this;
    }

    /**
     * 批量注册智能体
     * @param agents
     * @return
     */
    public PipelineFactory registerAll(HashMap<String, AgentBase> agents) {
        if (agents != null) this.agentPool.putAll(agents);
        return this;
    }

    /**
     * 获取智能体实例
     * @param name
     * @return
     */
    public AgentBase getAgent(String name) {
        AgentBase agent = this.agentPool.get(name);
        if (agent == null) throw new IllegalArgumentException(STR."Agent not found: \{name}");
        return agent;
    }
    /**
     * 获取智能体实例列表
     */
    public  HashMap<String, AgentBase> getAgents() {
        return this.agentPool;}

    // ==========================================
    //           2. 创建可复用管道对象 (Builder模式)
    // ==========================================

    /**
     * 创建顺序管道对象（不立即执行）
     */
    public SequentialPipeline createSequential(String... agentNames) {
        List<AgentBase> agents = resolveAgents(agentNames);
        SequentialPipeline.Builder builder = SequentialPipeline.builder();
        agents.forEach(builder::addAgent);
        return builder.build();
    }

    /**
     * 创建扇出管道对象（不立即执行）
     * @param concurrent true=并发, false=顺序
     */
    public FanoutPipeline createFanout(boolean concurrent, String... agentNames) {
        List<AgentBase> agents = resolveAgents(agentNames);
        FanoutPipeline.Builder builder = FanoutPipeline.builder();
        agents.forEach(builder::addAgent);
        if (concurrent) builder.concurrent(); else builder.sequential();
        return builder.build();
    }

    // ==========================================
    //           3. 立即执行方法 (Wrappers)
    // ==========================================

    /**
     * [Sequential] 带输入顺序执行
     * 对应: Pipelines.sequential(agents, input)
     */
    public Msg runSequential(Msg input, String... agentNames) {
        return Pipelines.sequential(resolveAgents(agentNames), input).block();
    }

    /**
     * [Sequential] 无输入顺序执行
     * 对应: Pipelines.sequential(agents)
     */
    public Msg runSequential(String... agentNames) {
        return Pipelines.sequential(resolveAgents(agentNames)).block();
    }

    /**
     * [Sequential] 带结构化输出的顺序执行
     * 对应: Pipelines.sequential(agents, input, outputClass)
     */
    public <T> Msg runSequential(Msg input, Class<T> outputClass, String... agentNames) {
        return Pipelines.sequential(resolveAgents(agentNames), input, outputClass).block();
    }

    /**
     * [Fanout] 并发执行 (Concurrent)
     * 对应: Pipelines.fanout(agents, input)
     */
    public List<Msg> runFanout(Msg input, String... agentNames) {
        return Pipelines.fanout(resolveAgents(agentNames), input).block();
    }

    /**
     * [Fanout] 无输入并发执行
     * 对应: Pipelines.fanout(agents)
     */
    public List<Msg> runFanout(String... agentNames) {
        return Pipelines.fanout(resolveAgents(agentNames)).block();
    }

    /**
     * [Fanout] 顺序执行 (Sequential Fanout)
     * 对应: Pipelines.fanoutSequential(agents, input)
     * 注意：这是虽然是Fanout结构，但是是一个接一个执行
     */
    public List<Msg> runFanoutSequential(Msg input, String... agentNames) {
        return Pipelines.fanoutSequential(resolveAgents(agentNames), input).block();
    }

    // ==========================================
    //           4. 管道组合方法
    // ==========================================

    /**
     * 组合两个管道
     * 对应: Pipelines.compose(pipeline1, pipeline2)
     */
    public Pipeline<Msg> compose(SequentialPipeline p1,SequentialPipeline p2) {
        return Pipelines.compose(p1, p2);
    }

    // ==========================================
    //                内部辅助
    // ==========================================

    private List<AgentBase> resolveAgents(String[] names) {
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("Agent names cannot be empty");
        }
        return Arrays.stream(names)
                .map(this::getAgent)
                .collect(Collectors.toList());
    }
}
