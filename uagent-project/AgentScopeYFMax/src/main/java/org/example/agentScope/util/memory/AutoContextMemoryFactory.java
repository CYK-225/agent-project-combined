package org.example.agentScope.util.memory;

import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.PromptConfig; // 假设这是那个PromptConfig的包路径
import io.agentscope.core.model.Model;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
//TODO @Resource注入
public class AutoContextMemoryFactory {

    @Resource
    private AutoContextDefaultConfig defaultConfig;


    /**
     * 获取一个构建器，初始值已填充为 DefaultConfig 中的配置
     */
    public MemoryBuilder builder(Model model) {
        return new MemoryBuilder(defaultConfig,model);
    }

    // ================== 内部 Builder 类 ==================
    public static class MemoryBuilder {
        // 这里的属性对应你要配置的参数
        private int msgThreshold;
        private int maxToken;
        private double tokenRatio;
        private int lastKeep;
        private int largePayloadThreshold;
        private int offloadSinglePreview; // 注意类型与你 Config 中保持一致
        private int minConsecutiveToolMessages;
        private double currentRoundCompressionRatio;
        private PromptConfig customPrompt;
        private Model model;


        public MemoryBuilder(AutoContextDefaultConfig defaults, Model model) {
            this.msgThreshold = defaults.getMsgThreshold();
            this.maxToken = defaults.getMaxToken();
            this.tokenRatio = defaults.getTokenRatio();
            this.lastKeep = defaults.getLastKeep();
            this.largePayloadThreshold = defaults.getLargePayloadThreshold();
            this.offloadSinglePreview = defaults.getOffloadSinglePreview();
            this.minConsecutiveToolMessages = defaults.getMinConsecutiveToolMessages();
            this.currentRoundCompressionRatio = defaults.getCurrentRoundCompressionRatio();
            this.customPrompt = defaults.getCustomPrompt();
            this.model = model;
        }

        // ================== 链式调用方法 (Setter 返回 this) ==================

        public MemoryBuilder msgThreshold(int msgThreshold) {
            this.msgThreshold = msgThreshold;
            return this;
        }

        public MemoryBuilder maxToken(int maxToken) {
            this.maxToken = maxToken;
            return this;
        }

        public MemoryBuilder tokenRatio(double tokenRatio) {
            this.tokenRatio = tokenRatio;
            return this;
        }

        public MemoryBuilder lastKeep(int lastKeep) {
            this.lastKeep = lastKeep;
            return this;

        }

        public MemoryBuilder largePayloadThreshold(int largePayloadThreshold) {
            this.largePayloadThreshold = largePayloadThreshold;
            return this;
        }

        public MemoryBuilder offloadSinglePreview(int offloadSinglePreview) {
            this.offloadSinglePreview = offloadSinglePreview;
            return this;
        }

        public MemoryBuilder minConsecutiveToolMessages(int minConsecutiveToolMessages) {
            this.minConsecutiveToolMessages = minConsecutiveToolMessages;
            return this;
        }

        public MemoryBuilder currentRoundCompressionRatio(double currentRoundCompressionRatio) {
            this.currentRoundCompressionRatio = currentRoundCompressionRatio;
            return this;
        }

        public MemoryBuilder customPrompt(PromptConfig customPrompt) {
            this.customPrompt = customPrompt;
            return this;
        }



        // ================== 最终构建方法 ==================

        public AutoContextMemory build() {
            // 1. 使用当前 Builder 的属性（可能是默认的，也可能是修改过的）构建第三方库的 Config
            AutoContextConfig config = AutoContextConfig.builder()
                    .msgThreshold(this.msgThreshold)
                    .maxToken(this.maxToken)
                    .tokenRatio(this.tokenRatio)
                    .lastKeep(this.lastKeep)
                    .largePayloadThreshold(this.largePayloadThreshold)
                    // 注意：如果第三方库需要 boolean，这里可能需要转换，如 (this.offloadSinglePreview == 1)
                    .offloadSinglePreview(this.offloadSinglePreview)
                    .minConsecutiveToolMessages(this.minConsecutiveToolMessages)
                    .currentRoundCompressionRatio(this.currentRoundCompressionRatio)
                    .customPrompt(this.customPrompt)
                    .build();

            // 2. 返回最终的 Memory 对象
            return new AutoContextMemory(config,model );
        }
    }
}