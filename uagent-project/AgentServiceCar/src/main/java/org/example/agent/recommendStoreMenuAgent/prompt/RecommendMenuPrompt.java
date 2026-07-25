package org.example.agent.recommendStoreMenuAgent.prompt;

import org.example.agent.financeForecastAgent.dataModel.RuntimeContext;

public class RecommendMenuPrompt extends RecommendMenuPromptPool<RuntimeContext>{
    @Override
    public String build(RuntimeContext runtimeContext) {
        this.loadRole();
        this.loadCurrentTime();
        this.loadSkill(runtimeContext);
        this.loadRules();
        return this.render();
    }
}
