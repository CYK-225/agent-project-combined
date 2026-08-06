package org.example.agentEmbabel;

import org.example.agentEmbabel.core.GoapPlanner;
import org.example.agentEmbabel.model.GoapActionDef;
import org.example.agentEmbabel.model.GoapGoalDef;
import org.example.agentEmbabel.model.WorldState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GoapPlanner A* 算法单元测试。
 * 使用菜单规划场景验证规划器的正确性。
 */
class GoapPlannerTest {

    private GoapPlanner planner;
    private List<GoapActionDef> menuPlanningActions;

    @BeforeEach
    void setUp() {
        planner = new GoapPlanner();

        // 构建菜单规划场景的动作集合
        menuPlanningActions = List.of(
                GoapActionDef.builder()
                        .name("gather-requirements")
                        .description("收集需求")
                        .preconditions(new String[]{})
                        .effects(new String[]{"requirementsGathered: true"})
                        .cost(1.0)
                        .build(),

                GoapActionDef.builder()
                        .name("analyze-preferences")
                        .description("分析偏好")
                        .preconditions(new String[]{"requirementsGathered: true"})
                        .effects(new String[]{"preferencesAnalyzed: true"})
                        .cost(1.5)
                        .build(),

                GoapActionDef.builder()
                        .name("search-dishes")
                        .description("搜索菜品")
                        .preconditions(new String[]{"preferencesAnalyzed: true"})
                        .effects(new String[]{"dishesSearched: true"})
                        .cost(2.0)
                        .build(),

                GoapActionDef.builder()
                        .name("select-menu")
                        .description("筛选菜品")
                        .preconditions(new String[]{"dishesSearched: true"})
                        .effects(new String[]{"menuSelected: true"})
                        .cost(1.5)
                        .build(),

                GoapActionDef.builder()
                        .name("generate-report")
                        .description("生成报告")
                        .preconditions(new String[]{"menuSelected: true"})
                        .effects(new String[]{"reportGenerated: true"})
                        .cost(1.0)
                        .build()
        );
    }

    @Test
    @DisplayName("完整路径：从空状态规划到报告生成")
    void testPlanFullPath() {
        WorldState initialState = new WorldState();
        GoapGoalDef goal = GoapGoalDef.builder()
                .name("menu-planning")
                .conditions(new String[]{"reportGenerated: true"})
                .priority(10)
                .build();

        List<GoapActionDef> plan = planner.plan(initialState, goal, menuPlanningActions);

        // 应该规划出完整的 5 步路径
        assertEquals(5, plan.size());
        assertEquals("gather-requirements", plan.get(0).getName());
        assertEquals("analyze-preferences", plan.get(1).getName());
        assertEquals("search-dishes", plan.get(2).getName());
        assertEquals("select-menu", plan.get(3).getName());
        assertEquals("generate-report", plan.get(4).getName());
    }

    @Test
    @DisplayName("短路径：从空状态规划到菜品搜索")
    void testPlanShortPath() {
        WorldState initialState = new WorldState();
        GoapGoalDef goal = GoapGoalDef.builder()
                .name("dishes-ready")
                .conditions(new String[]{"dishesSearched: true"})
                .priority(5)
                .build();

        List<GoapActionDef> plan = planner.plan(initialState, goal, menuPlanningActions);

        // 应该规划出 3 步路径（不需要筛选和报告）
        assertEquals(3, plan.size());
        assertEquals("gather-requirements", plan.get(0).getName());
        assertEquals("analyze-preferences", plan.get(1).getName());
        assertEquals("search-dishes", plan.get(2).getName());
    }

    @Test
    @DisplayName("部分完成：从中间状态开始规划")
    void testPlanFromPartialState() {
        // 假设已经完成了需求收集
        WorldState partialState = new WorldState();
        partialState.setFact("requirementsGathered", "true");

        GoapGoalDef goal = GoapGoalDef.builder()
                .name("menu-planning")
                .conditions(new String[]{"reportGenerated: true"})
                .priority(10)
                .build();

        List<GoapActionDef> plan = planner.plan(partialState, goal, menuPlanningActions);

        // 应该规划出 4 步路径（跳过需求收集）
        assertEquals(4, plan.size());
        assertEquals("analyze-preferences", plan.get(0).getName());
        assertEquals("search-dishes", plan.get(1).getName());
        assertEquals("select-menu", plan.get(2).getName());
        assertEquals("generate-report", plan.get(3).getName());
    }

    @Test
    @DisplayName("目标已达成：返回空规划")
    void testPlanGoalAlreadyAchieved() {
        WorldState completedState = new WorldState();
        completedState.setFact("reportGenerated", "true");

        GoapGoalDef goal = GoapGoalDef.builder()
                .name("menu-planning")
                .conditions(new String[]{"reportGenerated: true"})
                .priority(10)
                .build();

        List<GoapActionDef> plan = planner.plan(completedState, goal, menuPlanningActions);

        // 目标已达成，返回空规划
        assertTrue(plan.isEmpty());
    }

    @Test
    @DisplayName("无法到达：前置条件不满足时返回空规划")
    void testPlanUnreachableGoal() {
        // 只有"搜索菜品"这一个动作，但它的前置条件无法满足
        List<GoapActionDef> limitedActions = List.of(
                GoapActionDef.builder()
                        .name("search-dishes")
                        .description("搜索菜品")
                        .preconditions(new String[]{"preferencesAnalyzed: true"})
                        .effects(new String[]{"dishesSearched: true"})
                        .cost(2.0)
                        .build()
        );

        WorldState initialState = new WorldState();
        GoapGoalDef goal = GoapGoalDef.builder()
                .name("dishes-ready")
                .conditions(new String[]{"dishesSearched: true"})
                .priority(5)
                .build();

        List<GoapActionDef> plan = planner.plan(initialState, goal, limitedActions);

        // 无法到达目标，返回空规划
        assertTrue(plan.isEmpty());
    }

    @Test
    @DisplayName("代价最优：A* 应选择代价最低的路径")
    void testPlanOptimalCost() {
        // 添加一个高代价的捷径动作（跳过中间步骤）
        List<GoapActionDef> actionsWithShortcut = List.of(
                GoapActionDef.builder()
                        .name("quick-search")
                        .description("快速搜索（跳过分析）")
                        .preconditions(new String[]{"requirementsGathered: true"})
                        .effects(new String[]{"dishesSearched: true"})
                        .cost(10.0)  // 高代价
                        .build(),

                // 正常路径
                GoapActionDef.builder()
                        .name("gather-requirements")
                        .preconditions(new String[]{})
                        .effects(new String[]{"requirementsGathered: true"})
                        .cost(1.0)
                        .build(),
                GoapActionDef.builder()
                        .name("analyze-preferences")
                        .preconditions(new String[]{"requirementsGathered: true"})
                        .effects(new String[]{"preferencesAnalyzed: true"})
                        .cost(1.5)
                        .build(),
                GoapActionDef.builder()
                        .name("search-dishes")
                        .preconditions(new String[]{"preferencesAnalyzed: true"})
                        .effects(new String[]{"dishesSearched: true"})
                        .cost(2.0)
                        .build()
        );

        WorldState initialState = new WorldState();
        GoapGoalDef goal = GoapGoalDef.builder()
                .name("dishes-ready")
                .conditions(new String[]{"dishesSearched: true"})
                .priority(5)
                .build();

        List<GoapActionDef> plan = planner.plan(initialState, goal, actionsWithShortcut);

        // A* 应该选择正常路径（总代价 4.5）而非捷径（总代价 11.0）
        assertEquals(3, plan.size());
        assertEquals("gather-requirements", plan.get(0).getName());
        assertEquals("analyze-preferences", plan.get(1).getName());
        assertEquals("search-dishes", plan.get(2).getName());

        // 验证总代价
        double totalCost = plan.stream().mapToDouble(GoapActionDef::getCost).sum();
        assertEquals(4.5, totalCost, 0.001);
    }

    @Test
    @DisplayName("WorldState 满足条件判断")
    void testWorldStateSatisfies() {
        WorldState state = new WorldState();
        state.setFact("hasIngredients", "true");
        state.setFact("mealReady", "false");

        assertTrue(state.satisfies(new String[]{"hasIngredients: true"}));
        assertFalse(state.satisfies(new String[]{"mealReady: true"}));
        assertTrue(state.satisfies(new String[]{"hasIngredients: true", "mealReady: false"}));
        assertTrue(state.satisfies(new String[]{}));
    }

    @Test
    @DisplayName("WorldState 应用效果")
    void testWorldStateApplyEffects() {
        WorldState state = new WorldState();
        state.setFact("hasIngredients", "true");

        WorldState newState = state.applyEffects(new String[]{"mealReady: true", "served: true"});

        // 原状态不变
        assertFalse(state.hasFact("mealReady"));
        // 新状态包含效果
        assertEquals("true", newState.getFact("mealReady"));
        assertEquals("true", newState.getFact("served"));
        // 新状态保留原有事实
        assertEquals("true", newState.getFact("hasIngredients"));
    }

    @Test
    @DisplayName("WorldState 启发式估计")
    void testWorldStateHeuristic() {
        WorldState state = new WorldState();
        state.setFact("requirementsGathered", "true");

        String[] goalConditions = new String[]{
                "requirementsGathered: true",
                "preferencesAnalyzed: true",
                "dishesSearched: true",
                "menuSelected: true",
                "reportGenerated: true"
        };

        // 1 个已满足，4 个未满足
        assertEquals(4, state.heuristic(goalConditions));
    }
}
