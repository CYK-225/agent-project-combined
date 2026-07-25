package org.example.agentScope.util.hooksManager.hooks;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 针对模型推理后的拦截钩子 — 增加 JSON 格式校验 + 自动重试
 * <p>
 * 当模型返回的 ToolUseBlock 参数不是合法 JSON 时，
 * 通过 gotoReasoning 提示模型修正格式后重试。
 */
@Slf4j
@Component
public class MyPostReasoningHook implements Hook {

    /** 同一 Agent 最大重试次数，防止死循环 */
    private static final int MAX_RETRY = 2;

    /**
     * 【触发时机】
     * 当大模型（LLM）完成一轮生成并返回消息后立即触发。
     * 此时 Agent 已经拿到了模型的回复内容，但尚未解析工具参数或执行工具。
     */
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent postReasonEvent) {
            Msg reasoningMsg = postReasonEvent.getReasoningMessage();
            var agent = postReasonEvent.getAgent();

            log.info(">>> [Hook 触发] 推理后拦截 | 智能体: {}", agent.getName());

            // ===== 检查 ToolUseBlock 参数是否为合法 JSON =====
            if (reasoningMsg != null && reasoningMsg.getContent() != null) {
                for (var block : reasoningMsg.getContent()) {
                    if (block instanceof ToolUseBlock toolUse) {
                        var input = toolUse.getInput();
                        if (input != null && hasInvalidJsonValues(input)) {
                            // 检查重试次数
                            int retryCount = getRetryCount(postReasonEvent);
                            if (retryCount >= MAX_RETRY) {
                                log.warn(">>> [Hook] Agent [{}] JSON 重试已达上限 {}，放行原始输出",
                                        agent.getName(), MAX_RETRY);
                                break;
                            }

                            log.warn(">>> [Hook] Agent [{}] 工具 {} 参数含非法 JSON，触发重试 ({}/{})",
                                    agent.getName(), toolUse.getName(), retryCount + 1, MAX_RETRY);

                            Msg hintMsg = Msg.builder()
                                    .role(MsgRole.USER)
                                    .textContent("""
                                            【格式修正提示】你上一轮返回的工具调用参数不是合法 JSON 格式。
                                            请严格遵守以下规则重新生成工具调用：
                                            1. 参数必须是合法的 JSON 对象，使用双引号包裹 key 和 string value
                                            2. 不要在 JSON 外面包裹 markdown 代码块（不要用 ```json）
                                            3. 确保所有括号正确闭合
                                            4. 不要出现尾逗号
                                            """)
                                    .build();

                            postReasonEvent.gotoReasoning(hintMsg);
                            return Mono.just(event);
                        }
                    }
                }
            }
            // ===== 校验逻辑结束 =====
        }

        return Mono.just(event);
    }

    /**
     * 检查 input Map 中是否有值看起来像 JSON 但格式非法
     */
    private boolean hasInvalidJsonValues(java.util.Map<String, Object> input) {
        for (Object value : input.values()) {
            if (value instanceof String str) {
                String trimmed = str.stripLeading();
                if ((trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("```"))
                        && !isValidJson(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isValidJson(String str) {
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 metadata 中获取重试计数
     */
    private int getRetryCount(PostReasoningEvent event) {
        try {
            var agent = event.getAgent();
            // 通过 Agent 的 metadata 或 thread-local 记录重试次数
            // 简单实现：从 reasoningMessage 的 metadata 获取
            var msg = event.getReasoningMessage();
            if (msg != null && msg.getMetadata() != null) {
                Object count = msg.getMetadata().get("_jsonRetryCount");
                if (count instanceof Number n) return n.intValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @Override
    public int priority() {
        return 5;
    }
}
