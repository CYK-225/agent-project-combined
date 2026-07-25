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
 * SkillOpt 候选 Skill 快照实体。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "skillopt_candidate", schema = "agent_test")
public class SkillOptCandidateEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String jobId;

    /** Epoch 序号 */
    private Integer epoch;

    /** 候选 SKILL.md 内容 */
    @Column(value = "candidate_skill")
    private String candidateSkill;

    /** 应用的编辑列表（JSON） */
    @Column(value = "edits_applied")
    private String editsApplied;

    /** 验证分数 */
    @Column(value = "validation_score")
    private Double validationScore;

    /** 门控是否通过 */
    @Column(value = "gate_accepted")
    private Boolean gateAccepted;

    /** 门控拒绝原因 */
    @Column(value = "rejection_reason")
    private String rejectionReason;

    /** 是否为历史最佳 */
    @Builder.Default
    private Boolean isBest = false;

    private LocalDateTime createdAt;
}
