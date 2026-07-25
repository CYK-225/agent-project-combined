package org.example.agentScope.util.tool.DockerTool.V2;

import org.example.masfanplus.AgentScope.util.DockerTool.V1.DockerController;
import org.example.masfanplus.AgentScope.util.DockerTool.V2.config.AgentPoolProperties;
import org.example.masfanplus.AgentScope.util.DockerTool.V2.model.ContainerPod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker容器回调管理的REST控制器。
 * 处理来自Python容器的回调，并提供Docker管理端点。
 */
@RestController
@RequestMapping("/api/v2/docker")
public class DockerControllerV2 {

    private static final Logger log = LoggerFactory.getLogger(DockerController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DockerPoolManager poolManager;
    private final AgentPoolProperties properties;

    // 任务回调的内存存储（演示用；生产环境请使用适当的存储）
    private final Map<String, List<Map<String, Object>>> taskCallbacks = new ConcurrentHashMap<>();

    public DockerControllerV2(DockerPoolManager poolManager, AgentPoolProperties properties) {
        this.poolManager = poolManager;
        this.properties = properties;
    }


    /**
     * 处理来自容器的任务回调。
     * 容器在此发送回调以报告进度和完成情况。
     *
     * @param containerId 容器ID（来自路径）
     * @param payload     回调负载
     * @return 确认响应
     */
    @PostMapping("/callback/{containerId}")
    public ResponseEntity<String> handleContainerCallback(
            @PathVariable String containerId,
            @RequestBody Map<String, Object> payload) {

        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        log.info("[{}] 收到容器{}的回调：{}", timestamp, containerId.substring(0, 12), payload);

        String taskId = (String) payload.get("task_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        if (data == null) {
            log.warn("回调缺少'data'字段：{}", payload);
            return ResponseEntity.badRequest().body("缺少'data'字段");
        }

        String status = (String) data.get("status");
        Integer step = (Integer) data.get("step");
        String action = (String) data.get("action");
        String imageUrl = (String) data.get("image_url");
        String result = (String) data.get("result");


        // 存储回调用于任务跟踪
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

        // 根据状态处理
        switch (status) {
            case "processing":
                handleProcessingCallback(containerId, taskId, step, action, imageUrl, result, timestamp);
                break;

            case "processing_error":
                handleProcessingErrorCallback(containerId, taskId, step, action, imageUrl, result, timestamp);
                break;

            case "completed":
            case "terminated":
                handleCompletionCallback(containerId, taskId, status, result, timestamp);
                // 自动释放容器
                poolManager.handleTaskCallback(containerId, status);
                break;

            case "timeout":
            case "failure":
                handleFailureCallback(containerId, taskId, status, result, timestamp);
                // 失败时释放容器
                poolManager.handleTaskCallback(containerId, status);
                break;

            default:
                log.warn("收到未知回调状态：{} 来自容器{}", status, containerId.substring(0, 12));
        }

        return ResponseEntity.ok("RECEIVED");
    }

    /**
     * 获取任务的回调历史。
     *
     * @param taskId 任务ID
     * @return 任务的回调列表
     */
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

        // 获取最新状态
        Map<String, Object> latestCallback = callbacks.get(callbacks.size() - 1);
        response.put("latestStatus", latestCallback.get("status"));

        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有任务回调（用于调试/监控）。
     *
     * @return 所有任务回调
     */
    @GetMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> getAllCallbacks() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskCount", taskCallbacks.size());
        response.put("tasks", taskCallbacks.keySet());

        // 每个任务的摘要
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

    /**
     * 清除任务的回调历史。
     *
     * @param taskId 任务ID
     * @return 成功响应
     */
    @DeleteMapping("/callbacks/{taskId}")
    public ResponseEntity<Map<String, Object>> clearTaskCallbacks(@PathVariable String taskId) {
        List<Map<String, Object>> removed = taskCallbacks.remove(taskId);

        return ResponseEntity.ok(Map.of(
                "status", "cleared",
                "taskId", taskId,
                "removedCount", removed != null ? removed.size() : 0
        ));
    }

    /**
     * 获取Docker配置信息。
     *
     * @return Docker配置
     */
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

    /**
     * 健康检查端点。
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());

        Map<String, Object> poolStats = poolManager.getPoolStats();
        health.put("poolStats", poolStats);

        return ResponseEntity.ok(health);
    }

    // ==================== 私有辅助方法 ====================



    private void handleCompletionCallback(String containerId, String taskId,
                                          String status, String result, String timestamp) {
        log.info("[{}] 任务{} {} - 结果：{}",
                timestamp, taskId, status, truncate(result, 200));




        // 此处可以：
        // 1. 在数据库中标记任务完成
        // 2. 通知等待的客户端
        // 3. 触发后续操作
    }
    private void handleProcessingCallback(String containerId, String taskId, Integer step,
                                          String action, String imageUrl, String result, String timestamp) {
        log.info("[{}] 任务{} 处理中 - 步骤{}: {} - {}，结果：{}",
                timestamp, taskId, step, action, truncate(result, 200), imageUrl != null ? STR."图片URL: \{imageUrl}" : "无图片");

        // 此处可以：
        // 1. 更新任务进度
        // 2. 通知等待的客户端
        // 3. 存储中间结果
    }

    private void handleProcessingErrorCallback(String containerId, String taskId, Integer step,
                                               String action, String imageUrl, String result, String timestamp) {
        log.warn("[{}] 任务{} 处理中-异常输出 - 步骤{}: {} - 模型输出异常，结果：{}",
                timestamp, taskId, step, action, imageUrl != null ? STR."图片URL: \{imageUrl}" : "无图片", truncate(result, 200));

        // 此处可以：
        // 1. 记录异常输出到日志或数据库
        // 2. 通知监控/告警系统
        // 3. 触发模型输出分析
    }

    private void handleFailureCallback(String containerId, String taskId,
                                       String status, String result, String timestamp) {
        log.error("[{}] 任务{} {} - 错误：{}",
                timestamp, taskId, status, truncate(result, 200));

        // 此处可以：
        // 1. 在数据库中标记任务失败
        // 2. 发送警报/通知
        // 3. 启动重试逻辑
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
