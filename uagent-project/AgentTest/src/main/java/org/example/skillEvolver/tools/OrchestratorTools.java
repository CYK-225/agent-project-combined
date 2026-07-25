package org.example.skillEvolver.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.skillEvolver.entity.EvolverSkillVersionEntity;
import org.example.skillEvolver.entity.EvolverTaskEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator Agent 专用工具 — 任务读取、策略变体管理、版本控制、循环调度。
 * <p>
 * 遵循函数式注入模式：@FunctionalInterface + lambda，不依赖 Spring 容器。
 *
 * @author zhilin
 */
@Slf4j
public class OrchestratorTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== 函数式接口 ====================

    @FunctionalInterface
    public interface TaskReader {
        EvolverTaskEntity read(String taskId);
    }

    @FunctionalInterface
    public interface TaskUpdater {
        boolean update(String taskId, String status, Integer iteration);
    }

    @FunctionalInterface
    public interface VersionSaver {
        EvolverSkillVersionEntity save(EvolverSkillVersionEntity entity);
    }

    @FunctionalInterface
    public interface VersionReader {
        List<EvolverSkillVersionEntity> readByTask(String taskId);
    }

    @FunctionalInterface
    public interface BestSkillUpdater {
        boolean updateBest(String taskId, String versionId, String content, Double reward, Double passRate);
    }

    // ==================== 实例字段 ====================

    private final TaskReader taskReader;
    private final TaskUpdater taskUpdater;
    private final VersionSaver versionSaver;
    private final VersionReader versionReader;
    private final BestSkillUpdater bestSkillUpdater;

    public OrchestratorTools(TaskReader taskReader,
                             TaskUpdater taskUpdater,
                             VersionSaver versionSaver,
                             VersionReader versionReader,
                             BestSkillUpdater bestSkillUpdater) {
        this.taskReader = taskReader;
        this.taskUpdater = taskUpdater;
        this.versionSaver = versionSaver;
        this.versionReader = versionReader;
        this.bestSkillUpdater = bestSkillUpdater;
    }

    // ==================== @Tool 方法 ====================

    @Tool(
            name = "read_evolver_task",
            description = "读取进化任务的完整信息：指令、输入数据、验证规则、当前迭代轮次、奖励模式等。"
                    + "每次开始新一轮循环前必须先调用此工具获取最新状态。"
    )
    public String readEvolverTask(
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId
    ) {
        EvolverTaskEntity task = taskReader.read(taskId);
        if (task == null) {
            return "❌ 未找到进化任务: " + taskId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 进化任务信息\n\n");
        sb.append("- **任务 ID**: ").append(task.getTaskId()).append("\n");
        sb.append("- **任务名称**: ").append(task.getTaskName()).append("\n");
        sb.append("- **状态**: ").append(task.getStatus()).append("\n");
        sb.append("- **当前迭代**: ").append(task.getCurrentIteration())
                .append(" / ").append(task.getMaxIterations()).append("\n");
        sb.append("- **奖励模式**: ").append(task.getRewardMode()).append("\n");
        sb.append("- **探索 Trial 数**: ").append(task.getNExploration()).append("\n");
        sb.append("- **验证 Trial 数**: ").append(task.getNValidation()).append("\n\n");

        sb.append("### 任务指令\n").append(task.getInstruction()).append("\n\n");

        if (task.getTaskData() != null && !task.getTaskData().isBlank()) {
            sb.append("### 输入数据\n").append(task.getTaskData()).append("\n\n");
        }
        if (task.getVerifier() != null && !task.getVerifier().isBlank()) {
            sb.append("### 验证规则\n").append(task.getVerifier()).append("\n\n");
        }

        if (task.getBestSkillVersion() != null) {
            sb.append("### 当前最佳版本\n");
            sb.append("- 版本: ").append(task.getBestSkillVersion()).append("\n");
            sb.append("- 奖励: ").append(task.getBestReward()).append("\n");
            sb.append("- 验证通过率: ").append(task.getValidationPassRate()).append("\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "advance_iteration",
            description = "推进迭代计数器到下一轮，同时将任务状态设为 RUNNING。"
                    + "返回新的迭代号。如果已达到最大迭代次数则返回警告。"
    )
    public String advanceIteration(
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId
    ) {
        EvolverTaskEntity task = taskReader.read(taskId);
        if (task == null) {
            return "❌ 未找到进化任务: " + taskId;
        }

        int next = task.getCurrentIteration() + 1;
        if (next > task.getMaxIterations()) {
            return "⚠️ 已达到最大迭代轮数 " + task.getMaxIterations()
                    + "，不能再推进。请调用 select_best_and_validate 进入最终验证。";
        }

        taskUpdater.update(taskId, "RUNNING", next);
        log.info("[Evolver] 任务 {} 推进到迭代 {}/{}", taskId, next, task.getMaxIterations());
        return "✅ 迭代推进到 " + next + " / " + task.getMaxIterations();
    }

    @Tool(
            name = "save_skill_version",
            description = "保存一个 skill 版本快照。每轮迭代的策略变体和合并后的版本都需要调用此工具保存。"
                    + "返回版本 ID 和保存确认。"
    )
    public String saveSkillVersion(
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId,
            @ToolParam(name = "iteration", description = "迭代号（从 0 开始）") int iteration,
            @ToolParam(name = "variantIndex", description = "策略变体序号（合并版填 0）") int variantIndex,
            @ToolParam(name = "skillMarkdown", description = "SKILL.md 的完整 markdown 内容") String skillMarkdown,
            @ToolParam(name = "versionLabel", description = "版本标签（如 v0, v1-strategy-A）") String versionLabel
    ) {
        EvolverSkillVersionEntity entity = EvolverSkillVersionEntity.builder()
                .taskId(taskId)
                .iteration(iteration)
                .variantIndex(variantIndex)
                .versionLabel(versionLabel)
                .skillMarkdown(skillMarkdown)
                .createdAt(LocalDateTime.now())
                .build();

        EvolverSkillVersionEntity saved = versionSaver.save(entity);
        log.info("[Evolver] Skill 版本已保存: {} (迭代 {}, 变体 {})",
                versionLabel, iteration, variantIndex);
        return "✅ Skill 版本已保存\n- 版本 ID: " + saved.getVersionId()
                + "\n- 标签: " + versionLabel
                + "\n- 迭代: " + iteration + ", 变体: " + variantIndex;
    }

    @Tool(
            name = "update_version_result",
            description = "更新某个 skill 版本的 trial 结果：通过率、平均奖励、trace 摘要、分析结论。"
                    + "在 Explore 阶段的 trial 完成后调用。"
    )
    public String updateVersionResult(
            @ToolParam(name = "versionId", description = "Skill 版本 ID") String versionId,
            @ToolParam(name = "passRate", description = "通过率（0.0~1.0）") double passRate,
            @ToolParam(name = "meanReward", description = "平均奖励分数") double meanReward,
            @ToolParam(name = "successTraces", description = "成功 trace 摘要（JSON 数组字符串）") String successTraces,
            @ToolParam(name = "failureTraces", description = "失败 trace 摘要（JSON 数组字符串）") String failureTraces,
            @ToolParam(name = "analysis", description = "LLM 对成功 vs 失败差异的分析结论") String analysis
    ) {
        // 构造一个部分更新的 entity（mybatis-flex 按 ID 更新非 null 字段）
        EvolverSkillVersionEntity entity = new EvolverSkillVersionEntity();
        entity.setVersionId(versionId);
        entity.setPassRate(passRate);
        entity.setMeanReward(meanReward);
        entity.setSuccessTraces(successTraces);
        entity.setFailureTraces(failureTraces);
        entity.setAnalysis(analysis);

        versionSaver.save(entity);
        log.info("[Evolver] 版本 {} 结果已更新: passRate={}, reward={}", versionId, passRate, meanReward);
        return "✅ 版本 " + versionId + " 结果已更新: passRate=" + passRate + ", meanReward=" + meanReward;
    }

    @Tool(
            name = "list_skill_versions",
            description = "列出指定任务的所有 skill 版本，按迭代号和变体序号排序。"
                    + "用于在迭代结束后比较各版本表现、选择最佳版本。"
    )
    public String listSkillVersions(
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId,
            @ToolParam(name = "iteration", description = "筛选指定迭代（-1 表示全部）") int iteration
    ) {
        List<EvolverSkillVersionEntity> versions = versionReader.readByTask(taskId);

        StringBuilder sb = new StringBuilder();
        sb.append("## Skill 版本列表（共 ").append(versions.size()).append(" 个）\n\n");

        sb.append("| 版本 | 迭代 | 变体 | 通过率 | 奖励 | 最佳 |\n");
        sb.append("|------|------|------|--------|------|------|\n");

        for (EvolverSkillVersionEntity v : versions) {
            if (iteration >= 0 && !v.getIteration().equals(iteration)) continue;
            sb.append("| ").append(v.getVersionLabel());
            sb.append(" | ").append(v.getIteration());
            sb.append(" | ").append(v.getVariantIndex());
            sb.append(" | ").append(v.getPassRate() != null ? String.format("%.2f", v.getPassRate()) : "—");
            sb.append(" | ").append(v.getMeanReward() != null ? String.format("%.3f", v.getMeanReward()) : "—");
            sb.append(" | ").append(Boolean.TRUE.equals(v.getIsBest()) ? "⭐" : "");
            sb.append(" |\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "select_best_and_validate",
            description = "选择最佳 skill 版本并触发最终验证。"
                    + "根据奖励模式（discrete 看通过率，continuous 看平均奖励）选择最佳版本，"
                    + "更新任务状态并保存最终结果。"
    )
    public String selectBestAndValidate(
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId,
            @ToolParam(name = "bestVersionId", description = "选定的最佳版本 ID") String bestVersionId,
            @ToolParam(name = "reasoning", description = "选择该版本的理由（简要说明）") String reasoning
    ) {
        List<EvolverSkillVersionEntity> versions = versionReader.readByTask(taskId);
        EvolverSkillVersionEntity best = versions.stream()
                .filter(v -> v.getVersionId().equals(bestVersionId))
                .findFirst()
                .orElse(null);

        if (best == null) {
            return "❌ 未找到版本: " + bestVersionId;
        }

        bestSkillUpdater.updateBest(
                taskId,
                bestVersionId,
                best.getSkillMarkdown(),
                best.getMeanReward(),
                best.getPassRate()
        );

        log.info("[Evolver] 任务 {} 选中最佳版本: {} (奖励={})", taskId, bestVersionId, best.getMeanReward());
        return "✅ 最佳版本已选定\n"
                + "- 版本: " + best.getVersionLabel() + "\n"
                + "- 奖励: " + best.getMeanReward() + "\n"
                + "- 通过率: " + best.getPassRate() + "\n"
                + "- 理由: " + reasoning + "\n\n"
                + "接下来请调用 finalize_task 完成任务。";
    }

    @Tool(
            name = "finalize_task",
            description = "最终验证完成后，将任务状态设为 COMPLETED，保存最终验证通过率。"
                    + "这是整个进化流程的最后一步。"
    )
    public String finalizeTask(
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId,
            @ToolParam(name = "validationPassRate", description = "最终验证通过率（0.0~1.0）") double validationPassRate,
            @ToolParam(name = "tokenEstimate", description = "总 token 消耗估算") long tokenEstimate
    ) {
        taskUpdater.update(taskId, "COMPLETED", null);

        // 更新验证通过率
        EvolverTaskEntity task = taskReader.read(taskId);
        if (task != null) {
            task.setValidationPassRate(validationPassRate);
            task.setTokenEstimate(tokenEstimate);
            task.setUpdatedAt(LocalDateTime.now());
        }

        log.info("[Evolver] 任务 {} 已完成: validationPassRate={}", taskId, validationPassRate);
        return "✅ 进化任务完成！\n"
                + "- 任务: " + taskId + "\n"
                + "- 验证通过率: " + String.format("%.2f", validationPassRate) + "\n"
                + "- Token 消耗估算: " + tokenEstimate + "\n\n"
                + "PIPELINE COMPLETE";
    }

    @Tool(
            name = "fail_task",
            description = "将任务标记为失败状态，附带失败原因。在遇到不可恢复的错误时调用。"
    )
    public String failTask(
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId,
            @ToolParam(name = "reason", description = "失败原因") String reason
    ) {
        taskUpdater.update(taskId, "FAILED", null);
        log.warn("[Evolver] 任务 {} 标记为失败: {}", taskId, reason);
        return "❌ 任务已标记为失败: " + reason;
    }
}
