package org.example.sliders.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * SLIDERS 流水线执行日志表
 *
 * @see org.example.sliders.mapper.SlidersPipelineLogMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_pipeline_log", schema = "agent_test")
public class SlidersPipelineLogEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "task_id")
    private String taskId;

    /** Agent 名称 */
    @Column(value = "agent_name")
    private String agentName;

    /** 阶段: CHUNKING / SCHEMA / EXTRACTION / RECONCILIATION / ANSWER */
    @Column(value = "stage")
    private String stage;

    /** 动作: STARTED / COMPLETED / FAILED / MAIL_SENT */
    @Column(value = "action")
    private String action;

    /** 详细信息 */
    @Column(value = "detail")
    private String detail;

    /** 耗时（毫秒） */
    @Column(value = "duration_ms")
    private Long durationMs;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
