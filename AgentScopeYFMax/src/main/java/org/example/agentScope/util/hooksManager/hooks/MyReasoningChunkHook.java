package org.example.agentScope.util.hooksManager.hooks;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 推理过程拦截钩子 — 只记录阶段级事件，不输出流式内容。
 */
@Component
@Slf4j
public class MyReasoningChunkHook implements Hook {

    /** 上一次工具调用名称，用于去重（流式会多次触发同一个 ToolUseBlock） */
    private static final ThreadLocal<String> LAST_TOOL_NAME = ThreadLocal.withInitial(() -> "");

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ReasoningChunkEvent chunkEvent) {
            ContentBlock block = chunkEvent.getIncrementalChunk().getContent().getLast();

            if (block instanceof ToolUseBlock toolUse) {
                String name = toolUse.getName();
                // 只在工具名变化时记录（去重流式重复触发）
                if (!name.equals(LAST_TOOL_NAME.get())) {
                    LAST_TOOL_NAME.set(name);
                    log.info("[阶段] 调用工具: {}", name);
                }
            }
            else if (block instanceof ToolResultBlock) {
                // 不输出工具返回内容
            }
            // ThinkingBlock / TextBlock — 不输出
        }
        return Mono.just(event);
    }
}
