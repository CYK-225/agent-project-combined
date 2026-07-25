package org.example.sliders.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.example.graph.workflow.core.GraphPoolManager;
import org.example.graph.workflow.core.NodeActionPool;
import org.example.sliders.entity.SlidersDocumentEntity;
import org.example.sliders.entity.SlidersTaskEntity;
import org.example.sliders.mapper.SlidersDocumentMapper;
import org.example.sliders.service.SlidersTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * SLIDERS 分步诊断测试接口
 * <p>
 * Step 1: POST /api/sliders/test/step1-state — 测试 OverAllState 创建
 * Step 2: POST /api/sliders/test/step2-save  — 测试数据保存（Task + Document）
 * Step 3: POST /api/sliders/test/step3-graph — 测试 Graph 获取（不执行）
 * Step 4: POST /api/sliders/test/step4-run   — 测试 Graph 执行（完整管道）
 * Step 5: GET  /api/sliders/test/health       — 健康检查
 */
@Log4j2
@RestController
@RequestMapping("/api/sliders/test")
public class SlidersTestController {

    private final SlidersTaskService taskService;
    private final SlidersDocumentMapper documentMapper;
    private final GraphPoolManager graphPoolManager;
    private final NodeActionPool nodeActionPool;

    public SlidersTestController(SlidersTaskService taskService,
                                  SlidersDocumentMapper documentMapper,
                                  GraphPoolManager graphPoolManager,
                                  NodeActionPool nodeActionPool) {
        this.taskService = taskService;
        this.documentMapper = documentMapper;
        this.graphPoolManager = graphPoolManager;
        this.nodeActionPool = nodeActionPool;
    }

    @Data
    public static class TestRequest {
        private String question;
        private List<SlidersController.DocumentInput> documents;
    }

    /** 健康检查 */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("graphs", graphPoolManager.getMetadataRegistry().keySet());
        result.put("nodeActions", nodeActionPool.listAll());

        // 检查 sliders-pipeline 是否注册
        boolean hasSliders = graphPoolManager.getMetadataRegistry().containsKey("sliders-pipeline");
        result.put("slidersPipelineRegistered", hasSliders);

        return ResponseEntity.ok(result);
    }

    /** Step 1: 测试 OverAllState 创建 */
    @PostMapping("/step1-state")
    public ResponseEntity<Map<String, Object>> step1State() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            OverAllState state = new OverAllState();
            result.put("step", "1-create-state");
            result.put("stateCreated", true);

            state.updateState(Map.of("taskId", "test-123"));
            result.put("updateStateCalled", true);

            Object taskId = state.value("taskId").orElse(null);
            result.put("taskIdRetrieved", taskId);
            result.put("success", true);

            log.info("[Step1] OverAllState 创建成功, taskId={}", taskId);
        } catch (Throwable t) {
            result.put("success", false);
            result.put("error", t.getClass().getName());
            result.put("message", String.valueOf(t.getMessage()));
            result.put("cause", t.getCause() != null ? t.getCause().getClass().getName() + ": " + t.getCause().getMessage() : "null");
            log.error("[Step1] 失败", t);
        }
        return ResponseEntity.ok(result);
    }

    /** Step 2: 测试数据保存 */
    @PostMapping("/step2-save")
    public ResponseEntity<Map<String, Object>> step2Save(@RequestBody TestRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String taskId = "test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            // 保存 Task
            SlidersTaskEntity task = SlidersTaskEntity.builder()
                    .taskId(taskId)
                    .threadId("test-thread")
                    .question(request.getQuestion())
                    .status("PENDING")
                    .build();
            taskService.save(task);
            result.put("taskSaved", true);
            result.put("taskId", taskId);

            // 保存 Documents
            int docCount = 0;
            if (request.getDocuments() != null) {
                for (SlidersController.DocumentInput doc : request.getDocuments()) {
                    documentMapper.insert(SlidersDocumentEntity.builder()
                            .taskId(taskId)
                            .documentName(doc.getName())
                            .description(doc.getDescription())
                            .content(doc.getContent())
                            .build());
                    docCount++;
                }
            }
            result.put("documentsSaved", docCount);

            // 读回验证
            SlidersTaskEntity saved = taskService.getByTaskId(taskId);
            result.put("taskReadBack", saved != null ? saved.getStatus() : "NOT_FOUND");
            result.put("success", true);

            log.info("[Step2] 数据保存成功, taskId={}, docs={}", taskId, docCount);
        } catch (Throwable t) {
            result.put("success", false);
            result.put("error", t.getClass().getName());
            result.put("message", String.valueOf(t.getMessage()));
            result.put("cause", t.getCause() != null ? t.getCause().getClass().getName() + ": " + t.getCause().getMessage() : "null");
            log.error("[Step2] 失败", t);
        }
        return ResponseEntity.ok(result);
    }

    /** Step 3: 测试 Graph 获取（只获取，不执行） */
    @PostMapping("/step3-graph")
    public ResponseEntity<Map<String, Object>> step3Graph() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("step", "3-get-graph");

            // 尝试获取已编译的 Graph
            var graph = graphPoolManager.getGraph("sliders-pipeline");
            result.put("graphObtained", graph != null);
            result.put("graphClass", graph != null ? graph.getClass().getName() : "null");
            result.put("success", true);

            log.info("[Step3] Graph 获取成功: {}", graph != null ? graph.getClass().getName() : "null");
        } catch (Throwable t) {
            result.put("success", false);
            result.put("error", t.getClass().getName());
            result.put("message", String.valueOf(t.getMessage()));
            result.put("cause", t.getCause() != null ? t.getCause().getClass().getName() + ": " + t.getCause().getMessage() : "null");
            result.put("trace", Arrays.stream(t.getStackTrace()).limit(10).map(Object::toString).toList());
            log.error("[Step3] 失败", t);
        }
        return ResponseEntity.ok(result);
    }

    /** Step 4: 完整 Graph 执行测试 */
    @PostMapping("/step4-run")
    public ResponseEntity<Map<String, Object>> step4Run(@RequestBody TestRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        String taskId = null;
        try {
            taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String threadId = "sliders-test-" + taskId;

            // 保存数据
            SlidersTaskEntity task = SlidersTaskEntity.builder()
                    .taskId(taskId).threadId(threadId)
                    .question(request.getQuestion()).status("PENDING").build();
            taskService.save(task);

            if (request.getDocuments() != null) {
                for (SlidersController.DocumentInput doc : request.getDocuments()) {
                    documentMapper.insert(SlidersDocumentEntity.builder()
                            .taskId(taskId).documentName(doc.getName())
                            .description(doc.getDescription()).content(doc.getContent()).build());
                }
            }

            // 创建 State
            OverAllState initialState = new OverAllState();
            initialState.updateState(Map.of("taskId", taskId));
            result.put("taskId", taskId);
            result.put("dataPrepared", true);

            // 执行 Graph
            log.info("[Step4] 开始执行 Graph, taskId={}", taskId);
            OverAllState graphResult = graphPoolManager.invokeGraph("sliders-pipeline", initialState, threadId);
            result.put("graphCompleted", true);

            // 查询最终状态
            SlidersTaskEntity finalTask = taskService.getByTaskId(taskId);
            result.put("finalStatus", finalTask != null ? finalTask.getStatus() : "NOT_FOUND");
            result.put("answer", finalTask != null ? finalTask.getAnswer() : null);
            result.put("errorMessage", finalTask != null ? finalTask.getErrorMessage() : null);
            result.put("success", true);

            log.info("[Step4] Graph 执行完成, taskId={}, status={}", taskId,
                    finalTask != null ? finalTask.getStatus() : "NOT_FOUND");
        } catch (Throwable t) {
            result.put("success", false);
            result.put("taskId", taskId);
            result.put("error", t.getClass().getName());
            result.put("message", String.valueOf(t.getMessage()));
            result.put("cause", t.getCause() != null
                    ? t.getCause().getClass().getName() + ": " + t.getCause().getMessage() : "null");
            result.put("trace", Arrays.stream(t.getStackTrace()).limit(15).map(Object::toString).toList());
            log.error("[Step4] 失败, taskId={}", taskId, t);
        }
        return ResponseEntity.ok(result);
    }
}
