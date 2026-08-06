package org.example.agent.Sp.agents;


import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import jakarta.annotation.Resource;
import lombok.EqualsAndHashCode;
import org.example.agent.Sp.prompt.decisionMarkAgentPrompt.DecisionMarkPrompt;
import org.example.agent.Sp.prompt.decisionMarkAgentPrompt.DecisionMenuSkillPrompt;
import org.example.agent.Sp.prompt.decisionMarkAgentPrompt.DecisionSearchSkillPrompt;
import org.example.agent.Sp.tool.decisionMarkAgentTool.SupplyPlanDishSearchTools;
import org.example.agent.Sp.tool.decisionMarkAgentTool.SupplyPlanMenuManagementTools;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;

@EqualsAndHashCode(callSuper = true)
@AgentDefinition(
        name = "DecisionMarkAgent"
        , description = "餐饮智能体搜索菜单skill的决策者，负责选择调用工具时候的搜索参数，联通搜索者与管理者"
//        ,enablePlan  = true
        ,hooksType = "sp"
        ,group = "canche"
        ,enablePersistence = true   // ✅ 启用自动记忆持久化
//        ,scope = "singleton"
)
@Component
public class DecisionMarkAgent extends AbstractAgentTemplate {
    /**
     * 构造函数
     *
     * @param components 组件门面
     */
    protected DecisionMarkAgent(AgentComponentFacade components) {
        super(components);
    }

    // 直接注入底层的两个 Tool 工具类
    @Resource
    private ObjectProvider<SupplyPlanDishSearchTools> dishSearchToolsFactory;

    @Resource
    private ObjectProvider<SupplyPlanMenuManagementTools> menuManagementToolsFactory;

    @Override
    protected String setupSysPrompt() {
        return new DecisionMarkPrompt().init().render();
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("思考");
    }


    @Override
    protected SkillBox setupSkills() {
        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        createBaseAgentSkill(
                                "菜单搜索专家",
                                "根据菜单的结构和用户的需求，设置参数，搜索菜品。",
                                new DecisionSearchSkillPrompt().init().render()
                        ),dishSearchToolsFactory.getObject()
                )
                .addSkillWithTools(
                        createBaseAgentSkill(
                                "菜单管理专家",
                                "根据上游传入的参数和用户需求，调用工具进行菜单黑白名单的管理。",
                               new DecisionMenuSkillPrompt().init().render()
                        ), menuManagementToolsFactory.getObject()
                )
                .buildSkillBox();
    }

    @Override
    protected Boolean isUseStudio(){
        return true;
    }

    /**
     * 测试模式
     */
//    private final DecisionMarkEvent decisionMarkEvent=new DecisionMarkEvent();
//    private final SupplyPlanModelEvent supplyPlanModelEvent=new SupplyPlanModelEvent();
//    @Override
//    protected ToolExecutionContext setupToolExecutionContext() { return ToolExecutionContext.builder().register(supplyPlanModelEvent).register(decisionMarkEvent).build(); }
//
//    @Override
//    protected List<Hook> setupCustomHooks() { return List.of(
//
//            DynamicPickDishPromptHook.builder().supplyPlanModelEvent(supplyPlanModelEvent).decisionMarkEvent(decisionMarkEvent).build()
//
//            , DecisionHook.builder().supplyPlanModelEvent(supplyPlanModelEvent).build()
//
//             ); }


}
