package org.example.agentEmbabel.example.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentEmbabel.core.GoapOrchestrator;
import org.example.agentEmbabel.model.GoapActionDef;
import org.example.agentEmbabel.pool.GoapActionPool;
import org.example.agentEmbabel.pool.GoapGoalPool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * GOAP 菜单规划示例控制器。
 * 演示 AgentEmbabel 模块如何通过 GOAP 动态规划完成智能菜单推荐。
 *
 * <p>API 端点：
 * <ul>
 *   <li>POST /api/goap/menu-plan — 同步执行菜单规划（完整流程）</li>
 *   <li>POST /api/goap/dishes-ready — 执行到菜品搜索就绪（短路径）</li>
 *   <li>POST /api/goap/step — 单步调试</li>
 *   <li>GET /api/goap/demo — 一键示例</li>
 *   <li>GET /api/goap/debug — 查看已注册动作和目标</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/goap")
@RequiredArgsConstructor
public class GoapMenuPlanController {

    private final GoapOrchestrator orchestrator;
    private final GoapActionPool actionPool;
    private final GoapGoalPool goalPool;

    // ==================== 同步执行端点 ====================

    /**
     * 同步执行完整菜单规划。
     * GOAP 会自动规划 5 步：收集需求 → 分析偏好 → 搜索菜品 → 筛选菜品 → 生成报告
     */
    @PostMapping("/menu-plan")
    public ResponseEntity<Map<String, Object>> planMenu(@RequestBody MenuPlanRequest request) {
        log.info("[GOAP-API] 菜单规划请求: company={}, headcount={}, budget={}",
                request.getCompany(), request.getHeadcount(), request.getBudget());

        // 构建初始状态
        Map<String, Object> initData = new HashMap<>();
        initData.put("userQuery", request.getUserQuery());
        initData.put("company", request.getCompany());
        initData.put("headcount", request.getHeadcount() != 0 ? request.getHeadcount() : 50);
        initData.put("budget", request.getBudget() != 0 ? request.getBudget() : 15.0);
        OverAllState initialState = new OverAllState(initData);

        String threadId = "goap-menu-" + System.currentTimeMillis();

        // 执行 GOAP
        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "goap-goal-menu-planning", initialState, threadId);
        OverAllState result = report.finalState();

        // 提取结果
        String menuReport = (String) result.value("menuReport").orElse("(未生成报告)");
        int menuSize = (int) result.value("menuSize").orElse(0);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", report.achieved());
        response.put("threadId", threadId);
        response.put("menuSize", menuSize);
        response.put("menuReport", menuReport);
        response.put("steps", report.steps());
        response.put("exitReason", report.exitReason());
        response.put("traces", report.traces().stream()
                .map(t -> Map.of(
                        "step", t.stepIndex(),
                        "action", t.actionName() != null ? t.actionName() : "",
                        "status", t.status(),
                        "message", t.message() != null ? t.message() : ""
                )).toList());
        response.put("finalState", result.data());

        return ResponseEntity.ok(response);
    }

    /**
     * 执行到菜品搜索就绪（短路径）。
     * GOAP 会自动规划 3 步：收集需求 → 分析偏好 → 搜索菜品
     */
    @PostMapping("/dishes-ready")
    public ResponseEntity<Map<String, Object>> dishesReady(@RequestBody MenuPlanRequest request) {
        log.info("[GOAP-API] 菜品就绪请求: company={}", request.getCompany());

        Map<String, Object> initData = new HashMap<>();
        initData.put("userQuery", request.getUserQuery());
        initData.put("company", request.getCompany());
        initData.put("headcount", request.getHeadcount() != 0 ? request.getHeadcount() : 50);
        initData.put("budget", request.getBudget() != 0 ? request.getBudget() : 15.0);
        OverAllState initialState = new OverAllState(initData);

        String threadId = "goap-dishes-" + System.currentTimeMillis();

        GoapOrchestrator.ExecuteReport dishesReport = orchestrator.execute(
                "goap-goal-dishes-ready", initialState, threadId);
        OverAllState dishesResult = dishesReport.finalState();

        int candidateCount = (int) dishesResult.value("candidateCount").orElse(0);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", dishesReport.achieved());
        response.put("threadId", threadId);
        response.put("candidateCount", candidateCount);
        response.put("steps", dishesReport.steps());
        response.put("exitReason", dishesReport.exitReason());
        response.put("traces", dishesReport.traces().stream()
                .map(t -> Map.of(
                        "step", t.stepIndex(),
                        "action", t.actionName() != null ? t.actionName() : "",
                        "status", t.status(),
                        "message", t.message() != null ? t.message() : ""
                )).toList());
        response.put("finalState", dishesResult.data());

        return ResponseEntity.ok(response);
    }

    // ==================== 单步调试端点 ====================

    /**
     * 单步执行（用于调试 GOAP 规划过程）。
     * 每次调用只规划并执行一个动作。
     */
    @PostMapping("/step")
    public ResponseEntity<Map<String, Object>> executeStep(@RequestBody StepRequest request) {
        log.info("[GOAP-API] 单步执行: goal={}, state={}", request.getGoalName(), request.getCurrentState());

        Map<String, Object> data = new HashMap<>();
        if (request.getCurrentState() != null) {
            data.putAll(request.getCurrentState());
        }
        OverAllState currentState = new OverAllState(data);

        String threadId = "goap-step-" + System.currentTimeMillis();

        GoapOrchestrator.StepResult result = orchestrator.executeStep(
                request.getGoalName(), currentState, threadId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("goalAchieved", result.isGoalAchieved());
        response.put("actionExecuted", result.getActionExecuted() != null ?
                Map.of(
                        "name", result.getActionExecuted().getName(),
                        "description", result.getActionExecuted().getDescription(),
                        "cost", result.getActionExecuted().getCost()
                ) : null);
        response.put("newState", result.getState().data());
        if (result.getError() != null) {
            response.put("error", result.getError());
        }

        return ResponseEntity.ok(response);
    }

    // ==================== 一键示例端点 ====================

    /**
     * 一键演示：使用默认参数执行完整菜单规划。
     */
    @GetMapping("/demo")
    public ResponseEntity<Map<String, Object>> demo() {
        log.info("[GOAP-API] 一键演示");

        Map<String, Object> initData = new HashMap<>();
        initData.put("userQuery", "想要辣的菜，预算充足");
        initData.put("company", "悠饭总部");
        initData.put("headcount", 100);
        initData.put("budget", 20.0);
        OverAllState initialState = new OverAllState(initData);

        String threadId = "goap-demo-" + System.currentTimeMillis();

        GoapOrchestrator.ExecuteReport demoReport = orchestrator.execute(
                "goap-goal-menu-planning", initialState, threadId);
        OverAllState demoResult = demoReport.finalState();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", demoReport.achieved());
        response.put("threadId", threadId);
        response.put("demo", "悠饭总部100人菜单规划");
        response.put("menuReport", demoResult.value("menuReport").orElse("(未生成)"));
        response.put("menuSize", demoResult.value("menuSize").orElse(0));
        response.put("totalCost", demoResult.value("totalCost").orElse(0.0));
        response.put("steps", demoReport.steps());
        response.put("exitReason", demoReport.exitReason());
        response.put("failedActions", demoReport.failedActions());

        return ResponseEntity.ok(response);
    }

    /**
     * 一键演示：短路径（仅到菜品搜索）。
     */
    @GetMapping("/demo-short")
    public ResponseEntity<Map<String, Object>> demoShort() {
        Map<String, Object> initData = new HashMap<>();
        initData.put("userQuery", "清淡饮食");
        initData.put("company", "测试公司");
        initData.put("headcount", 30);
        initData.put("budget", 12.0);
        OverAllState initialState = new OverAllState(initData);

        String threadId = "goap-demo-short-" + System.currentTimeMillis();

        GoapOrchestrator.ExecuteReport report = orchestrator.execute(
                "goap-goal-dishes-ready", initialState, threadId);
        OverAllState result = report.finalState();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", report.achieved());
        response.put("threadId", threadId);
        response.put("demo", "短路径演示（仅搜索菜品）");
        response.put("candidateCount", result.value("candidateCount").orElse(0));
        response.put("spiceLevel", result.value("spiceLevel").orElse(0));
        response.put("steps", report.steps());
        response.put("exitReason", report.exitReason());

        return ResponseEntity.ok(response);
    }

    // ==================== 诊断端点 ====================

    /**
     * 查看已注册的 GOAP 动作和目标。
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug() {
        List<Map<String, Object>> actions = new ArrayList<>();
        for (GoapActionDef action : actionPool.getAll()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", action.getName());
            info.put("description", action.getDescription());
            info.put("preconditions", action.getPreconditions());
            info.put("effects", action.getEffects());
            info.put("cost", action.getCost());
            info.put("nodeActionName", action.getNodeActionName());
            actions.add(info);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("registeredActions", actions);
        response.put("registeredGoals", goalPool.getAll().stream()
                .map(g -> Map.of(
                        "name", g.getName(),
                        "description", g.getDescription(),
                        "conditions", g.getConditions(),
                        "priority", g.getPriority()
                )).toList());

        return ResponseEntity.ok(response);
    }

    // ==================== 请求 DTO ====================

    @lombok.Data
    public static class MenuPlanRequest {
        private String userQuery = "需要辣的菜";
        private String company = "悠饭总部";
        private int headcount = 50;
        private double budget = 15.0;
    }

    @lombok.Data
    public static class StepRequest {
        private String goalName = "goap-goal-menu-planning";
        private Map<String, Object> currentState;
    }
}
