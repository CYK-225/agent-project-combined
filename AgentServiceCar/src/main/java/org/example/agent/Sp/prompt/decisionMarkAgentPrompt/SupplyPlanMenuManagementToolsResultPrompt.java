package org.example.agent.Sp.prompt.decisionMarkAgentPrompt;

import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.common.proptcraft.PromptComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SupplyPlanMenuManagementToolsResultPrompt extends PromptComponent {

    public String generateStructureReport(SupplyPlanModelEvent event) {

        List<DishInfoAndScore> whitelist=new ArrayList<>(event.getWhiteDishMap().values());
        System.out.println(whitelist);
        // 1. 使用 Stream API 进行聚合统计
        // 注意：这里处理了嵌套结构 d.getDishInfo().getDishType()
        Map<String, Long> typeCounts = whitelist.stream()
                .map(d -> d.getDishInfo().getDishType()) // 提取分类
                .peek(System.out::println) // 调试输出，查看提取结果
                .filter(Objects::nonNull)            // 防御性编程：过滤空值
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));

        // 2. 获取各分类数量（默认为 0）
        long bigMeatCount = typeCounts.getOrDefault("大荤", 0L);
        long smallMeatCount = typeCounts.getOrDefault("小荤", 0L);
        long veggieCount = typeCounts.getOrDefault("纯素", 0L);
        System.out.println(STR."bigMeatCount: \{bigMeatCount}, smallMeatCount: \{smallMeatCount}, veggieCount: \{veggieCount}");
        // 3. 定义目标（Hardcoded rules，或者从配置读取）
        int targetBig = 2;
        int targetSmall = 3; // 假设之前提到是3
        int targetVeggie = 3;

        // 4. 生成自然语言战报 (这是给 LLM 看的“唯一真理”)
        String result= String.format("""
        【当前菜单结构审计报告】(数据来源：Java系统实时统计)
        ------------------------------------------------
        [大荤] 目标: %d | 当前: %d | 状态: %s
        [小荤] 目标: %d | 当前: %d | 状态: %s
        [纯素] 目标: %d | 当前: %d | 状态: %s
        ------------------------------------------------
        结论：你需要优先填补所有状态为 [MISSING] 的缺口。
        """,
                targetBig, bigMeatCount, getStatusLabel(bigMeatCount, targetBig),
                targetSmall, smallMeatCount, getStatusLabel(smallMeatCount, targetSmall),
                targetVeggie, veggieCount, getStatusLabel(veggieCount, targetVeggie)
        );
        System.out.println("Result"+result);
        return result;
    }

    // 辅助方法：生成状态标签
    private String getStatusLabel(long current, int target) {
        if (current >= target) {
            return "✅ FULL (已满/溢出)";
        } else {
            return STR."❌ MISSING (急缺 \{target - current} 个)";
        }
    }
}
