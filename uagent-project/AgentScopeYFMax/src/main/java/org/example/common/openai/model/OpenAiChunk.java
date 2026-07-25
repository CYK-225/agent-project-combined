package org.example.common.openai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion Chunk（流式响应单帧）
 * <p>
 * 严格遵循 OpenAI SSE Chunk 格式：
 * data: {"id":"...","object":"chat.completion.chunk","choices":[{"delta":{"content":"xxx"}}]}
 * </p>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChunk {

    /**
     * 唯一 ID，整个流中保持一致
     */
    private String id;

    /**
     * 固定为 "chat.completion.chunk"
     */
    private String object;

    /**
     * 创建时间（Unix 秒）
     */
    private Long created;

    /**
     * 模型名（即 Agent 名）
     */
    private String model;

    /**
     * 选项列表
     */
    private List<Choice> choices;

    // ==================== 内部类 ====================

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {
        private Integer index;
        private Delta delta;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        /**
         * 角色（通常只在第一个 chunk 中出现）
         */
        private String role;
        /**
         * 文本增量内容
         */
        private String content;
        /**
         * 推理/思考内容（扩展字段，LobeChat 支持）
         */
        @JsonProperty("reasoning_content")
        private String reasoningContent;
        /**
         * 工具调用（暂不实现，预留）
         */
        @JsonProperty("tool_calls")
        private List<OpenAiChatRequest.ToolCall> toolCalls;
    }

    // ==================== 工厂方法 ====================

    /**
     * 构建角色首帧（role: assistant）
     */
    public static OpenAiChunk roleChunk(String id, String model) {
        return OpenAiChunk.builder()
                .id(id)
                .object("chat.completion.chunk")
                .created(System.currentTimeMillis() / 1000)
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .delta(Delta.builder().role("assistant").build())
                                .finishReason(null)
                                .build()
                ))
                .build();
    }

    /**
     * 构建内容增量帧
     */
    public static OpenAiChunk contentChunk(String id, String model, String content) {
        return OpenAiChunk.builder()
                .id(id)
                .object("chat.completion.chunk")
                .created(System.currentTimeMillis() / 1000)
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .delta(Delta.builder().content(content).build())
                                .finishReason(null)
                                .build()
                ))
                .build();
    }

    /**
     * 构建推理/思考内容增量帧
     */
    public static OpenAiChunk reasoningChunk(String id, String model, String reasoning) {
        return OpenAiChunk.builder()
                .id(id)
                .object("chat.completion.chunk")
                .created(System.currentTimeMillis() / 1000)
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .delta(Delta.builder().reasoningContent(reasoning).build())
                                .finishReason(null)
                                .build()
                ))
                .build();
    }

    /**
     * 构建结束帧
     */
    public static OpenAiChunk stopChunk(String id, String model) {
        return OpenAiChunk.builder()
                .id(id)
                .object("chat.completion.chunk")
                .created(System.currentTimeMillis() / 1000)
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .delta(Delta.builder().build())
                                .finishReason("stop")
                                .build()
                ))
                .build();
    }
}
