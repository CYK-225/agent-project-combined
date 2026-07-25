package org.example.agentEmbabel.core;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.extern.slf4j.Slf4j;
import org.example.agentEmbabel.model.GoapActionDef;
import org.example.agentEmbabel.model.GoapGoalDef;
import org.example.agentEmbabel.model.WorldState;
import org.example.agentEmbabel.pool.GoapActionPool;
import org.example.agentEmbabel.pool.GoapGoalPool;
import org.example.graph.createGraph.engine.GraphEngine;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GOAP 编排器。
 * 实现 GOAP 的核心控制循环：规划一步 → 执行 → 更新状态 → 重新规划。
 *
 * <p>这是 AgentEmbabel 模块的核心组件，负责：
 * <ul>
 *   <li>驱动 GOAP 规划器进行动态规划</li>
 *   <li>调用 GraphFragmentBuilder 构建可执行的图片段</li>
 *   <li>通过 GraphEngine 执行图片段</li>
 *   <li>使用 StateAdapter 维护世界状态</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 * // 执行到目标达成
 * ExecuteReport report = orchestrator.execute("prepare-meal", initialState, threadId);
 * OverAllState result = report.finalState();
 *
 * // 或指定最大步数
 * ExecuteReport report = orchestrator.execute("prepare-meal", initialState, threadId, 10);
 * </pre>
 */
@Slf4j
@Component
public class GoapOrchestrator {

    private final GoapPlanner planner;
    private final GoapActionPool actionPool;
    private final GoapGoalPool goalPool;
    private final GraphFragmentBuilder fragmentBuilder;
    private final GraphEngine graphEngine;
    private final StateAdapter stateAdapter;

    @lombok.Getter
    private final GoapActionPool actionPoolGetter;
    @lombok.Getter
    private final GoapGoalPool goalPoolGetter;

    public GoapOrchestrator(GoapPlanner planner,
                            GoapActionPool actionPool,
                            GoapGoalPool goalPool,
                            GraphFragmentBuilder fragmentBuilder,
                            GraphEngine graphEngine,
                            StateAdapter stateAdapter) {
        this.planner = planner;
        this.actionPool = actionPool;
        this.goalPool = goalPool;
        this.fragmentBuilder = fragmentBuilder;
        this.graphEngine = graphEngine;
        this.stateAdapter = stateAdapter;
        this.actionPoolGetter = actionPool;
        this.goalPoolGetter = goalPool;
    }

    /**
     * 执行 GOAP 控制循环，直到目标达成或无法规划。
     *
     * @param goalName     目标名称（@GoapGoal 注册的名称）
     * @param initialState 初始状态
     * @param threadId     会话 ID（用于 Agent 隔离）
     * @return 执行报告（包含最终状态、执行轨迹、失败动作等）
     */
    public ExecuteReport execute(String goalName, OverAllState initialState, String threadId) {
        return execute(goalName, initialState, threadId, 50);
    }

    /**
     * 执行 GOAP 控制循环，带最大步数限制。
     *
     * <p>动态规划特性：
     * <ul>
     *   <li>每步执行后从【新的实际状态】重新规划，而非预规划路径</li>
     *   <li>动作失败时自动标记为不可用，重新规划寻找替代路径</li>
     *   <li>状态未变化时检测为死循环，自动标记动作不可用</li>
     * </ul>
     *
     * @param goalName     目标名称
     * @param initialState 初始状态
     * @param threadId     会话 ID
     * @param maxSteps     最大步数（防止无限循环）
     * @return 执行报告（包含最终状态和完整执行轨迹）
     */
    public ExecuteReport execute(String goalName, OverAllState initialState, String threadId, int maxSteps) {
        log.info("[GOAP] 开始执行: goalName={}, threadId={}, maxSteps={}", goalName, threadId, maxSteps);

        // 1. 获取目标定义
        GoapGoalDef goal = goalPool.get(goalName);
        log.info("[GOAP] 目标: name={}, conditions={}", goal.getName(), goal.getConditions());

        // 2. 转换初始状态
        WorldState worldState = stateAdapter.toWorldState(initialState);
        log.info("[GOAP] 初始状态: {}", worldState.getFacts());

        // 3. 失败跟踪：记录失败动作，重新规划时排除
        Set<String> failedActions = new LinkedHashSet<>();
        List<StepTrace> traces = new ArrayList<>();
        int step = 0;

        // 4. 控制循环
        while (!goal.isAchieved(worldState) && step < maxSteps) {
            log.info("[GOAP] Step {}: 当前状态={}, 已失败动作={}",
                    step, worldState.getFacts(), failedActions);

            // 4.1 排除已失败动作，重新规划
            List<GoapActionDef> availableActions = actionPool.getAll().stream()
                    .filter(a -> !failedActions.contains(a.getName()))
                    .collect(Collectors.toList());

            List<GoapActionDef> plan = planner.plan(worldState, goal, availableActions);

            if (plan.isEmpty()) {
                log.warn("[GOAP] 无法找到到达目标的规划: goal={}, 已排除动作={}",
                        goal.getName(), failedActions);
                traces.add(new StepTrace(step, null, "NO_PLAN",
                        "无可用路径（已排除 " + failedActions.size() + " 个失败动作）"));
                break;
            }

            GoapActionDef nextAction = plan.get(0);
            log.info("[GOAP] 规划动作: name={}, cost={}, 剩余路径长度={}",
                    nextAction.getName(), nextAction.getCost(), plan.size());

            // 4.2 记录执行前状态快照（用于检测状态是否变化）
            Map<String, String> stateBefore = new HashMap<>(worldState.getFacts());

            // 4.3 构建图片段并执行
            try {
                StateGraph fragment = fragmentBuilder.build(nextAction);
                OverAllState graphState = stateAdapter.toGraphState(worldState, initialState);

                CompiledGraph compiled = fragment.compile();
                OverAllState result = graphEngine.invoke(compiled, graphState, threadId, null);

                // 4.4 更新世界状态
                worldState = stateAdapter.updateFromResult(worldState, nextAction, result);

                // 4.5 检测状态是否真的变化了（死循环保护）
                if (worldState.getFacts().equals(stateBefore)) {
                    log.warn("[GOAP] 动作执行后状态未变化，标记为失败: action={}", nextAction.getName());
                    failedActions.add(nextAction.getName());
                    traces.add(new StepTrace(step, nextAction.getName(), "STALE_STATE",
                            "动作执行成功但状态未变化，可能存在死循环"));
                    step++;
                    continue;
                }

                log.info("[GOAP] 动作执行成功: name={}, 新状态={}", nextAction.getName(), worldState.getFacts());
                traces.add(new StepTrace(step, nextAction.getName(), "SUCCESS", null));

            } catch (Exception e) {
                // 4.6 动作执行失败：标记为不可用，继续循环重新规划
                log.warn("[GOAP] 动作执行失败，将尝试替代路径: action={}, error={}",
                        nextAction.getName(), e.getMessage());
                failedActions.add(nextAction.getName());
                traces.add(new StepTrace(step, nextAction.getName(), "FAILED", e.getMessage()));
                step++;
                continue;
            }

            step++;
        }

        // 5. 构建执行报告
        OverAllState finalState = stateAdapter.toGraphState(worldState, initialState);
        boolean achieved = goal.isAchieved(worldState);

        String exitReason;
        if (achieved) {
            exitReason = "GOAL_ACHIEVED";
        } else if (step >= maxSteps) {
            exitReason = "MAX_STEPS";
        } else {
            exitReason = "NO_PLAN";
        }

        log.info("[GOAP] 执行结束: goal={}, steps={}, achieved={}, failedActions={}, exitReason={}",
                goal.getName(), step, achieved, failedActions, exitReason);

        return new ExecuteReport(
                goalName, achieved, finalState, step,
                Collections.unmodifiableList(traces),
                Collections.unmodifiableSet(failedActions),
                exitReason
        );
    }

    /**
     * 执行单步规划和执行。
     * 用于需要更细粒度控制的场景。
     *
     * @param goalName     目标名称
     * @param currentState 当前状态
     * @param threadId     会话 ID
     * @return 执行结果（包含更新后的状态和是否达成目标）
     */
    public StepResult executeStep(String goalName, OverAllState currentState, String threadId) {
        GoapGoalDef goal = goalPool.get(goalName);
        WorldState worldState = stateAdapter.toWorldState(currentState);

        // 检查目标是否已达成
        if (goal.isAchieved(worldState)) {
            return StepResult.builder()
                    .state(currentState)
                    .goalAchieved(true)
                    .actionExecuted(null)
                    .build();
        }

        // 规划
        List<GoapActionDef> availableActions = actionPool.getAll();
        List<GoapActionDef> plan = planner.plan(worldState, goal, availableActions);

        if (plan.isEmpty()) {
            return StepResult.builder()
                    .state(currentState)
                    .goalAchieved(false)
                    .actionExecuted(null)
                    .build();
        }

        GoapActionDef nextAction = plan.get(0);

        // 执行
        try {
            StateGraph fragment = fragmentBuilder.build(nextAction);
            OverAllState graphState = stateAdapter.toGraphState(worldState, currentState);

            CompiledGraph compiled = fragment.compile();
            OverAllState result = graphEngine.invoke(compiled, graphState, threadId, null);

            WorldState newState = stateAdapter.updateFromResult(worldState, nextAction, result);
            OverAllState finalState = stateAdapter.toGraphState(newState, currentState);

            return StepResult.builder()
                    .state(finalState)
                    .goalAchieved(goal.isAchieved(newState))
                    .actionExecuted(nextAction)
                    .build();

        } catch (Exception e) {
            log.error("[GOAP] 单步执行失败: action={}", nextAction.getName(), e);
            return StepResult.builder()
                    .state(currentState)
                    .goalAchieved(false)
                    .actionExecuted(nextAction)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * 单步执行结果。
     */
    @lombok.Data
    @lombok.Builder
    public static class StepResult {
        private OverAllState state;
        private boolean goalAchieved;
        private GoapActionDef actionExecuted;
        private String error;
    }

    /**
     * GOAP 完整执行报告。
     * 包含最终状态、执行轨迹、失败动作集合、退出原因。
     */
    public record ExecuteReport(
            String goalName,
            boolean achieved,
            OverAllState finalState,
            int steps,
            List<StepTrace> traces,
            Set<String> failedActions,
            String exitReason
    ) {
        /**
         * 获取成功执行的步骤数。
         */
        public int successSteps() {
            return (int) traces.stream()
                    .filter(t -> "SUCCESS".equals(t.status()))
                    .count();
        }

        /**
         * 获取失败步骤数。
         */
        public int failedSteps() {
            return (int) traces.stream()
                    .filter(t -> "FAILED".equals(t.status()) || "STALE_STATE".equals(t.status()))
                    .count();
        }
    }

    /**
     * 单步执行轨迹。
     *
     * @param stepIndex   步骤序号
     * @param actionName  执行的动作名称（可能为 null）
     * @param status      状态：SUCCESS / FAILED / STALE_STATE / NO_PLAN
     * @param message     附加信息（失败原因等）
     */
    public record StepTrace(
            int stepIndex,
            String actionName,
            String status,
            String message
    ) {
    }
}
