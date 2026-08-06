package org.example.acl.hook;

import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.message.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;
import org.example.agentScope.util.hooksManager.SessionContext;

import java.util.Map;

/**
 * HR Agent 核心 Hook
 * <p>
 * 职责：
 * 1. 打印推理完整文本（思考链 + 正文）
 * 2. 打印工具调用名称
 * 3. 打印工具调用参数（String 形式）
 * 4. 管理 SessionContext 和工具平台通信
 * </p>
 */
@Slf4j
public class callRQHook extends AbstractAgentHook {

    private static final String TAG = "[HR-Agent]";

    private final SessionContext sc;
    @Getter
    private  AgentTaskNotifyDTO taskNotifyDTO;

    /** 当前 step 内的工具调用计数器（每次 handlePreActing 递增） */
    private int toolStepCounter = 0;

    /** step记录器 */
    private int stepRecord = 0;

    public callRQHook(SessionContext sc) {
        this.sc = sc;
        this.taskNotifyDTO=sc.get(AgentTaskNotifyDTO.class);
    }

    @Override
    protected void handlePreCall(PreCallEvent event) {
        String threadId = sc.getThreadId();
    }

    @Override
    protected void handlePostCall(PostCallEvent event) {
        // 添加返回给工具平台的状态
    }

    // ==================== 推理完成：打印完整推理文本 ====================

    @Override
    protected void handlePostReasoning(PostReasoningEvent event) {
        Msg reasoningMsg = event.getReasoningMessage();
        String agentName = event.getAgent().getName();

        log.info("{} [{}] ===== 推理完成 =====", TAG, agentName);

        for (ContentBlock block : reasoningMsg.getContent()) {
            if (block instanceof ThinkingBlock thinking) {
                String thought = thinking.getThinking();
                if (thought != null && !thought.isBlank()) {
                    log.info("{} [{}] [思考链] {}", TAG, agentName, thought);
                }
            } else if (block instanceof TextBlock text) {
                String textContent = text.getText();
                if (textContent != null && !textContent.isBlank()) {
                    taskNotifyDTO.setOutputResult(textContent);
                    log.info("{} [{}] [正文] {}", TAG, agentName, textContent);
                }
            } else if (block instanceof ToolUseBlock toolUse) {
                String toolName = toolUse.getName();
                Map<String, Object> input = toolUse.getInput();
                log.info("{} [{}] [推理阶段-工具调用] 工具: {} | 参数: {}",
                        TAG, agentName, toolName, String.valueOf(input));
            }
        }

        log.info("{} [{}] ===== 推理文本结束 =====", TAG, agentName);
    }

    // ==================== 工具执行前：打印工具名称和参数 ====================

    @Override
    protected void handlePreActing(PreActingEvent event) {
        String agentName = event.getAgent().getName();
        ToolUseBlock toolUse = event.getToolUse();

        String toolName = toolUse.getName();
        Map<String, Object> input = toolUse.getInput();

        /**
         * 如果步骤变化，则重置工具调用次数
         */
        if (stepRecord != taskNotifyDTO.getStep()){
            stepRecord = taskNotifyDTO.getStep();
            toolStepCounter = 0;
        }

        // 递增步骤内的工具调用序号
        toolStepCounter++;

        // 写入 DTO，供 resumeTask → notifyStepLog / saveExecutionDetail 使用
        taskNotifyDTO.setToolStep(toolStepCounter);
        taskNotifyDTO.setToolName(toolName);
        taskNotifyDTO.setToolInput(String.valueOf(input));

        log.info("{} [{}] ===== 工具调用 =====", TAG, agentName);
        log.info("{} [{}] 工具名称: {}", TAG, agentName, toolName);
        log.info("{} [{}] 工具参数: {}", TAG, agentName, String.valueOf(input));
        log.info("{} [{}] ===== 工具调用结束 =====", TAG, agentName);
    }

}
