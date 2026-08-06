package org.example.agentEmbabel.example.action;

import org.example.agentEmbabel.annotation.GoapAction;

/**
 * GOAP 动作：收集用户需求。
 * 前置条件：无（起始动作）
 * 效果：需求已收集
 */
@GoapAction(
    value = "goap-action-gather-requirements",
    description = "收集并结构化用户餐饮需求（口味、人数、预算）",
    preconditions = {},
    effects = {"requirementsGathered: true"},
    cost = 1.0,
    nodeActionName = "goap-gather-requirements"
)
public class GatherRequirementsGoapAction {
}
