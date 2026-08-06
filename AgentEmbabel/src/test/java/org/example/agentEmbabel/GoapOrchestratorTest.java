package org.example.agentEmbabel;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import org.example.agentEmbabel.core.GoapOrchestrator;
import org.example.agentEmbabel.core.GoapPlanner;
import org.example.agentEmbabel.core.GraphFragmentBuilder;
import org.example.agentEmbabel.core.StateAdapter;
import org.example.agentEmbabel.model.GoapActionDef;
import org.example.agentEmbabel.model.GoapGoalDef;
import org.example.agentEmbabel.pool.GoapActionPool;
import org.example.agentEmbabel.pool.GoapGoalPool;
import org.example.graph.createGraph.engine.GraphEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GoapOrchestrator 单元测试。
 * 验证动态规划控制循环中的失败重规划、死循环检测和执行报告功能。
 */
@ExtendWith(MockitoExtension.class)
class GoapOrchestratorTest {

    @Mock
    private GoapActionPool actionPool;

    @Mock
    private GoapGoalPool goalPool;

    @Mock
    private GraphFragmentBuilder fragmentBuilder;

    @Mock
    private GraphEngine graphEngine;

    private GoapOrchestrator orchestrator;
    private StateAdapter stateAdapter;
    private GoapPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new GoapPlanner();
        stateAdapter = new StateAdapter();
        orchestrator = new GoapOrchestrator(
                planner, actionPool, goalPool, fragmentBuilder, graphEngine, stateAdapter);
    }

    // ==================== 辅助方法 ====================

    private List<GoapActionDef> createStandardActions() {
        return List.of(
                GoapActionDef.builder()
                        .name("gather")
                        .description("收集需求")
                        .preconditions(new String[]{})
                        .effects(new String[]{"requirementsGathered: true"})
                        .cost(1.0)
                        .build(),
                GoapActionDef.builder()
                        .name("analyze")
                        .description("分析偏好")
                        .preconditions(new String[]{"requirementsGathered: true"})
                        .effects(new String[]{"preferencesAnalyzed: true"})
                        .cost(1.5)
                        .build(),
                GoapActionDef.builder()
                        .name("report")
                        .description("生成报告")
                        .preconditions(new String[]{"preferencesAnalyzed: true"})
                        .effects(new String[]{"reportGenerated: true"})
                        .cost(1.0)
                        .build()
        );
    }

    /**
     * 创建包含替代路径的动作集合。
     * gather-alt 是 gather 的替代动作（无前置条件，效果相同）。
     */
    private List<GoapActionDef> createActionsWithAlternative() {
        return List.of(
                GoapActionDef.builder()
                        .name("gather")
                        .description("收集需求（主路径）")
                        .preconditions(new String[]{})
                        .effects(new String[]{"requirementsGathered: true"})
                        .cost(1.0)
                        .build(),
                GoapActionDef.builder()
                        .name("gather-alt")
                        .description("收集需求（替代路径）")
                        .preconditions(new String[]{})
                        .effects(new String[]{"requirementsGathered: true"})
                        .cost(2.0)
                        .build(),
                GoapActionDef.builder()
                        .name("analyze")
                        .description("分析偏好")
                        .preconditions(new String[]{"requirementsGathered: true"})
                        .effects(new String[]{"preferencesAnalyzed: true"})
                        .cost(1.5)
                        .build(),
                GoapActionDef.builder()
                        .name("report")
                        .description("生成报告")
                        .preconditions(new String[]{"preferencesAnalyzed: true"})
                        .effects(new String[]{"reportGenerated: true"})
                        .cost(1.0)
                        .build()
        );
    }

    private GoapGoalDef createGoal() {
        return GoapGoalDef.builder()
                .name("test-goal")
                .conditions(new String[]{"reportGenerated: true"})
                .priority(10)
                .build();
    }

    /**
     * 为每个动作名称创建独立的 mock StateGraph 和 CompiledGraph。
     * 返回 actionName → CompiledGraph 的映射，用于精确匹配 graphEngine.invoke() 的参数。
     */
    private Map<String, CompiledGraph> setupMockGraphChain(List<GoapActionDef> actions) throws Exception {
        Map<String, CompiledGraph> compiledMap = new HashMap<>();

        for (GoapActionDef action : actions) {
            StateGraph mockGraph = mock(StateGraph.class);
            CompiledGraph mockCompiled = mock(CompiledGraph.class);
            lenient().when(fragmentBuilder.build(argThat(a -> a != null && a.getName().equals(action.getName()))))
                    .thenReturn(mockGraph);
            lenient().when(mockGraph.compile()).thenReturn(mockCompiled);
            compiledMap.put(action.getName(), mockCompiled);
        }

        return compiledMap;
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("动作失败后重规划：主路径失败应走替代路径完成目标")
    void testActionFailureReplan() throws Exception {
        List<GoapActionDef> actions = createActionsWithAlternative();
        GoapGoalDef goal = createGoal();

        when(actionPool.getAll()).thenReturn(actions);
        when(goalPool.get("test-goal")).thenReturn(goal);

        Map<String, CompiledGraph> compiledMap = setupMockGraphChain(actions);

        // gather 的 CompiledGraph 抛异常（模拟节点执行失败）
        when(graphEngine.invoke(eq(compiledMap.get("gather")), any(OverAllState.class), anyString(), isNull()))
                .thenThrow(new RuntimeException("节点执行超时"));

        // 其他动作正常返回
        when(graphEngine.invoke(eq(compiledMap.get("gather-alt")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));
        when(graphEngine.invoke(eq(compiledMap.get("analyze")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));
        when(graphEngine.invoke(eq(compiledMap.get("report")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));

        OverAllState initialState = new OverAllState(new HashMap<>());
        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "test-goal", initialState, "test-1");

        // 目标应通过替代路径达成
        assertTrue(report.achieved(), "目标应通过替代路径达成");
        assertEquals("GOAL_ACHIEVED", report.exitReason());
        assertTrue(report.failedActions().contains("gather"), "gather 应在失败集合中");
        assertEquals(1, report.failedActions().size());
        assertEquals(4, report.steps(), "gather失败(1步) + gather-alt + analyze + report = 4步");
        assertEquals(3, report.successSteps(), "3 步成功");
        assertEquals(1, report.failedSteps(), "1 步失败");
    }

    @Test
    @DisplayName("所有路径均失败：planner 无法找到任何可行路径时返回 NO_PLAN")
    void testAllPathsFail() throws Exception {
        // 动作只能满足目标的2/3条件，planner 从一开始就找不到完整路径
        List<GoapActionDef> actions = List.of(
                GoapActionDef.builder()
                        .name("step-a")
                        .preconditions(new String[]{})
                        .effects(new String[]{"aDone: true"})
                        .cost(1.0)
                        .build(),
                GoapActionDef.builder()
                        .name("step-b")
                        .preconditions(new String[]{})
                        .effects(new String[]{"bDone: true"})
                        .cost(1.0)
                        .build()
        );

        GoapGoalDef goal = GoapGoalDef.builder()
                .name("fail-goal")
                .conditions(new String[]{"aDone: true", "bDone: true", "cDone: true"})
                .priority(10)
                .build();

        when(actionPool.getAll()).thenReturn(actions);
        when(goalPool.get("fail-goal")).thenReturn(goal);

        OverAllState initialState = new OverAllState(new HashMap<>());
        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "fail-goal", initialState, "test-2");

        assertFalse(report.achieved(), "目标不可能达成");
        assertEquals("NO_PLAN", report.exitReason());
        assertEquals(0, report.steps(), "planner 一开始就无法规划，0步执行");
        assertTrue(report.failedActions().isEmpty(), "无动作被尝试");
        assertEquals(1, report.traces().size(), "只有1条 NO_PLAN 轨迹");
        assertEquals("NO_PLAN", report.traces().get(0).status());
    }

    @Test
    @DisplayName("失败排除后重规划：动作失败后从可用集合排除，planner 走替代路径")
    void testFailedActionExcludedFromReplan() throws Exception {
        // 两条路径到达目标：gather→analyze→report 或 gather-alt→analyze→report
        // gather 失败后排除，planner 自动切换到 gather-alt 路径
        List<GoapActionDef> actions = createActionsWithAlternative();
        GoapGoalDef goal = createGoal();

        when(actionPool.getAll()).thenReturn(actions);
        when(goalPool.get("test-goal")).thenReturn(goal);

        Map<String, CompiledGraph> compiledMap = setupMockGraphChain(actions);

        // gather 失败
        when(graphEngine.invoke(eq(compiledMap.get("gather")), any(OverAllState.class), anyString(), isNull()))
                .thenThrow(new RuntimeException("gather 节点不可用"));

        // 其他动作正常
        when(graphEngine.invoke(eq(compiledMap.get("gather-alt")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));
        when(graphEngine.invoke(eq(compiledMap.get("analyze")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));
        when(graphEngine.invoke(eq(compiledMap.get("report")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));

        OverAllState initialState = new OverAllState(new HashMap<>());
        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "test-goal", initialState, "test-3");

        assertTrue(report.achieved());
        assertEquals("GOAL_ACHIEVED", report.exitReason());
        assertTrue(report.failedActions().contains("gather"));
        assertEquals(1, report.failedActions().size());

        // gather失败(1) + gather-alt(1) + analyze(1) + report(1) = 4步
        assertEquals(4, report.steps());
        assertEquals(3, report.successSteps());
        assertEquals(1, report.failedSteps());

        // 验证轨迹顺序：先失败，后成功
        assertEquals("FAILED", report.traces().get(0).status());
        assertEquals("gather", report.traces().get(0).actionName());
        assertEquals("SUCCESS", report.traces().get(1).status());
        assertEquals("gather-alt", report.traces().get(1).actionName());
    }

    @Test
    @DisplayName("正常执行：ExecuteReport 包含完整的执行轨迹")
    void testExecuteReportSuccess() throws Exception {
        List<GoapActionDef> actions = createStandardActions();
        GoapGoalDef goal = createGoal();

        when(actionPool.getAll()).thenReturn(actions);
        when(goalPool.get("test-goal")).thenReturn(goal);

        setupMockGraphChain(actions);

        // 所有动作正常返回
        when(graphEngine.invoke(any(CompiledGraph.class), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));

        OverAllState initialState = new OverAllState(new HashMap<>());
        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "test-goal", initialState, "test-4");

        // 验证基本信息
        assertEquals("test-goal", report.goalName());
        assertTrue(report.achieved());
        assertEquals("GOAL_ACHIEVED", report.exitReason());
        assertEquals(3, report.steps(), "3个动作各执行1步");
        assertEquals(3, report.successSteps());
        assertEquals(0, report.failedSteps());
        assertTrue(report.failedActions().isEmpty());

        // 验证轨迹完整性和顺序
        assertEquals(3, report.traces().size());
        assertEquals("gather", report.traces().get(0).actionName());
        assertEquals("SUCCESS", report.traces().get(0).status());
        assertEquals(0, report.traces().get(0).stepIndex());

        assertEquals("analyze", report.traces().get(1).actionName());
        assertEquals("SUCCESS", report.traces().get(1).status());
        assertEquals(1, report.traces().get(1).stepIndex());

        assertEquals("report", report.traces().get(2).actionName());
        assertEquals("SUCCESS", report.traces().get(2).status());
        assertEquals(2, report.traces().get(2).stepIndex());
    }

    @Test
    @DisplayName("最大步数限制：超出后返回 MAX_STEPS 退出原因")
    void testMaxStepsReached() throws Exception {
        List<GoapActionDef> actions = createStandardActions();
        GoapGoalDef goal = createGoal();

        when(actionPool.getAll()).thenReturn(actions);
        when(goalPool.get("test-goal")).thenReturn(goal);

        setupMockGraphChain(actions);

        // 所有动作正常执行，但设置 maxSteps=1，只允许执行1步
        when(graphEngine.invoke(any(CompiledGraph.class), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));

        OverAllState initialState = new OverAllState(new HashMap<>());

        // 最多执行 1 步（完整路径需要 3 步）
        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "test-goal", initialState, "test-5", 1);

        assertFalse(report.achieved(), "1步不足以完成3步路径");
        assertEquals("MAX_STEPS", report.exitReason());
        assertEquals(1, report.steps());
        assertEquals(1, report.successSteps());
        assertTrue(report.failedActions().isEmpty(), "没有失败动作，只是步数不够");

        // 只执行了 gather 这 1 步
        assertEquals(1, report.traces().size());
        assertEquals("gather", report.traces().get(0).actionName());
    }

    @Test
    @DisplayName("目标已达成：初始状态已满足目标条件时直接返回")
    void testGoalAlreadyAchieved() throws Exception {
        GoapGoalDef goal = createGoal();

        when(goalPool.get("test-goal")).thenReturn(goal);

        // 初始状态已包含目标条件
        Map<String, Object> initData = new HashMap<>();
        initData.put("reportGenerated", "true");
        OverAllState initialState = new OverAllState(initData);

        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "test-goal", initialState, "test-6");

        assertTrue(report.achieved(), "目标条件已满足");
        assertEquals("GOAL_ACHIEVED", report.exitReason());
        assertEquals(0, report.steps(), "无需执行任何步骤");
        assertTrue(report.traces().isEmpty(), "无执行轨迹");
        assertTrue(report.failedActions().isEmpty());
    }

    @Test
    @DisplayName("部分失败后重规划成功：中间动作失败，后续动作仍能完成")
    void testPartialFailureThenSuccess() throws Exception {
        // 构建有两层替代路径的动作集合
        List<GoapActionDef> actions = List.of(
                GoapActionDef.builder()
                        .name("step-a")
                        .preconditions(new String[]{})
                        .effects(new String[]{"aDone: true"})
                        .cost(1.0)
                        .build(),
                GoapActionDef.builder()
                        .name("step-a-alt")
                        .preconditions(new String[]{})
                        .effects(new String[]{"aDone: true"})
                        .cost(3.0)
                        .build(),
                GoapActionDef.builder()
                        .name("step-b")
                        .preconditions(new String[]{"aDone: true"})
                        .effects(new String[]{"bDone: true"})
                        .cost(1.0)
                        .build()
        );

        GoapGoalDef goal = GoapGoalDef.builder()
                .name("partial-goal")
                .conditions(new String[]{"bDone: true"})
                .priority(5)
                .build();

        when(actionPool.getAll()).thenReturn(actions);
        when(goalPool.get("partial-goal")).thenReturn(goal);

        Map<String, CompiledGraph> compiledMap = setupMockGraphChain(actions);

        // step-a 失败，step-a-alt 和 step-b 成功
        when(graphEngine.invoke(eq(compiledMap.get("step-a")), any(OverAllState.class), anyString(), isNull()))
                .thenThrow(new RuntimeException("step-a 执行异常"));
        when(graphEngine.invoke(eq(compiledMap.get("step-a-alt")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));
        when(graphEngine.invoke(eq(compiledMap.get("step-b")), any(OverAllState.class), anyString(), isNull()))
                .thenReturn(new OverAllState(Map.of()));

        OverAllState initialState = new OverAllState(new HashMap<>());
        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "partial-goal", initialState, "test-7");

        assertTrue(report.achieved());
        assertEquals("GOAL_ACHIEVED", report.exitReason());
        assertTrue(report.failedActions().contains("step-a"));
        assertEquals(1, report.failedActions().size());

        // step-a失败(1步) + step-a-alt(1步) + step-b(1步) = 3步
        assertEquals(3, report.steps());
        assertEquals(2, report.successSteps());
        assertEquals(1, report.failedSteps());
    }

    // ==================== ExecuteReport 和 StepTrace 单元测试 ====================

    @Test
    @DisplayName("ExecuteReport record 统计方法验证")
    void testExecuteReportRecordMethods() {
        List<GoapOrchestrator.StepTrace> traces = List.of(
                new GoapOrchestrator.StepTrace(0, "action-1", "SUCCESS", null),
                new GoapOrchestrator.StepTrace(1, "action-2", "FAILED", "执行超时"),
                new GoapOrchestrator.StepTrace(2, "action-3", "SUCCESS", null),
                new GoapOrchestrator.StepTrace(3, "action-4", "STALE_STATE", "状态未变化"),
                new GoapOrchestrator.StepTrace(4, "action-5", "SUCCESS", null)
        );

        GoapOrchestrator.ExecuteReport report = new GoapOrchestrator.ExecuteReport(
                "test-goal", true, null, 5, traces,
                Set.of("action-2", "action-4"), "GOAL_ACHIEVED");

        assertEquals(3, report.successSteps());
        assertEquals(2, report.failedSteps());
        assertEquals("test-goal", report.goalName());
        assertTrue(report.achieved());
        assertEquals(2, report.failedActions().size());
    }

    @Test
    @DisplayName("StepTrace record 字段验证")
    void testStepTraceRecord() {
        GoapOrchestrator.StepTrace trace = new GoapOrchestrator.StepTrace(
                2, "search-dishes", "SUCCESS", "搜索到5道菜品");

        assertEquals(2, trace.stepIndex());
        assertEquals("search-dishes", trace.actionName());
        assertEquals("SUCCESS", trace.status());
        assertEquals("搜索到5道菜品", trace.message());
    }
}
