package org.example.agentEmbabel.example.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.*;

/**
 * 生成菜单报告节点。
 * 将选中的菜品组装为标准 Markdown 格式的菜单报告。
 */
@Slf4j
@NodeAction(value = "goap-generate-report", description = "生成 Markdown 菜单报告")
public class GenerateReportNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        List<Map<String, Object>> selectedMenu =
                (List<Map<String, Object>>) state.value("selectedMenu").orElse(List.of());
        String company = (String) state.value("parsedCompany").orElse("默认公司");
        int headcount = (int) state.value("parsedHeadcount").orElse(50);
        String mealDate = (String) state.value("mealDate").orElse("");

        log.info("[GOAP-Node] 生成报告: company={}, dishes={}", company, selectedMenu.size());

        // 生成 Markdown 报告
        StringBuilder report = new StringBuilder();
        report.append("# ").append(company).append(" 菜单规划报告\n\n");
        report.append("**日期**: ").append(mealDate).append("  \n");
        report.append("**就餐人数**: ").append(headcount).append("人  \n\n");
        report.append("## 菜品清单\n\n");
        report.append("| 序号 | 菜名 | 类型 | 辣度 | 单价 | 评分 |\n");
        report.append("|------|------|------|------|------|------|\n");

        int idx = 1;
        double totalCost = 0;
        for (Map<String, Object> dish : selectedMenu) {
            String name = (String) dish.get("name");
            String type = (String) dish.get("type");
            int spicyLevel = (int) dish.get("spicyLevel");
            double cost = (double) dish.get("cost");
            double score = (double) dish.get("score");
            totalCost += cost;

            String spicyLabel = switch (spicyLevel) {
                case 0 -> "不辣";
                case 1 -> "微辣";
                case 2 -> "中辣";
                case 3 -> "超辣";
                default -> "";
            };

            report.append(String.format("| %d | %s | %s | %s | ¥%.1f | %.1f |\n",
                    idx++, name, type, spicyLabel, cost, score));
        }

        report.append("\n## 汇总\n\n");
        report.append(String.format("- **菜品总数**: %d 道\n", selectedMenu.size()));
        report.append(String.format("- **人均成本**: ¥%.1f\n", totalCost / Math.max(headcount, 1) * 10));
        report.append(String.format("- **总成本**: ¥%.1f\n", totalCost));

        log.info("[GOAP-Node] 报告生成完成, 长度={}", report.length());

        return Map.of(
                "reportGenerated", "true",
                "menuReport", report.toString(),
                "reportLength", report.length()
        );
    }
}
