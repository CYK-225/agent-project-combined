package org.example.agent.user.tool;

import io.agentscope.core.tool.Tool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;


@Slf4j
@Data
public class ReportTools {
    @Tool(
            description = "这是一个获取报告生成的标准格式的工具",
            name ="getDiningReportStandardGuide"
    )
    public String getDiningReportStandardGuide(){
        System.out.println(STR."getDiningReportStandardGuide工具被调用了，正在生成报告格式指南");
        ZeroShotPrompt zeroShotPrompt = new ZeroShotPrompt();
        StringBuilder formatBuilder = new StringBuilder();
        formatBuilder.append("这是报告的生成说明指南");
        formatBuilder.append("请严格按照以下Markdown格式输出报告，不要包含多余的寒暄语：\n\n");
        // 对应目标一：表格化输出
        formatBuilder.append("## 1. 饮食禁忌与风控分析\n");
        formatBuilder.append("| 禁忌食材/菜品 | 提及人数 | 风险等级 (高/中/低) | 建议策略 (如：彻底移除/标注过敏源) |\n");
        formatBuilder.append("|---|---|---|---|\n");
        formatBuilder.append("| (示例) 香菜 | 5 | 高风险 | 设为可选配料，不直接入菜 |\n\n");
        // 对应目标二：表格化输出 + 指标
        formatBuilder.append("## 2. 核心偏好趋势量化表\n");
        formatBuilder.append("| 热门菜系/单品 | 提及频次 | 偏好渗透率 (%) | 主要口味标签 |\n");
        formatBuilder.append("|---|---|---|---|\n");
        formatBuilder.append("| (示例) 宫保鸡丁 | 8 | 25.5% | 荔枝味、微辣 |\n\n");
        // 对应目标三：推理结论
        formatBuilder.append("## 3. 潜在高满意度菜品预测\n");
        formatBuilder.append("| 用户画像特征 (省份+辣度) | 推理逻辑 | 预测推荐菜品 (Top 3) |\n");
        formatBuilder.append("|---|---|---|\n");
        formatBuilder.append("| (示例) 湖南 + 重辣 | 湘菜基因结合高辣度耐受 | 剁椒鱼头、小炒黄牛肉、口味虾 |\n\n");
        formatBuilder.append("## 4. 综合建议\n");
        formatBuilder.append("基于以上数据，给出一句简短的菜单设计核心指导原则。");

        zeroShotPrompt.xml("输出报告格式", formatBuilder.toString());
        System.out.println(zeroShotPrompt.render());

        return zeroShotPrompt.render();
    }

}
