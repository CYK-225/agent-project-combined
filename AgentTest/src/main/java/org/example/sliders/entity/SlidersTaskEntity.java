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
 * SLIDERS 流水线任务表
 *
 * @see org.example.sliders.mapper.SlidersTaskMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_task", schema = "agent_test")
public class SlidersTaskEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 业务 ID (UUID) */
    @Column(value = "task_id")
    private String taskId;

    /** 会话 ID（邮件隔离维度） */
    @Column(value = "thread_id")
    private String threadId;

    /** 用户问题 */
    @Column(value = "question")
    private String question;

    /** 任务状态 */
    @Column(value = "status")
    private String status;

    /** 最终答案 */
    @Column(value = "answer")
    private String answer;

    /** 错误信息 */
    @Column(value = "error_message")
    private String errorMessage;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;

    @Column(value = "updated_at", onInsertValue = "now()", onUpdateValue = "now()")
    private Date updatedAt;
}
