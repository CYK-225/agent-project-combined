package org.example.agent.recommendStoreMenuAgent.agents;


import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.financeForecastAgent.dataModel.RuntimeContext;
import org.example.agent.recommendStoreMenuAgent.prompt.RecommendMenuPrompt;
import org.example.agent.recommendStoreMenuAgent.tool.CommonTool;
import org.example.agent.recommendStoreMenuAgent.tool.CompanyTool;
import org.example.agent.recommendStoreMenuAgent.tool.DataAnalyzeTool;
import org.example.agent.recommendStoreMenuAgent.tool.MerchantStoreTool;
import org.example.agent.utils.SkillLoader;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;


@Slf4j
@AgentDefinition(
        name = "RecommendMenuAgent",
        description = """
                团餐平台智能助手，可以帮用户解决团餐领域的各种问题
                """,
        enableMemory = false
)
@Component
public class RecommendMenuAgent extends AbstractAgentTemplate {

    @Resource
    private CompanyTool companyTool;

    @Resource
    private MerchantStoreTool merchantStoreTool;

    @Resource
    private DataAnalyzeTool dataAnalyzeTool;

    @Resource
    private CommonTool commonTool;

    @Value("${spring.ai.skill.fileDir}")
    private String skillDir;


    private final RuntimeContext runtimeContext = new RuntimeContext();


    protected RecommendMenuAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return null;
    }

    @Override
    protected String setupSysPrompt(String skillName) {
        runtimeContext.setSkillDir(skillDir);
        runtimeContext.setSkillName(skillName);
        return new RecommendMenuPrompt().build(runtimeContext);
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("聊天");
    }

    @Override
    protected Toolkit setupTools() {
        return components.toolkit().create().addTools(
                commonTool,merchantStoreTool,dataAnalyzeTool,companyTool
        ).build();
    }


    @Override
    protected ToolExecutionContext setupToolExecutionContext() {
        return ToolExecutionContext.builder()
                .register(runtimeContext)
                .build();
    }

}
