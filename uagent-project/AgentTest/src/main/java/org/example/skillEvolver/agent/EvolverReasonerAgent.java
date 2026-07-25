package org.example.skillEvolver.agent;

import io.agentscope.core.model.Model;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;

/**
 * SkillEvolver 推理 Agent — 用于策略生成、Trace 分析、Skill 合成等纯 LLM 推理任务。
 * <p>
 * 无工具、无记忆、纯推理。图节点通过 AgentPoolManager 获取实例，
 * 发一条 prompt，取回结构化响应。
 * <p>
 * scope=prototype：每次获取新实例，保证隔离。
 *
 * @author zhilin
 */
@Log4j2
@AgentDefinition(
        name = "EvolverReasoner",
        scope = "prototype",
        enableMemory = false,
        enablePersistence = false,
        maxIters = 5,
        description = "SkillEvolver 推理 Agent — 策略生成/Trace 分析/Skill 合成"
)
public class EvolverReasonerAgent extends AbstractAgentTemplate {

    /** ThreadLocal 传递推理上下文 */
    private static final ThreadLocal<ReasonerContext> CONTEXT = new ThreadLocal<>();

    public static final class ReasonerContext {
        private final String systemPrompt;
        private final String taskPrompt;

        public ReasonerContext(String systemPrompt, String taskPrompt) {
            this.systemPrompt = systemPrompt;
            this.taskPrompt = taskPrompt;
        }

        public String getSystemPrompt() { return systemPrompt; }
        public String getTaskPrompt() { return taskPrompt; }
    }

    public static void setContext(ReasonerContext ctx) { CONTEXT.set(ctx); }
    public static ReasonerContext getContext() { return CONTEXT.get(); }
    public static void clearContext() { CONTEXT.remove(); }

    public EvolverReasonerAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        ReasonerContext ctx = getContext();
        return ctx != null ? ctx.getSystemPrompt() : "你是一个有用的 AI 助手。";
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("思考");
    }
}
