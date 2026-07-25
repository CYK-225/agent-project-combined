package org.example.agent.Sp.tool.decisionMarkAgentTool;

import io.agentscope.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.agent.Sp.prompt.decisionMarkAgentPrompt.SupplyPlanReportPrompt;

@Slf4j
//TODO 更换成对应的Prompt模板
public class PromptReportTools {
    @Tool(
            description = "【核心触发器】当开始生成报告前，必须首先调用此工具获取报告模板"
            ,name="getMenuManagementPrompt")
    public String getMenuManagementPrompt(SupplyPlanModelEvent event){
        SupplyPlanReportPrompt planReportProptcraft=new SupplyPlanReportPrompt();
        planReportProptcraft.init(event);
        return planReportProptcraft.render();
    }
}
