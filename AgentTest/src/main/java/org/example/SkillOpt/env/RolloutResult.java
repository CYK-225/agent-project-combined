package org.example.skillOpt.env;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rollout 执行结果。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolloutResult {

    /** Worker 序号 */
    private int variantIndex;

    /** 状态：PASSED / FAILED / TIMEOUT / ERROR */
    private String status;

    /** 精确匹配分数（0.0 或 1.0） */
    private double hardScore;

    /** 部分信用分数（0.0~1.0） */
    private double softScore;

    /** 执行轨迹 */
    private String trajectory;

    /** Agent 最终回复 */
    private String agentResponse;

    /** 任务实例 */
    private Map<String, Object> taskInstance;

    // ==================== 多轮轨迹支持 ====================

    /** 是否使用了工具调用 */
    @Builder.Default
    private boolean toolCallingUsed = false;

    /** 多轮轨迹记录 */
    @Builder.Default
    private List<StepRecord> stepRecords = new ArrayList<>();

    /** 轨迹总步数 */
    private int totalSteps;

    /** 是否达到最大步数限制 */
    private boolean maxStepsReached;

    /**
     * 添加一步记录
     */
    public void addStepRecord(StepRecord record) {
        if (stepRecords == null) {
            stepRecords = new ArrayList<>();
        }
        stepRecords.add(record);
        totalSteps = stepRecords.size();
    }

    /**
     * 从 stepRecords 生成文本轨迹（用于存储和分析）
     */
    public String generateTrajectoryFromSteps() {
        if (stepRecords == null || stepRecords.isEmpty()) {
            return trajectory;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Rollout Trajectory\n\n");

        for (StepRecord step : stepRecords) {
            sb.append(step.toMarkdown()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取最终答案（最后一步的 action 或 agentResponse）
     */
    public String getFinalAnswer() {
        if (stepRecords != null && !stepRecords.isEmpty()) {
            StepRecord lastStep = stepRecords.get(stepRecords.size() - 1);
            if (!lastStep.isToolCall()) {
                return lastStep.getAction();
            }
        }
        return agentResponse;
    }
}
