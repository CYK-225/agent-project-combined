package org.example.repository.dal.controller.VO;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data

public class ChatMessage {

    private String id;
    private String name;
    private String role; // 这里的 role 变成了 "TOOL"
    private List<ContentItem> content;
    private MessageMetadata metadata;
    private String timestamp;



    // ==========================================
    // 内部类 1：ContentItem (核心大一统类)
    // ==========================================
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data

    public static class ContentItem {
        private String type;

        // --- 对话相关字段 ---
        private String thinking;
        private String text;
        private Object metadata;

        // --- 工具调用相关新增字段 ---
        private String id;       // 工具调用的唯一标识，例如 "call_213db967..."
        private String name;     // 工具的名称，例如 "getDiningReportStandardGuide"
        private List<ToolOutput> output; // 工具的返回值列表

    }

    // ==========================================
    // 内部类 2：ToolOutput (新增：专门解析工具的 output 数组)
    // ==========================================
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data

    public static class ToolOutput {
        private String type; // 例如 "text"
        private String text; // 工具返回的具体内容（Markdown 报告等）

    }

    // ==========================================
    // 内部类 3：外层 Metadata
    // ==========================================
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data

    public static class MessageMetadata {
        @JsonProperty("_chat_usage")
        private ChatUsage chatUsage;
    }

    // ==========================================
    // 内部类 4：ChatUsage 消耗统计
    // ==========================================
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data

    public static class ChatUsage {
        private Integer inputTokens;
        private Integer outputTokens;
        private Double time;
        private Integer totalTokens;
    }
}
