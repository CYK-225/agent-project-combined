package org.example.sliders.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.example.sliders.entity.SlidersDocumentEntity;
import org.example.sliders.entity.SlidersTaskEntity;
import org.example.sliders.mapper.SlidersDocumentMapper;
import org.example.sliders.service.SlidersTaskService;
import org.example.graph.workflow.core.GraphPoolManager;
import org.example.graph.workflow.core.NodeActionPool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.example.sliders.entity.table.SlidersTaskEntityTableDef.SLIDERS_TASK_ENTITY;

/**
 * SLIDERS 流水线 REST API
 * <p>
 * 提供：
 * - POST /api/sliders/submit — 提交问题和文档，触发流水线
 * - GET  /api/sliders/task/{taskId} — 查询任务状态和结果
 * - GET  /api/sliders/tasks — 列出所有任务
 */
@Log4j2
@RestController
@RequestMapping("/api/sliders")
public class SlidersController {

    private final SlidersTaskService taskService;
    private final SlidersDocumentMapper documentMapper;
    private final GraphPoolManager graphPoolManager;
    private final NodeActionPool nodeActionPool;

    /** 异步执行线程池（Graph + LLM 耗时较长） */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SlidersController(SlidersTaskService taskService,
                             SlidersDocumentMapper documentMapper,
                             GraphPoolManager graphPoolManager,
                             NodeActionPool nodeActionPool) {
        this.taskService = taskService;
        this.documentMapper = documentMapper;
        this.graphPoolManager = graphPoolManager;
        this.nodeActionPool = nodeActionPool;
    }

    // ======================== DTO ========================

    @Data
    public static class SubmitRequest {
        /** 用户问题 */
        private String question;
        /** 文档列表（name + content） */
        private List<DocumentInput> documents;
    }

    @Data
    public static class DocumentInput {
        /** 文档名称 */
        private String name;
        /** 文档内容（Markdown） */
        private String content;
        /** 文档描述（可选） */
        private String description;
    }

    @Data
    public static class TaskResponse {
        private String taskId;
        private String status;
        private String question;
        private String answer;
        private String errorMessage;
        private Date createdAt;
    }

    // ======================== API ========================

    /**
     * 提交问题和文档，触发 SLIDERS 流水线（Graph 顺序管道）
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SubmitRequest request) {
        // 1. 生成 taskId 和 threadId
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String threadId = "sliders-" + taskId;

        // 2. 创建任务记录
        SlidersTaskEntity task = SlidersTaskEntity.builder()
                .taskId(taskId)
                .threadId(threadId)
                .question(request.getQuestion())
                .status("PENDING")
                .build();
        taskService.save(task);

        // 3. 保存文档
        for (DocumentInput doc : request.getDocuments()) {
            documentMapper.insert(SlidersDocumentEntity.builder()
                    .taskId(taskId)
                    .documentName(doc.getName())
                    .description(doc.getDescription())
                    .content(doc.getContent())
                    .build());
        }

        // 4. 异步触发 Graph 管道
        CompletableFuture.runAsync(() -> {
            try {
                log.info("触发 sliders-pipeline Graph，taskId={}", taskId);

                OverAllState initialState = new OverAllState();
                initialState.updateState(Map.of("taskId", taskId, "question", request.getQuestion()));

                OverAllState result = graphPoolManager.invokeGraph(
                        "sliders-pipeline", initialState, threadId);

                log.info("sliders-pipeline Graph 执行完毕，taskId={}", taskId);

                // 检查任务状态，如果 Agent 没更新则标记完成
                SlidersTaskEntity t = taskService.getByTaskId(taskId);
                if (t != null && "PENDING".equals(t.getStatus())) {
                    taskService.updateStatus(taskId, "COMPLETED");
                }
            } catch (Exception e) {
                log.error("sliders-pipeline Graph 执行失败，taskId={}", taskId, e);
                taskService.markFailed(taskId, "Graph 执行失败: " + e.getMessage());
            }
        }, executor);

        log.info("SLIDERS task created: taskId={}, question={}", taskId, request.getQuestion());

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "threadId", threadId,
                "status", "PENDING",
                "message", "任务已创建，Graph 流水线已触发"
        ));
    }

    /**
     * 查询任务状态和结果
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable String taskId) {
        SlidersTaskEntity task = taskService.getByTaskId(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        TaskResponse resp = new TaskResponse();
        resp.setTaskId(task.getTaskId());
        resp.setStatus(task.getStatus());
        resp.setQuestion(task.getQuestion());
        resp.setAnswer(task.getAnswer());
        resp.setErrorMessage(task.getErrorMessage());
        resp.setCreatedAt(task.getCreatedAt());
        return ResponseEntity.ok(resp);
    }

    /**
     * 列出所有任务
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<TaskResponse>> listTasks() {
        List<SlidersTaskEntity> tasks = taskService.list();
        List<TaskResponse> responses = new ArrayList<>();
        for (SlidersTaskEntity task : tasks) {
            TaskResponse resp = new TaskResponse();
            resp.setTaskId(task.getTaskId());
            resp.setStatus(task.getStatus());
            resp.setQuestion(task.getQuestion());
            resp.setAnswer(task.getAnswer());
            resp.setErrorMessage(task.getErrorMessage());
            resp.setCreatedAt(task.getCreatedAt());
            responses.add(resp);
        }
        return ResponseEntity.ok(responses);
    }

    /**
     * 同步测试接口 — 直接调用 Graph 管道，返回结果或错误
     */
    @PostMapping("/test-sync")
    public ResponseEntity<Map<String, Object>> testSync(@RequestBody SubmitRequest request) {
        try {
            String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String threadId = "sliders-" + taskId;

            SlidersTaskEntity task = SlidersTaskEntity.builder()
                    .taskId(taskId).threadId(threadId)
                    .question(request.getQuestion()).status("PENDING").build();
            taskService.save(task);

            for (DocumentInput doc : request.getDocuments()) {
                documentMapper.insert(SlidersDocumentEntity.builder()
                        .taskId(taskId).documentName(doc.getName())
                        .description(doc.getDescription()).content(doc.getContent()).build());
            }

            OverAllState initialState = new OverAllState();
            initialState.updateState(Map.of("taskId", taskId));

            OverAllState result = graphPoolManager.invokeGraph("sliders-pipeline", initialState, threadId);

            SlidersTaskEntity finalTask = taskService.getByTaskId(taskId);
            Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
            resultMap.put("taskId", taskId);
            resultMap.put("status", finalTask != null ? finalTask.getStatus() : "UNKNOWN");
            resultMap.put("answer", finalTask != null ? finalTask.getAnswer() : null);
            resultMap.put("errorMessage", finalTask != null ? finalTask.getErrorMessage() : null);
            return ResponseEntity.ok(resultMap);
        } catch (Throwable t) {
            t.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "error", t.getClass().getName(),
                    "message", String.valueOf(t.getMessage()),
                    "cause", t.getCause() != null ? t.getCause().getClass().getName() + ": " + t.getCause().getMessage() : "null",
                    "trace", java.util.Arrays.stream(t.getStackTrace())
                            .limit(15).map(Object::toString).toList()
            ));
        }
    }

    /**
     * 诊断接口 — 查看已注册的 Graph 和 NodeAction
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("graphs", graphPoolManager.getMetadataRegistry().keySet());
        info.put("nodeActions", nodeActionPool.listAll());
        return ResponseEntity.ok(info);
    }
}
