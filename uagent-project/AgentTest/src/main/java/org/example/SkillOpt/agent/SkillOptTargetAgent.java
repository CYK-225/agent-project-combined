package org.example.skillOpt.agent;

import io.agentscope.core.model.Model;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.agentScope.util.skill.BaseSkillBoxFactory;

/**
 * SkillOpt Target Agent — 在给定 skill 指导下执行 rollout 任务。
 * <p>
 * scope=prototype：每次 rollout 获取新实例，保证隔离。
 * ThreadLocal 上下文传递 skill 内容和任务实例。
 *
 * @author zhilin
 */
@Log4j2
@AgentDefinition(
        name = "SkillOptTarget",
        scope = "prototype",
        enableMemory = false,
        enablePersistence = false,
        maxIters = 40,
        description = "SkillOpt Target Agent — 执行 rollout，按 skill 指导完成任务"
)
public class SkillOptTargetAgent extends AbstractAgentTemplate {

    /** ThreadLocal 传递 rollout 上下文 */
    private static final ThreadLocal<TargetContext> CONTEXT = new ThreadLocal<>();

    public static final class TargetContext {
        private final String jobId;
        private final int epoch;
        private final int variantIndex;
        private final String skillMarkdown;
        private final String taskPrompt;

        public TargetContext(String jobId, int epoch, int variantIndex,
                             String skillMarkdown, String taskPrompt) {
            this.jobId = jobId;
            this.epoch = epoch;
            this.variantIndex = variantIndex;
            this.skillMarkdown = skillMarkdown;
            this.taskPrompt = taskPrompt;
        }

        public String getJobId() { return jobId; }
        public int getEpoch() { return epoch; }
        public int getVariantIndex() { return variantIndex; }
        public String getSkillMarkdown() { return skillMarkdown; }
        public String getTaskPrompt() { return taskPrompt; }
    }

    public static void setContext(TargetContext ctx) { CONTEXT.set(ctx); }
    public static TargetContext getContext() { return CONTEXT.get(); }
    public static void clearContext() { CONTEXT.remove(); }

    public SkillOptTargetAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        TargetContext ctx = getContext();
        String skillSection = "";
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
        }

        if (skillSection.isBlank()) {
            skillSection = "## Skill Guidance\n\n(No skill guidance yet — use your best judgment)\n\n";
        }

        return """
                You are a SkillOpt Target Agent. Your job is to execute tasks following the skill guidance.

                ## Context
                - **Job ID**: %s
                - **Epoch**: %d
                - **Variant**: %d

                %s
                ## Execution Rules
                1. Follow the Skill Guidance strictly if provided
                2. Provide clear, actionable answers
                3. End your response with your final answer in the format: **Answer: <your answer>**
                4. If you cannot complete the task, explain what went wrong
                """.formatted(jobId, epoch, variantIndex, skillSection);
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected SkillBox setupSkills() {
        // Target Agent 无额外工具，纯文本推理
        AgentSkill baseSkill = BaseSkillBoxFactory.createBaseAgentSkill(
                "skillopt-target", "SkillOpt Target Agent",
                "Execute tasks following skill guidance for SkillOpt training.");
        return components.skillBox().create(getToolkit())
                .addSkillWithTools(baseSkill, new Object[]{})
                .buildSkillBox();
    }
}
