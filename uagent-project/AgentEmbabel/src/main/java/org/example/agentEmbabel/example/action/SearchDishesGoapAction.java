package org.example.agentEmbabel.example.action;

import org.example.agentEmbabel.annotation.GoapAction;

/**
 * GOAP 动作：搜索候选菜品。
 * 前置条件：偏好已分析
 * 效果：菜品已搜索
 */
@GoapAction(
    value = "goap-action-search-dishes",
    description = "根据口味偏好和预算搜索候选菜品",
    preconditions = {"preferencesAnalyzed: true"},
    effects = {"dishesSearched: true"},
    cost = 2.0,
    nodeActionName = "goap-search-dishes"
)
public class SearchDishesGoapAction {
}
