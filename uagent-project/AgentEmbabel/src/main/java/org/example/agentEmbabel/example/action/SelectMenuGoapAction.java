package org.example.agentEmbabel.example.action;

import org.example.agentEmbabel.annotation.GoapAction;

/**
 * GOAP 动作：筛选并选中菜品。
 * 前置条件：菜品已搜索
 * 效果：菜单已选中
 */
@GoapAction(
    value = "goap-action-select-menu",
    description = "按营养配比从候选菜品中筛选最终菜单",
    preconditions = {"dishesSearched: true"},
    effects = {"menuSelected: true"},
    cost = 1.5,
    nodeActionName = "goap-select-menu"
)
public class SelectMenuGoapAction {
}
