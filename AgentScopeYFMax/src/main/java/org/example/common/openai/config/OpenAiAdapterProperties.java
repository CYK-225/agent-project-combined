package org.example.common.openai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容适配层配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "openai.adapter")
public class OpenAiAdapterProperties {

    /**
     * 允许的 API Key 列表
     * LobeChat 等客户端必须携带其中之一才能调用
     */
    private List<String> apiKeys = new ArrayList<>();

    /**
     * SSE 连接超时（毫秒），默认 30 分钟
     */
    private long sseTimeout = 30 * 60 * 1000L;

    /**
     * 暴露给 OpenAI 接口的 Agent 分组名
     * 只有属于该分组的 Agent 才会出现在 /v1/models 中
     * 对应 @AgentDefinition(group = "xxx") 中的值
     */
    private String group = "openai";

    /**
     * 是否启用 /v1 接口
     */
    private boolean enabled = true;
}
