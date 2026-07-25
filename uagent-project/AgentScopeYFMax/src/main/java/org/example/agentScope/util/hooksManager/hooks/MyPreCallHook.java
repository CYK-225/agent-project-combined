package org.example.agentScope.util.hooksManager.hooks;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 针对 Agent 调用前的拦截钩子 (适配 1.0.8 版本)
 */

@Component
@Slf4j
public class MyPreCallHook implements Hook {

    /**
     * 【触发时机】
     * 1. 触发于 agent.step() 或 agent.call() 被执行的最开始阶段。
     * 2. 此时输入消息（List<Msg>）刚进入 Agent 实例，但 Agent 尚未进行任何 Prompt 构建或模型推理。
     * 3. 它是修改输入内容、记录请求日志或进行输入合法性校验的唯一黄金时机。
     */
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 1. 手动判断事件类型是否为 PreCallEvent
        if (event instanceof PreCallEvent) {
            PreCallEvent preCallEvent = (PreCallEvent) event;


            // 2. 获取 Agent 实例和输入消息
            var agent = preCallEvent.getAgent();
            List<Msg> inputs = preCallEvent.getInputMessages();

            // --- 开发者封装逻辑 ---
            log.info(">>> [Hook 审计] Agent: {} | 输入消息数: {}", agent.getName(), inputs.size());

            // 如果需要修改消息，在此处调用 preCallEvent.setInputMessages(...)
            // --- 逻辑结束 ---
        }

        // 3. 必须返回 Mono.just(event)，确保异步流水线继续执行
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 5;
    }
}