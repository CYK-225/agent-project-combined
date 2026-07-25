package org.example.agentEmbabel.example.goal;

import org.example.agentEmbabel.annotation.GoapGoal;

/**
 * GOAP 目标：完成菜单规划。
 * 达成条件：报告已生成
 *
 * <p>GOAP 会自动规划：
 * 需求收集 → 偏好分析 → 菜品搜索 → 菜品筛选 → 报告生成
 */
@GoapGoal(
    value = "goap-goal-menu-planning",
    description = "完成一顿饭的菜单规划（从需求到报告）",
    conditions = {"reportGenerated: true"},
    priority = 10
)
public class MenuPlanningGoal {
}
