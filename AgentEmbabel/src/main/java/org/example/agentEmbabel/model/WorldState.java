package org.example.agentEmbabel.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * GOAP 世界状态。
 * 表示当前世界的事实集合，用于 GOAP 规划时的状态判断。
 *
 * <p>状态以 key:value 形式存储，例如：
 * <ul>
 *   <li>"hasIngredients" -> "true"</li>
 *   <li>"mealReady" -> "true"</li>
 *   <li>"ingredientCount" -> "5"</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class WorldState {

    /**
     * 事实集合。
     */
    private Map<String, String> facts = new HashMap<>();

    public WorldState(Map<String, String> facts) {
        this.facts = new HashMap<>(facts);
    }

    /**
     * 检查当前状态是否满足指定条件。
     *
     * @param conditions 条件数组，格式：["key:value", "key2:value2"]
     * @return 如果满足所有条件返回 true
     */
    public boolean satisfies(String[] conditions) {
        if (conditions == null || conditions.length == 0) {
            return true;
        }
        for (String condition : conditions) {
            String[] parts = condition.split(":");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (!value.equals(facts.get(key))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 应用效果，返回新的世界状态。
     *
     * @param effects 效果数组，格式：["key:value", "key2:value2"]
     * @return 应用效果后的新状态
     */
    public WorldState applyEffects(String[] effects) {
        WorldState newState = new WorldState();
        newState.facts.putAll(this.facts);
        if (effects != null) {
            for (String effect : effects) {
                String[] parts = effect.split(":");
                if (parts.length == 2) {
                    newState.facts.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return newState;
    }

    /**
     * 计算到目标的启发式估计（未满足条件数）。
     *
     * @param goalConditions 目标条件
     * @return 未满足的条件数
     */
    public int heuristic(String[] goalConditions) {
        if (goalConditions == null || goalConditions.length == 0) {
            return 0;
        }
        int unsatisfied = 0;
        for (String condition : goalConditions) {
            String[] parts = condition.split(":");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (!value.equals(facts.get(key))) {
                    unsatisfied++;
                }
            }
        }
        return unsatisfied;
    }

    /**
     * 设置事实。
     */
    public WorldState setFact(String key, String value) {
        this.facts.put(key, value);
        return this;
    }

    /**
     * 获取事实。
     */
    public String getFact(String key) {
        return this.facts.get(key);
    }

    /**
     * 检查是否包含指定事实。
     */
    public boolean hasFact(String key) {
        return this.facts.containsKey(key);
    }

    @Override
    public String toString() {
        return "WorldState" + facts;
    }
}
