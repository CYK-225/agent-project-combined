package org.example.agentEmbabel.model;

import lombok.Builder;
import lombok.Data;

/**
 * GOAP 目标定义。
 * 从 @GoapGoal 注解解析而来，包含目标的所有元数据。
 */
@Data
@Builder
public class GoapGoalDef {

    /**
     * 目标名称。
     */
    private String name;

    /**
     * 目标描述。
     */
    private String description;

    /**
     * 目标达成条件。
     * 格式：["key:value", "key2:value2"]
     */
    private String[] conditions;

    /**
     * 优先级。数字越大优先级越高。
     */
    private int priority;

    /**
     * 检查当前状态是否已达成目标。
     *
     * @param state 当前世界状态
     * @return 如果目标已达成返回 true
     */
    public boolean isAchieved(WorldState state) {
        return state.satisfies(conditions);
    }

    /**
     * 计算当前状态到目标的启发式估计。
     *
     * @param state 当前世界状态
     * @return 未满足的条件数
     */
    public int heuristic(WorldState state) {
        return state.heuristic(conditions);
    }
}
