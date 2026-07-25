package org.example.agentScope.mas.phone.dataModel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 中台 → 工具平台 的回调通知 DTO
 * <p>
 * 当 Agent 执行完成、失败或挂起时，AI 中台通过 notifyToolPlatform() 将任务结果
 * 推送给工具平台。工具平台的 AgentCallbackController 接收此对象。
 * <p>
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>Agent 执行完成 → 通知工具平台获取最终结果</li>
 *   <li>Agent 执行失败 → 通知工具平台错误信息</li>
 *   <li>Agent 挂起/恢复 → 通知工具平台当前进度</li>
 * </ul>
 *
 * <h3>对应工具平台接口：</h3>
 * <pre>
 * POST callbackUrl
 * Content-Type: application/json
 *
 * {
 *   "taskId": "task_xxx",
 *   "sessionId": "task_xxx",
 *   "containerUrl": "http://8.129.128.167:8090",
 *   "step": 3,
 *   "success": true,
 *   "result": "已完成...",
 *   "screenshotPath": "/app/anno/task_xxx/step_003.png",
 *   "modelOutput": "Agent 的完整输出",
 *   "log": "执行日志",
 *   "timestamp": 1717234567890,
 *   "callbackUrl": "http://tool-platform/api/callback"
 * }
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentTaskNotifyDTO {
    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 会话 ID（与 taskId 关联，flowID = sessionId = taskId）
     */
    private String sessionId;

    /**
     * Agent 名称（如 "hr-agent"）
     */
    private String agentName;

    /**
     * 用户指令
     */
    private String instruction;

    /**
     * 提示词组 ID（关联业务系统的提示词组）
     */
    private String promptsId;

    /**
     * 提示词类型
     */
    private String promptType;

    /**
     * 容器地址
     */
    private String containerUrl;

    /**
     * 当前步骤内的步骤编号
     */
    private Integer toolStep;

    /**
     * 当前的大步骤编号
     */
    private Integer step;

    /**
     * GUI 操作是否成功
     */
    private Boolean success;

    /**
     * GUI 操作结果描述
     */
    private String result;

    /**
     * 任务状态：RUNNING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 截图 base64 编码（容器回调时携带，用于 Agent 恢复上下文）
     */
    private String screenshot;

    /**
     * 截图在宿主机上的保存路径
     */
    private String screenshotPath;

    /**
     * 落盘结果（JSON 格式，Map 类型的结构化输出）
     */
    private String outputResult;

    /**
     * Agent 的完整输出文本
     */
    private String modelOutput;

    /**
     * 本次调用的工具名称
     */
    private String toolName;

    /**
     * 本次调用的工具输入参数（JSON 字符串）
     */
    private String toolInput;

    /**
     * 通知时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 工具平台的回调地址
     */
    private String callbackUrl;

    /**
     * AI 中台的回调地址
     */
    private String aiCallbackUrl;

    /**
     * 系统提示词
     */
    private String sysPrompt;
}
