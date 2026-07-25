package io.agentscope.core.agui.adapter;

/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agui.converter.AguiMessageConverter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import reactor.core.publisher.Flux;

/**
 * Adapter that bridges AgentScope agents to the AG-UI protocol.
 * Fixed to support Sub-agent event forwarding (Gap 1 & Gap 2) with Logging.
 */

public class AguiAgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(AguiAgentAdapter.class);

    private final Agent agent;
    private final AguiAdapterConfig config;
    private final AguiMessageConverter messageConverter;

    public AguiAgentAdapter(Agent agent, AguiAdapterConfig config) {
        this.agent = Objects.requireNonNull(agent, "agent cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.messageConverter = new AguiMessageConverter();
        log.info("🚀 [自定义 AguiAgentAdapter] 成功加载并实例化！覆盖原生类生效。");
    }

    public Flux<AguiEvent> run(RunAgentInput input) {
        String threadId = input.getThreadId();
        String runId = input.getRunId();

        log.debug("▶️ [自定义 AguiAgentAdapter] 开始执行 run()，ThreadId: {}, RunId: {}", threadId, runId);

        List<Msg> msgs = messageConverter.toMsgList(input.getMessages());

        // GAP 1 FIX: Include ActingChunk to receive forwarded events from SubAgentTool
        StreamOptions options = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(true)
                .includeActingChunk(true)
                .build();

        EventConversionState state = new EventConversionState(threadId, runId);

        return Flux.concat(
                        Flux.just(new AguiEvent.RunStarted(threadId, runId)),
                        agent.stream(msgs, options)
                                .concatMapIterable(event -> convertEvent(event, state)),
                        Flux.defer(() -> finishRun(state)))
                .onErrorResume(
                        error -> {
                            log.error("❌ [自定义 AguiAgentAdapter] 运行异常", error);
                            String errorMessage = error.getMessage() != null
                                    ? error.getMessage() : error.getClass().getSimpleName();
                            return Flux.just(
                                    new AguiEvent.Raw(threadId, runId, Map.of("error", errorMessage)),
                                    new AguiEvent.RunFinished(threadId, runId));
                        });
    }

    private List<AguiEvent> convertEvent(Event event, EventConversionState state) {
        List<AguiEvent> events = new ArrayList<>();
        Msg msg = event.getMessage();
        EventType type = event.getType();

        if (type == EventType.REASONING) {
            handleReasoningEvent(event, msg, state, events);
        } else if (type == EventType.TOOL_RESULT && event.isLast()) {
            handleToolResultEvent(msg, state, events);
        } else {
            // GAP 2 FIX: 兜底捕获所有的中间 Chunk。
            handleActingEvent(msg, state, events);
        }

        return events;
    }

    private void handleReasoningEvent(Event event, Msg msg, EventConversionState state, List<AguiEvent> events) {
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock textBlock) {
                String text = textBlock.getText();
                if (text != null && !text.isEmpty()) {
                    String messageId = msg.getId();
                    if (!state.hasStartedMessage(messageId)) {
                        events.add(new AguiEvent.TextMessageStart(state.threadId, state.runId, messageId, "assistant"));
                        state.startMessage(messageId);
                    }
                    if (!event.isLast()) {
                        events.add(new AguiEvent.TextMessageContent(state.threadId, state.runId, messageId, text));
                    } else if (!state.hasEndedMessage(messageId)) {
                        events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, messageId));
                        state.endMessage(messageId);
                    }
                }
            } else if (block instanceof ThinkingBlock thinkingBlock && config.isEnableReasoning()) {
                String thinking = thinkingBlock.getThinking();
                if (thinking != null && !thinking.isEmpty()) {
                    String messageId = msg.getId();
                    if (!state.hasStartedReasoningMessage(messageId)) {
                        events.add(new AguiEvent.ReasoningMessageStart(state.threadId, state.runId, messageId, "assistant"));
                        state.startReasoningMessage(messageId);
                    }
                    if (!event.isLast()) {
                        events.add(new AguiEvent.ReasoningMessageContent(state.threadId, state.runId, messageId, thinking));
                    } else {
                        events.add(new AguiEvent.ReasoningMessageEnd(state.threadId, state.runId, messageId));
                        state.endReasoningMessage(messageId);
                    }
                }
            } else if (block instanceof ToolUseBlock toolUse) {
                if (state.hasActiveTextMessage()) {
                    String activeId = state.getCurrentTextMessageId();
                    events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, activeId));
                    state.endMessage(activeId);
                }
                String toolCallId = toolUse.getId() != null ? toolUse.getId() : UUID.randomUUID().toString();
                if (!state.hasStartedToolCall(toolCallId)) {
                    events.add(new AguiEvent.ToolCallStart(state.threadId, state.runId, toolCallId, toolUse.getName()));
                    state.startToolCall(toolCallId);
                }
                if (config.isEmitToolCallArgs() && !event.isLast()) {
                    String args = toolUse.getContent();
                    if (args != null && !args.isEmpty()) {
                        events.add(new AguiEvent.ToolCallArgs(state.threadId, state.runId, toolCallId, args));
                    }
                }
            }
        }
    }

    /**
     * Handles forwarded events from Sub-agents.
     */
    private void handleActingEvent(Msg msg, EventConversionState state, List<AguiEvent> events) {
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolResultBlock toolResult) {
                Map<String, Object> metadata = toolResult.getMetadata();
                if (metadata != null && metadata.containsKey("subagent_event")) {
                    log.debug("📦 [自定义 AguiAgentAdapter] 发现包含 'subagent_event' 的 Metadata，准备转发子事件！");
                    Object subEventObj = metadata.get("subagent_event");
                    if (subEventObj instanceof Event subEvent) {
                        String subAgentName = (String) metadata.getOrDefault("subagent_name", "SubAgent");
                        log.debug("  -> 子代理名称: {}, 事件类型: {}", subAgentName, subEvent.getType());
                        events.addAll(convertSubEvent(subEvent, subAgentName, state));
                    } else {
                        log.warn("  -> 警告：'subagent_event' 的类型不是 io.agentscope.core.agent.Event，实际类型：{}", subEventObj.getClass().getName());
                    }
                }
            }
        }
    }

    /**
     * Specifically converts sub-agent events to be displayed under the current execution context.
     */
    private List<AguiEvent> convertSubEvent(Event subEvent, String agentName, EventConversionState state) {
        List<AguiEvent> events = new ArrayList<>();
        Msg subMsg = subEvent.getMessage();
        EventType type = subEvent.getType();

        // 1. If Sub-agent is thinking or speaking
        if (type == EventType.REASONING) {
            for (ContentBlock block : subMsg.getContent()) {
                if (block instanceof TextBlock textBlock) {
                    String text = textBlock.getText();
                    if (text != null && !text.isEmpty()) {
                        String subMsgId = subMsg.getId() + "_sub_" + agentName;

                        if (!state.hasStartedMessage(subMsgId)) {
                            log.debug("  -> [推流] 发送子代理 TextMessageStart: {}", subMsgId);
                            events.add(new AguiEvent.TextMessageStart(state.threadId, state.runId, subMsgId, "assistant"));
                            state.startMessage(subMsgId);
                            events.add(new AguiEvent.TextMessageContent(state.threadId, state.runId, subMsgId, "\n**[" + agentName + "]**: "));
                        }

                        if (!subEvent.isLast()) {
                            events.add(new AguiEvent.TextMessageContent(state.threadId, state.runId, subMsgId, text));
                        } else {
                            log.debug("  -> [推流] 发送子代理 TextMessageEnd: {}", subMsgId);
                            events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, subMsgId));
                            state.endMessage(subMsgId);
                        }
                    }
                } else if (block instanceof ThinkingBlock thinkingBlock && config.isEnableReasoning()) {
                    String thinking = thinkingBlock.getThinking();
                    String subReasoningId = subMsg.getId() + "_reasoning_" + agentName;

                    if (!state.hasStartedReasoningMessage(subReasoningId)) {
                        log.debug("  -> [推流] 发送子代理 ReasoningMessageStart: {}", subReasoningId);
                        events.add(new AguiEvent.ReasoningMessageStart(state.threadId, state.runId, subReasoningId, "assistant"));
                        state.startReasoningMessage(subReasoningId);
                    }

                    if (!subEvent.isLast()) {
                        events.add(new AguiEvent.ReasoningMessageContent(state.threadId, state.runId, subReasoningId, thinking));
                    } else {
                        log.debug("  -> [推流] 发送子代理 ReasoningMessageEnd: {}", subReasoningId);
                        events.add(new AguiEvent.ReasoningMessageEnd(state.threadId, state.runId, subReasoningId));
                        state.endReasoningMessage(subReasoningId);
                    }
                }
                else if (block instanceof ToolUseBlock subToolUse) {
                    String subToolCallId = subToolUse.getId() != null ? subToolUse.getId() : UUID.randomUUID().toString();
                    if (!state.hasStartedToolCall(subToolCallId)) {
                        events.add(new AguiEvent.ToolCallStart(state.threadId, state.runId, subToolCallId, agentName + "." + subToolUse.getName()));
                        state.startToolCall(subToolCallId);
                    }
                    if (config.isEmitToolCallArgs() && !subEvent.isLast()) {
                        events.add(new AguiEvent.ToolCallArgs(state.threadId, state.runId, subToolCallId, subToolUse.getContent()));
                    }
                }
            }
        }
        // 2. If Sub-agent finished a tool call
        else if (type == EventType.TOOL_RESULT && subEvent.isLast()) {
            handleToolResultEvent(subMsg, state, events);
        }

        return events;
    }

    private void handleToolResultEvent(Msg msg, EventConversionState state, List<AguiEvent> events) {
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolResultBlock toolResult) {
                String toolCallId = toolResult.getId();
                String result = extractToolResultText(toolResult);

                if (!state.hasStartedToolCall(toolCallId)) {
                    events.add(new AguiEvent.ToolCallStart(state.threadId, state.runId, toolCallId, "unknown"));
                    state.startToolCall(toolCallId);
                }

                events.add(new AguiEvent.ToolCallEnd(state.threadId, state.runId, toolCallId));
                events.add(new AguiEvent.ToolCallResult(state.threadId, state.runId, toolCallId, result, "tool", msg.getId()));
                state.endToolCall(toolCallId);
            }
        }
    }

    private Flux<AguiEvent> finishRun(EventConversionState state) {
        log.debug("🏁 [自定义 AguiAgentAdapter] 执行 finishRun，清理并结束流");
        List<AguiEvent> events = new ArrayList<>();
        for (String messageId : state.getStartedMessages()) {
            if (!state.hasEndedMessage(messageId)) {
                events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, messageId));
            }
        }
        for (String toolCallId : state.getStartedToolCalls()) {
            if (!state.hasEndedToolCall(toolCallId)) {
                events.add(new AguiEvent.ToolCallEnd(state.threadId, state.runId, toolCallId));
            }
        }
        for (String messageId : state.getStartedReasoningMessages()) {
            if (!state.hasEndedReasoningMessage(messageId)) {
                events.add(new AguiEvent.ReasoningMessageEnd(state.threadId, state.runId, messageId));
            }
        }
        events.add(new AguiEvent.RunFinished(state.threadId, state.runId));
        return Flux.fromIterable(events);
    }

    private String extractToolResultText(ToolResultBlock toolResult) {
        if (toolResult.getOutput() == null || toolResult.getOutput().isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ContentBlock output : toolResult.getOutput()) {
            if (output instanceof TextBlock textBlock) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(textBlock.getText());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static class EventConversionState {
        final String threadId;
        final String runId;
        private final Set<String> startedMessages = new LinkedHashSet<>();
        private final Set<String> endedMessages = new LinkedHashSet<>();
        private final Set<String> startedToolCalls = new LinkedHashSet<>();
        private final Set<String> endedToolCalls = new LinkedHashSet<>();
        private final Set<String> startedReasoningMessages = new LinkedHashSet<>();
        private final Set<String> endedReasoningMessages = new LinkedHashSet<>();
        private String currentTextMessageId = null;

        EventConversionState(String threadId, String runId) {
            this.threadId = threadId;
            this.runId = runId;
        }

        boolean hasStartedMessage(String messageId) { return startedMessages.contains(messageId); }
        void startMessage(String messageId) { startedMessages.add(messageId); currentTextMessageId = messageId; }
        void endMessage(String messageId) { endedMessages.add(messageId); if (messageId.equals(currentTextMessageId)) currentTextMessageId = null; }
        boolean hasEndedMessage(String messageId) { return endedMessages.contains(messageId); }
        String getCurrentTextMessageId() { return currentTextMessageId; }
        boolean hasActiveTextMessage() { return currentTextMessageId != null && !hasEndedMessage(currentTextMessageId); }
        Set<String> getStartedMessages() { return startedMessages; }
        boolean hasStartedToolCall(String toolCallId) { return startedToolCalls.contains(toolCallId); }
        void startToolCall(String toolCallId) { startedToolCalls.add(toolCallId); }
        void endToolCall(String toolCallId) { endedToolCalls.add(toolCallId); }
        boolean hasEndedToolCall(String toolCallId) { return endedToolCalls.contains(toolCallId); }
        Set<String> getStartedToolCalls() { return startedToolCalls; }
        boolean hasStartedReasoningMessage(String messageId) { return startedReasoningMessages.contains(messageId); }
        void startReasoningMessage(String messageId) { startedReasoningMessages.add(messageId); }
        void endReasoningMessage(String messageId) { endedReasoningMessages.add(messageId); }
        boolean hasEndedReasoningMessage(String messageId) { return endedReasoningMessages.contains(messageId); }
        Set<String> getStartedReasoningMessages() { return startedReasoningMessages; }
    }
}
