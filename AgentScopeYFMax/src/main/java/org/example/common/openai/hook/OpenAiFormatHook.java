package org.example.common.openai.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import lombok.extern.slf4j.Slf4j;
import org.example.common.openai.model.OpenAiChunk;
import org.example.common.stream.manager.SseEmitterManager;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 格式翻译 Hook
 * <p>
 * 拦截 AgentScope 的推理/工具事件，实时翻译为 OpenAI SSE Chunk 格式，
 * 通过 SseEmitterManager 推送给 LobeChat 等前端。
 * </p>
 *
 * <h3>数据流：</h3>
 * <pre>
 * Agent 执行 → ReasoningChunkEvent / ActingChunkEvent → 本 Hook 拦截
 *   → 格式化为 OpenAiChunk JSON → SSE data: {...}\n\n → LobeChat 渲染
 * </pre>
 *
 * <h3>关键设计：</h3>
 * <ul>
 *   <li>ThinkingBlock → reasoning_content 字段（LobeChat 深度思考展示）</li>
 *   <li>TextBlock → content 字段（正文打字机效果）</li>
 *   <li>ToolUseBlock → content 字段（折叠显示工具调用信息）</li>
 *   <li>ToolResultBlock → content 字段（折叠显示工具结果）</li>
 * </ul>
 */
@Slf4j
public class OpenAiFormatHook implements Hook {

    private final String taskId;
    private final String model;
    private final String chatId;
    private final ObjectMapper objectMapper;

    /**
     * 标记是否已发送角色首帧（role: assistant）
     * SSE 流必须在第一个 chunk 带上 role 字段
     */
    private final AtomicBoolean roleSent = new AtomicBoolean(false);

    /**
     * 标记流是否已结束，防止重复发送 [DONE]
     */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public OpenAiFormatHook(String taskId, String model, ObjectMapper objectMapper) {
        this.taskId = taskId;
        this.model = model;
        this.chatId = "chatcmpl-" + taskId.replace("-", "").substring(0, 24);
        this.objectMapper = objectMapper;
    }

    @Override
    public <E extends HookEvent> Mono<E> onEvent(E event) {
        try {
            // 确保首帧是 role: assistant
            ensureRoleSent();

            if (event instanceof ReasoningChunkEvent) {
                handleReasoning((ReasoningChunkEvent) event);
            } else if (event instanceof ActingChunkEvent) {
                handleActingChunk((ActingChunkEvent) event);
            } else if (event instanceof PostActingEvent) {
                handlePostActing((PostActingEvent) event);
            }
        } catch (Exception e) {
            log.error("[OpenAI Hook] 事件处理异常, taskId={}", taskId, e);
            errorFinish(e);
        }
        return Mono.just(event);
    }

    // ==================== 推理输出处理 ====================

    private void handleReasoning(ReasoningChunkEvent event) {
        Msg chunk = event.getIncrementalChunk();
        if (chunk == null || chunk.getContent() == null) return;

        for (ContentBlock block : chunk.getContent()) {
            if (block instanceof ThinkingBlock thinking) {
                // 思考内容 → reasoning_content
                String text = thinking.getThinking();
                if (text != null && !text.isEmpty()) {
                    sendChunk(OpenAiChunk.reasoningChunk(chatId, model, text));
                }
            } else if (block instanceof TextBlock textBlock) {
                // 正文内容 → content
                String text = textBlock.getText();
                if (text != null && !text.isEmpty()) {
                    sendChunk(OpenAiChunk.contentChunk(chatId, model, text));
                }
            } else if (block instanceof ToolUseBlock toolUse) {
                // 工具调用 → content（以折叠格式展示）
                String toolInfo = String.format("\n🔧 调用工具: %s\n", toolUse.getName());
                sendChunk(OpenAiChunk.contentChunk(chatId, model, toolInfo));
            } else if (block instanceof ToolResultBlock toolResult) {
                // 工具结果 → content（以折叠格式展示）
                String result = formatToolResult(toolResult);
                if (result != null && !result.isEmpty()) {
                    sendChunk(OpenAiChunk.contentChunk(chatId, model, result));
                }
            }
        }
    }

    // ==================== 工具执行过程处理 ====================

    private void handleActingChunk(ActingChunkEvent event) {
        String toolName = event.getToolUse().getName();
        String partialOutput = event.getChunk().getOutput().toString();

        // 工具执行中间进度 → content
        if (partialOutput != null && !partialOutput.isEmpty()) {
            String progress = String.format("⚙️ [%s] %s", toolName, partialOutput);
            sendChunk(OpenAiChunk.contentChunk(chatId, model, progress));
        }
    }

    private void handlePostActing(PostActingEvent event) {
        String toolName = event.getToolUse().getName();
        Object rawOutput = event.getToolResult().getOutput();

        if (rawOutput != null) {
            String outputStr = rawOutput.toString();
            // 截断过长的工具输出，避免 SSE 帧过大
            if (outputStr.length() > 500) {
                outputStr = outputStr.substring(0, 500) + "\n...(输出已截断)";
            }
            String result = String.format("\n✅ [%s] 执行完成\n```\n%s\n```\n", toolName, outputStr);
            sendChunk(OpenAiChunk.contentChunk(chatId, model, result));
        }
    }

    // ==================== 流控制 ====================

    /**
     * 确保角色首帧已发送
     */
    private void ensureRoleSent() {
        if (roleSent.compareAndSet(false, true)) {
            sendChunk(OpenAiChunk.roleChunk(chatId, model));
        }
    }

    /**
     * 正常结束流
     */
    public void finish() {
        if (finished.compareAndSet(false, true)) {
            try {
                // 发送 stop 帧
                sendChunk(OpenAiChunk.stopChunk(chatId, model));
                // 发送 [DONE] 结束标记
                SseEmitterManager.sendDone(taskId);
            } catch (Exception e) {
                log.warn("[OpenAI Hook] 结束流异常, taskId={}", taskId, e);
            } finally {
                SseEmitterManager.complete(taskId);
            }
        }
    }

    /**
     * 异常结束流
     */
    public void errorFinish(Throwable e) {
        if (finished.compareAndSet(false, true)) {
            try {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                String errorContent = String.format("\n\n❌ 执行异常: %s", errorMsg);
                sendChunk(OpenAiChunk.contentChunk(chatId, model, errorContent));
                sendChunk(OpenAiChunk.stopChunk(chatId, model));
                SseEmitterManager.sendDone(taskId);
            } catch (Exception ignored) {
            } finally {
                SseEmitterManager.completeWithError(taskId, e);
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 发送一个 OpenAI SSE Chunk
     */
    private void sendChunk(OpenAiChunk chunk) {
        try {
            String json = objectMapper.writeValueAsString(chunk);
            SseEmitterManager.sendEvent(taskId, json);
        } catch (JsonProcessingException e) {
            log.error("[OpenAI Hook] JSON 序列化失败", e);
        }
    }

    /**
     * 格式化工具结果
     */
    private String formatToolResult(ToolResultBlock toolResult) {
        if (toolResult.getOutput() == null || toolResult.getOutput().isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : toolResult.getOutput()) {
            if (block instanceof TextBlock textBlock) {
                String text = textBlock.getText();
                if (text != null && !text.isEmpty()) {
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }
}
