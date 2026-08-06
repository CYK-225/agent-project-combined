package org.example.agentScope.util.tool.DockerTool.V2;

import lombok.Data;
import org.example.masfanplus.AgentScope.util.DockerTool.V2.config.AgentPoolProperties;
import org.example.masfanplus.AgentScope.util.DockerTool.V2.model.ContainerPod;
import org.example.masfanplus.Controller.AgentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GUI代理操作的REST控制器。
 * 提供提交任务和管理代理容器的端点。
 */
@RestController
@RequestMapping("/api/v2/agent")
public class AgentV2Controller {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final DockerPoolManager poolManager;
    private final AgentPoolProperties properties;

    public AgentV2Controller(DockerPoolManager poolManager, AgentPoolProperties properties) {
        this.poolManager = poolManager;
        this.properties = properties;
    }

    /**
     * 提交GUI自动化任务。
     * 必须提供用户名以实现用户-容器绑定。
     *
     * @param request 任务请求（必须包含username）
     * @return 包含任务ID和容器信息的响应
     */
    @PostMapping("/task")
    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody TaskRequest request) {
        log.info("收到任务请求：username={}, instruction={}",
                request.getUsername(), truncate(request.getInstruction(), 100));



        // 验证请求
        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "指令不能为空"
            ));
        }

        // 验证用户名（必须提供）
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "用户名不能为空"
            ));
        }

        // 如果未提供则生成任务ID
        String taskId = request.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            taskId = "task_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        }

        // 获取配置文件名称（如未提供则使用用户名）
        String profileName = request.getProfileName();
        if (profileName == null || profileName.isBlank()) {
            profileName = request.getUsername(); // 默认使用用户名作为配置文件名
        }

        // 获取 isUpdateProfile 参数（默认为 false，即只读模式）
        boolean isUpdateProfile = request.getIsUpdateProfile() != null && request.getIsUpdateProfile();
        log.info("任务配置：isUpdateProfile={}（{}模式）", isUpdateProfile, isUpdateProfile ? "可写/养号" : "只读/搜索");

        try {
            // 获取或创建容器Pod（使用用户名进行绑定，传入 isUpdateProfile 参数）
            ContainerPod pod = poolManager.getOrCreatePod(request.getUsername(), profileName, isUpdateProfile);

            // 从请求中获取配置，如果未提供则使用默认值
            String apiKey = request.getApiKey() != null && !request.getApiKey().isBlank()
                    ? request.getApiKey() : properties.getDefaultApiKey();
            String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isBlank()
                    ? request.getBaseUrl() : properties.getDefaultBaseUrl();
            String model = request.getModel() != null && !request.getModel().isBlank()
                    ? request.getModel() : properties.getDefaultModel();
            int maxSteps = request.getMaxSteps() != null && request.getMaxSteps() > 0
                    ? request.getMaxSteps() : properties.getDefaultMaxSteps();

            // 向容器分派任务
            boolean dispatched = poolManager.dispatchTask(
                    pod,
                    taskId,
                    request.getInstruction(),
                    apiKey,
                    baseUrl,
                    model,
                    maxSteps,
                    isUpdateProfile
            );

            if (dispatched) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "accepted");
                response.put("task_id", taskId);
                response.put("username", request.getUsername());
                response.put("container_id", pod.getContainerId());
                response.put("container_short_id", pod.getShortId());
                response.put("api_port", pod.getAssignedPort());
                response.put("vnc_port", pod.getVncPort());
                response.put("profile", pod.getProfileName());
                response.put("message", "任务分派成功");

                return ResponseEntity.accepted().body(response);
            } else {
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "向容器分派任务失败",
                        "task_id", taskId,
                        "username", request.getUsername()
                ));
            }

        } catch (IllegalStateException e) {
            log.error("池已耗尽：{}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "服务不可用：" + e.getMessage(),
                    "task_id", taskId,
                    "username", request.getUsername()
            ));

        } catch (Exception e) {
            log.error("提交任务失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "内部错误：" + e.getMessage(),
                    "task_id", taskId,
                    "username", request.getUsername()
            ));
        }
    }

    /**
     * 使用指定配置文件提交GUI自动化任务。
     *
     * @param profileName 配置文件名称
     * @param request     任务请求
     * @return 包含任务ID和容器信息的响应
     */
    @PostMapping("/task/{profileName}")
    public ResponseEntity<Map<String, Object>> submitTaskWithProfile(
            @PathVariable String profileName,
            @RequestBody TaskRequest request) {

        // 覆盖请求中的配置文件名称
        request.setProfileName(profileName);
        return submitTask(request);
    }

    /**
     * 获取池统计信息。
     *
     * @return 池统计信息
     */
    @GetMapping("/pool/stats")
    public ResponseEntity<Map<String, Object>> getPoolStats() {
        Map<String, Object> stats = new LinkedHashMap<>(poolManager.getPoolStats());

        // 添加配置信息
        stats.put("maxPoolSize", properties.getMaxPoolSize());
        stats.put("portRange", properties.getPortRangeStart() + "-" + properties.getPortRangeEnd());
        stats.put("idleTimeoutSeconds", properties.getIdleTimeoutSeconds());

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取所有活动容器。
     *
     * @return 活动容器列表
     */
    @GetMapping("/pool/containers")
    public ResponseEntity<List<Map<String, Object>>> getActiveContainers() {
        List<Map<String, Object>> containers = new ArrayList<>();

        for (ContainerPod pod : poolManager.getActivePods()) {
            Map<String, Object> containerInfo = new LinkedHashMap<>();
            containerInfo.put("containerId", pod.getContainerId());
            containerInfo.put("shortId", pod.getShortId());
            containerInfo.put("username", pod.getUsername());
            containerInfo.put("apiPort", pod.getAssignedPort());
            containerInfo.put("vncPort", pod.getVncPort());
            containerInfo.put("profile", pod.getProfileName());
            containerInfo.put("status", pod.getStatus().get().name());
            containerInfo.put("currentTaskId", pod.getCurrentTaskId());

            containers.add(containerInfo);
        }

        return ResponseEntity.ok(containers);
    }

    /**
     * 获取特定容器状态。
     *
     * @param containerId 容器ID
     * @return 容器状态
     */
    @GetMapping("/container/{containerId}")
    public ResponseEntity<Map<String, Object>> getContainerStatus(@PathVariable String containerId) {
        Optional<ContainerPod> podOpt = poolManager.getPod(containerId);

        if (podOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ContainerPod pod = podOpt.get();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("containerId", pod.getContainerId());
        status.put("shortId", pod.getShortId());
        status.put("username", pod.getUsername());
        status.put("apiPort", pod.getAssignedPort());
        status.put("vncPort", pod.getVncPort());
        status.put("profile", pod.getProfileName());
        status.put("status", pod.getStatus().get().name());
        status.put("currentTaskId", pod.getCurrentTaskId());
        status.put("macAddress", pod.getMacAddress());

        return ResponseEntity.ok(status);
    }

    /**
     * 根据用户名获取容器状态。
     * 查询用户绑定的容器信息。
     *
     * @param username 用户名
     * @return 容器状态
     */
    @GetMapping("/user/{username}/container")
    public ResponseEntity<Map<String, Object>> getContainerByUsername(@PathVariable String username) {
        Optional<ContainerPod> podOpt = poolManager.getPodByUsername(username);

        if (podOpt.isEmpty()) {
            return ResponseEntity.ofNullable(Map.of(
                    "error", "用户没有绑定的容器",
                    "username", username
            ));
        }

        ContainerPod pod = podOpt.get();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("containerId", pod.getContainerId());
        status.put("shortId", pod.getShortId());
        status.put("username", pod.getUsername());
        status.put("apiPort", pod.getAssignedPort());
        status.put("vncPort", pod.getVncPort());
        status.put("profile", pod.getProfileName());
        status.put("status", pod.getStatus().get().name());
        status.put("currentTaskId", pod.getCurrentTaskId());
        status.put("macAddress", pod.getMacAddress());

        return ResponseEntity.ok(status);
    }

    /**
     * 释放容器（将其标记为可用于新任务）。
     *
     * @param containerId 容器ID
     * @return 成功/失败响应
     */
    @PostMapping("/container/{containerId}/release")
    public ResponseEntity<Map<String, Object>> releaseContainer(@PathVariable String containerId) {
        Optional<ContainerPod> podOpt = poolManager.getPod(containerId);

        if (podOpt.isEmpty()) {
            return ResponseEntity.ofNullable(Map.of(
                    "error", "容器未找到",
                    "containerId", containerId
            ));
        }

        ContainerPod pod = podOpt.get();
        poolManager.releasePod(containerId);

        return ResponseEntity.ok(Map.of(
                "status", "released",
                "containerId", containerId,
                "shortId", pod.getShortId()
        ));
    }

    /**
     * 停止并移除容器。
     *
     * @param containerId 容器ID
     * @return 成功/失败响应
     */
    @DeleteMapping("/container/{containerId}")
    public ResponseEntity<Map<String, Object>> removeContainer(@PathVariable String containerId) {
        Optional<ContainerPod> podOpt = poolManager.getPod(containerId);

        if (podOpt.isEmpty()) {
            return ResponseEntity.ofNullable(Map.of(
                    "error", "容器未找到",
                    "containerId", containerId
            ));
        }

        poolManager.stopAndRemoveContainer(containerId);

        return ResponseEntity.ok(Map.of(
                "status", "removed",
                "containerId", containerId
        ));
    }

    // ==================== 辅助方法 ====================

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    // ==================== 内部类 ====================

    /**
     * 任务请求数据传输对象。
     */
    @Data
    public static class TaskRequest {

        /** 用户名（必填，用于容器绑定和目录挂载） */
        private String username;
        /** 任务指令（必填） */
        private String instruction;


        /** 任务ID（可选，不提供则自动生成） */
        private String taskId;
        /** API密钥（可选，不提供则使用配置默认值） */
        private String apiKey;
        /** API基础URL（可选，不提供则使用配置默认值） */
        private String baseUrl;
        /** 模型名称（可选，不提供则使用配置默认值） */
        private String model;
        /** 最大步骤数（可选，不提供则使用配置默认值） */
        private Integer maxSteps;
        /** 配置文件名称（可选，不提供则使用用户名） */
        private String profileName;
        /** 是否更新配置文件（可选） */
        private Boolean isUpdateProfile;
        /** MAC地址（可选，不提供则自动生成） */
        private String macAddress;
        /** 额外参数 */
        private Map<String, Object> extraParams = new HashMap<>();

    }
}