package org.example.agentEmbabel.example.action;

import org.example.agentEmbabel.annotation.GoapAction;

/**
 * GOAP 动作：分析用户偏好。
 * 前置条件：需求已收集
 * 效果：偏好已分析
 */
@GoapAction(
    value = "goap-action-analyze-preferences",
    description = "根据公司ID和收集的需求分析用户口味偏好",
    preconditions = {"requirementsGathered: true"},
    effects = {"preferencesAnalyzed: true"},
    cost = 1.5,
    nodeActionName = "goap-analyze-preferences"
)
public class AnalyzePreferencesGoapAction {
}
