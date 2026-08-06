package org.example.agentEmbabel.core;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.agentEmbabel.model.GoapActionDef;
import org.example.agentEmbabel.model.WorldState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态适配器。
 * 负责 GOAP 的 WorldState 与 Graph 的 OverAllState 之间的双向转换。
 *
 * <p>转换规则：
 * <ul>
 *   <li>WorldState.facts (Map&lt;String, String&gt;) ↔ OverAllState.data() (Map&lt;String, Object&gt;)</li>
 *   <li>Boolean 类型：true/false ↔ "true"/"false"</li>
 *   <li>String 类型：直接映射</li>
 * </ul>
 */
@Slf4j
@Component
public class StateAdapter {

    /**
     * OverAllState → WorldState 转换。
     * 提取 Graph 状态中的布尔值和字符串值作为世界事实。
     *
     * @param state Graph 的全局状态
     * @return GOAP 的世界状态
     */
    public WorldState toWorldState(OverAllState state) {
        WorldState worldState = new WorldState();
        if (state == null || state.data() == null) {
            return worldState;
        }

        state.data().forEach((key, value) -> {
            if (value instanceof Boolean boolVal) {
                worldState.setFact(key, String.valueOf(boolVal));
            } else if (value instanceof String strVal) {
                worldState.setFact(key, strVal);
            } else if (value instanceof Number numVal) {
                worldState.setFact(key, String.valueOf(numVal));
            }
        });

        log.debug("OverAllState → WorldState: {} facts", worldState.getFacts().size());
        return worldState;
    }

    /**
     * WorldState → OverAllState 转换。
     * 将世界事实合并到基础 Graph 状态中。
     *
     * @param worldState GOAP 的世界状态
     * @param baseState  基础 Graph 状态（用于保留非事实字段）
     * @return 更新后的 Graph 状态
     */
    public OverAllState toGraphState(WorldState worldState, OverAllState baseState) {
        if (worldState == null) {
            return baseState;
        }

        Map<String, Object> update = new HashMap<>(worldState.getFacts());
        log.debug("WorldState → OverAllState: {} facts to merge", update.size());

        // 创建新的 OverAllState 实例
        Map<String, Object> mergedData = new HashMap<>(baseState.data());
        mergedData.putAll(update);
        return new OverAllState(mergedData);
    }

    /**
     * 根据 Action 执行结果更新 WorldState。
     * 先应用动作的声明式效果，再从执行结果中提取新事实。
     *
     * @param current 当前世界状态
     * @param action  执行的动作定义
     * @param result  Graph 执行结果
     * @return 更新后的世界状态
     */
    public WorldState updateFromResult(WorldState current, GoapActionDef action, OverAllState result) {
        // 1. 应用动作的声明式效果
        WorldState newState = current.applyEffects(action.getEffects());

        // 2. 从执行结果中提取新事实
        if (result != null && result.data() != null) {
            result.data().forEach((key, value) -> {
                if (value instanceof Boolean boolVal) {
                    newState.setFact(key, String.valueOf(boolVal));
                } else if (value instanceof String strVal) {
                    newState.setFact(key, strVal);
                } else if (value instanceof Number numVal) {
                    newState.setFact(key, String.valueOf(numVal));
                }
            });
        }

        log.debug("状态更新: action={}, effects={}, resultFacts={}",
                action.getName(), action.getEffects(),
                result != null ? result.data().size() : 0);

        return newState;
    }

    /**
     * 合并多个事实到 WorldState。
     *
     * @param state  当前世界状态
     * @param facts  要合并的事实
     * @return 更新后的世界状态
     */
    public WorldState mergeFacts(WorldState state, Map<String, String> facts) {
        WorldState newState = new WorldState();
        newState.getFacts().putAll(state.getFacts());
        newState.getFacts().putAll(facts);
        return newState;
    }
}
