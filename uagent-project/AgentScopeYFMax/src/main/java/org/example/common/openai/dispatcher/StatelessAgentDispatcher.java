package org.example.common.openai.dispatcher;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.common.openai.hook.OpenAiFormatHook;
import org.example.common.openai.model.OpenAiChatRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 无状态调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatelessAgentDispatcher {

    private final AgentPoolManager agentPoolManager;

    public String dispatch(OpenAiChatRequest request, OpenAiFormatHook hook) {

        String agentName = request.getModel();
        log.info("[Dispatcher] ========== 开始调度 ==========");
        log.info("[Dispatcher] 路由到 Agent: {}", agentName);

        // 1. 获取 Agent
        ReActAgent agent = agentPoolManager.getAgent(agentName, null, List.of(hook));

        // 2. 取原始消息
        List<OpenAiChatRequest.Message> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            messages = List.of(fallback("user", "Hi"));
        }
        log.info("[Dispatcher] 收到原始消息 {} 条", messages.size());
        for (int i = 0; i < messages.size(); i++) {
            OpenAiChatRequest.Message m = messages.get(i);
            log.info("[Dispatcher]   消息[{}] role={}, content={}", i, m.getRole(),
                    truncate(m.extractText(), 80));
        }

        // 3. 转换为 AgentScope Msg
        List<Msg> allMsgs = messages.stream()
                .map(this::convertMessage)
                .toList();
        log.info("[Dispatcher] 转换后 AgentScope Msg {} 条", allMsgs.size());

        // 4. 注入记忆前，先看记忆状态
        log.info("[Dispatcher] 注入前记忆消息数: {}", agent.getMemory().getMessages().size());

        // 5. 全量注入（除最后一条）
        if (allMsgs.size() > 1) {
            List<Msg> history = allMsgs.subList(0, allMsgs.size() - 1);
            for (Msg msg : history) {
                agent.getMemory().addMessage(msg);
            }
            log.info("[Dispatcher] 注入历史消息 {} 条到记忆", history.size());
        } else {
            log.info("[Dispatcher] 只有 1 条消息，跳过记忆注入");
        }

        // 6. 注入后看记忆状态
        log.info("[Dispatcher] 注入后记忆消息数: {}", agent.getMemory().getMessages().size());
        for (int i = 0; i < agent.getMemory().getMessages().size(); i++) {
            Msg m = agent.getMemory().getMessages().get(i);
            log.info("[Dispatcher]   记忆[{}] role={}, content={}", i,
                    m.getRole(), truncate(extractText(m), 80));
        }

        // 7. 用最后一条触发
        Msg lastMsg = allMsgs.getLast();
        log.info("[Dispatcher] call() 输入: role={}, content={}",
                lastMsg.getRole(), truncate(extractText(lastMsg), 80));

        Msg response = agent.call(lastMsg).block();

        // 8. 提取回复
        String replyText = extractReplyText(response);
        log.info("[Dispatcher] Agent 回复长度: {}", replyText.length());
        log.info("[Dispatcher] Agent 回复: {}", truncate(replyText, 200));
        log.info("[Dispatcher] ========== 调度结束 ==========");

        return replyText;
    }

    // ==================== 工具方法 ====================

    private Msg convertMessage(OpenAiChatRequest.Message msg) {
        MsgRole role = switch (msg.getRole()) {
            case "system" -> MsgRole.SYSTEM;
            case "assistant" -> MsgRole.ASSISTANT;
            case "tool" -> MsgRole.TOOL;
            default -> MsgRole.USER;
        };

        String text = msg.extractText();
        if (text == null) text = "";

        List<io.agentscope.core.message.ContentBlock> blocks = new ArrayList<>();

        if (msg.getReasoningContent() != null && !msg.getReasoningContent().isEmpty()) {
            blocks.add(io.agentscope.core.message.ThinkingBlock.builder()
                    .thinking(msg.getReasoningContent())
                    .build());
        }

        blocks.add(TextBlock.builder().text(text).build());

        return Msg.builder()
                .name(msg.getName() != null ? msg.getName() : msg.getRole())
                .role(role)
                .content(blocks)
                .build();
    }

    private OpenAiChatRequest.Message fallback(String role, String content) {
        OpenAiChatRequest.Message msg = new OpenAiChatRequest.Message();
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (var block : msg.getContent()) {
            if (block instanceof TextBlock t) sb.append(t.getText());
        }
        return sb.toString();
    }

    private String extractReplyText(Msg response) {
        return extractText(response);
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
