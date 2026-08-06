package org.example.skillEvolver.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Trial 执行追踪 Hook — 记录 testAgent 的每一步操作用于 Skill 进化分析。
 * <p>
 * 捕获 4 个关键生命周期点位：
 * <ul>
 *   <li>{@code handlePreActing}    — 工具调用前：记录工具名 + 输入参数</li>
 *   <li>{@code handlePostActing}   — 工具调用后：记录工具执行结果</li>
 *   <li>{@code handlePostReasoning}— LLM 推理后：记录推理输出</li>
 *   <li>{@code handlePostCall}     — Agent 完成后：记录最终回复</li>
 * </ul>
 * <p>
 * 线程安全：使用 {@link CopyOnWriteArrayList} 存储 trace 条目，支持并发读取。
 *
 * @author skillEvolver
 */
@Slf4j
public class TrialTraceHook extends AbstractAgentHook {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 所有追踪条目 */
    private final List<TraceEntry> trace = new CopyOnWriteArrayList<>();

    /** 当前正在执行的工具调用（preActing 设置，postActing 完成后清除） */
    private volatile TraceEntry pendingEntry;

    public TrialTraceHook() {
        super(2); // 高优先级，确保在其他 Hook 之前记录
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

            TraceEntry entry = new TraceEntry();
            entry.type = "tool_call";
            entry.toolName = toolUse.getName();
            entry.toolInput = toJson(toolUse.getInput());
            entry.timestamp = Instant.now().toString();
            entry.agentName = event.getAgent().getName();

            trace.add(entry);
            pendingEntry = entry;

            log.debug("[TrialTraceHook] 🔧 PreActing: {}({})", entry.toolName, truncate(entry.toolInput, 200));
        } catch (Exception e) {
            log.warn("[TrialTraceHook] handlePreActing 异常", e);
        }
    }

    /**
     * 工具执行后 — 记录工具结果，补充到对应的 pending 条目。
     * PostActingEvent 直接提供 getToolResult() → ToolResultBlock
     */
    @Override
    protected void handlePostActing(PostActingEvent event) {
        try {
            String result = null;

            // PostActingEvent 有直接的 getToolResult() 方法
            ToolResultBlock toolResult = event.getToolResult();
            if (toolResult != null) {
                result = extractText(toolResult);
            }

            TraceEntry entry = pendingEntry;
            if (entry != null && result != null) {
                entry.toolResult = truncate(result, 2000);
                log.debug("[TrialTraceHook] ✅ PostActing: {} -> {}", entry.toolName, truncate(result, 200));
            }
            pendingEntry = null;

        } catch (Exception e) {
            log.warn("[TrialTraceHook] handlePostActing 异常", e);
        }
    }

    /**
     * LLM 推理后 — 记录推理输出（含文本内容和 tool_use 决策）
     */
    @Override
    protected void handlePostReasoning(PostReasoningEvent event) {
        try {
            Msg reasoningMsg = event.getReasoningMessage();
            if (reasoningMsg == null) return;

            String textContent = reasoningMsg.getTextContent();
            if (textContent == null || textContent.isBlank()) return;

            TraceEntry entry = new TraceEntry();
            entry.type = "reasoning";
            entry.content = truncate(textContent, 2000);
            entry.timestamp = Instant.now().toString();
            entry.agentName = event.getAgent().getName();

            trace.add(entry);

            log.debug("[TrialTraceHook] 🧠 PostReasoning: {}", truncate(textContent, 200));
        } catch (Exception e) {
            log.warn("[TrialTraceHook] handlePostReasoning 异常", e);
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

            TraceEntry entry = new TraceEntry();
            entry.type = "final_reply";
            entry.content = truncate(textContent, 2000);
            entry.timestamp = Instant.now().toString();
            entry.agentName = event.getAgent().getName();

            trace.add(entry);

            log.debug("[TrialTraceHook] 📝 PostCall (final): {}", truncate(textContent, 200));
        } catch (Exception e) {
            log.warn("[TrialTraceHook] handlePostCall 异常", e);
        }
    }

    // ======================== 访问追踪数据 ========================

    /**
     * 获取所有追踪条目（不可变快照）
     */
    public List<TraceEntry> getTrace() {
        return List.copyOf(trace);
    }

    /**
     * 获取追踪条目总数
     */
    public int getTraceSize() {
        return trace.size();
    }

    /**
     * 将完整 trace 格式化为 Markdown（供 Analyzer 消费）
     */
    public String toMarkdown() {
        if (trace.isEmpty()) {
            return "_No trace recorded._";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Trial Execution Trace\n\n");

        int step = 0;
        for (TraceEntry entry : trace) {
            step++;
            sb.append("### Step ").append(step).append(": ").append(entry.type).append("\n");
            sb.append("- **Agent**: ").append(nullToEmpty(entry.agentName)).append("\n");
            sb.append("- **Time**: ").append(nullToEmpty(entry.timestamp)).append("\n");

            if ("tool_call".equals(entry.type)) {
                sb.append("- **Tool**: `").append(nullToEmpty(entry.toolName)).append("`\n");
                sb.append("- **Input**: ```").append(truncate(entry.toolInput, 500)).append("```\n");
                if (entry.toolResult != null) {
                    sb.append("- **Result**: ```").append(truncate(entry.toolResult, 500)).append("```\n");
                }
            } else {
                sb.append("- **Content**: ").append(truncate(entry.content, 500)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 清空追踪数据（每次 trial 开始前调用）
     */
    public void clear() {
        trace.clear();
        pendingEntry = null;
    }

    // ======================== 内部工具方法 ========================

    private static String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    private static String extractText(ToolResultBlock block) {
        try {
            var output = block.getOutput();
            if (output == null) {
                log.warn("[TrialTraceHook] extractText: getOutput() returned null");
                return block.toString();
            }
            log.info("[TrialTraceHook] extractText: outputSize={}, types={}",
                    output.size(),
                    output.stream().map(b -> b.getClass().getSimpleName()).toList());
            if (!output.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var contentBlock : output) {
                    if (contentBlock instanceof io.agentscope.core.message.TextBlock tb) {
                        sb.append(tb.getText());
                    } else {
                        log.warn("[TrialTraceHook] extractText: non-TextBlock type={}",
                                contentBlock.getClass().getName());
                        sb.append("[").append(contentBlock.getClass().getSimpleName()).append("]");
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }
            log.warn("[TrialTraceHook] extractText: output empty or no text found");
        } catch (Exception e) {
            log.warn("[TrialTraceHook] extractText via getOutput() failed", e);
        }
        return block.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ======================== 数据结构 ========================

    /**
     * 单条追踪记录
     */
    public static class TraceEntry {
        /** 类型: reasoning / tool_call / final_reply */
        public String type;
        /** agent 名称 */
        public String agentName;
        /** 时间戳 */
        public String timestamp;
        /** reasoning / final_reply 的文本内容 */
        public String content;
        /** tool_call 的工具名 */
        public String toolName;
        /** tool_call 的输入参数 (JSON) */
        public String toolInput;
        /** tool_call 的执行结果 */
        public String toolResult;
    }
}
