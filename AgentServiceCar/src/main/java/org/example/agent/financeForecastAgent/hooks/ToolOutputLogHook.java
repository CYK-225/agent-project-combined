package org.example.agent.financeForecastAgent.hooks;



import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * 针对 Agent 执行动作后的拦截钩子 (适配 1.0.8 版本)
 */
@Component
@Slf4j
public class ToolOutputLogHook implements Hook {

    /**
     * 【触发时机】
     * 工具执行完毕并返回结果后触发。
     */
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 1. 手动通过 instanceof 识别事件类型
        if (event instanceof PostActingEvent) {
            PostActingEvent postActingEvent = (PostActingEvent) event;

            // 2. 获取工具调用块信息
            var toolUse = postActingEvent.getToolUse();
            // 根据 image_cf1ca6.png，使用 getName() 获取函数名
            String funcName = toolUse.getName();
            // 如果需要获取参数，根据截图应使用 getInput()
            var inputArgs = toolUse.getInput();
            log.debug(">>> [Hook] 工具 {} 执行参数为: {}", funcName, inputArgs);

            // 3. 获取工具执行结果块信息
            var toolResult = postActingEvent.getToolResult();
            // 处理 image_cf1503.png 提到的 getContent 无法解析问题
            // 建议尝试调用 content()，如果仍报错请检查 IDE 补全列表
            // String resultText = toolResult.content();
            var outputStr = toolResult.getOutput().stream()
                    .map(b -> String.format("[内容=%s]", b))  // 提取具体字段，%s 自动调用 toString()
                    .collect(Collectors.joining(", "));

            log.debug(">>> [Hook] 工具 {} 执行结果为: {}", funcName, outputStr);
        }

        // 4. 必须返回 Mono.just(event) 以维持响应式链路
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 5;
    }
}
