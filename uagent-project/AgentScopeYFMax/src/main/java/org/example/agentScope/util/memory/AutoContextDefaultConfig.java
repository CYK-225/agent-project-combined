package org.example.agentScope.util.memory;

import io.agentscope.core.memory.autocontext.PromptConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class AutoContextDefaultConfig {

    @Value("${AutoContextConfig.msgThreshold}")
    private int msgThreshold;

    @Value("${AutoContextConfig.maxToken}")
    private int maxToken;

    @Value("${AutoContextConfig.tokenRatio}")
    private double tokenRatio;

    @Value("${AutoContextConfig.lastKeep}")
    private int lastKeep;

    @Value("${AutoContextConfig.largePayloadThreshold}")
    private int largePayloadThreshold;

    @Value("${AutoContextConfig.offloadSinglePreview}")
    private int offloadSinglePreview;

    @Value("${AutoContextConfig.minConsecutiveToolMessages}")
    private int minConsecutiveToolMessages;

    @Value("${AutoContextConfig.currentRoundCompressionRatio}")
    private double currentRoundCompressionRatio;

    @Value("${AutoContextConfig.customPrompt:#{null}}")
    private PromptConfig customPrompt;

}
