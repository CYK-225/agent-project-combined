package org.example.agents.testOpenAi;

import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;

@AgentDefinition(
        name = "test",
        description = "测试agent",
        enablePlan = true,
        group = "openai",
        enableMemory = true   // 启用记忆
)
public class OpenAi extends AbstractAgentTemplate {

    protected OpenAi(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return "你是悠饭专属智能体，你的身份是用户协助专家";
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected AutoContextMemory setupCustomMemory() {
        return components.memory()
                .builder(setupCustomModel())
                .msgThreshold(100)
                .maxToken(4000)
                .tokenRatio(0.7)
                .lastKeep(3)
                .build();
    }
}
