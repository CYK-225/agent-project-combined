package org.example.skillEvolver.skill;

import io.agentscope.core.skill.AgentSkill;
import org.example.skillEvolver.tools.OrchestratorTools;
import org.example.skillEvolver.tools.SkillVaultTools;
import org.example.skillEvolver.tools.TraceAnalyzerTools;
import org.example.skillEvolver.tools.TrialTools;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;

/**
 * SkillEvolver 各 Agent 的 Skill + Tool 工厂。
 * <p>
 * 纯静态工厂，产出 {@code AgentSkill + Object[]}，
 * 由 Agent Template 的 {@code addSkillWithTools} 消费。
 * <p>
 * 使用示例（在 Agent Template 中）：
 * <pre>
 * // Orchestrator Agent
 * components.skillBox().create(getToolkit())
 *     .addSkillWithTools(
 *         EvolverSkillFactory.createOrchestratorSkill(),
 *         EvolverSkillFactory.createOrchestratorTools(...)
 *     )
 *     .buildSkillBox()
 * </pre>
 *
 * @author zhilin
 */
public class EvolverSkillFactory {

    private EvolverSkillFactory() {
    }

    // ======================== Orchestrator ========================

    public static AgentSkill createOrchestratorSkill() {
        return createBaseAgentSkill(
                "skill_evolver_orchestrator",
                "Skill 进化主调度",
                "你是 SkillEvolver 主调度器，负责执行 Explore → Analyze → Update 循环，"
                        + "迭代优化可复用的 SKILL.md。你通过 read_evolver_task 读取任务状态，"
                        + "通过 save_skill_version 保存版本，通过 analyze_trial_traces 分析差异，"
                        + "最终选择最佳版本并持久化。"
        );
    }

    /**
     * 创建 Orchestrator 全套工具（OrchestratorTools + TraceAnalyzerTools + SkillVaultTools）。
     *
     * @param orchestratorTools   任务/版本管理工具
     * @param traceAnalyzerTools  trace 差异分析工具
     * @param skillVaultTools     skill 文件持久化工具
     */
    public static Object[] createOrchestratorTools(
            OrchestratorTools orchestratorTools,
            TraceAnalyzerTools traceAnalyzerTools,
            SkillVaultTools skillVaultTools
    ) {
        return new Object[]{orchestratorTools, traceAnalyzerTools, skillVaultTools};
    }

    // ======================== Trial Runner ========================

    public static AgentSkill createTrialRunnerSkill() {
        return createBaseAgentSkill(
                "skill_evolver_trial_runner",
                "Trial 执行器",
                "你是 SkillEvolver 的 Trial Runner，负责在隔离工作区中执行单次 trial。"
                        + "你按照给定的策略变体 SKILL.md 指导执行任务，收集执行 trace，"
                        + "然后验证结果。成功和失败的 trace 都有价值。"
        );
    }

    /**
     * 创建 TrialRunner 的执行工具。
     *
     * @param trialTools trial 执行/验证/文件工具
     */
    public static Object[] createTrialRunnerTools(TrialTools trialTools) {
        return new Object[]{trialTools};
    }
}
