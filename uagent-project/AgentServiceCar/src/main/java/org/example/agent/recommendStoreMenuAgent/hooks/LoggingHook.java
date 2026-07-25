package org.example.agent.recommendStoreMenuAgent.hooks;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import io.agentscope.core.tool.ToolExecutionContext;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;


@Slf4j
@Setter
public class LoggingHook implements Hook {

    private Long userId;

    public LoggingHook(Long userId) {
        this.userId = userId;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        String logM = "【悠饭AI】对话记录,用户【" + this.userId + "】";
        if (event instanceof PreCallEvent preCallEvent) {
            List<Msg> inputMessages = preCallEvent.getInputMessages();
            for (Msg inputMessage : inputMessages) {
                String textContent = inputMessage.getTextContent();
                log.info(logM + "接收到用户提问，textContent={}", textContent);
            }
        }
        if (event instanceof PostCallEvent postCallEvent) {
            Msg finalMessage = postCallEvent.getFinalMessage();
            log.info(logM + "agent执行完成,text={}", finalMessage.getTextContent());
        }
        if (event instanceof PreActingEvent preActingEvent) {
            ToolUseBlock toolUse = preActingEvent.getToolUse();
            log.info(logM + "即将执行tool,name={},参数={}", toolUse.getName(), toolUse.getContent());
        }
        if (event instanceof PostActingEvent postActingEvent) {
            ToolResultBlock toolResult = postActingEvent.getToolResult();
            String name = toolResult.getName();
            List<ContentBlock> output = toolResult.getOutput();
            for (ContentBlock contentBlock : output) {
                TextBlock textBlock = (TextBlock) contentBlock;
                log.info(logM + "tool执行结果,name={},res={}", name, textBlock.getText());
            }
        }
        return Mono.just(event);
    }


}