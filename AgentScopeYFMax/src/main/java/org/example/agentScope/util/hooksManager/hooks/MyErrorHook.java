package org.example.agentScope.util.hooksManager.hooks;

import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 针对 Agent 运行异常的拦截钩子 (适配 1.0.8 版本)
 */
@Component
@Slf4j
public class MyErrorHook implements Hook {

    /**
     * 【触发时机】
     * 1. 触发于 Agent 生命周期内的任何阶段发生异常时。
     * 2. 包括但不限于：模型通信失败、工具执行抛出异常、消息格式解析错误等。
     * 3. 它是 Agent 停止运行并抛出错误前的最后一次拦截机会。
     *
     * @param event 包含异常详情的事件对象
     * @return 返回 Mono 包装后的事件以维持响应式流
     */
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 1. 识别并过滤 ErrorEvent
        if (event instanceof ErrorEvent) {
            ErrorEvent errorEvent = (ErrorEvent) event;

            // 2. 获取异常上下文
            var agent = errorEvent.getAgent();
            // 获取具体的异常对象
            Throwable error = errorEvent.getError();

            // --- 开发者封装逻辑 ---
            log.error(">>> [Hook 告警] 智能体 [" + agent.getName() + "] 发生致命错误！", error);
             log.error(">>> 错误类型: " + error.getClass().getName());
            log.error(">>> 错误信息: " + error.getMessage());

            // 示例 A：发送告警通知 (如对接钉钉或邮件)
            // alertService.send(agent.getName(), error);

            // 示例 B：日志持久化
            // errorLogger.save(agent.getId(), error);
            // --- 逻辑结束 ---
        }

        // 3. 必须通过 Mono.just 返回
        return Mono.just(event);
    }

    @Override
    public int priority() {
        // 错误钩子通常建议优先级设高一点（数值小），确保能第一时间捕获
        return 0;
    }
}