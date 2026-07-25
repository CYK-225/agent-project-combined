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
 * Agent 执行明细实体类
 * <p>
 * 对应数据库表：agent_execution_detail
 * 数据源：postgresql-session
 * </p>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>记录 Agent 每一步的模型输出和工具调用</li>
 *   <li>一个 step 可能对应多行（多个工具调用）</li>
 *   <li>每行包含：模型输出 + 工具名称 + 工具输入 + 截图路径</li>
 * </ul>
 */
@Data
@Table(value = "agent_execution_detail")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentExecutionDetailEntity {

    /**
     * 主键 ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 任务 ID，关联 agent_task.task_id
     */
    @Column(value = "task_id")
    private String taskId;

    /**
     * 会话 ID
     */
    @Column(value = "session_id")
    private String sessionId;

    /**
     * 大步骤编号
     */
    @Column(value = "step")
    private Integer step;

    /**
     * 步骤内的工具编号（同一 step 内多个工具调用的序号）
     */
    @Column(value = "tool_step")
    private Integer toolStep;

    /**
     * 模型输出（LLM 原始文本决策）
     */
    @Column(value = "model_output")
    private String modelOutput;

    /**
     * 工具名称，如 gui_left_click、gui_input_text
     */
    @Column(value = "tool_name")
    private String toolName;

    /**
     * 工具输入参数（JSON 字符串）
     */
    @Column(value = "tool_input")
    private String toolInput;

    /**
     * 当前步骤截图在宿主机上的保存路径
     */
    @Column(value = "screenshot_path")
    private String screenshotPath;

    /**
     * 落盘结果（JSON 格式，存储 Map 类型的结构化输出）
     */
    @Column(value = "output_result")
    private String outputResult;

    /**
     * 记录创建时间
     */
    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
