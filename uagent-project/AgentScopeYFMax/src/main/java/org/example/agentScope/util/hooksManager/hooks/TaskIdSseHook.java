package org.example.agentScope.util.hooksManager.hooks;

import com.alibaba.fastjson2.JSON;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.example.common.stream.Status;
import org.example.common.stream.manager.SseEmitterManager;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 适配现有 SseEmitterManager 的 Hook
 * 支持泛型 T，用于解析工具返回的结果
 * @param <T> 工具结果期望转换的目标类型
 */
@Slf4j
public class TaskIdSseHook<T> implements Hook {

    // 使用 Getter 以便外部获取结果
    @Getter
    private List<T> toolResults;

    private final String taskId;
    // 运行时需要具体的 Class 信息来做 JSON 反序列化
    private final Class<T> targetType;

    // 记录当前的状态
    private final AtomicReference<Status> currentStatus = new AtomicReference<>(null);

    /**
     * 构造函数：指定泛型类型
     * @param taskId 任务ID
     * @param targetType 目标类型的 Class 对象 (例如: User.class)
     */
    public TaskIdSseHook(String taskId, Class<T> targetType) {
        this.taskId = taskId;
        this.targetType = targetType;
    }

    /**
     * 构造函数：不指定类型（默认为 Object）
     * 这种情况下解析结果可能是 JSONObject 或 Map
     */
    public TaskIdSseHook(String taskId) {
        this.taskId = taskId;
        this.targetType = null;
    }

    @Override
    public <E extends HookEvent> Mono<E> onEvent(E event) {
        try {
            if (event instanceof ReasoningChunkEvent) {
                handleReasoning((ReasoningChunkEvent) event);
            } else if (event instanceof ActingChunkEvent) {
                handleActingProgress((ActingChunkEvent) event);
            } else if (event instanceof PostActingEvent) {
                handleActingResult((PostActingEvent) event);
            }
        } catch (Exception e) {
            log.error("SSE推送异常", e);
        }
        return Mono.just(event);
    }

    private void handleReasoning(ReasoningChunkEvent event) {
        Msg chunk = event.getIncrementalChunk();
        if (chunk.getContent() == null) return;

        for (ContentBlock block : chunk.getContent()) {
            if (block instanceof ThinkingBlock) {
                switchStatus(Status.THINK, "正在深度思考...");
                sendDirectly(((ThinkingBlock) block).getThinking());
            } else if (block instanceof TextBlock) {
                switchStatus(Status.DIALOGUE, "正在回复...");
                sendDirectly(((TextBlock) block).getText());
            }
        }
    }

    private void handleActingProgress(ActingChunkEvent event) {
        String toolName = event.getToolUse().getName();
        switchStatus(Status.TOOL, "正在执行工具: " + toolName);
        String chunkOutput = event.getChunk().getOutput().toString();
         log.debug("工具执行中间结果: {}", event.getChunk().getOutput());
         // 可选日志
        sendDirectly(chunkOutput);
    }

    private void handleActingResult(PostActingEvent event) {
        String toolName = event.getToolUse().getName();
        // 注意：Java 21 新特性 STR 模板，如果版本较低请改回 String.format
        switchStatus(Status.TOOL, STR."工具执行完成: \{toolName}");

        // 获取原始工具结果
        Object rawOutput = event.getToolResult().getOutput();

        log.info("工具调用结果(原始): {}", rawOutput);

        // --- 核心修改：泛型解析 ---
        if (rawOutput != null && targetType != null) {
            try {
                // 将结果解析为指定泛型的 List
                this.toolResults = JSON.parseArray(rawOutput.toString(), targetType);
                log.info(STR."解析后的结果: \{this.toolResults}");
            } catch (Exception e) {
                log.error("工具结果反序列化失败", e);
            }
        }

        if (rawOutput != null) {
            // 将结果推送到前端
            sendDirectly("\n[Execution Result]:\n" + rawOutput + "\n");
        }
    }

    private void switchStatus(Status newStatus, String detail) {
        Status oldStatus = currentStatus.get();
        if (oldStatus == newStatus) return;

        if (oldStatus != null) {
            SseEmitterManager.sendMessageToClient(taskId, oldStatus.getEndTag(""));
        }
        currentStatus.set(newStatus);
        if (newStatus != null) {
            SseEmitterManager.sendMessageToClient(taskId, newStatus.getStartTag(detail));
        }
    }

    private void sendDirectly(String content) {
        if (content != null && !content.isEmpty()) {
            SseEmitterManager.sendMessageToClient(taskId, content);
        }
    }

    public void finish() {
        Status lastStatus = currentStatus.get();
        if (lastStatus != null) {
            SseEmitterManager.sendMessageToClient(taskId, lastStatus.getEndTag("完成"));
        }
        SseEmitterManager.sendMessageToClient(taskId, "<彻底结束>:END");
        SseEmitterManager.complete(taskId);
    }

    public void error(Throwable e) {
        SseEmitterManager.sendMessageToClient(taskId, STR."<对话异常>:\{e.getMessage()}");
        SseEmitterManager.completeWithError(taskId, e);
    }
}