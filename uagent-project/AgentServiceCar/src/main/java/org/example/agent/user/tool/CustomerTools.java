package org.example.agent.user.tool;



import io.agentscope.core.tool.Tool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@Component
public class CustomerTools {

    @Tool(
            description = "获取餐饮原始数据的核心分析指标计算指南，用于指导大模型如何对用户偏好进行量化分析与指标提取",
            name = "getCateringAnalysisMetricsPrompt"
    )
    public String getCateringAnalysisMetricsPrompt() {
        ZeroShotPrompt zeroShotPrompt = new ZeroShotPrompt();
        StringBuilder metricsBuilder = new StringBuilder();

        metricsBuilder.append("在分析用户餐饮数据时，请严格提取并计算以下核心量化指标，确保数据准确性：\n\n");

        // 指标一：风险指标
        metricsBuilder.append("### 1. 风险与禁忌指标\n");
        metricsBuilder.append("- **禁忌触碰率**：计算带有明确忌口/过敏（如免葱姜蒜、海鲜过敏）的人数占总样本的百分比。\n");
        metricsBuilder.append("- **高危食材Top3**：统计被列为禁忌频次最高的三种食材。\n\n");

        // 指标二：口味偏好
        metricsBuilder.append("### 2. 口味与辣度耐受度\n");
        metricsBuilder.append("- **辣度分布比例**：将用户辣度偏好归类为【不吃辣/微辣/中辣/重辣】，计算各区间人数占比。\n");
        metricsBuilder.append("- **核心味型标签**：提取被提及最多的三个口味标签（如：酸甜、咸鲜、麻辣）。\n\n");

        // 指标三：菜系与单品
        metricsBuilder.append("### 3. 菜系与单品聚集度\n");
        metricsBuilder.append("- **菜系渗透率**：特定菜系（如川菜、粤菜）提及人数 ÷ 总人数 × 100%。\n");
        metricsBuilder.append("- **绝对爆款单品**：统计点名率最高的具体菜品名称及其被提及的绝对次数。\n\n");

        metricsBuilder.append("请确保所有的百分比计算保留一位小数。如果某项指标在原始数据中缺失，请明确标注“数据不足，无法计算”。");

        zeroShotPrompt.xml("分析指标提取指南", metricsBuilder.toString());

        return zeroShotPrompt.render();
    }
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
