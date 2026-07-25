package org.example.agentEmbabel.core;

import lombok.extern.slf4j.Slf4j;
import org.example.agentEmbabel.model.GoapActionDef;
import org.example.agentEmbabel.model.GoapGoalDef;
import org.example.agentEmbabel.model.WorldState;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * GOAP 规划器。
 * 使用 A* 算法规划从当前状态到达目标的最优动作序列。
 *
 * <p>A* 算法核心：
 * <ul>
 *   <li>f(n) = g(n) + h(n)</li>
 *   <li>g(n) = 从起点到节点 n 的实际代价（累积动作代价）</li>
 *   <li>h(n) = 从节点 n 到目标的启发式估计（未满足条件数）</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 * List&lt;GoapActionDef&gt; plan = planner.plan(currentState, goal, availableActions);
 * if (!plan.isEmpty()) {
 *     GoapActionDef nextAction = plan.get(0);
 *     // 执行 nextAction...
 * }
 * </pre>
 */
@Slf4j
@Component
public class GoapPlanner {

    /**
     * 使用 A* 算法规划从 currentState 到 goal 的动作序列。
     *
     * @param currentState     当前世界状态
     * @param goal             目标定义
     * @param availableActions 所有可用动作
     * @return 动作序列（按执行顺序），如果无法到达目标返回空列表
     */
    public List<GoapActionDef> plan(WorldState currentState, GoapGoalDef goal,
                                     List<GoapActionDef> availableActions) {
        log.debug("开始规划: goal={}, currentFacts={}", goal.getName(), currentState.getFacts());

        // 如果目标已达成，返回空规划
        if (goal.isAchieved(currentState)) {
            log.debug("目标已达成: {}", goal.getName());
            return List.of();
        }

        // A* 搜索
        PriorityQueue<SearchNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Set<String> closedSet = new HashSet<>();

        // 初始节点
        SearchNode startNode = new SearchNode(currentState, List.of(), 0, goal.heuristic(currentState));
        openSet.add(startNode);

        int maxIterations = 1000; // 防止无限循环
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            SearchNode current = openSet.poll();

            // 检查是否到达目标
            if (goal.isAchieved(current.state)) {
                log.debug("规划完成: goal={}, steps={}, cost={}, iterations={}",
                        goal.getName(), current.plan.size(), current.g, iterations);
                return current.plan;
            }

            // 跳过已访问的状态
            String stateKey = stateToKey(current.state);
            if (closedSet.contains(stateKey)) {
                continue;
            }
            closedSet.add(stateKey);

            // 扩展所有可执行的动作
            for (GoapActionDef action : availableActions) {
                if (action.isExecutable(current.state)) {
                    // 应用效果，生成新状态
                    WorldState newState = action.applyEffects(current.state);
                    String newStateKey = stateToKey(newState);

                    if (!closedSet.contains(newStateKey)) {
                        // 计算新的代价
                        double newG = current.g + action.getCost();
                        double newH = goal.heuristic(newState);

                        // 构建新的规划
                        List<GoapActionDef> newPlan = new ArrayList<>(current.plan);
                        newPlan.add(action);

                        SearchNode newNode = new SearchNode(newState, newPlan, newG, newH);
                        openSet.add(newNode);
                    }
                }
            }
        }

        log.warn("规划失败: goal={}, iterations={}", goal.getName(), iterations);
        return List.of();
    }

    /**
     * 将状态转换为字符串键（用于去重）。
     */
    private String stateToKey(WorldState state) {
        TreeMap<String, String> sorted = new TreeMap<>(state.getFacts());
        return sorted.toString();
    }

    /**
     * A* 搜索节点。
     */
    private static class SearchNode {
        final WorldState state;
        final List<GoapActionDef> plan;
        final double g; // 实际代价
        final double h; // 启发式估计
        final double f; // 总代价

        SearchNode(WorldState state, List<GoapActionDef> plan, double g, double h) {
            this.state = state;
            this.plan = plan;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }
    }
}
