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
 * SkillOpt 训练任务实体。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "skillopt_training_job", schema = "agent_test")
public class SkillOptTrainingJobEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String jobId;

    /** 任务描述 */
    @Column(value = "task_description")
    private String taskDescription;

    /** 初始 SKILL.md 内容 */
    @Column(value = "initial_skill")
    private String initialSkill;

    /** 训练数据（JSON 格式） */
    @Column(value = "train_data")
    private String trainData;

    /** 验证数据（JSON 格式） */
    @Column(value = "val_data")
    private String valData;

    /** 环境适配器类型（如 llm-qa） */
    @Column(value = "env_adapter_type")
    private String envAdapterType;

    /** LR 调度器类型：constant / linear / cosine / autonomous */
    @Column(value = "lr_scheduler_type")
    private String lrSchedulerType;

    /** 验证门控类型：hard / soft / mixed */
    @Column(value = "gate_type")
    private String gateType;

    /** 最大 epoch 数 */
    @Column(value = "max_epochs")
    @Builder.Default
    private Integer maxEpochs = 5;

    /** 每 epoch rollout 数量 */
    @Column(value = "batch_size")
    @Builder.Default
    private Integer batchSize = 4;

    /** 基础编辑预算 */
    @Column(value = "edit_budget_base")
    @Builder.Default
    private Integer editBudgetBase = 5;

    /** 当前 epoch */
    @Column(value = "current_epoch")
    @Builder.Default
    private Integer currentEpoch = 0;

    /** 任务状态：PENDING / RUNNING / COMPLETED / FAILED */
    @Builder.Default
    private String status = "PENDING";

    /** 最佳 Skill 内容 */
    @Column(value = "best_skill_content")
    private String bestSkillContent;

    /** 最佳 Skill 来自第几个 epoch */
    @Column(value = "best_skill_epoch")
    private Integer bestSkillEpoch;

    /** 最佳验证分数 */
    @Column(value = "best_validation_score")
    private Double bestValidationScore;

    /** 最终 meta-skill 内容 */
    @Column(value = "final_meta_skill")
    private String finalMetaSkill;

    /** 累积 token 消耗估算 */
    @Builder.Default
    private Long tokenEstimate = 0L;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
