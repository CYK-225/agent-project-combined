package com.cyk.DockerTool.Weixin;//package com.cyk.baidu.DockerTool.Weixin;
//
//import com.cyk.baidu.DockerTool.V2.model.ContainerPod;
//import com.cyk.baidu.DockerTool.Weixin.config.WeChatDockerConfiguration;
//import lombok.Data;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.*;
//
///**
// * 微信 GUI 代理操作的 REST 控制器。
// * 提供提交任务和管理微信代理容器的端点。
// *
// * 与 V2 的 Chrome 实现完全隔离，使用独立的：
// * - 端口范围：9101-9119
// * - 镜像：wechat-agent:v2
// * - API 路径：/api/wechat/agent
// */
//@RestController
//@RequestMapping("/api/wechat/agent")
//public class AgentControllerWeixin {
//
//    private static final Logger log = LoggerFactory.getLogger(AgentControllerWeixin.class);
//
//    private final WeChatDockerPoolManager poolManager;
//    private final WeChatDockerConfiguration config;
//
//    public AgentControllerWeixin(WeChatDockerPoolManager poolManager, WeChatDockerConfiguration config) {
//        this.poolManager = poolManager;
//        this.config = config;
//    }
//
//    /**
//     * 提交微信 GUI 自动化任务。
//     * 必须提供用户名以实现用户-容器绑定和确定性 MAC 地址生成。
//     *
//     * @param request 任务请求（必须包含 username）
//     * @return 包含任务 ID 和容器信息的响应
//     */
//    @PostMapping("/task")
//    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody WeChatTaskRequest request) {
//        log.info("收到微信任务请求：username={}, instruction={}",
//                request.getUsername(), truncate(request.getInstruction(), 100));
//
//        // 验证请求
//        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
//            return ResponseEntity.badRequest().body(Map.of(
//                    "error", "指令不能为空",
//                    "app_type", "wechat"
//            ));
//        }
//
//        // 验证用户名（必须提供，用于确定性 MAC 地址生成）
//        if (request.getUsername() == null || request.getUsername().isBlank()) {
//            return ResponseEntity.badRequest().body(Map.of(
//                    "error", "用户名不能为空（用于生成确定性 MAC 地址，保持微信登录状态）",
//                    "app_type", "wechat"
//            ));
//        }
//
//        // 如果未提供则生成任务 ID
//        String taskId = request.getTaskId();
//        if (taskId == null || taskId.isBlank()) {
//            taskId = "wechat_task_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
//        }
//
//        // 获取配置文件名称（如未提供则使用用户名）
//        String profileName = request.getProfileName();
//        if (profileName == null || profileName.isBlank()) {
//            profileName = request.getUsername();
//        }
//
//        log.info("微信任务配置：用户={}, 配置文件={}", request.getUsername(), profileName);
//
//        try {
//            // 获取或创建容器 Pod（使用用户名进行绑定）
//            // MAC 地址将基于用户名确定性生成
//            // 注意：微信模块不需要区分读写模式，所有数据目录都以读写模式挂载
//            ContainerPod pod = poolManager.getOrCreatePod(request.getUsername(), profileName);
//
//            // 从请求中获取配置，如果未提供则使用默认值
//            String apiKey = request.getApiKey() != null && !request.getApiKey().isBlank()
//                    ? request.getApiKey() : config.getDefaultApiKey();
//            String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isBlank()
//                    ? request.getBaseUrl() : config.getDefaultBaseUrl();
//            String model = request.getModel() != null && !request.getModel().isBlank()
//                    ? request.getModel() : config.getDefaultModel();
//            int maxSteps = request.getMaxSteps() != null && request.getMaxSteps() > 0
//                    ? request.getMaxSteps() : config.getDefaultMaxSteps();
//
//            // 向容器分派任务
//            boolean dispatched = poolManager.dispatchTask(
//                    pod,
//                    taskId,
//                    request.getInstruction(),
//                    apiKey,
//                    baseUrl,
//                    model,
//                    maxSteps
//            );
//
//            if (dispatched) {
//                Map<String, Object> response = new LinkedHashMap<>();
//                response.put("status", "accepted");
//                response.put("task_id", taskId);
//                response.put("app_type", "wechat");
//                response.put("username", request.getUsername());
//                response.put("container_id", pod.getContainerId());
//                response.put("container_short_id", pod.getShortId());
//                response.put("api_port", pod.getAssignedPort());
//                response.put("vnc_port", pod.getVncPort());
//                response.put("profile", pod.getProfileName());
//                response.put("mac_address", pod.getMacAddress());
//                response.put("vnc_url", "http://localhost:" + pod.getVncPort() + "/vnc.html");
//                response.put("message", "微信任务分派成功");
//
//                log.info("微信任务 {} 已分派到容器 {}，用户 {}，MAC {}",
//                        taskId, pod.getShortId(), request.getUsername(), pod.getMacAddress());
//
//                return ResponseEntity.accepted().body(response);
//            } else {
//                return ResponseEntity.internalServerError().body(Map.of(
//                        "error", "向微信容器分派任务失败",
//                        "task_id", taskId,
//                        "username", request.getUsername(),
//                        "app_type", "wechat"
//                ));
//            }
//
//        } catch (IllegalStateException e) {
//            log.error("微信代理池已耗尽：{}", e.getMessage());
//            return ResponseEntity.status(503).body(Map.of(
//                    "error", "服务不可用：" + e.getMessage(),
//                    "task_id", taskId,
//                    "username", request.getUsername(),
//                    "app_type", "wechat"
//            ));
//
//        } catch (Exception e) {
//            log.error("提交微信任务失败：{}", e.getMessage(), e);
//            return ResponseEntity.internalServerError().body(Map.of(
//                    "error", "内部错误：" + e.getMessage(),
//                    "task_id", taskId,
//                    "username", request.getUsername(),
//                    "app_type", "wechat"
//            ));
//        }
//    }
//
//    /**
//     * 使用指定配置文件提交微信 GUI 自动化任务。
//     *
//     * @param profileName 配置文件名称
//     * @param request     任务请求
//     * @return 包含任务 ID 和容器信息的响应
//     */
//    @PostMapping("/task/{profileName}")
//    public ResponseEntity<Map<String, Object>> submitTaskWithProfile(
//            @PathVariable String profileName,
//            @RequestBody WeChatTaskRequest request) {
//
//        // 覆盖请求中的配置文件名称
//        request.setProfileName(profileName);
//        return submitTask(request);
//    }
//
//    /**
//     * 获取微信代理池统计信息。
//     *
//     * @return 池统计信息
//     */
//    @GetMapping("/pool/stats")
//    public ResponseEntity<Map<String, Object>> getPoolStats() {
//        Map<String, Object> stats = new LinkedHashMap<>(poolManager.getPoolStats());
//
//        // 添加配置信息
//        stats.put("maxPoolSize", config.getMaxPoolSize());
//        stats.put("portRange", config.getPortRangeStart() + "-" + config.getPortRangeEnd());
//        stats.put("idleTimeoutSeconds", config.getIdleTimeoutSeconds());
//        stats.put("imageName", config.getImageName());
//        stats.put("app_type", "wechat");
//
//        return ResponseEntity.ok(stats);
//    }
//
//    /**
//     * 获取所有活动的微信容器。
//     *
//     * @return 活动容器列表
//     */
//    @GetMapping("/pool/containers")
//    public ResponseEntity<List<Map<String, Object>>> getActiveContainers() {
//        List<Map<String, Object>> containers = new ArrayList<>();
//
//        for (ContainerPod pod : poolManager.getActivePods()) {
//            Map<String, Object> containerInfo = new LinkedHashMap<>();
//            containerInfo.put("containerId", pod.getContainerId());
//            containerInfo.put("shortId", pod.getShortId());
//            containerInfo.put("username", pod.getUsername());
//            containerInfo.put("apiPort", pod.getAssignedPort());
//            containerInfo.put("vncPort", pod.getVncPort());
//            containerInfo.put("profile", pod.getProfileName());
//            containerInfo.put("status", pod.getStatus().get().name());
//            containerInfo.put("currentTaskId", pod.getCurrentTaskId());
//            containerInfo.put("macAddress", pod.getMacAddress());
//            containerInfo.put("app_type", "wechat");
//
//            containers.add(containerInfo);
//        }
//
//        return ResponseEntity.ok(containers);
//    }
//
//    /**
//     * 获取特定微信容器状态。
//     *
//     * @param containerId 容器 ID
//     * @return 容器状态
//     */
//    @GetMapping("/container/{containerId}")
//    public ResponseEntity<Map<String, Object>> getContainerStatus(@PathVariable String containerId) {
//        Optional<ContainerPod> podOpt = poolManager.getPod(containerId);
//
//        if (podOpt.isEmpty()) {
//            return ResponseEntity.notFound().build();
//        }
//
//        ContainerPod pod = podOpt.get();
//        Map<String, Object> status = new LinkedHashMap<>();
//        status.put("containerId", pod.getContainerId());
//        status.put("shortId", pod.getShortId());
//        status.put("username", pod.getUsername());
//        status.put("apiPort", pod.getAssignedPort());
//        status.put("vncPort", pod.getVncPort());
//        status.put("profile", pod.getProfileName());
//        status.put("status", pod.getStatus().get().name());
//        status.put("currentTaskId", pod.getCurrentTaskId());
//        status.put("macAddress", pod.getMacAddress());
//        status.put("vncUrl", "http://localhost:" + pod.getVncPort() + "/vnc.html");
//        status.put("app_type", "wechat");
//
//        return ResponseEntity.ok(status);
//    }
//
//    /**
//     * 根据用户名获取微信容器状态。
//     * 查询用户绑定的容器信息。
//     *
//     * @param username 用户名
//     * @return 容器状态
//     */
//    @GetMapping("/user/{username}/container")
//    public ResponseEntity<Map<String, Object>> getContainerByUsername(@PathVariable String username) {
//        Optional<ContainerPod> podOpt = poolManager.getPodByUsername(username);
//
//        if (podOpt.isEmpty()) {
//            return ResponseEntity.ok(Map.of(
//                    "error", "用户没有绑定的微信容器",
//                    "username", username,
//                    "app_type", "wechat"
//            ));
//        }
//
//        ContainerPod pod = podOpt.get();
//        Map<String, Object> status = new LinkedHashMap<>();
//        status.put("containerId", pod.getContainerId());
//        status.put("shortId", pod.getShortId());
//        status.put("username", pod.getUsername());
//        status.put("apiPort", pod.getAssignedPort());
//        status.put("vncPort", pod.getVncPort());
//        status.put("profile", pod.getProfileName());
//        status.put("status", pod.getStatus().get().name());
//        status.put("currentTaskId", pod.getCurrentTaskId());
//        status.put("macAddress", pod.getMacAddress());
//        status.put("vncUrl", "http://localhost:" + pod.getVncPort() + "/vnc.html");
//        status.put("app_type", "wechat");
//
//        return ResponseEntity.ok(status);
//    }
//
//    /**
//     * 释放微信容器（将其标记为可用于新任务）。
//     *
//     * @param containerId 容器 ID
//     * @return 成功/失败响应
//     */
//    @PostMapping("/container/{containerId}/release")
//    public ResponseEntity<Map<String, Object>> releaseContainer(@PathVariable String containerId) {
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
//        poolManager.releasePod(containerId);
//
//        return ResponseEntity.ok(Map.of(
//                "status", "released",
//                "containerId", containerId,
//                "shortId", pod.getShortId(),
//                "app_type", "wechat"
//        ));
//    }
//
//    /**
//     * 停止并移除微信容器。
//     *
//     * @param containerId 容器 ID
//     * @return 成功/失败响应
//     */
//    @DeleteMapping("/container/{containerId}")
//    public ResponseEntity<Map<String, Object>> removeContainer(@PathVariable String containerId) {
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
//        poolManager.stopAndRemoveContainer(containerId);
//
//        return ResponseEntity.ok(Map.of(
//                "status", "removed",
//                "containerId", containerId,
//                "app_type", "wechat"
//        ));
//    }
//
//    /**
//     * 获取用户的确定性 MAC 地址。
//     * 用于调试和验证。
//     *
//     * @param username 用户名
//     * @return MAC 地址
//     */
//    @GetMapping("/user/{username}/mac")
//    public ResponseEntity<Map<String, Object>> getUserMacAddress(@PathVariable String username) {
//        String macAddress = poolManager.generateDeterministicMacAddress(username);
//
//        return ResponseEntity.ok(Map.of(
//                "username", username,
//                "macAddress", macAddress,
//                "description", "基于用户名 MD5 哈希生成的确定性 MAC 地址",
//                "app_type", "wechat"
//        ));
//    }
//
//    // ==================== 辅助方法 ====================
//
//    private String truncate(String str, int maxLength) {
//        if (str == null) return null;
//        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
//    }
//
//    // ==================== 内部类 ====================
//
//    /**
//     * 微信任务请求数据传输对象。
//     */
//    @Data
//    public static class WeChatTaskRequest {
//
//        /** 用户名（必填，用于容器绑定、确定性 MAC 地址生成和微信数据挂载） */
//        private String username;
//
//        /** 任务指令（必填） */
//        private String instruction;
//
//        /** 任务 ID（可选，不提供则自动生成） */
//        private String taskId;
//
//        /** API 密钥（可选，不提供则使用配置默认值） */
//        private String apiKey;
//
//        /** API 基础 URL（可选，不提供则使用配置默认值） */
//        private String baseUrl;
//
//        /** 模型名称（可选，不提供则使用配置默认值） */
//        private String model;
//
//        /** 最大步骤数（可选，不提供则使用配置默认值） */
//        private Integer maxSteps;
//
//        /** 配置文件名称（可选，不提供则使用用户名） */
//        private String profileName;
//
//        /** 额外参数 */
//        private Map<String, Object> extraParams = new HashMap<>();
//    }
//}
