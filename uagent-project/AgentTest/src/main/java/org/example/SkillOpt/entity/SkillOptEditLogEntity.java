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
 * SkillOpt 编辑操作日志实体。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "skillopt_edit_log", schema = "agent_test")
public class SkillOptEditLogEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String jobId;

    /** Epoch 序号 */
    private Integer epoch;

    /** 编辑序号 */
    @Column(value = "edit_index")
    private Integer editIndex;

    /** 编辑类型：APPEND / INSERT_AFTER / REPLACE / DELETE */
    @Column(value = "edit_type")
    private String editType;

    /** 目标 section 标题 */
    @Column(value = "target_section")
    private String targetSection;

    /** 编辑内容 */
    private String content;

    /** 编辑原因 */
    private String rationale;

    /** 优先级（0.0~1.0） */
    private Double priority;

    /** 是否实际应用 */
    @Builder.Default
    private Boolean applied = true;

    /** 被裁剪的原因 */
    @Column(value = "skipped_reason")
    private String skippedReason;

    private LocalDateTime createdAt;
}
