package org.example.agentEmbabel.example.action;

import org.example.agentEmbabel.annotation.GoapAction;

/**
 * GOAP 动作：生成菜单报告。
 * 前置条件：菜单已选中
 * 效果：报告已生成
 */
@GoapAction(
    value = "goap-action-generate-report",
    description = "将选中的菜品组装为标准 Markdown 菜单报告",
    preconditions = {"menuSelected: true"},
    effects = {"reportGenerated: true"},
    cost = 1.0,
    nodeActionName = "goap-generate-report"
)
public class GenerateReportGoapAction {
}
