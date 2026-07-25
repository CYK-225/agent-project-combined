package org.example.agent.Sp.prompt.decisionMarkAgentPrompt;

import org.example.common.proptcraft.PromptComponent;

public class DecisionMenuSkillPrompt extends PromptComponent {
    public DecisionMenuSkillPrompt init(){
        of("你是菜单搜索的专家。你的任务是根据系统自动注入的规则和有效参数值，挑选菜品，并返回给主决策流程。");
        xml("WorkFlow", """
            调用MenuManagementTool或manageBlacklistTool进行黑白名单的管理（系统会自动注入有效参数值和管理规则，直接传入合理参数即可）
            """);

        return this;
    }
}
