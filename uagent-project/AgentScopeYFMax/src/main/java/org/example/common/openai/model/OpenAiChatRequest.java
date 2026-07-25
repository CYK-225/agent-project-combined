package org.example.common.openai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion 请求体
 * <p>
 * 完整兼容 OpenAI /v1/chat/completions 请求格式
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatRequest {

    /**
     * 模型名称 —— 在本系统中作为 Agent 路由键
     * LobeChat 选择的"模型"实际就是你的 Agent 名
     */
    private String model;

    /**
     * 消息列表（对话历史）
     */
    private List<Message> messages;

    /**
     * 是否流式输出
     */
    private Boolean stream = true;

    /**
     * 温度
     */
    private Double temperature;

    /**
     * top_p
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 最大生成 token 数
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 停止序列
     */
    private List<String> stop;

    // ==================== 内部类 ====================

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {

        /**
         * 角色：system / user / assistant / tool
         */
        private String role;

        /**
         * 消息内容。
         * OpenAI 规范中 content 可以是 String（纯文本）
         * 或 Array（多模态，如 text + image_url）。
         * 用 Object 统一接收，按需转换。
         */
        private Object content;

        /**
         * 工具调用（assistant 消息可能携带）
         */
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;

        /**
         * 推理/思考内容（部分前端支持，如 LobeChat 的 reasoning_content）
         */
        @JsonProperty("reasoning_content")
        private String reasoningContent;

        /**
         * 工具调用 ID（tool 角色消息）
         */
        @JsonProperty("tool_call_id")
        private String toolCallId;

        /**
         * 名字
         */
        private String name;

        // ==================== content 便捷方法 ====================

        /**
         * 提取纯文本内容。
         * - content 为 String → 直接返回
         * - content 为 Array → 拼接所有 type=text 的 text 字段
         * - 其他 → 返回 toString()
         */
        @JsonIgnore
        @SuppressWarnings("unchecked")
        public String extractText() {
            if (content == null) return null;
            if (content instanceof String s) return s;
            if (content instanceof List<?> parts) {
                StringBuilder sb = new StringBuilder();
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> m) {
                        if ("text".equals(m.get("type"))) {
                            Object text = m.get("text");
                            if (text != null) {
                                if (!sb.isEmpty()) sb.append("\n");
                                sb.append(text);
                            }
                        }
                        // type=image_url 等多媒体内容暂忽略
                    }
                }
                return sb.toString();
            }
            return content.toString();
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {
        private Integer index;
        private String id;
        private String type;
        private Function function;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        private String name;
        private String arguments;
    }

    // ==================== 便捷方法 ====================

    /**
     * 快速判断是否为流式请求
     */
    @JsonIgnore
    public boolean isStream() {
        return Boolean.TRUE.equals(stream);
    }
}
