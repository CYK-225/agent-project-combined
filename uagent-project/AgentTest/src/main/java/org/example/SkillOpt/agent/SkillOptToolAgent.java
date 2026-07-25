package org.example.skillOpt.agent;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.agentScope.util.skill.BaseSkillBoxFactory;
import org.example.skillOpt.env.EnvAdapter;
import org.example.skillOpt.env.ToolFeedback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkillOpt 带工具的 Target Agent — 支持多轮工具调用的 rollout 执行。
 * <p>
 * 与 SkillOptTargetAgent 的区别：
 * <ul>
 *   <li>支持工具调用（通过 EnvAdapter 提供工具）</li>
 *   <li>支持多轮对话（maxSteps 控制）</li>
 *   <li>支持 Hook 追踪（记录完整轨迹）</li>
 * </ul>
 *
 * @author zhilin
 */
@Log4j2
@AgentDefinition(
        name = "SkillOptToolTarget",
        scope = "prototype",
        enableMemory = false,
        enablePersistence = false,
        maxIters = 60,
        description = "SkillOpt Tool Target Agent — 执行带工具调用的 rollout"
)
public class SkillOptToolAgent extends AbstractAgentTemplate {

    /** ThreadLocal 传递 rollout 上下文 */
    private static final ThreadLocal<ToolTargetContext> CONTEXT = new ThreadLocal<>();

    /** 工具执行结果缓存（用于跨线程传递） */
    private static final Map<String, ToolFeedback> TOOL_FEEDBACK_CACHE = new ConcurrentHashMap<>();

    public static final class ToolTargetContext {
        private final String jobId;
        private final int epoch;
        private final int variantIndex;
        private final String skillMarkdown;
        private final String taskPrompt;
        private final EnvAdapter envAdapter;
        private final Map<String, Object> taskInstance;

        public ToolTargetContext(String jobId, int epoch, int variantIndex,
                                 String skillMarkdown, String taskPrompt,
                                 EnvAdapter envAdapter, Map<String, Object> taskInstance) {
            this.jobId = jobId;
            this.epoch = epoch;
            this.variantIndex = variantIndex;
            this.skillMarkdown = skillMarkdown;
            this.taskPrompt = taskPrompt;
            this.envAdapter = envAdapter;
            this.taskInstance = taskInstance;
        }

        public String getJobId() { return jobId; }
        public int getEpoch() { return epoch; }
        public int getVariantIndex() { return variantIndex; }
        public String getSkillMarkdown() { return skillMarkdown; }
        public String getTaskPrompt() { return taskPrompt; }
        public EnvAdapter getEnvAdapter() { return envAdapter; }
        public Map<String, Object> getTaskInstance() { return taskInstance; }
    }

    public static void setContext(ToolTargetContext ctx) { CONTEXT.set(ctx); }
    public static ToolTargetContext getContext() { return CONTEXT.get(); }
    public static void clearContext() { CONTEXT.remove(); }

    /**
     * 注册工具执行结果（供 Hook 或外部调用）
     */
    public static void registerToolFeedback(String toolCallId, ToolFeedback feedback) {
        TOOL_FEEDBACK_CACHE.put(toolCallId, feedback);
    }

    /**
     * 获取工具执行结果
     */
    public static ToolFeedback getToolFeedback(String toolCallId) {
        return TOOL_FEEDBACK_CACHE.remove(toolCallId);
    }

    public SkillOptToolAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        ToolTargetContext ctx = getContext();
        String skillSection = "";
        String toolSection = "";
        String jobId = "unknown";
        int epoch = 0;
        int variantIndex = 0;

        if (ctx != null) {
            jobId = ctx.getJobId() != null ? ctx.getJobId() : jobId;
            epoch = ctx.getEpoch();
            variantIndex = ctx.getVariantIndex();

            if (ctx.getSkillMarkdown() != null && !ctx.getSkillMarkdown().isBlank()) {
                skillSection = "## Skill Guidance\n\n" + ctx.getSkillMarkdown() + "\n\n";
            }

            if (ctx.getEnvAdapter() != null && !ctx.getEnvAdapter().getAvailableTools().isEmpty()) {
                toolSection = "## Available Tools\n\nYou have access to the following tools:\n\n";
                for (String toolDesc : ctx.getEnvAdapter().getAvailableTools()) {
                    toolSection += "- " + toolDesc + "\n";
                }
                toolSection += "\nUse tools when needed to gather information or perform actions. ";
                toolSection += "After getting tool results, continue reasoning until you reach a final answer.\n\n";
            }
        }

        if (skillSection.isBlank()) {
            skillSection = "## Skill Guidance\n\n(No skill guidance yet — use your best judgment)\n\n";
        }

        return """
                You are a SkillOpt Tool Target Agent. Your job is to execute tasks following the skill guidance.

                ## Context
                - **Job ID**: %s
                - **Epoch**: %d
                - **Variant**: %d

                %s
                %s
                ## Execution Rules
                1. Follow the Skill Guidance strictly if provided
                2. When you need information, ALWAYS use the available tools (execute_action or query_environment) — do NOT guess or rely on internal knowledge
                3. Call tools using the function calling mechanism — do NOT just describe the tool call in text
                4. After each tool call, analyze the result and decide next steps
                5. When you have enough information, provide your final answer
                6. Format your final answer as: **Answer: <your answer>**
                7. If you cannot complete the task, explain what went wrong
                """.formatted(jobId, epoch, variantIndex, skillSection, toolSection);
    }

    @Override
    protected Model setupCustomModel() {
        // 使用"工具"预设模型 — enableThinking(true) + 低温度(0.1)，更适合函数调用
        return components.model().dashScope().buildDashScopeModel("工具");
    }

    @Override
    protected Toolkit setupTools() {
        ToolTargetContext ctx = getContext();
        Toolkit tk = super.setupTools();
        if (ctx != null && ctx.getEnvAdapter() != null) {
            EnvAdapterTool tool = new EnvAdapterTool(ctx.getEnvAdapter());
            tk.registerTool(tool);
            log.info("[SkillOptToolAgent] Directly registered tools into Toolkit: execute_action, query_environment");
        }
        return tk;
    }

    @Override
    protected SkillBox setupSkills() {
        ToolTargetContext ctx = getContext();

        // 创建基础 skill
        AgentSkill baseSkill = BaseSkillBoxFactory.createBaseAgentSkill(
                "skillopt-tool-target", "SkillOpt Tool Target Agent",
                "Execute tasks with tool calling support for SkillOpt training.");

        // 如果有 EnvAdapter 提供的工具，添加到 skill
        if (ctx != null && ctx.getEnvAdapter() != null) {
            List<Object> tools = createToolsFromAdapter(ctx.getEnvAdapter());
            if (!tools.isEmpty()) {
                return components.skillBox().create(getToolkit())
                        .addSkillWithTools(baseSkill, tools.toArray())
                        .buildSkillBox();
            }
        }

        return components.skillBox().create(getToolkit())
                .addSkillWithTools(baseSkill, new Object[]{})
                .buildSkillBox();
    }

    @Override
    protected List<Hook> setupCustomHooks() {
        List<Hook> hooks = new ArrayList<>(super.setupCustomHooks());
        // 添加轨迹追踪 Hook
        hooks.add(new SkillOptTraceHook());
        return hooks;
    }

    /**
     * 从 EnvAdapter 创建工具实例
     */
    private List<Object> createToolsFromAdapter(EnvAdapter adapter) {
        List<Object> tools = new ArrayList<>();
        // 创建通用的工具调用工具
        tools.add(new EnvAdapterTool(adapter));
        return tools;
    }

    /**
     * 通用环境适配器工具 — 将 EnvAdapter 的能力封装为 Agent 可调用的工具
     */
    public static class EnvAdapterTool {
        private final EnvAdapter adapter;

        public EnvAdapterTool(EnvAdapter adapter) {
            this.adapter = adapter;
        }

        @Tool(
                name = "execute_action",
                description = "Execute an action in the environment. Use this tool to perform operations, "
                        + "search for information, or interact with the task environment. "
                        + "The action should be a clear description of what you want to do."
        )
        public String executeAction(
                @ToolParam(name = "action", description = "The action to execute (e.g., 'search for X', 'calculate Y', 'check Z')") String action,
                @ToolParam(name = "reasoning", description = "Your reasoning for why you're taking this action") String reasoning
        ) {
            log.info("[EnvAdapterTool] Executing action: {}", action);

            // 解析工具调用
            var toolAction = adapter.parseToolAction(action);
            if (toolAction == null) {
                // 如果不是工具调用，直接返回
                return "Action recorded. Continue with your reasoning.";
            }

            // 执行工具
            var feedback = adapter.executeToolAction(toolAction, Map.of("reasoning", reasoning));

            // 缓存结果
            registerToolFeedback(UUID.randomUUID().toString(), feedback);

            return feedback.getContent();
        }

        @Tool(
                name = "query_environment",
                description = "Query the environment for information. Use this to gather data needed to answer the question."
        )
        public String queryEnvironment(
                @ToolParam(name = "query", description = "The query or question to ask the environment") String query
        ) {
            log.info("[EnvAdapterTool] Querying environment: {}", query);

            var toolAction = org.example.skillOpt.env.ToolAction.builder()
                    .toolName("query")
                    .toolInput(query)
                    .rawOutput(query)
                    .build();

            var feedback = adapter.executeToolAction(toolAction, Map.of());
            return feedback.getContent();
        }
    }
}
