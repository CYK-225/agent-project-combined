package org.example.agent.Sp.tool.masterSPAgentTool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@Slf4j
public class UserQuestionAndUserReportWriteTools {
    @Tool(
            name = "SupplyPlanUserContextWriteTool",
            description = "统一写入或更新用户的【画像报告】与【具体问题】。当你通过对话收集到了用户的偏好（口味、忌口、预算等），或者用户提出了具体的餐饮规划、菜单调整诉求时，调用此工具将其保存到系统上下文中。两者可以同时更新，也可以只更新其中一个。"
    )
    public String writeUserContext(
            @ToolParam(description = "要保存的用户画像内容（例如：人数:10人, 预算:高端, 忌口:海鲜）。如果本次不需要更新画像，请严格传入空字符串 \"\"。",name = "user_report")
            String userReport,

            @ToolParam(description = "用户提出的原始问题或核心诉求语句。如果本次不需要更新问题，请严格传入空字符串 \"\"。",name = "user_question")
            String userQuestion,

            // 框架自动注入事件总线
            SupplyPlanModelEvent event
    ) {
        try {
            if (event == null) {
                log.warn(">>> [UserContextWriteTool] 失败：上下文中缺失 SupplyPlanModelEvent");
                return "【失败】未能获取到系统上下文 (SupplyPlanModelEvent)。";
            }

            StringBuilder resultMsg = new StringBuilder("【成功】系统上下文已更新记录：");
            boolean isUpdated = false;

            // 判空逻辑：大模型传入的如果不为空，才进行覆盖写入
            if (userReport != null && !userReport.trim().isEmpty() && !"null".equalsIgnoreCase(userReport.trim())) {
                event.setUserReport(userReport);
                log.info(">>> [UserContextWriteTool] 写入用户画像: {}", userReport);
                resultMsg.append("[用户画像] ");
                isUpdated = true;
            }

            if (userQuestion != null && !userQuestion.trim().isEmpty() && !"null".equalsIgnoreCase(userQuestion.trim())) {
                event.setUserQuestion(userQuestion);
                log.info(">>> [UserContextWriteTool] 写入用户问题: {}", userQuestion);
                resultMsg.append("[用户问题] ");
                isUpdated = true;
            }

            if (!isUpdated) {
                log.info(">>> [UserContextWriteTool] 执行完毕，但未传入任何有效内容。");
                return "【提示】未传入任何有效内容，未做更新。";
            }

            return resultMsg.toString();

        } catch (Exception e) {
            log.error(">>> [UserContextWriteTool] 执行异常: ", e);
            return "【异常】执行失败：" + e.getMessage();
        }
    }
}
