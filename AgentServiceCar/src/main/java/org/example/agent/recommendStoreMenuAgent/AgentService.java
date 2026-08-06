package org.example.agent.recommendStoreMenuAgent;


import com.alibaba.fastjson2.JSON;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.*;
import io.agentscope.core.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.recommendStoreMenuAgent.domain.resp.ChatEvent;
import org.example.agent.recommendStoreMenuAgent.hooks.LoggingHook;
import org.example.agent.recommendStoreMenuAgent.manager.MemoryManager;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AgentService {

    @Autowired
    private MemoryManager memoryManager;

    @Autowired
    @Qualifier("MenuRecommendRouterAgent")
    private ReActAgent routerAgent;

    @Autowired
    private AgentPoolManager agentPoolManager;


    public Flux<ChatEvent> chat(String message, String userId) {
        ToolExecutionContext toolExecutionContext = ToolExecutionContext.builder().register("userId", userId).build();

        Msg userMsg =
                Msg.builder()
                        .name("User")
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(message).build())
                        .build();
        return routerAgent.stream(userMsg)
                .last()
                .flatMapMany(event -> {
                    String skillName = event.getMessage().getTextContent().trim();
                    log.info("【悠饭AI】对话记录,用户【{}】,意图分析，选择skill：{}", userId, skillName);

                    ReActAgent agent = agentPoolManager.getAgentBuilder("RecommendMenuAgent", toolExecutionContext, List.of(new LoggingHook(Long.parseLong(userId))),skillName)
                            .memory(memoryManager.get(userId))
                            .build();
                    return agent
                            .stream(userMsg)
                            .flatMap(this::convertEventToChatEvents)
                            .concatWith(Flux.just(ChatEvent.complete()))
                            .doFinally(signal -> {
                            });
                })
                .onErrorResume(error ->
                        Flux.just(
                                ChatEvent.error(error.getMessage()),
                                ChatEvent.complete()
                        )
                );
    }


    private Flux<ChatEvent> convertEventToChatEvents(Event event) {
        List<ChatEvent> events = new ArrayList<>();
        Msg msg = event.getMessage();
        switch (event.getType()) {
            case REASONING -> {
                String text = extractText(msg);
                if (text != null && !text.isEmpty()) {
                    events.add(ChatEvent.text(text, !event.isLast()));
                }
            }
            case TOOL_RESULT -> {
                for (ToolResultBlock result : msg.getContentBlocks(ToolResultBlock.class)) {
                    events.add(
                            ChatEvent.toolResult(
                                    result.getId(), result.getName(), extractToolOutput(result)));
                }
            }
            case AGENT_RESULT -> {
                String text = msg.getTextContent();
                if (text != null && !text.isEmpty()) {
                    events.add(ChatEvent.text(text, false));
                }
            }
            default -> {
            }
        }
        return Flux.fromIterable(events);
    }

    private String extractText(Msg msg) {
        List<TextBlock> textBlocks = msg.getContentBlocks(TextBlock.class);
        if (textBlocks.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (TextBlock block : textBlocks) {
            sb.append(block.getText());
        }
        return sb.toString();
    }


    private String extractToolOutput(ToolResultBlock result) {
        List<ContentBlock> outputs = result.getOutput();
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : outputs) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }
}
