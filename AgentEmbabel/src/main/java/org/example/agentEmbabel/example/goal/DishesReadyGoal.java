package org.example.agentEmbabel.example.goal;

import org.example.agentEmbabel.annotation.GoapGoal;

/**
 * GOAP 目标：完成菜品搜索（中间状态）。
 * 达成条件：菜品已搜索
 *
 * <p>GOAP 会自动规划一条较短路径：
 * 需求收集 → 偏好分析 → 菜品搜索
 * （不需要筛选和报告生成）
 */
@GoapGoal(
    value = "goap-goal-dishes-ready",
    description = "完成候选菜品搜索（不需要生成最终报告）",
    conditions = {"dishesSearched: true"},
    priority = 5
)
public class DishesReadyGoal {
}
