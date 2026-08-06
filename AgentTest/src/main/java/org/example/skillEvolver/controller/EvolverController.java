package org.example.skillEvolver.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.core.GraphPoolManager;
import org.example.skillEvolver.entity.EvolverSkillVersionEntity;
import org.example.skillEvolver.entity.EvolverTaskEntity;
import org.example.skillEvolver.mapper.EvolverSkillVersionMapper;
import org.example.skillEvolver.service.EvolverTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.example.skillEvolver.entity.table.EvolverTaskEntityTableDef.EVOLVER_TASK_ENTITY;

/**
 * SkillEvolver REST API。
 * <p>
 * 提供：
 * <ul>
 *   <li>POST /api/evolver/submit — 提交进化任务，触发 SkillEvolverLoop 图</li>
 *   <li>GET  /api/evolver/task/{taskId} — 查询任务状态和结果</li>
 *   <li>GET  /api/evolver/task/{taskId}/versions — 查询 skill 版本链</li>
 *   <li>GET  /api/evolver/tasks — 列出所有任务</li>
 * </ul>
 *
 * @author zhilin
 */
@Slf4j
@RestController
@RequestMapping("/api/evolver")
public class EvolverController {

    private final EvolverTaskService taskService;
    private final EvolverSkillVersionMapper versionMapper;
    private final GraphPoolManager graphPoolManager;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public EvolverController(EvolverTaskService taskService,
                             EvolverSkillVersionMapper versionMapper,
                             GraphPoolManager graphPoolManager) {
        this.taskService = taskService;
        this.versionMapper = versionMapper;
        this.graphPoolManager = graphPoolManager;
    }

    // ======================== DTO ========================

    @Data
    public static class SubmitRequest {
        /** 任务名称（人类可读，如 "sales-pivot-analysis"） */
        private String taskName;
        /** 任务指令（Agent 要完成的目标描述） */
        @JsonAlias({"taskInstruction"})
        private String instruction = "";
        /** 任务输入数据（JSON 格式：文件路径或内联数据） */
        private String taskData;
        /** 验证规则（JSON 格式：文件存在性、关键词匹配、exit code 等） */
        private String verifier;
        /** 奖励信号模式：discrete / continuous */
        private String rewardMode = "discrete";
        /** 总迭代轮数 R（默认 2） */
        private Integer maxIterations = 2;
        /** 每轮探索 trial 数 K（默认 4） */
        private Integer nExploration = 4;
        /** 最终验证 trial 数（默认 5） */
        private Integer nValidation = 5;
    }

    @Data
    public static class TaskResponse {
        private String taskId;
        private String taskName;
        private String status;
        private String instruction;
        private Integer currentIteration;
        private Integer maxIterations;
        private String bestSkillVersion;
        private Double bestReward;
        private Double validationPassRate;
        private String bestSkillContent;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class VersionResponse {
        private String versionId;
        private String taskId;
        private Integer iteration;
        private String versionLabel;
        private Integer variantIndex;
        private String skillMarkdown;
        private Double passRate;
        private Double meanReward;
        private String analysis;
        private Boolean isBest;
        private LocalDateTime createdAt;
    }

    // ======================== API ========================

    /**
     * 提交 Skill 进化任务，触发 SkillEvolverLoop 图
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SubmitRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String threadId = "evolver-" + taskId;

        // 创建任务记录
        EvolverTaskEntity task = EvolverTaskEntity.builder()
                .taskId(taskId)
                .taskName(request.getTaskName())
                .instruction(request.getInstruction())
                .taskData(request.getTaskData())
                .verifier(request.getVerifier())
                .rewardMode(request.getRewardMode())
                .maxIterations(request.getMaxIterations())
                .nExploration(request.getNExploration())
                .nValidation(request.getNValidation())
                .currentIteration(0)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        taskService.save(task);

        // 异步触发 Graph
        CompletableFuture.runAsync(() -> {
            try {
                taskService.updateStatus(taskId, "RUNNING");
                log.info("[EvolverController] 触发 SkillEvolverLoop Graph，taskId={}", taskId);

                Map<String, Object> initMap = new HashMap<>();
                initMap.put("taskId", taskId);
                initMap.put("taskInstruction", request.getInstruction() != null ? request.getInstruction() : "");
                initMap.put("taskData", request.getTaskData() != null ? request.getTaskData() : "");
                initMap.put("currentIteration", 0);
                initMap.put("maxIterations", request.getMaxIterations() != null ? request.getMaxIterations() : 2);
                initMap.put("nExploration", request.getNExploration() != null ? request.getNExploration() : 4);
                initMap.put("currentSkill", "");
                initMap.put("strategyVariants", List.of());
                initMap.put("trialResults", List.of());
                initMap.put("analysisReport", "");
                initMap.put("bestReward", 0.0);
                initMap.put("verifier", request.getVerifier() != null ? request.getVerifier() : "");
                OverAllState initialState = new OverAllState(initMap);

                OverAllState result = graphPoolManager.invokeGraph(
                        "SkillEvolverLoop", initialState, threadId);

                log.info("[EvolverController] SkillEvolverLoop Graph 执行完毕，taskId={}", taskId);

                // 从 Graph 结果更新任务
                EvolverTaskEntity t = taskService.getByTaskId(taskId);
                if (t != null && !"FAILED".equals(t.getStatus())) {
                    String finalSkill = (String) result.value("currentSkill").orElse("");
                    String version = (String) result.value("skillVersion").orElse("v-final");
                    Double reward = (Double) result.value("bestReward").orElse(0.0);
                    taskService.updateBestSkill(taskId, version, finalSkill, reward);

                    // 标记完成
                    Double passRate = (Double) result.value("trialPassRate").orElse(0.0);
                    taskService.updateValidationResult(taskId, passRate, 0L);
                }
            } catch (Exception e) {
                log.error("[EvolverController] SkillEvolverLoop Graph 执行失败，taskId={}", taskId, e);
                taskService.markFailed(taskId, "Graph 执行失败: " + e.getMessage());
            }
        }, executor);

        log.info("[EvolverController] 进化任务已创建: taskId={}, instruction={}",
                taskId, request.getInstruction());

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "threadId", threadId,
                "status", "PENDING",
                "message", "进化任务已创建，SkillEvolverLoop 图已触发"
        ));
    }

    /**
     * 查询任务状态和结果
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable String taskId) {
        EvolverTaskEntity task = taskService.getByTaskId(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toTaskResponse(task));
    }

    /**
     * 查询任务的 Skill 版本链
     */
    @GetMapping("/task/{taskId}/versions")
    public ResponseEntity<List<VersionResponse>> getVersions(@PathVariable String taskId) {
        List<EvolverSkillVersionEntity> versions = versionMapper.selectListByQuery(
                QueryWrapper.create().where(
                        EvolverSkillVersionEntity::getTaskId).eq(taskId)
                        .orderBy(EvolverSkillVersionEntity::getIteration, true));

        List<VersionResponse> resp = new ArrayList<>();
        for (EvolverSkillVersionEntity v : versions) {
            resp.add(toVersionResponse(v));
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * 列出所有任务（分页）
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<TaskResponse>> listTasks(
            @RequestParam(defaultValue = "20") int limit) {
        List<EvolverTaskEntity> tasks = taskService.list(
                QueryWrapper.create()
                        .orderBy(EVOLVER_TASK_ENTITY.CREATED_AT, false)
                        .limit(limit));
        List<TaskResponse> resp = new ArrayList<>();
        for (EvolverTaskEntity t : tasks) {
            resp.add(toTaskResponse(t));
        }
        return ResponseEntity.ok(resp);
    }

    // ======================== 转换方法 ========================

    private TaskResponse toTaskResponse(EvolverTaskEntity task) {
        TaskResponse r = new TaskResponse();
        r.setTaskId(task.getTaskId());
        r.setTaskName(task.getTaskName());
        r.setStatus(task.getStatus());
        r.setInstruction(task.getInstruction());
        r.setCurrentIteration(task.getCurrentIteration());
        r.setMaxIterations(task.getMaxIterations());
        r.setBestSkillVersion(task.getBestSkillVersion());
        r.setBestReward(task.getBestReward());
        r.setValidationPassRate(task.getValidationPassRate());
        r.setBestSkillContent(task.getBestSkillContent());
        r.setCreatedAt(task.getCreatedAt());
        r.setUpdatedAt(task.getUpdatedAt());
        return r;
    }

    private VersionResponse toVersionResponse(EvolverSkillVersionEntity v) {
        VersionResponse r = new VersionResponse();
        r.setVersionId(v.getVersionId());
        r.setTaskId(v.getTaskId());
        r.setIteration(v.getIteration());
        r.setVersionLabel(v.getVersionLabel());
        r.setVariantIndex(v.getVariantIndex());
        r.setSkillMarkdown(v.getSkillMarkdown());
        r.setPassRate(v.getPassRate());
        r.setMeanReward(v.getMeanReward());
        r.setAnalysis(v.getAnalysis());
        r.setIsBest(v.getIsBest());
        r.setCreatedAt(v.getCreatedAt());
        return r;
    }

    // ======================== 测试 / 调试端点 ========================

    /**
     * 同步测试 — 阻塞等图跑完再返回，方便调试。
     * <p>
     * 示例请求：
     * <pre>
     * POST /api/evolver/test-sync
     * {
     *   "taskName": "test-echo",
     *   "instruction": "编写一个函数，输入字符串返回反转后的结果",
     *   "maxIterations": 1,
     *   "nExploration": 2
     * }
     * </pre>
     */
    @PostMapping("/test-sync")
    public ResponseEntity<Map<String, Object>> testSync(@RequestBody SubmitRequest request) {
        String taskId = "test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String threadId = "evolver-test-" + taskId;

        try {
            // 1. 创建任务
            String safeInstruction = request.getInstruction() != null && !request.getInstruction().isBlank()
                    ? request.getInstruction() : "(no instruction provided)";
            String safeTaskName = request.getTaskName() != null && !request.getTaskName().isBlank()
                    ? request.getTaskName() : taskId;

            EvolverTaskEntity task = EvolverTaskEntity.builder()
                    .taskId(taskId)
                    .taskName(safeTaskName)
                    .instruction(safeInstruction)
                    .taskData(request.getTaskData() != null ? request.getTaskData() : "")
                    .verifier(request.getVerifier() != null ? request.getVerifier() : "")
                    .rewardMode(request.getRewardMode() != null ? request.getRewardMode() : "discrete")
                    .maxIterations(request.getMaxIterations() != null ? request.getMaxIterations() : 2)
                    .nExploration(request.getNExploration() != null ? request.getNExploration() : 4)
                    .nValidation(request.getNValidation() != null ? request.getNValidation() : 5)
                    .currentIteration(0)
                    .status("RUNNING")
                    .tokenEstimate(0L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            taskService.save(task);

            // 2. 同步触发 Graph
            Map<String, Object> initMap = new HashMap<>();
            initMap.put("taskId", taskId);
            initMap.put("taskInstruction", request.getInstruction() != null ? request.getInstruction() : "");
            initMap.put("taskData", request.getTaskData() != null ? request.getTaskData() : "");
            initMap.put("currentIteration", 0);
            initMap.put("maxIterations", request.getMaxIterations() != null ? request.getMaxIterations() : 2);
            initMap.put("nExploration", request.getNExploration() != null ? request.getNExploration() : 4);
            initMap.put("currentSkill", "");
            initMap.put("strategyVariants", List.of());
            initMap.put("trialResults", List.of());
            initMap.put("analysisReport", "");
            initMap.put("bestReward", 0.0);
            initMap.put("bestSkillContent", "");
            initMap.put("bestSkillVersion", "");
            initMap.put("bestRewardEver", 0.0);
            initMap.put("verifier", request.getVerifier() != null ? request.getVerifier() : "");
            OverAllState initialState = new OverAllState(initMap);

            OverAllState result = graphPoolManager.invokeGraph(
                    "SkillEvolverLoop", initialState, threadId);

            // 3. 回写结果
            EvolverTaskEntity finalTask = taskService.getByTaskId(taskId);
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("taskId", taskId);
            resultMap.put("status", finalTask != null ? finalTask.getStatus() : "UNKNOWN");
            resultMap.put("bestSkillContent", finalTask != null ? finalTask.getBestSkillContent() : null);
            resultMap.put("bestReward", finalTask != null ? finalTask.getBestReward() : null);
            resultMap.put("graphState", result.data());
            return ResponseEntity.ok(resultMap);

        } catch (Throwable t) {
            t.printStackTrace();
            taskService.markFailed(taskId, t.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "taskId", taskId,
                    "error", t.getClass().getName(),
                    "message", String.valueOf(t.getMessage()),
                    "cause", t.getCause() != null
                            ? t.getCause().getClass().getName() + ": " + t.getCause().getMessage()
                            : "null",
                    "trace", Arrays.stream(t.getStackTrace())
                            .limit(15).map(Object::toString).toList()
            ));
        }
    }

    /**
     * 快速提交示例 — 一键创建一个典型进化任务（异步）。
     * <p>
     * 直接 GET 调用即可，无需构造 body。
     * <pre>
     * GET /api/evolver/demo-submit
     * </pre>
     */
    @GetMapping("/demo-submit")
    public ResponseEntity<Map<String, Object>> demoSubmit() {
        SubmitRequest demo = new SubmitRequest();
        demo.setTaskName("demo-echo-function");
        demo.setInstruction("编写一个 Python 函数 echo(text)，返回输入字符串的反转结果。要求：1) 处理空字符串 2) 保留 Unicode 字符 3) 附带单元测试");
        demo.setRewardMode("discrete");
        demo.setMaxIterations(2);
        demo.setNExploration(4);
        demo.setNValidation(5);
        return submit(demo);
    }

    /**
     * 诊断 — 查看已注册的 Graph / NodeAction / EdgeCondition
     * <pre>
     * GET /api/evolver/debug
     * </pre>
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            info.put("registeredGraphs", graphPoolManager.getMetadataRegistry().keySet());
        } catch (Exception e) {
            info.put("graphPoolError", e.getMessage());
        }
        info.put("endpoints", List.of(
                "POST /api/evolver/submit          — 提交进化任务（异步）",
                "GET  /api/evolver/demo-submit      — 一键示例任务",
                "POST /api/evolver/test-sync        — 同步测试（阻塞等结果）",
                "GET  /api/evolver/task/{id}        — 查询任务状态",
                "GET  /api/evolver/task/{id}/versions — 查询版本链",
                "GET  /api/evolver/tasks            — 列出所有任务",
                "GET  /api/evolver/debug            — 本页面"
        ));
        return ResponseEntity.ok(info);
    }
}
