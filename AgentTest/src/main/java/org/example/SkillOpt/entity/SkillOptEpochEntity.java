package org.example.skillOpt.entity;

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
 * SkillOpt Epoch 记录实体。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "skillopt_epoch", schema = "agent_test")
public class SkillOptEpochEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String jobId;

    /** Epoch 序号（从 0 开始） */
    private Integer epoch;

    /** Epoch 开始时的 skill */
    @Column(value = "skill_before")
    private String skillBefore;

    /** Epoch 结束时的 skill（门控拒绝时等于 skillBefore） */
    @Column(value = "skill_after")
    private String skillAfter;

    /** 候选 skill（Update 后的） */
    @Column(value = "candidate_skill")
    private String candidateSkill;

    /** 训练集 hard score */
    @Column(value = "train_hard_score")
    private Double trainHardScore;

    /** 训练集 soft score */
    @Column(value = "train_soft_score")
    private Double trainSoftScore;

    /** 验证集分数 */
    @Column(value = "validation_score")
    private Double validationScore;

    /** 上一 epoch 的验证分数 */
    @Column(value = "previous_validation_score")
    private Double previousValidationScore;

    /** 验证门控是否通过 */
    @Column(value = "gate_accepted")
    private Boolean gateAccepted;

    /** 门控类型 */
    @Column(value = "gate_type")
    private String gateType;

    /** 本 epoch 的编辑预算 */
    @Column(value = "edit_budget")
    private Integer editBudget;

    /** 实际应用的编辑数 */
    @Column(value = "actual_edit_count")
    private Integer actualEditCount;

    /** 受保护的 section（JSON 数组） */
    @Column(value = "protected_regions")
    private String protectedRegions;

    /** 该 epoch 的 meta-skill 快照 */
    @Column(value = "meta_skill")
    private String metaSkill;

    /** Epoch 摘要 */
    @Column(value = "epoch_summary")
    private String epochSummary;

    private LocalDateTime createdAt;
}
