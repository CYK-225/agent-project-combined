package org.example.agent.financeForecastAgent.hooks;

import io.agentscope.core.hook.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 结构化状态反馈钩子
 * 替代原有的 ToolOutputLogHook，在工具执行后记录状态并注入反馈，
 * 在推理偏差时进行纠正，在会话开始时注入执行上下文。
 *
 * 调试开关：finance-agent.debug-enabled
 * - true  → 向 ToolResult 注入 [系统状态提示]，开发者可在前端看到
 * - false → 仅内部追踪状态，不注入可见提示，适合普通用户
 *
 * author: zhilin
 * 2026.04.14
 */
@Component
/*
    * 使用 prototype 作用域确保每个会话/线程有独立的状态跟踪器，
 */
@Scope("prototype")
@Slf4j
public class StateFeedbackHook implements Hook {

    @Value("${finance-agent.debug-enabled:false}")
    private boolean debugEnabled;

    @PostConstruct
    public void logDebugMode() {
        log.info("[StateFeedbackHook] 调试模式: debugEnabled={}", debugEnabled);
    }

    /**
     * 每个线程维护独立的执行状态跟踪器，
     * 确保并发请求之间互不干扰。
     */
    private final ThreadLocal<ExecutionStateTracker> stateTracker =
            ThreadLocal.withInitial(ExecutionStateTracker::new);

    // ========== PostActing: 工具执行后记录状态 ==========

    private void handlePostActing(PostActingEvent event) {
        String toolName = event.getToolUse().getName();
        String resultText = extractResultText(event.getToolResult());

        // 1. 记录工具结果到状态跟踪器
        ExecutionStateTracker tracker = stateTracker.get();
        tracker.recordToolResult(toolName, resultText);

        log.debug("[StateFeedback] step={}, tool={}, status={}",
                tracker.getCurrentStep(), toolName, tracker.getLastToolStatus());

        // 2. 异常状态：修改工具返回结果，附加状态提示（仅调试模式）
        if (tracker.needsIntervention()) {
            String interventionHint = tracker.buildInterventionHint();
            log.info("[StateFeedback] 检测到异常，注入干预提示: {}", interventionHint);

            if (debugEnabled) {
                // 调试模式：注入可见的 [系统状态提示]，前端可展示
                String enrichedResult = resultText + "\n\n[系统状态提示] " + interventionHint;
                ToolResultBlock original = event.getToolResult();
                ToolResultBlock enriched = ToolResultBlock.of(
                        original.getId(),
                        original.getName(),
                        ToolResultBlock.text(enrichedResult).getOutput()
                );
                event.setToolResult(enriched);
            }
            // 非调试模式：仅记录日志，不修改 ToolResult，用户端不可见
        }
    }

    // ========== PostReasoning: 推理偏差纠正 ==========

    private void handlePostReasoning(PostReasoningEvent event) {
        ExecutionStateTracker tracker = stateTracker.get();

        // 仅在明确异常且需要干预时才强制纠正
        if (tracker.needsIntervention() && tracker.getCurrentStep() == ExecutionStateTracker.Step.ERROR) {
            log.info("[StateFeedback] 推理阶段检测到严重异常，强制 gotoReasoning");

            Msg hintMsg = Msg.builder()
                    .role(MsgRole.valueOf("system"))
                    .textContent("[系统强制纠正] " + tracker.buildInterventionHint())
                    .build();
            event.gotoReasoning(hintMsg);
        }
    }

    // ========== PreCall: 注入执行状态上下文 ==========

    private void handlePreCall(PreCallEvent event) {
        ExecutionStateTracker tracker = stateTracker.get();
        String stateContext = tracker.buildStateContext();

        if (stateContext.isEmpty()) {
            return;
        }

        // 构建状态上下文消息
        Msg stateMsg = Msg.builder()
                .role(MsgRole.valueOf("system"))
                .textContent("[执行状态上下文] " + stateContext)
                .build();

        List<Msg> originalMessages = event.getInputMessages();
        List<Msg> enrichedMessages = new ArrayList<>(originalMessages);

        // 插入到第二位（第一个是 system prompt，保持不变）
        if (enrichedMessages.size() > 1) {
            enrichedMessages.add(1, stateMsg);
        } else {
            enrichedMessages.add(stateMsg);
        }

        event.setInputMessages(enrichedMessages);
        log.debug("[StateFeedback] 已注入执行状态上下文: {}", stateContext);
    }

    // ========== Hook 入口 ==========

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {

        if (event instanceof PostActingEvent postActing) {
            handlePostActing(postActing);
        } else if (event instanceof PostReasoningEvent postReasoning) {
            handlePostReasoning(postReasoning);
        } else if (event instanceof PreCallEvent preCall) {
            handlePreCall(preCall);
        }

        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 5;
    }

    // ========== 辅助方法 ==========

    /**
     * 从 ToolResultBlock 提取文本内容
     */
    private String extractResultText(ToolResultBlock toolResult) {
        if (toolResult == null) {
            return "";
        }
        return toolResult.getOutput().stream()
                .map(Object::toString)
                .collect(Collectors.joining());
    }

    /**
     * 重置当前线程的执行状态（供外部调用）
     */
    public void resetState() {
        stateTracker.get().reset();
    }
}
