package org.example.repository.dal.entity;

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
 * Agent任务实体类
 * <p>
 * 对应数据库表：agent_task
 * 数据源：postgresql-session
 * </p>
 * 
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>HR智能体任务</li>
 *   <li>其他智能体任务</li>
 *   <li>工具平台与Agent平台的任务同步</li>
 * </ul>
 */
@Data
@Table(value = "agent_task")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentTaskEntity {

    /**
     * 主键ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * Agent名称
     */
    @Column(value = "agent_name")
    private String agentName;

    /**
     * 任务ID（唯一标识）
     */
    @Column(value = "task_id")
    private String taskId;

    /**
     * 会话ID（可用任务ID替代）
     */
    @Column(value = "session_id")
    private String sessionId;

    /**
     * 传输的内容/指令
     */
    @Column(value = "instruction")
    private String instruction;

    /**
     * 提示词组 ID（关联业务系统的提示词组）
     */
    @Column(value = "prompts_id")
    private String promptsId;

    /**
     * 提示词类型（区分不同业务场景的提示词模板）
     */
    @Column(value = "prompt_type")
    private String promptType;

    /**
     * 工具平台的回调地址
     */
    @Column(value = "callback_url")
    private String callbackUrl;
    
    /**
     * ai中台的回调地址
     */
    @Column(value = "ai_callback_url")
    private String aiCallbackUrl;

    /**
     * 状态：RUNNING-执行中, SUCCESS-已完成, FAILED-失败
     */
    @Column(value = "status")
    private String status;

    /**
     * 错误信息
     */
    @Column(value = "error_message")
    private String errorMessage;

    /**
     * 创建者
     */
    @Column(value = "created_by")
    private String createdBy;

    /**
     * 修改者
     */
    @Column(value = "updated_by")
    private String updatedBy;

    /**
     * 创建时间
     */
    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;

    /**
     * 更新时间
     */
    @Column(value = "updated_at", onInsertValue = "now()", onUpdateValue = "now()")
    private Date updatedAt;

    /**
     * 开始执行时间
     */
    @Column(value = "started_at")
    private Date startedAt;

    /**
     * 完成时间
     */
    @Column(value = "completed_at")
    private Date completedAt;

    // ==================== 工具执行结果字段（已迁移至 agent_execution_detail 表）====================

    /**
     * 工具执行是否成功
     */
    @Column(value = "success")
    private Boolean success;

    /**
     * 成功时的结果描述
     */
    @Column(value = "result")
    private String result;

    // 以下字段已迁移至 agent_execution_detail 表，DB 列暂保留，后续 DROP COLUMN：
    // - screenshot (TEXT)       → agent_execution_detail.screenshot_path
    // - screenshot_path (VARCHAR) → agent_execution_detail.screenshot_path
    // - step (INTEGER)          → agent_execution_detail.step
    // - model_output (TEXT)     → agent_execution_detail.model_output
    // - log (TEXT)              → 已废弃（明细表即日志）
    // - container_port (INTEGER) → 已废弃（运行时由调用方传入 containerUrl）

    /**
     * 状态枚举 — 只有三个终态
     */
    public static class Status {
        /** 执行中 */
        public static final String RUNNING = "RUNNING";
        /** 已完成 */
        public static final String SUCCESS = "SUCCESS";
        /** 失败 */
        public static final String FAILED = "FAILED";
    }
}
