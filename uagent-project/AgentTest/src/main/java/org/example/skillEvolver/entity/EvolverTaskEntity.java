package org.example.skillEvolver.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Skill 进化任务实体。
 * <p>
 * 记录一次完整的 Explore → Analyze → Update 循环任务，
 * 包含当前迭代状态、策略变体、最佳 skill 版本等信息。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "evolver_task", schema = "agent_test")
public class EvolverTaskEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String taskId;

    /** 任务名称（人类可读，如 "sales-pivot-analysis"） */
    private String taskName;

    /** 任务指令（Agent 要完成的目标描述） */
    private String instruction;

    /** 任务输入数据（JSON 格式，文件路径或内联数据） */
    @Column(value = "task_data")
    private String taskData;

    /** 验证规则（JSON 格式：文件存在性、关键词匹配、exit code 等） */
    @Column(value = "verifier")
    private String verifier;

    /** 奖励信号模式：discrete（pass/fail）/ continuous（标量奖励） */
    @Builder.Default
    private String rewardMode = "discrete";

    /** 总迭代轮数 R */
    @Builder.Default
    private Integer maxIterations = 2;

    /** 每轮探索 trial 数 K */
    @Builder.Default
    private Integer nExploration = 4;

    /** 最终验证 trial 数 */
    @Builder.Default
    private Integer nValidation = 5;

    /** 当前迭代轮次（从 0 开始） */
    @Builder.Default
    private Integer currentIteration = 0;

    /** 任务状态：PENDING / RUNNING / COMPLETED / FAILED */
    @Builder.Default
    private String status = "PENDING";

    /** 最佳 skill 版本号 */
    private String bestSkillVersion;

    /** 最佳 skill 内容（SKILL.md markdown） */
    @Column(value = "best_skill_content")
    private String bestSkillContent;

    /** 最佳 skill 的平均奖励分数 */
    private Double bestReward;

    /** 最终验证通过率 */
    private Double validationPassRate;

    /** 进化过程中累积的 token 消耗估算 */
    @Builder.Default
    private Long tokenEstimate = 0L;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
