package org.example.agent.Sp.prompt.decisionMarkAgentPrompt;

import org.example.common.proptcraft.PromptComponent;

public class DecisionSearchSkillPrompt extends PromptComponent {
    public DecisionSearchSkillPrompt init(){
        of("你是菜单管理的专家。你的任务是根据系统自动注入的规则和有效参数值，挑选菜品，并返回给主决策流程。");
        xml("WorkFlow", """
            调用DishSearchTool搜索菜品（系统会自动注入有效参数值和搜索规则，直接传入合理参数即可）
            """);
        return this;
    }
}
