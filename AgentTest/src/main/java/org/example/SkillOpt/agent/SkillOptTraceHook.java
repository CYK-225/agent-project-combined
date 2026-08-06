package org.example.skillOpt.agent;

import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;
import org.example.skillOpt.env.StepRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SkillOpt 轨迹追踪 Hook — 记录带工具调用的 rollout 完整轨迹。
 * <p>
 * 捕获 4 个关键生命周期点位：
 * <ul>
 *   <li>{@code handlePreActing}    — 工具调用前：记录工具名 + 输入参数</li>
 *   <li>{@code handlePostActing}   — 工具调用后：记录工具执行结果</li>
 *   <li>{@code handlePostReasoning}— LLM 推理后：记录推理输出</li>
 *   <li>{@code handlePostCall}     — Agent 完成后：记录最终回复</li>
 * </ul>
 * <p>
 * 线程安全：每个线程维护独立的轨迹列表，最终合并。
 *
 * @author zhilin
 */
@Slf4j
public class SkillOptTraceHook extends AbstractAgentHook {

    /** 线程级轨迹存储 */
    private static final Map<Long, List<StepRecord>> THREAD_TRACES = new ConcurrentHashMap<>();

    /** 当前步骤计数器 */
    private static final Map<Long, Integer> STEP_COUNTERS = new ConcurrentHashMap<>();

    /** 当前正在执行的工具调用 */
    private static final Map<Long, PendingToolCall> PENDING_CALLS = new ConcurrentHashMap<>();

    public SkillOptTraceHook() {
        super(2); // 高优先级
    }

    // ======================== 生命周期钩子 ========================

    /**
     * 工具执行前 — 记录工具名和输入参数
     */
    @Override
    protected void handlePreActing(PreActingEvent event) {
        try {
            ToolUseBlock toolUse = event.getToolUse();
            if (toolUse == null) return;

            long threadId = Thread.currentThread().getId();

            PendingToolCall pending = new PendingToolCall();
            pending.toolName = toolUse.getName();
            pending.toolInput = toJson(toolUse.getInput());
            pending.timestamp = System.currentTimeMillis();
            pending.agentName = event.getAgent().getName();

            PENDING_CALLS.put(threadId, pending);

            log.debug("[SkillOptTraceHook] PreActing: {}({})",
                    pending.toolName, truncate(pending.toolInput, 200));
        } catch (Exception e) {
            log.warn("[SkillOptTraceHook] handlePreActing 异常", e);
        }
    }

    /**
     * 工具执行后 — 记录工具结果，创建 StepRecord
     */
    @Override
    protected void handlePostActing(PostActingEvent event) {
        try {
            long threadId = Thread.currentThread().getId();
            PendingToolCall pending = PENDING_CALLS.remove(threadId);

            if (pending == null) return;

            String result = null;
            ToolResultBlock toolResult = event.getToolResult();
            if (toolResult != null) {
                result = extractText(toolResult);
            }

            // 创建 StepRecord
            StepRecord record = StepRecord.builder()
                    .stepIndex(getNextStepIndex(threadId))
                    .isToolCall(true)
                    .toolName(pending.toolName)
                    .toolInput(pending.toolInput)
                    .toolResult(result != null ? truncate(result, 5000) : "")
                    .toolSuccess(result != null && !result.contains("❌"))
                    .rawAgentOutput(pending.toolName + "(" + pending.toolInput + ")")
                    .timestamp(System.currentTimeMillis())
                    .build();

            addStepRecord(threadId, record);

            log.debug("[SkillOptTraceHook] PostActing: {} -> {}",
                    pending.toolName, truncate(result, 200));
        } catch (Exception e) {
            log.warn("[SkillOptTraceHook] handlePostActing 异常", e);
        }
    }

    /**
     * LLM 推理后 — 记录推理输出
     */
    @Override
    protected void handlePostReasoning(PostReasoningEvent event) {
        try {
            Msg reasoningMsg = event.getReasoningMessage();
            if (reasoningMsg == null) return;

            String textContent = reasoningMsg.getTextContent();
            if (textContent == null || textContent.isBlank()) return;

            long threadId = Thread.currentThread().getId();

            // 更新 pending call 的推理内容
            PendingToolCall pending = PENDING_CALLS.get(threadId);
            if (pending != null) {
                pending.reasoning = textContent;
            }

            log.debug("[SkillOptTraceHook] PostReasoning: {}", truncate(textContent, 200));
        } catch (Exception e) {
            log.warn("[SkillOptTraceHook] handlePostReasoning 异常", e);
        }
    }

    /**
     * Agent 调用后 — 记录最终回复
     */
    @Override
    protected void handlePostCall(PostCallEvent event) {
        try {
            Msg msg = event.getFinalMessage();
            if (msg == null) return;

            String textContent = msg.getTextContent();
            if (textContent == null || textContent.isBlank()) return;

            long threadId = Thread.currentThread().getId();

            // 创建最终回复的 StepRecord
            StepRecord record = StepRecord.builder()
                    .stepIndex(getNextStepIndex(threadId))
                    .isToolCall(false)
                    .action(textContent)
                    .reasoning("")
                    .rawAgentOutput(textContent)
                    .timestamp(System.currentTimeMillis())
                    .build();

            addStepRecord(threadId, record);

            log.debug("[SkillOptTraceHook] PostCall (final): {}", truncate(textContent, 200));
        } catch (Exception e) {
            log.warn("[SkillOptTraceHook] handlePostCall 异常", e);
        }
    }

    // ======================== 访问追踪数据 ========================

    /**
     * 获取当前线程的所有追踪记录
     */
    public static List<StepRecord> getThreadTrace() {
        long threadId = Thread.currentThread().getId();
        List<StepRecord> trace = THREAD_TRACES.get(threadId);
        return trace != null ? List.copyOf(trace) : List.of();
    }

    /**
     * 获取并清除当前线程的追踪记录
     */
    public static List<StepRecord> drainThreadTrace() {
        long threadId = Thread.currentThread().getId();
        List<StepRecord> trace = THREAD_TRACES.remove(threadId);
        STEP_COUNTERS.remove(threadId);
        return trace != null ? List.copyOf(trace) : List.of();
    }

    /**
     * 清除当前线程的追踪数据
     */
    public static void clearThreadTrace() {
        long threadId = Thread.currentThread().getId();
        THREAD_TRACES.remove(threadId);
        STEP_COUNTERS.remove(threadId);
        PENDING_CALLS.remove(threadId);
    }

    // ======================== 内部工具方法 ========================

    private static int getNextStepIndex(long threadId) {
        return STEP_COUNTERS.compute(threadId, (k, v) -> v == null ? 0 : v + 1);
    }

    private static void addStepRecord(long threadId, StepRecord record) {
        THREAD_TRACES.computeIfAbsent(threadId, k -> new CopyOnWriteArrayList<>()).add(record);
    }

    private static String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private static String extractText(ToolResultBlock block) {
        try {
            var output = block.getOutput();
            if (output == null) {
                return block.toString();
            }
            if (!output.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var contentBlock : output) {
                    if (contentBlock instanceof io.agentscope.core.message.TextBlock tb) {
                        sb.append(tb.getText());
                    } else {
                        sb.append("[").append(contentBlock.getClass().getSimpleName()).append("]");
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }
        } catch (Exception e) {
            log.warn("[SkillOptTraceHook] extractText failed", e);
        }
        return block.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ======================== 内部数据结构 ========================

    private static class PendingToolCall {
        String toolName;
        String toolInput;
        String reasoning;
        long timestamp;
        String agentName;
    }
}
