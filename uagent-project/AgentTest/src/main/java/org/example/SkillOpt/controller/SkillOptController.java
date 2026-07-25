package org.example.skillOpt.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.core.GraphPoolManager;
import org.example.skillOpt.dto.TrainingJobRequest;
import org.example.skillOpt.dto.TrainingJobResponse;
import org.example.skillOpt.entity.SkillOptCandidateEntity;
import org.example.skillOpt.entity.SkillOptEpochEntity;
import org.example.skillOpt.entity.SkillOptTrainingJobEntity;
import org.example.skillOpt.entity.SkillOptEditLogEntity;
import org.example.skillOpt.mapper.SkillOptEditLogMapper;
import org.example.skillOpt.service.SkillOptCandidateService;
import org.example.skillOpt.service.SkillOptEpochService;
import org.example.skillOpt.service.SkillOptTrainingJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.example.skillOpt.entity.table.SkillOptTrainingJobEntityTableDef.SKILL_OPT_TRAINING_JOB_ENTITY;

/**
 * SkillOpt REST API。
 * <p>
 * 提供：
 * <ul>
 *   <li>POST /api/skillopt/submit — 提交训练任务（异步）</li>
 *   <li>POST /api/skillopt/test-sync — 同步测试</li>
 *   <li>GET  /api/skillopt/job/{jobId} — 查询任务状态</li>
 *   <li>GET  /api/skillopt/job/{jobId}/epochs — 查询 epoch 链</li>
 *   <li>GET  /api/skillopt/job/{jobId}/candidates — 查询候选 skill</li>
 *   <li>GET  /api/skillopt/jobs — 列出所有任务</li>
 *   <li>GET  /api/skillopt/demo-submit — 一键示例</li>
 *   <li>GET  /api/skillopt/debug — 诊断信息</li>
 * </ul>
 *
 * @author zhilin
 */
@Slf4j
@RestController
@RequestMapping("/api/skillopt")
public class SkillOptController {

    private final SkillOptTrainingJobService jobService;
    private final SkillOptEpochService epochService;
    private final SkillOptCandidateService candidateService;
    private final SkillOptEditLogMapper editLogMapper;
    private final GraphPoolManager graphPoolManager;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SkillOptController(SkillOptTrainingJobService jobService,
                               SkillOptEpochService epochService,
                               SkillOptCandidateService candidateService,
                               SkillOptEditLogMapper editLogMapper,
                               GraphPoolManager graphPoolManager) {
        this.jobService = jobService;
        this.epochService = epochService;
        this.candidateService = candidateService;
        this.editLogMapper = editLogMapper;
        this.graphPoolManager = graphPoolManager;
    }

    // ======================== API ========================

    /**
     * 提交训练任务（异步）
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody TrainingJobRequest request) {
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String threadId = "skillopt-" + jobId;

        // 创建任务记录
        SkillOptTrainingJobEntity job = SkillOptTrainingJobEntity.builder()
                .jobId(jobId)
                .taskDescription(request.getTaskDescription())
                .initialSkill(request.getInitialSkill())
                .trainData(request.getTrainData())
                .valData(request.getValData())
                .envAdapterType(request.getEnvAdapterType())
                .lrSchedulerType(request.getLrSchedulerType())
                .gateType(request.getGateType())
                .maxEpochs(request.getMaxEpochs())
                .batchSize(request.getBatchSize())
                .editBudgetBase(request.getEditBudgetBase())
                .currentEpoch(0)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        jobService.save(job);

        // 异步触发 Graph
        CompletableFuture.runAsync(() -> {
            try {
                jobService.updateStatus(jobId, "RUNNING");
                log.info("[SkillOptController] 触发 SkillOptTrainingLoop Graph, jobId={}", jobId);

                Map<String, Object> initMap = new HashMap<>();
                initMap.put("jobId", jobId);
                OverAllState initialState = new OverAllState(initMap);

                OverAllState result = graphPoolManager.invokeGraph(
                        "SkillOptTrainingLoop", initialState, threadId);

                log.info("[SkillOptController] SkillOptTrainingLoop Graph 执行完毕, jobId={}", jobId);
            } catch (Exception e) {
                log.error("[SkillOptController] Graph 执行失败, jobId={}", jobId, e);
                jobService.markFailed(jobId, "Graph 执行失败: " + e.getMessage());
            }
        }, executor);

        log.info("[SkillOptController] 训练任务已创建: jobId={}", jobId);

        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "threadId", threadId,
                "status", "PENDING",
                "message", "SkillOpt 训练任务已创建，SkillOptTrainingLoop 图已触发"
        ));
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/job/{jobId}")
    public ResponseEntity<TrainingJobResponse> getJob(@PathVariable String jobId) {
        SkillOptTrainingJobEntity job = jobService.getByJobId(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toJobResponse(job));
    }

    /**
     * 查询 epoch 链
     */
    @GetMapping("/job/{jobId}/epochs")
    public ResponseEntity<List<SkillOptEpochEntity>> getEpochs(@PathVariable String jobId) {
        return ResponseEntity.ok(epochService.listByJobId(jobId));
    }

    /**
     * 查询候选 skill
     */
    @GetMapping("/job/{jobId}/candidates")
    public ResponseEntity<List<SkillOptCandidateEntity>> getCandidates(@PathVariable String jobId) {
        return ResponseEntity.ok(candidateService.listByJobId(jobId));
    }

    /**
     * 列出所有任务
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<TrainingJobResponse>> listJobs(
            @RequestParam(defaultValue = "20") int limit) {
        List<SkillOptTrainingJobEntity> jobs = jobService.list(
                QueryWrapper.create()
                        .orderBy(SKILL_OPT_TRAINING_JOB_ENTITY.CREATED_AT, false)
                        .limit(limit));
        List<TrainingJobResponse> resp = new ArrayList<>();
        for (SkillOptTrainingJobEntity j : jobs) {
            resp.add(toJobResponse(j));
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * 同步测试 — 阻塞等图跑完
     */
    @PostMapping("/test-sync")
    public ResponseEntity<Map<String, Object>> testSync(@RequestBody TrainingJobRequest request) {
        String jobId = "test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String threadId = "skillopt-test-" + jobId;

        try {
            SkillOptTrainingJobEntity job = SkillOptTrainingJobEntity.builder()
                    .jobId(jobId)
                    .taskDescription(request.getTaskDescription())
                    .initialSkill(request.getInitialSkill())
                    .trainData(request.getTrainData())
                    .valData(request.getValData())
                    .envAdapterType(request.getEnvAdapterType())
                    .lrSchedulerType(request.getLrSchedulerType())
                    .gateType(request.getGateType())
                    .maxEpochs(request.getMaxEpochs())
                    .batchSize(request.getBatchSize())
                    .editBudgetBase(request.getEditBudgetBase())
                    .currentEpoch(0)
                    .status("RUNNING")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            jobService.save(job);

            Map<String, Object> initMap = new HashMap<>();
            initMap.put("jobId", jobId);
            OverAllState initialState = new OverAllState(initMap);

            OverAllState result = graphPoolManager.invokeGraph(
                    "SkillOptTrainingLoop", initialState, threadId);

            SkillOptTrainingJobEntity finalJob = jobService.getByJobId(jobId);
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("jobId", jobId);
            resultMap.put("status", finalJob != null ? finalJob.getStatus() : "UNKNOWN");
            resultMap.put("bestSkillContent", finalJob != null ? finalJob.getBestSkillContent() : null);
            resultMap.put("bestValidationScore", finalJob != null ? finalJob.getBestValidationScore() : null);
            resultMap.put("graphState", result.data());
            return ResponseEntity.ok(resultMap);

        } catch (Throwable t) {
            t.printStackTrace();
            jobService.markFailed(jobId, t.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "jobId", jobId,
                    "error", t.getClass().getName(),
                    "message", String.valueOf(t.getMessage()),
                    "trace", Arrays.stream(t.getStackTrace())
                            .limit(15).map(Object::toString).toList()
            ));
        }
    }

    /**
     * 一键示例任务
     */
    @GetMapping("/demo-submit")
    public ResponseEntity<Map<String, Object>> demoSubmit() {
        TrainingJobRequest demo = new TrainingJobRequest();
        demo.setTaskDescription("Answer factual questions about world knowledge accurately");
        demo.setTrainData("[{\"question\":\"What is the capital of France?\",\"answer\":\"Paris\"}," +
                "{\"question\":\"What is 2+2?\",\"answer\":\"4\"}," +
                "{\"question\":\"Who wrote Romeo and Juliet?\",\"answer\":\"Shakespeare\"}," +
                "{\"question\":\"What planet is closest to the sun?\",\"answer\":\"Mercury\"}]");
        demo.setValData("[{\"question\":\"What is the largest ocean?\",\"answer\":\"Pacific\"}," +
                "{\"question\":\"What color is the sky on a clear day?\",\"answer\":\"Blue\"}]");
        demo.setMaxEpochs(2);
        demo.setBatchSize(4);
        demo.setEditBudgetBase(3);
        demo.setLrSchedulerType("cosine");
        demo.setGateType("mixed");
        return submit(demo);
    }

    /**
     * 诊断信息
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
                "POST /api/skillopt/submit                — 提交训练任务（异步）",
                "POST /api/skillopt/test-sync            — 同步测试（阻塞等结果）",
                "GET  /api/skillopt/demo-submit           — 一键示例任务",
                "GET  /api/skillopt/job/{jobId}           — 查询任务状态",
                "GET  /api/skillopt/job/{jobId}/epochs    — 查询 epoch 链",
                "GET  /api/skillopt/job/{jobId}/candidates — 查询候选 skill",
                "GET  /api/skillopt/job/{jobId}/diff       — Skill 前后对比（每 epoch diff）",
                "GET  /api/skillopt/job/{jobId}/skill-history — Skill 进化时间线",
                "GET  /api/skillopt/job/{jobId}/edits       — 编辑操作日志",
                "GET  /api/skillopt/jobs                  — 列出所有任务",
                "GET  /api/skillopt/debug                 — 本页面"
        ));
        return ResponseEntity.ok(info);
    }

    // ======================== Skill 对比 API ========================

    /**
     * Skill 前后对比 — 展示每个 epoch 的 skillBefore vs skillAfter + 编辑操作。
     * <p>
     * GET /api/skillopt/job/{jobId}/diff
     */
    @GetMapping("/job/{jobId}/diff")
    public ResponseEntity<List<Map<String, Object>>> getSkillDiff(@PathVariable String jobId) {
        List<SkillOptEpochEntity> epochs = epochService.listByJobId(jobId);
        List<SkillOptEditLogEntity> allEdits = editLogMapper.selectListByQuery(
                QueryWrapper.create().where(SkillOptEditLogEntity::getJobId).eq(jobId)
                        .orderBy(SkillOptEditLogEntity::getEpoch, true)
                        .orderBy(SkillOptEditLogEntity::getEditIndex, true));

        // 按 epoch 分组编辑
        Map<Integer, List<SkillOptEditLogEntity>> editsByEpoch = new LinkedHashMap<>();
        for (SkillOptEditLogEntity edit : allEdits) {
            editsByEpoch.computeIfAbsent(edit.getEpoch(), k -> new ArrayList<>()).add(edit);
        }

        List<Map<String, Object>> diffReport = new ArrayList<>();
        for (SkillOptEpochEntity epoch : epochs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("epoch", epoch.getEpoch());
            entry.put("gateAccepted", epoch.getGateAccepted());
            entry.put("validationScore", epoch.getValidationScore());
            entry.put("previousValidationScore", epoch.getPreviousValidationScore());
            entry.put("editBudget", epoch.getEditBudget());
            entry.put("actualEditCount", epoch.getActualEditCount());

            // Skill 前后对比
            entry.put("skillBefore", epoch.getSkillBefore() != null ? epoch.getSkillBefore() : "");
            entry.put("skillAfter", epoch.getSkillAfter() != null ? epoch.getSkillAfter() : "");
            entry.put("candidateSkill", epoch.getCandidateSkill() != null ? epoch.getCandidateSkill() : "");

            // 计算字符级变化统计
            String before = epoch.getSkillBefore() != null ? epoch.getSkillBefore() : "";
            String after = epoch.getSkillAfter() != null ? epoch.getSkillAfter() : "";
            entry.put("beforeLength", before.length());
            entry.put("afterLength", after.length());
            entry.put("charsAdded", Math.max(0, after.length() - before.length()));
            entry.put("charsRemoved", Math.max(0, before.length() - after.length()));

            // 该 epoch 的编辑操作
            List<SkillOptEditLogEntity> epochEdits = editsByEpoch.getOrDefault(epoch.getEpoch(), List.of());
            entry.put("edits", epochEdits);

            // 门控原因
            entry.put("epochSummary", epoch.getEpochSummary());

            diffReport.add(entry);
        }

        return ResponseEntity.ok(diffReport);
    }

    /**
     * Skill 进化时间线 — 展示从初始 skill 到最终 best skill 的完整进化路径。
     * <p>
     * GET /api/skillopt/job/{jobId}/skill-history
     */
    @GetMapping("/job/{jobId}/skill-history")
    public ResponseEntity<Map<String, Object>> getSkillHistory(@PathVariable String jobId) {
        SkillOptTrainingJobEntity job = jobService.getByJobId(jobId);
        if (job == null) return ResponseEntity.notFound().build();

        List<SkillOptEpochEntity> epochs = epochService.listByJobId(jobId);
        List<SkillOptCandidateEntity> candidates = candidateService.listByJobId(jobId);

        Map<String, Object> history = new LinkedHashMap<>();
        history.put("jobId", jobId);
        history.put("taskDescription", job.getTaskDescription());
        history.put("status", job.getStatus());

        // 初始 skill
        history.put("initialSkill", job.getInitialSkill() != null ? job.getInitialSkill() : "");

        // 每个 epoch 的 skill 版本快照
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (SkillOptEpochEntity epoch : epochs) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("epoch", epoch.getEpoch());
            snapshot.put("skillContent", epoch.getSkillAfter() != null ? epoch.getSkillAfter() : "");
            snapshot.put("validationScore", epoch.getValidationScore());
            snapshot.put("gateAccepted", epoch.getGateAccepted());
            snapshot.put("editCount", epoch.getActualEditCount());
            snapshot.put("metaSkill", epoch.getMetaSkill() != null ? epoch.getMetaSkill() : "");
            snapshot.put("createdAt", epoch.getCreatedAt() != null ? epoch.getCreatedAt().toString() : "");
            timeline.add(snapshot);
        }
        history.put("timeline", timeline);

        // 候选 skill 中标记为 best 的
        String bestCandidate = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsBest()))
                .map(SkillOptCandidateEntity::getCandidateSkill)
                .findFirst()
                .orElse(null);
        history.put("bestCandidateSkill", bestCandidate);

        // 最终 best skill
        history.put("bestSkillContent", job.getBestSkillContent());
        history.put("bestSkillEpoch", job.getBestSkillEpoch());
        history.put("bestValidationScore", job.getBestValidationScore());

        // 分数变化曲线
        List<Double> scoreCurve = epochs.stream()
                .map(e -> e.getValidationScore() != null ? e.getValidationScore() : 0.0)
                .toList();
        history.put("scoreCurve", scoreCurve);

        return ResponseEntity.ok(history);
    }

    /**
     * 查询编辑操作日志 — 每个 epoch 的所有编辑操作详情。
     * <p>
     * GET /api/skillopt/job/{jobId}/edits
     */
    @GetMapping("/job/{jobId}/edits")
    public ResponseEntity<List<SkillOptEditLogEntity>> getEditLogs(@PathVariable String jobId) {
        List<SkillOptEditLogEntity> edits = editLogMapper.selectListByQuery(
                QueryWrapper.create().where(SkillOptEditLogEntity::getJobId).eq(jobId)
                        .orderBy(SkillOptEditLogEntity::getEpoch, true)
                        .orderBy(SkillOptEditLogEntity::getEditIndex, true));
        return ResponseEntity.ok(edits);
    }

    // ======================== 转换方法 ========================

    private TrainingJobResponse toJobResponse(SkillOptTrainingJobEntity job) {
        TrainingJobResponse r = new TrainingJobResponse();
        r.setJobId(job.getJobId());
        r.setTaskDescription(job.getTaskDescription());
        r.setStatus(job.getStatus());
        r.setEnvAdapterType(job.getEnvAdapterType());
        r.setLrSchedulerType(job.getLrSchedulerType());
        r.setGateType(job.getGateType());
        r.setCurrentEpoch(job.getCurrentEpoch());
        r.setMaxEpochs(job.getMaxEpochs());
        r.setBatchSize(job.getBatchSize());
        r.setEditBudgetBase(job.getEditBudgetBase());
        r.setBestSkillContent(job.getBestSkillContent());
        r.setBestSkillEpoch(job.getBestSkillEpoch());
        r.setBestValidationScore(job.getBestValidationScore());
        r.setFinalMetaSkill(job.getFinalMetaSkill());
        r.setCreatedAt(job.getCreatedAt());
        r.setUpdatedAt(job.getUpdatedAt());
        return r;
    }
}
