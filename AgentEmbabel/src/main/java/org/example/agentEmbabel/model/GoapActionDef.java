package org.example.agentEmbabel.model;

import lombok.Builder;
import lombok.Data;

/**
 * GOAP 动作定义。
 * 从 @GoapAction 注解解析而来，包含动作的所有元数据。
 */
@Data
@Builder
public class GoapActionDef {

    /**
     * 动作名称。
     */
    private String name;

    /**
     * 动作描述。
     */
    private String description;

    /**
     * 前置条件。
     * 格式：["key:value", "key2:value2"]
     */
    private String[] preconditions;

    /**
     * 执行效果。
     * 格式：["key:value", "key2:value2"]
     */
    private String[] effects;

    /**
     * 动作代价。
     */
    private double cost;

    /**
     * 关联的 Agent 名称（引用 @AgentDefinition）。
     */
    private String agentName;

    /**
     * 关联的 NodeAction 名称（引用 @NodeAction）。
     */
    private String nodeActionName;

    /**
     * 检查当前状态是否满足前置条件。
     *
     * @param state 当前世界状态
     * @return 如果满足所有前置条件返回 true
     */
    public boolean isExecutable(WorldState state) {
        return state.satisfies(preconditions);
    }

    /**
     * 应用效果到当前状态，返回新状态。
     *
     * @param state 当前世界状态
     * @return 应用效果后的新状态
     */
    public WorldState applyEffects(WorldState state) {
        return state.applyEffects(effects);
    }
}
