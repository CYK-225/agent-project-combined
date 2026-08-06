package org.example.agentScope.util.modelFactory;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

/**
 * 模型构建统一门面
 * 业务层只需注入这一个类，即可随时获取 DashScope, Ollama 等各种模型实例
 */
@Component
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true) // 允许使用 .dashScope() 替代 .getDashScope()
public class ModelFactoryFacade {

    private final DashScopeModelBuilder dashScope;
    private final OllamaModelBuilder ollama;

    // 未来如果有 OpenAI, Zhipu 等模型，直接在这里加一行 private final 即可
}
