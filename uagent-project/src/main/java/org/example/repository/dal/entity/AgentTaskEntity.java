package org.example.repository.dal.entity;

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
 * HR Agent 任务表
 * 对应数据库表：agent_task
 */
@Data
@Table(value = "agent_task")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentTaskEntity {

    // ========== AI-GENERATED-START: fields ==========
    @Id(keyType = KeyType.Auto)
    @Column(value = "id")
    private Long id;

    @Column(value = "agent_name")
    private String agentName;

    @Column(value = "task_id")
    private String taskId;

    @Column(value = "session_id")
    private String sessionId;

    @Column(value = "container_port")
    private Integer containerPort;

    @Column(value = "instruction")
    private String instruction;

    @Column(value = "log")
    private String log;

    @Column(value = "model_output")
    private String modelOutput;

    @Column(value = "callback_url")
    private String callbackUrl;

    @Column(value = "status")
    private String status;

    @Column(value = "error_message")
    private String errorMessage;

    @Column(value = "created_by")
    private String createdBy;

    @Column(value = "updated_by")
    private String updatedBy;

    @Column(value = "created_at")
    private LocalDateTime createdAt;

    @Column(value = "updated_at")
    private LocalDateTime updatedAt;

    @Column(value = "started_at")
    private LocalDateTime startedAt;

    @Column(value = "completed_at")
    private LocalDateTime completedAt;

    @Column(value = "success")
    private Boolean success;

    @Column(value = "result")
    private String result;

    @Column(value = "screenshot")
    private String screenshot;

    @Column(value = "screenshot_path")
    private String screenshotPath;

    @Column(value = "step")
    private Integer step;

    @Column(value = "prompts_id")
    private String promptsId;

    @Column(value = "prompt_type")
    private String promptType;

    @Column(value = "ai_callback_url")
    private String aiCallbackUrl;
    // ========== AI-GENERATED-END: fields ==========

    // ========== HUMAN-AREA: custom ==========
    // 在此区域添加自定义字段或方法
    // ========== HUMAN-AREA-END: custom ==========
}