package com.cyk.DockerTool.V3;
import com.cyk.DockerTool.V3.config.AgentPoolPropertiesV3;
import com.cyk.Utils.V3EmitterManager;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V3 Docker容器回调管理的REST控制器。
 * 处理来自Python容器的回调，并提供Docker管理端点。
 * API路径：/api/v3/docker/
 */
@RestController
@RequestMapping("/api/v3/docker")
public class DockerControllerV3 {

    @Resource
    private V3EmitterManager emitterManager;

    private static final Logger log = LoggerFactory.getLogger(DockerControllerV3.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DockerPoolManagerV3 poolManager;
    private final AgentPoolPropertiesV3 properties;

    private final Map<String, List<Map<String, Object>>> taskCallbacks = new ConcurrentHashMap<>();

    public DockerControllerV3(DockerPoolManagerV3 poolManager, AgentPoolPropertiesV3 properties) {
        this.poolManager = poolManager;
        this.properties = properties;
    }

    /**
     * 处理来自容器的任务回调。
     */
    @PostMapping("/callback/{containerId}")
    public ResponseEntity<String> handleContainerCallback(
            @PathVariable String containerId,
            @RequestBody Map<String, Object> payload) {

        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        log.info("[V3][{}] 收到容器{}的回调：{}", timestamp, containerId.substring(0, 12), payload);

        String taskId = (String) payload.get("task_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        if (data == null) {
            log.warn("[V3] 回调缺少'data'字段：{}", payload);
            return ResponseEntity.badRequest().body("缺少'data'字段");
        }

        String status = (String) data.get("status");
        Integer step = (Integer) data.get("step");
        String action = (String) data.get("action");
        String imageUrl = (String) data.get("image_url");
        String result = (String) data.get("result");

        Map<String, Object> stepPayload = new LinkedHashMap<>();
        stepPayload.put("containerId", containerId);
        stepPayload.put("taskId", taskId);
        stepPayload.put("status", status);
        stepPayload.put("step", step);
        stepPayload.put("action", action);
        stepPayload.put("imageUrl", imageUrl);
        stepPayload.put("result", result);

        DockerPoolManagerV3.TaskContext currentCtx = poolManager.getTaskContext(taskId);
        if (currentCtx != null) {
            stepPayload.put("instruction", currentCtx.getInstruction());
        }
        emitterManager.sendMessageByContainerId(containerId, stepPayload);

        if (taskId != null) {
            taskCallbacks.computeIfAbsent(taskId, k -> new ArrayList<>()).add(new LinkedHashMap<>() {{
                put("timestamp", timestamp);
                put("containerId", containerId);
                put("status", status);
                put("step", step);
                put("action", action);
                put("imageUrl", imageUrl);
                put("result", result);
            }});
        }

        boolean isTaskStopped = false;

        switch (status) {
            case "processing":
            case "processing_error":
                handleProcessingCallback(containerId, taskId, step, action, imageUrl, result, timestamp);
                log.info("[V3][{}] 容器{}处理进度回调：{}", timestamp, containerId.substring(0, 12), payload);

                DockerPoolManagerV3.TaskContext ctx = poolManager.getTaskContext(taskId);
                if (ctx != null && !ctx.isAborted() && !"等候指令".equals(ctx.getInstruction())) {
                    ctx.setCurrentStep(ctx.getCurrentStep() + 1);
                    if (ctx.getCurrentStep() < ctx.getMaxSteps()) {
                        log.info("[V3] Task {} 第 {} 步完成，准备触发第 {} 步...", taskId, ctx.getCurrentStep(), ctx.getCurrentStep() + 1);
                        poolManager.triggerNextStepAsync(taskId);
                    } else {
                        log.warn("[V3] Task {} 已达到最大步数 {}，强制停止。", taskId, ctx.getMaxSteps());
                        poolManager.handleTaskCallback(containerId, "timeout");
                        isTaskStopped = true;
                    }
                }
                break;

            case "completed":
            case "terminated": {
                handleCompletionCallback(containerId, taskId, status, result, timestamp);

                DockerPoolManagerV3.TaskContext ctxComplete = poolManager.getTaskContext(taskId);

                if (ctxComplete != null && !ctxComplete.isAborted() && !ctxComplete.getInstructionQueue().isEmpty()) {
                    String nextInstruction = ctxComplete.getInstructionQueue().poll();
                    log.info("[V3][{}] 任务 {} 当前阶段已完成，准备无缝执行队列下一条指令: {}", timestamp, taskId, truncate(nextInstruction, 50));

                    Map<String, Object> nextInstructionPayload = new LinkedHashMap<>();
                    nextInstructionPayload.put("containerId", containerId);
                    nextInstructionPayload.put("taskId", taskId);
                    nextInstructionPayload.put("status", "next_instruction_started");
                    nextInstructionPayload.put("instruction", nextInstruction);
                    nextInstructionPayload.put("remaining_count", ctxComplete.getInstructionQueue().size());
                    nextInstructionPayload.put("message", "开始执行下一条指令: " + nextInstruction);

                    emitterManager.sendMessageByContainerId(containerId, nextInstructionPayload);

                    ctxComplete.setInstruction(nextInstruction);
                    ctxComplete.setCurrentStep(0);

                    poolManager.triggerNextStepAsync(taskId);

                } else {
                    if (ctxComplete != null) {
                        ctxComplete.setInstruction("等候指令");
                    }
                    poolManager.handleTaskCallback(containerId, status);
                    log.info("[V3][{}] 任务队列已清空/彻底完成，停止发球。释放容器{}", timestamp, containerId.substring(0, 12));
                    isTaskStopped = true;
                }
                break;
            }

            case "timeout":
            case "failure":
                handleFailureCallback(containerId, taskId, status, result, timestamp);
                poolManager.handleTaskCallback(containerId, status);
                log.error("[V3][{}] 任务发生异常/超时，停止发球。释放容器{}", timestamp, containerId.substring(0, 12));
                isTaskStopped = true;
                break;

            default:
                log.warn("[V3] 收到未知回调状态：{} 来自容器{}", status, containerId.substring(0, 12));
        }

        if (isTaskStopped) {
            Map<String, Object> completePayload = Map.of(
                    "containerId", containerId,
                    "status", "complete",
                    "message", "任务彻底结束"
            );
            emitterManager.sendMessageByContainerId(containerId, completePayload);
            log.info("[V3][{}] 已向前端发送任务 complete 事件，容器{}", timestamp, containerId.substring(0, 12));
        }
        return ResponseEntity.ok("RECEIVED");
    }

    @GetMapping("/callbacks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskCallbacks(@PathVariable String taskId) {
        List<Map<String, Object>> callbacks = taskCallbacks.get(taskId);

        if (callbacks == null || callbacks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("callbackCount", callbacks.size());
        response.put("callbacks", callbacks);

        Map<String, Object> latestCallback = callbacks.get(callbacks.size() - 1);
        response.put("latestStatus", latestCallback.get("status"));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> getAllCallbacks() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskCount", taskCallbacks.size());
        response.put("tasks", taskCallbacks.keySet());

        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        taskCallbacks.forEach((taskId, callbacks) -> {
            if (!callbacks.isEmpty()) {
                Map<String, Object> latest = callbacks.get(callbacks.size() - 1);
                Map<String, Object> taskSummary = new LinkedHashMap<>();
                taskSummary.put("callbackCount", callbacks.size());
                taskSummary.put("latestStatus", latest.get("status"));
                taskSummary.put("latestStep", latest.get("step"));
                taskSummary.put("containerId", latest.get("containerId"));
                summary.put(taskId, taskSummary);
            }
        });
        response.put("summary", summary);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/callbacks/{taskId}")
    public ResponseEntity<Map<String, Object>> clearTaskCallbacks(@PathVariable String taskId) {
        List<Map<String, Object>> removed = taskCallbacks.remove(taskId);

        return ResponseEntity.ok(Map.of(
                "status", "cleared",
                "taskId", taskId,
                "removedCount", removed != null ? removed.size() : 0
        ));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getDockerConfig() {
        Map<String, Object> config = new LinkedHashMap<>();

        config.put("callbackBaseUrl", properties.getCallbackBaseUrl());
        config.put("imageName", properties.getImageName());
        config.put("maxPoolSize", properties.getMaxPoolSize());
        config.put("portRange", properties.getPortRangeStart() + "-" + properties.getPortRangeEnd());
        config.put("memoryLimit", formatBytes(properties.getMemoryLimit()));
        config.put("memorySwap", formatBytes(properties.getMemorySwap()));
        config.put("shmSize", formatBytes(properties.getShmSize()));
        config.put("idleTimeoutSeconds", properties.getIdleTimeoutSeconds());
        config.put("profileBasePath", properties.getProfileBasePath());

        return ResponseEntity.ok(config);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("version", "V3");
        health.put("timestamp", LocalDateTime.now().toString());

        Map<String, Object> poolStats = poolManager.getPoolStats();
        health.put("poolStats", poolStats);

        return ResponseEntity.ok(health);
    }

    // ==================== 私有辅助方法 ====================

    private void handleCompletionCallback(String containerId, String taskId,
                                          String status, String result, String timestamp) {
        log.info("[V3][{}] 任务{} {} - 结果：{}",
                timestamp, taskId, status, truncate(result, 200));
    }

    private void handleProcessingCallback(String containerId, String taskId, Integer step,
                                          String action, String imageUrl, String result, String timestamp) {
        log.info("[V3][{}] 任务{} 处理中 - 步骤{}: {} - {}，结果：{}",
                timestamp, taskId, step, action, truncate(result, 200), imageUrl != null ? STR."图片URL: \{imageUrl}" : "无图片");
    }

    private void handleProcessingErrorCallback(String containerId, String taskId, Integer step,
                                               String action, String imageUrl, String result, String timestamp) {
        log.warn("[V3][{}] 任务{} 处理中-异常输出 - 步骤{}: {} - 模型输出异常，结果：{}",
                timestamp, taskId, step, action, imageUrl != null ? STR."图片URL: \{imageUrl}" : "无图片", truncate(result, 200));
    }

    private void handleFailureCallback(String containerId, String taskId,
                                       String status, String result, String timestamp) {
        log.error("[V3][{}] 任务{} {} - 错误：{}",
                timestamp, taskId, status, truncate(result, 200));
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}
