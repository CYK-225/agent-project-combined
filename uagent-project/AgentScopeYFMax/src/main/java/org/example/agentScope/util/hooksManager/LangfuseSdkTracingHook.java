package org.example.agentScope.util.hooksManager;

import io.agentscope.core.hook.*;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// Langfuse SDK Imports
import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.ingestion.requests.IngestionRequest;
import com.langfuse.client.resources.ingestion.types.*;
import com.langfuse.client.resources.commons.types.ObservationLevel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LangfuseSdkTracingHook implements Hook {

    private final LangfuseClient langfuseClient;

    // 用于存储上下文 ID：AgentName -> TraceID / GenerationID
    // 在生产环境中，建议使用 SessionID 或 RequestID 来隔离并发请求
    private final Map<String, String> traceIdMap = new ConcurrentHashMap<>();
    private final Map<String, String> generationIdMap = new ConcurrentHashMap<>();

    public LangfuseSdkTracingHook(LangfuseClient langfuseClient) {
        this.langfuseClient = langfuseClient;
    }
    @Override
    public int priority() {
        return 0;
    }


    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        return Mono.fromCallable(() -> {
            processEvent(event);
            return event;
        }).subscribeOn(Schedulers.boundedElastic()); // 在后台线程执行网络请求，避免阻塞 Agent
    }

    private void processEvent(HookEvent event) {
        try {
            if (event instanceof PreCallEvent e) {
                handlePreCall(e);
            } else if (event instanceof PostCallEvent e) {
                handlePostCall(e);
            } else if (event instanceof PreReasoningEvent e) {
                handlePreReasoning(e);
            } else if (event instanceof PostReasoningEvent e) {
                handlePostReasoning(e);
            } else if (event instanceof ErrorEvent e) {
                handleError(e);
            }
        } catch (Exception ex) {
            System.err.println("Langfuse Tracing Error: " + ex.getMessage());
        }
    }

    // --- 事件处理逻辑 ---

    private void handlePreCall(PreCallEvent e) {
        String agentName = e.getAgent().getName();
        String traceId = UUID.randomUUID().toString();

        /**
         * 添加读取 python 传过来的 ID
         */
        if (e.getInputMessages() != null && !e.getInputMessages().isEmpty()) {
            String msgId = e.getInputMessages().get(0).getId();
            if (msgId != null && !msgId.isEmpty()) {
                traceId = msgId;
            }
        }
        traceIdMap.put(agentName, traceId);

        // 构建 TraceBody
        TraceBody body = TraceBody.builder()
                .id(traceId)
                .name(agentName)
                .timestamp(OffsetDateTime.now())
                .input(formatMessages(e.getInputMessages()))
                .userId("user-default") // 可根据实际上下文动态设置
                .build();

        // 构建 TraceEvent (Envelope)
        TraceEvent traceEvent = TraceEvent.builder()
                .id(UUID.randomUUID().toString()) // 事件本身的唯一 ID
                .timestamp(OffsetDateTime.now().toString())
                .body(body)
                .build();

        // 发送事件
        sendEvent(IngestionEvent.traceCreate(traceEvent));
    }

    private void handlePostCall(PostCallEvent e) {
        String agentName = e.getAgent().getName();
        String traceId = traceIdMap.remove(agentName); // 结束 Trace 上下文

        if (traceId != null) {
            // Langfuse 支持通过发送相同的 TraceID 来更新 Trace（例如添加 Output）
            // 注意：IngestionClient 说明中提到 "if you want to update a trace, you'd use the same body id"
            TraceBody body = TraceBody.builder()
                    .id(traceId)
                    .output(e.getFinalMessage().getContent())
                    .build();

            TraceEvent traceEvent = TraceEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(OffsetDateTime.now().toString())
                    .body(body)
                    .build();

            sendEvent(IngestionEvent.traceCreate(traceEvent));
        }
    }

    private void handlePreReasoning(PreReasoningEvent e) {
        String agentName = e.getAgent().getName();
        String traceId = traceIdMap.get(agentName); // 获取父级 Trace ID
        String generationId = UUID.randomUUID().toString();
        generationIdMap.put(agentName, generationId);

        // 构建 CreateGenerationBody
        CreateGenerationBody body = CreateGenerationBody.builder()
                .id(generationId)
                .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
                .name("LLM Generation")
                .startTime(OffsetDateTime.now())
                .model(e.getAgent().getName()) // 假设 Agent 有 getModel 方法，或者硬编码
                .input(formatMessages(e.getInputMessages()))
                .build();

        // 构建 CreateGenerationEvent
        CreateGenerationEvent genEvent = CreateGenerationEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(OffsetDateTime.now().toString())
                .body(body)
                .build();

        sendEvent(IngestionEvent.generationCreate(genEvent));
    }

    private void handlePostReasoning(PostReasoningEvent e) {
        String agentName = e.getAgent().getName();
        String generationId = generationIdMap.remove(agentName);

        if (generationId != null) {
            // 构建 UpdateGenerationBody
            UpdateGenerationBody body = UpdateGenerationBody.builder()
                    .id(generationId)
                    .endTime(OffsetDateTime.now())
                    .output(formatMessages( List.of(e.getReasoningMessage())))
                    // 如果有 Token 统计，可以在这里构建 IngestionUsage 对象并设置
                    // .usage(...)
                    .build();

            // 构建 UpdateGenerationEvent
            UpdateGenerationEvent updateEvent = UpdateGenerationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(OffsetDateTime.now().toString())
                    .body(body)
                    .build();

            sendEvent(IngestionEvent.generationUpdate(updateEvent));
        }
    }

    private void handleError(ErrorEvent e) {
        // 当发生错误时，尝试更新当前的 Generation 状态
        String agentName = e.getAgent().getName();
        String generationId = generationIdMap.get(agentName);

        if (generationId != null) {
            UpdateGenerationBody body = UpdateGenerationBody.builder()
                    .id(generationId)
                    .endTime(OffsetDateTime.now())
                    .level(ObservationLevel.ERROR)
                    .statusMessage(e.getError().getMessage())
                    .build();

            UpdateGenerationEvent updateEvent = UpdateGenerationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(OffsetDateTime.now().toString())
                    .body(body)
                    .build();

            sendEvent(IngestionEvent.generationUpdate(updateEvent));
        }
    }

    // --- 辅助方法 ---

    private void sendEvent(IngestionEvent event) {
        // 使用 batch 接口发送单个事件
        // 在高吞吐场景下，建议实现一个内部缓冲区，累积一定数量的事件后再调用 batch
        IngestionRequest request = IngestionRequest.builder()
                .addBatch(event)
                .build();

        // 直接调用 ingestion().batch()
        langfuseClient.ingestion().batch(request);
    }

//    private String formatMessages(List<Msg> messages) {
//        if (messages == null) return "";
//        return messages.stream()
//                .map(msg -> msg.getRole() + ": " + msg.getContent())
//                .collect(Collectors.joining("\n"));
//    }
    private String formatMessages(List<Msg> messages) {
        if (messages == null) return "";
        return messages.stream()
                .map(msg -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(msg.getRole()).append(":\n");

                    if (msg.getContent() != null) {
                        // 遍历消息中的所有的多模态 Block (文本块、工具调用块等)
                        for (io.agentscope.core.message.ContentBlock block : msg.getContent()) {
                            if (block instanceof io.agentscope.core.message.TextBlock) {
                                // 如果是纯文本，直接取文本
                                sb.append(((io.agentscope.core.message.TextBlock) block).getText()).append("\n");
                            } else {
                                // 💡【核心修复】如果是 ToolUseBlock，利用你项目里的 Fastjson 将其转化为明文 JSON
                                try {
                                    sb.append(com.alibaba.fastjson2.JSON.toJSONString(block)).append("\n");
                                } catch (Exception ex) {
                                    // 兜底防御
                                    sb.append(block.toString()).append("\n");
                                }
                            }
                        }
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n------------------\n"));
    }
}