package org.example.agentScope.util.hooksManager.hooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.common.openai.hook.JsonRepairUtil;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 推理前 Hook — 修复对话历史中残留的非法 JSON tool_use 参数
 * <p>
 * DashScope API 校验 function.arguments 必须是合法 JSON。
 * 如果历史消息中的 ToolUseBlock.content（原始参数字符串）不是合法 JSON，
 * 会在下一次 API 请求时被 DashScope 拒绝（400 错误）。
 * <p>
 * 本 Hook 在每次推理前扫描输入消息，自动修复非法 JSON content。
 */
@Component
@Slf4j
public class MyPreReasoningHook implements Hook {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent preReasonEvent) {
            var agent = preReasonEvent.getAgent();
            List<Msg> inputs = preReasonEvent.getInputMessages();

            log.info(">>> [Hook 触发] 推理前准备 | 智能体: {}", agent.getName());

            // ===== 扫描历史消息，修复非法 JSON content =====
            boolean repaired = false;
            for (Msg msg : inputs) {
                if (msg.getContent() == null) continue;
                for (ContentBlock block : msg.getContent()) {
                    if (block instanceof ToolUseBlock toolUse) {
                        String content = toolUse.getContent();
                        if (content != null && !isValidJson(content)) {
                            String fixed = JsonRepairUtil.repair(content);
                            if (fixed != null) {
                                // 重建 ToolUseBlock，替换非法 content
                                ToolUseBlock repairedBlock = ToolUseBlock.builder()
                                        .id(toolUse.getId())
                                        .name(toolUse.getName())
                                        .input(toolUse.getInput())
                                        .content(fixed)
                                        .metadata(toolUse.getMetadata())
                                        .build();
                                replaceBlock(msg, toolUse, repairedBlock);
                                repaired = true;
                                log.info(">>> [Hook 修复] 智能体 [{}] 历史消息中工具 {} 的 content 已修复",
                                        agent.getName(), toolUse.getName());
                            } else {
                                // 无法修复，用 input Map 重新序列化
                                if (toolUse.getInput() != null) {
                                    try {
                                        String serialized = MAPPER.writeValueAsString(toolUse.getInput());
                                        ToolUseBlock repairedBlock = ToolUseBlock.builder()
                                                .id(toolUse.getId())
                                                .name(toolUse.getName())
                                                .input(toolUse.getInput())
                                                .content(serialized)
                                                .metadata(toolUse.getMetadata())
                                                .build();
                                        replaceBlock(msg, toolUse, repairedBlock);
                                        repaired = true;
                                        log.info(">>> [Hook 修复] 智能体 [{}] 工具 {} content 从 input Map 重新序列化",
                                                agent.getName(), toolUse.getName());
                                    } catch (Exception e) {
                                        log.error(">>> [Hook 修复] 工具 {} content 和 input 均无法修复",
                                                toolUse.getName(), e);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (repaired) {
                // 修复后需要更新输入消息（触发器需重新设置）
                preReasonEvent.setInputMessages(inputs);
            }
            // ===== 修复逻辑结束 =====
        }

        return Mono.just(event);
    }

    /**
     * 替换 Msg 中的 ContentBlock
     */
    private void replaceBlock(Msg msg, ToolUseBlock oldBlock, ToolUseBlock newBlock) {
        List<ContentBlock> content = msg.getContent();
        if (content == null) return;
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i) == oldBlock) {
                content.set(i, newBlock);
                return;
            }
        }
    }

    private boolean isValidJson(String str) {
        try {
            MAPPER.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int priority() {
        return 5;
    }
}
