package org.example.agentScope.util.modelFactory;

import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DashScopeModelBuilder {

    @Value("${qwen.apiKey}")
     String qwen_api_key;

    public  Model buildDashScopeModel() {
        return DashScopeChatModel
                .builder()
                .apiKey("sk-e26ef7931f2a480b9d7ec6a2fb56527a")
                .modelName("qwen-plus")
                .stream(true)
                .enableThinking(false)
                .enableSearch(false)
                .defaultOptions(
                        GenerateOptions.builder()
                                .temperature(0.7)
                                .build()
                )

                .build();
    }
    public  Model buildDashScopeModel(String modelName, Double temperature) {
        if (modelName == null) modelName = "qwen-plus";
        if (temperature == null) temperature = 0.7;
        return DashScopeChatModel
                .builder()
                .apiKey(qwen_api_key)
                .modelName(modelName)
                .stream(true)
                .enableThinking(true)
                .enableSearch(false)
                .defaultOptions(
                        GenerateOptions.builder()
                                .temperature(temperature)
                                .build()
                )
                .build();
    }

    public  Model buildDashScopeModel(GenerateOptions options) {
        return DashScopeChatModel
                .builder()
                .apiKey(qwen_api_key)
                .modelName("qwen-plus")
                .stream(true)
                .enableThinking(true)
                .enableSearch(false)
                .defaultOptions(options)
                .build();
    }

    public  Model buildDashScopeModel(String OptionName) {
        return switch (OptionName) {
            case "思考" -> DashScopeChatModel
                    .builder()
                    .apiKey(qwen_api_key)
                    .modelName("qwen-plus")
                    .stream(true)
                    .enableThinking(false)
                    .enableSearch(false)
                    .defaultOptions(
                            GenerateOptions.builder()
                                    .temperature(0.5)
                                    .build()
                    )
                    .build();
            case "工具" -> DashScopeChatModel
                    .builder()
                    .apiKey(qwen_api_key)
                    .modelName("qwen-plus")
                    .stream(true)
                    .enableThinking(true)
                    .enableSearch(false)
                    .defaultOptions(
                            GenerateOptions.builder()
                                    .temperature(0.1)
                                    .build()
                    )
                    .build();
            case "聊天" -> DashScopeChatModel
                    .builder()
                    .apiKey(qwen_api_key)
                    .modelName("qwen-plus")
                    .stream(true)
                    .enableThinking(false)
                    .enableSearch(false)
                    .defaultOptions(
                            GenerateOptions.builder()
                                    .temperature(0.7)
                                    .build()
                    )
                    .build();
            case "视觉模型" ->DashScopeChatModel
                    .builder()
                    .apiKey(qwen_api_key)
                    .modelName("qwen3.5-plus")
                    .stream(true)
                    .formatter(new DashScopeChatFormatter())
                    .enableThinking(false)
                    .enableSearch(false)
                    .defaultOptions(
                            GenerateOptions.builder()
                                    .temperature(0.7)
                                    .build()
                    )
                    .build();
            default -> buildDashScopeModel();
        };
    }
}
