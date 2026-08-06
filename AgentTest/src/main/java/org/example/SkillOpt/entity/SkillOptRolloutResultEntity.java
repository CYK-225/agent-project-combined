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
 * SkillOpt Rollout 结果实体。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "skillopt_rollout_result", schema = "agent_test")
public class SkillOptRolloutResultEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String jobId;

    /** Epoch 序号 */
    private Integer epoch;

    /** Rollout worker 序号（1-based） */
    @Column(value = "variant_index")
    private Integer variantIndex;

    /** rollout 类型：train / val */
    @Column(value = "rollout_type")
    @Builder.Default
    private String rolloutType = "train";

    /** 任务实例数据（JSON） */
    @Column(value = "task_instance")
    private String taskInstance;

    /** 状态：PASSED / FAILED / TIMEOUT / ERROR */
    private String status;

    /** 精确匹配分数（0.0 或 1.0） */
    @Column(value = "hard_score")
    private Double hardScore;

    /** 部分信用分数（0.0~1.0） */
    @Column(value = "soft_score")
    private Double softScore;

    /** 执行轨迹 */
    private String trajectory;

    /** Agent 最终回复 */
    @Column(value = "agent_response")
    private String agentResponse;

    private LocalDateTime createdAt;
}
