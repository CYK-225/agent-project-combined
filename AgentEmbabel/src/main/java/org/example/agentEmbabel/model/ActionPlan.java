package org.example.agentEmbabel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * GOAP 规划结果。
 * 包含从当前状态到达目标的动作序列。
 */
@Data
@Builder
public class ActionPlan {

    /**
     * 目标名称。
     */
    private String goalName;

    /**
     * 动作序列（按执行顺序）。
     */
    private List<GoapActionDef> actions;

    /**
     * 总代价。
     */
    private double totalCost;

    /**
     * 初始状态。
     */
    private WorldState initialState;

    /**
     * 目标状态。
     */
    private WorldState goalState;

    /**
     * 检查规划是否有效（非空）。
     */
    public boolean isValid() {
        return actions != null && !actions.isEmpty();
    }

    /**
     * 获取下一个要执行的动作。
     */
    public GoapActionDef nextAction() {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        return actions.get(0);
    }

    /**
     * 移除已执行的动作，返回剩余规划。
     */
    public ActionPlan remaining() {
        if (actions == null || actions.size() <= 1) {
            return ActionPlan.builder()
                    .goalName(goalName)
                    .actions(List.of())
                    .totalCost(0)
                    .initialState(initialState)
                    .goalState(goalState)
                    .build();
        }
        return ActionPlan.builder()
                .goalName(goalName)
                .actions(actions.subList(1, actions.size()))
                .totalCost(totalCost - actions.get(0).getCost())
                .initialState(initialState)
                .goalState(goalState)
                .build();
    }
}
