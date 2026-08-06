package org.example.agentScope.util.hooksManager.hooks;

import io.agentscope.core.hook.ActingChunkEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 针对动作（工具）执行过程中产生的增量块的拦截钩子
 */

@Component
@Slf4j
public class MyActingChunkHook implements Hook {



    /**
     * 【触发时机】
     * 1. 触发于工具正在执行的过程中。
     * 2. 当某些支持流式返回结果的工具产生部分输出（Chunk）时，会立即触发该事件。
     * 3. 它位于 PreActingEvent（执行前）和 PostActingEvent（执行后）之间。
     *
     * @param event 包含当前增量结果块的事件对象
     * @return 返回 Mono 包装后的事件以维持响应式流
     */
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 1. 识别并过滤 ActingChunkEvent
        if (event instanceof ActingChunkEvent) {
            ActingChunkEvent chunkEvent = (ActingChunkEvent) event;

            // 2. 获取上下文与工具调用信息
            var agent = chunkEvent.getAgent();
            ToolUseBlock toolUse = chunkEvent.getToolUse();
            
            // 3. 获取当前的增量结果块
            ToolResultBlock chunk = chunkEvent.getChunk();

            // --- 业务逻辑封装 ---
            String toolName = toolUse.getName();
            String partialContent = chunk.getOutput().toString();


            log.debug(">>> [Hook 实时监控] 智能体: {} | 工具: {} | 增量内容: {}", agent.getName(), toolName, partialContent);
            // 示例：实时推送到 Vue 前端显示工具执行进度
            // pushToFrontend(agent.getId(), toolName, partialContent);
            // --- 逻辑结束 ---
        }

        // 4. 必须通过 Mono.just 返回
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 5;
    }
}