package com.cyk.DockerTool.Weixin;//package com.cyk.baidu.DockerTool.Weixin;
//
//
//import com.cyk.baidu.DockerTool.V2.model.ContainerPod;
//import com.cyk.baidu.DockerTool.Weixin.config.WeChatDockerConfiguration;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * 微信 Docker 容器回调管理的 REST 控制器。
// * 处理来自微信 Python 容器的回调，并提供 Docker 管理端点。
// *
// * 与 V2 的 Chrome 实现完全隔离，使用独立的：
// * - 回调路径：/api/wechat/docker/callback
// * - 池管理器：WeChatDockerPoolManager
// * - 配置：WeChatDockerConfiguration
// */
//@RestController
//@RequestMapping("/api/wechat/docker")
//public class DockerControllerWeixin {
//
//    private static final Logger log = LoggerFactory.getLogger(DockerControllerWeixin.class);
//    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
//
//    private final WeChatDockerPoolManager poolManager;
//    private final WeChatDockerConfiguration config;
//
//    /** 任务回调的内存存储（演示用；生产环境请使用适当的存储） */
//    private final Map<String, List<Map<String, Object>>> taskCallbacks = new ConcurrentHashMap<>();
//
//    public DockerControllerWeixin(WeChatDockerPoolManager poolManager, WeChatDockerConfiguration config) {
//        this.poolManager = poolManager;
//        this.config = config;
//    }
//
//    /**
//     * 处理来自微信容器的任务回调。
//     * 容器在此发送回调以报告进度和完成情况。
//     *
//     * @param containerId 容器 ID（来自路径）
//     * @param payload     回调负载
//     * @return 确认响应
//     */
//    @PostMapping("/callback/{containerId}")
//    public ResponseEntity<String> handleContainerCallback(
//            @PathVariable String containerId,
//            @RequestBody Map<String, Object> payload) {
//
//        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
//        log.info("[微信回调] [{}] 收到容器 {} 的回调：{}",
//                timestamp, containerId.substring(0, 12), payload);
//
//        String taskId = (String) payload.get("task_id");
//        @SuppressWarnings("unchecked")
//        Map<String, Object> data = (Map<String, Object>) payload.get("data");
//
//        if (data == null) {
//            log.warn("[微信回调] 回调缺少 'data' 字段：{}", payload);
//            return ResponseEntity.badRequest().body("缺少 'data' 字段");
//        }
//
//        String status = (String) data.get("status");
//        Integer step = (Integer) data.get("step");
//        String action = (String) data.get("action");
//        String imageUrl = (String) data.get("image_url");
//        String result = (String) data.get("result");
//
//        // 存储回调用于任务跟踪
//        if (taskId != null) {
//            String finalTaskId = taskId;
//            String finalStatus = status;
//            Integer finalStep = step;
//            String finalAction = action;
//            String finalImageUrl = imageUrl;
//            String finalResult = result;
//            String finalTimestamp = timestamp;
//
//            taskCallbacks.computeIfAbsent(taskId, k -> new ArrayList<>()).add(new LinkedHashMap<>() {{
//                put("timestamp", finalTimestamp);
//                put("containerId", containerId);
//                put("status", finalStatus);
//                put("step", finalStep);
//                put("action", finalAction);
//                put("imageUrl", finalImageUrl);
//                put("result", finalResult);
//                put("app_type", "wechat");
//            }});
//        }
//
//        // 根据状态处理
//        switch (status) {
//            case "processing":
//                handleProcessingCallback(containerId, taskId, step, action, imageUrl, result, timestamp);
//                break;
//
//            case "processing_error":
//                handleProcessingErrorCallback(containerId, taskId, step, action, imageUrl, result, timestamp);
//                break;
//
//            case "completed":
//            case "terminated":
//                handleCompletionCallback(containerId, taskId, status, result, timestamp);
//                // 自动释放容器
//                poolManager.handleTaskCallback(containerId, status);
//                break;
//
//            case "timeout":
//            case "failure":
//                handleFailureCallback(containerId, taskId, status, result, timestamp);
//                // 失败时释放容器
//                poolManager.handleTaskCallback(containerId, status);
//                break;
//
//            default:
//                log.warn("[微信回调] 收到未知回调状态：{} 来自容器 {}", status, containerId.substring(0, 12));
//        }
//
//        return ResponseEntity.ok("RECEIVED");
//    }
//
//    /**
//     * 获取微信任务的回调历史。
//     *
//     * @param taskId 任务 ID
//     * @return 任务的回调列表
//     */
//    @GetMapping("/callbacks/{taskId}")
//    public ResponseEntity<Map<String, Object>> getTaskCallbacks(@PathVariable String taskId) {
//        List<Map<String, Object>> callbacks = taskCallbacks.get(taskId);
//
//        if (callbacks == null || callbacks.isEmpty()) {
//            return ResponseEntity.notFound().build();
//        }
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("taskId", taskId);
//        response.put("callbackCount", callbacks.size());
//        response.put("callbacks", callbacks);
//        response.put("app_type", "wechat");
//
//        // 获取最新状态
//        Map<String, Object> latestCallback = callbacks.get(callbacks.size() - 1);
//        response.put("latestStatus", latestCallback.get("status"));
//
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * 获取所有微信任务回调（用于调试/监控）。
//     *
//     * @return 所有任务回调
//     */
//    @GetMapping("/callbacks")
//    public ResponseEntity<Map<String, Object>> getAllCallbacks() {
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("taskCount", taskCallbacks.size());
//        response.put("tasks", taskCallbacks.keySet());
//        response.put("app_type", "wechat");
//
//        // 每个任务的摘要
//        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
//        taskCallbacks.forEach((taskId, callbacks) -> {
//            if (!callbacks.isEmpty()) {
//                Map<String, Object> latest = callbacks.get(callbacks.size() - 1);
//                Map<String, Object> taskSummary = new LinkedHashMap<>();
//                taskSummary.put("callbackCount", callbacks.size());
//                taskSummary.put("latestStatus", latest.get("status"));
//                taskSummary.put("latestStep", latest.get("step"));
//                taskSummary.put("containerId", latest.get("containerId"));
//                summary.put(taskId, taskSummary);
//            }
//        });
//        response.put("summary", summary);
//
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * 清除微信任务的回调历史。
//     *
//     * @param taskId 任务 ID
//     * @return 成功响应
//     */
//    @DeleteMapping("/callbacks/{taskId}")
//    public ResponseEntity<Map<String, Object>> clearTaskCallbacks(@PathVariable String taskId) {
//        List<Map<String, Object>> removed = taskCallbacks.remove(taskId);
//
//        return ResponseEntity.ok(Map.of(
//                "status", "cleared",
//                "taskId", taskId,
//                "removedCount", removed != null ? removed.size() : 0,
//                "app_type", "wechat"
//        ));
//    }
//
//    /**
//     * 获取微信 Docker 配置信息。
//     *
//     * @return Docker 配置
//     */
//    @GetMapping("/config")
//    public ResponseEntity<Map<String, Object>> getDockerConfig() {
//        Map<String, Object> dockerConfig = new LinkedHashMap<>();
//
//        dockerConfig.put("callbackBaseUrl", config.getCallbackBaseUrl());
//        dockerConfig.put("javaBaseUrl", config.getJavaBaseUrl());
//        dockerConfig.put("imageName", config.getImageName());
//        dockerConfig.put("maxPoolSize", config.getMaxPoolSize());
//        dockerConfig.put("portRange", config.getPortRangeStart() + "-" + config.getPortRangeEnd());
//        dockerConfig.put("memoryLimit", formatBytes(config.getMemoryLimit()));
//        dockerConfig.put("memorySwap", formatBytes(config.getMemorySwap()));
//        dockerConfig.put("shmSize", formatBytes(config.getShmSize()));
//        dockerConfig.put("idleTimeoutSeconds", config.getIdleTimeoutSeconds());
//        dockerConfig.put("profileBasePath", config.getProfileBasePath());
//        dockerConfig.put("wechatDataDir", config.getWechatDataDirName());
//        dockerConfig.put("wechatFilesDir", config.getWechatFilesDirName());
//        dockerConfig.put("app_type", "wechat");
//
//        return ResponseEntity.ok(dockerConfig);
//    }
//
//    /**
//     * 获取微信数据目录信息。
//     *
//     * @param username 用户名
//     * @return 数据目录信息
//     */
//    @GetMapping("/user/{username}/paths")
//    public ResponseEntity<Map<String, Object>> getUserPaths(@PathVariable String username) {
//        Map<String, Object> paths = new LinkedHashMap<>();
//        paths.put("username", username);
//        paths.put("basePath", config.getUserBasePath(username));
//        paths.put("outputPath", config.getUserOutputPath(username));
//        paths.put("wechatDataPath", config.getUserWeChatDataPath(username));
//        paths.put("wechatFilesPath", config.getUserWeChatFilesPath(username));
//        paths.put("macAddress", poolManager.generateDeterministicMacAddress(username));
//        paths.put("app_type", "wechat");
//
//        return ResponseEntity.ok(paths);
//    }
//
//    /**
//     * 健康检查端点。
//     *
//     * @return 健康状态
//     */
//    @GetMapping("/health")
//    public ResponseEntity<Map<String, Object>> healthCheck() {
//        Map<String, Object> health = new LinkedHashMap<>();
//        health.put("status", "UP");
//        health.put("timestamp", LocalDateTime.now().toString());
//        health.put("app_type", "wechat");
//
//        Map<String, Object> poolStats = poolManager.getPoolStats();
//        health.put("poolStats", poolStats);
//
//        return ResponseEntity.ok(health);
//    }
//
//    /**
//     * 获取微信容器详情。
//     *
//     * @param containerId 容器 ID
//     * @return 容器详情
//     */
//    @GetMapping("/container/{containerId}/details")
//    public ResponseEntity<Map<String, Object>> getContainerDetails(@PathVariable String containerId) {
//        Optional<ContainerPod> podOpt = poolManager.getPod(containerId);
//
//        if (podOpt.isEmpty()) {
//            return ResponseEntity.ok(Map.of(
//                    "error", "微信容器未找到",
//                    "containerId", containerId,
//                    "app_type", "wechat"
//            ));
//        }
//
//        ContainerPod pod = podOpt.get();
//        Map<String, Object> details = new LinkedHashMap<>();
//        details.put("containerId", pod.getContainerId());
//        details.put("shortId", pod.getShortId());
//        details.put("username", pod.getUsername());
//        details.put("profile", pod.getProfileName());
//        details.put("apiPort", pod.getAssignedPort());
//        details.put("vncPort", pod.getVncPort());
//        details.put("macAddress", pod.getMacAddress());
//        details.put("status", pod.getStatus().get().name());
//        details.put("currentTaskId", pod.getCurrentTaskId());
//        details.put("idleSeconds", pod.getIdleSeconds());
//        details.put("vncUrl", "http://localhost:" + pod.getVncPort() + "/vnc.html");
//
//        // 微信数据路径信息
//        if (pod.getUsername() != null) {
//            details.put("wechatDataPath", config.getUserWeChatDataPath(pod.getUsername()));
//            details.put("wechatFilesPath", config.getUserWeChatFilesPath(pod.getUsername()));
//        }
//
//        details.put("app_type", "wechat");
//
//        return ResponseEntity.ok(details);
//    }
//
//    // ==================== 私有辅助方法 ====================
//
//    private void handleProcessingCallback(String containerId, String taskId, Integer step,
//                                          String action, String imageUrl, String result, String timestamp) {
//        log.info("[微信回调] [{}] 任务 {} 处理中 - 步骤 {}: {}，结果：{}，{}",
//                timestamp, taskId, step, action, truncate(result, 200),
//                imageUrl != null ? "图片: " + imageUrl : "无图片");
//
//        // 此处可以：
//        // 1. 更新任务进度
//        // 2. 通知等待的客户端
//        // 3. 存储中间结果
//    }
//
//    private void handleProcessingErrorCallback(String containerId, String taskId, Integer step,
//                                               String action, String imageUrl, String result, String timestamp) {
//        log.warn("[微信回调] [{}] 任务 {} 处理中-异常输出 - 步骤 {}: {}，结果：{}",
//                timestamp, taskId, step, action, truncate(result, 200));
//
//        // 此处可以：
//        // 1. 记录异常输出到日志或数据库
//        // 2. 通知监控/告警系统
//    }
//
//    private void handleCompletionCallback(String containerId, String taskId,
//                                          String status, String result, String timestamp) {
//        log.info("[微信回调] [{}] 微信任务 {} {} - 结果：{}",
//                timestamp, taskId, status, truncate(result, 200));
//
//        // 此处可以：
//        // 1. 在数据库中标记任务完成
//        // 2. 通知等待的客户端
//        // 3. 触发后续操作
//    }
//
//    private void handleFailureCallback(String containerId, String taskId,
//                                       String status, String result, String timestamp) {
//        log.error("[微信回调] [{}] 微信任务 {} {} - 错误：{}",
//                timestamp, taskId, status, truncate(result, 200));
//
//        // 此处可以：
//        // 1. 在数据库中标记任务失败
//        // 2. 发送警报/通知
//        // 3. 启动重试逻辑
//    }
//
//    private String truncate(String str, int maxLength) {
//        if (str == null) return null;
//        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
//    }
//
//    private String formatBytes(long bytes) {
//        if (bytes < 1024) return bytes + " B";
//        int exp = (int) (Math.log(bytes) / Math.log(1024));
//        String pre = "KMGTPE".charAt(exp - 1) + "B";
//        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
//    }
//}
