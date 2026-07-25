package org.example.agentScope.util.hooksManager.hooks;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 针对 Agent 调用结束后的拦截钩子 (适配 1.0.8 版本)
 */
@Slf4j
@Component
public class MyPostCallHook implements Hook {

    /**
     * 【触发时机】
     * 1. 触发于 agent.step() 或 agent.call() 执行逻辑全部结束之后。
     * 2. 此时 Agent 已经完成了所有的模型推理（Thought）、工具调用（Action）以及结果观察（Observation）。
     * 3. 它是结果返回给调用者（比如你的 Service 层或 Controller）之前的最后一站。
     * 4. 在这里，你可以通过 setFinalMessage() 方法对 AI 的最终回复进行最后的修正或格式化。
     */
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 1. 手动判断事件类型是否为 PostCallEvent
        if (event instanceof PostCallEvent) {
            PostCallEvent postCallEvent = (PostCallEvent) event;

            // 2. 获取 Agent 实例和模型生成的最终消息
            var agent = postCallEvent.getAgent();
            Msg finalMsg = postCallEvent.getFinalMessage();

            // --- 开发者封装逻辑 ---
            // 示例：记录 AI 的回复内容，用于审计或存储到数据库

            log.info("<<< [Hook 审计] Agent: {} 执行完毕，最终回复: {}", agent.getName(), finalMsg.getContent());

            log.info("<<< [最终回复] : {}", finalMsg.getContent());

            // 示例：如果回复中包含敏感词，可以强行修改结果
            // Msg safeMsg = Msg.builder().role("assistant").content("内容已被过滤").build();
            // postCallEvent.setFinalMessage(safeMsg);
            // --- 逻辑结束 ---
        }

        // 3. 必须通过 Mono.just 返回，确保响应式链路完整
        return Mono.just(event);
    }

    @Override
    public int priority() {
        // 建议优先级设低一点（数值大一点），确保它是最后一个处理结果的
        return 10;
    }
}