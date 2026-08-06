package org.example.agentScope.util.modelFactory;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.ollama.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.springframework.util.ObjectUtils.isEmpty;

@Component
//TODO 直接静态方法使用
public class OllamaModelBuilder {

    @Value("${ollama.base-url}")
     String ollama_base_url;

    @Value("${ollama.modelName}")
     String ollama_modelName;

    public  Model buildOllamaModel() {
        OllamaOptions options = OllamaOptions.builder()
                .numCtx(8192)
                .temperature(0.8)
                .topK(40)
                .topP(0.9)
                .repeatPenalty(1.1)
                .build();

        return OllamaChatModel
                .builder()
                .defaultOptions(options)
                .baseUrl(ollama_base_url)
                .modelName(ollama_modelName)
                .build();
    }

    public  Model buildOllamaModel(String modelName, Double temperature) {

        if (modelName == null) modelName = ollama_modelName;
        if (temperature == null) temperature = 0.7;

        OllamaOptions options = OllamaOptions.builder()
                .numCtx(8192)
                .temperature(temperature)
                .topK(40)
                .topP(0.9)
                .repeatPenalty(1.1)
                .build();
        return OllamaChatModel
                .builder()
                .defaultOptions(options)
                .baseUrl(ollama_base_url)
                .modelName(modelName)
                .build();
    }

    public  Model buildOllamaModel(OllamaOptions  options) {
        return OllamaChatModel
                .builder()
                .defaultOptions(options)
                .baseUrl(ollama_base_url)
                .modelName(ollama_modelName)
                .build();
    }

    public  Model buildOllamaModel(String OptionName) {

        return switch (OptionName) {
            case "思考" -> {
                OllamaOptions options = OllamaOptions.builder()
                        .numCtx(8192)
                        .temperature(0.5)
                        .topK(40)
                        .topP(0.9)
                        .repeatPenalty(1.1)
                        .build();
                yield OllamaChatModel
                        .builder()
                        .defaultOptions(options)
                        .baseUrl(ollama_base_url)
                        .modelName(ollama_modelName)
                        .build();
            }
            case "工具" -> {
                OllamaOptions options2 = OllamaOptions.builder()
                        .numCtx(4096)
                        .temperature(0.1)
                        .topK(40)
                        .topP(0.9)
                        .repeatPenalty(1.1)
                        .build();
                yield OllamaChatModel
                        .builder()
                        .defaultOptions(options2)
                        .baseUrl(ollama_base_url)
                        .modelName(ollama_modelName)
                        .build();
            }
            case "聊天" -> {
                OllamaOptions options3 = OllamaOptions.builder()
                        .numCtx(8192)
                        .temperature(0.8)
                        .topK(40)
                        .topP(0.9)
                        .repeatPenalty(1.1)
                        .build();
                yield OllamaChatModel
                        .builder()
                        .defaultOptions(options3)
                        .baseUrl(ollama_base_url)
                        .modelName(ollama_modelName)
                        .build();
            }
            default -> buildOllamaModel();
        };
    }

}
