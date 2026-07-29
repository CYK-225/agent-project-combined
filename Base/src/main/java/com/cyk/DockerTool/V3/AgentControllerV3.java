package com.cyk.DockerTool.V3;


import com.cyk.DockerTool.V3.config.AgentPoolPropertiesV3;
import com.cyk.DockerTool.V3.model.ContainerPodV3;
import com.cyk.acl.agent.AgentBridgeManager;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import com.cyk.Utils.V3EmitterManager;
import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.Service.impl.AuthInfoServiceImpl;
import jakarta.annotation.Resource;
import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


/**
 * V3 GUI代理操作的REST控制器。
 * 提供提交任务和管理代理容器的端点。
 * API路径：/api/v3/agent/
 */
@RestController
@RequestMapping("/api/v3/agent")
public class AgentControllerV3 {

    @Resource
    private V3EmitterManager emitterManager;

    @Resource
    private AuthInfoServiceImpl authInfoService;

    private static final Logger log = LoggerFactory.getLogger(AgentControllerV3.class);

    private final DockerPoolManagerV3 poolManager;
    private final AgentPoolPropertiesV3 properties;
    private final AgentBridgeManager agentBridgeManager;

    public AgentControllerV3(DockerPoolManagerV3 poolManager, AgentPoolPropertiesV3 properties, AgentBridgeManager agentBridgeManager) {
        this.poolManager = poolManager;
        this.properties = properties;
        this.agentBridgeManager = agentBridgeManager;
    }

    /**
     * 获取emitter(以容器id创建)
     */
    @PostMapping("/task/emitter/{containerId}")
    public SseEmitter getEmitter(@PathVariable String containerId) {
        return emitterManager.createEmitter(containerId, 0L);
    }

    /**
     * 提交GUI自动化任务。
     */
    @PostMapping("/task")
    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody TaskRequest request) {
        log.info("[V3] 收到任务请求：username={}, instruction={}",
                request.getUsername(), truncate(request.getInstruction(), 100));

        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
            // 如果 instruction 为空，尝试从 instructions 数组取第一个
            if (request.getInstructions() != null && !request.getInstructions().isEmpty()) {
                request.setInstruction(request.getInstructions().get(0));
                log.info("[V3] instruction 为空，从 instructions 数组取第一个: {}", request.getInstruction());
            } else {
                request.setInstruction("等候指令");
            }
        }

        String taskId = request.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            taskId = String.valueOf(System.currentTimeMillis());
        }

        String profileName = request.getProfileName() != null && !request.getProfileName().isBlank()
                ? request.getProfileName() : "default";
        int port = request.getPort();

        log.info("[V3] 任务配置：profileName={}, 只读模式", profileName);

        try {
            ContainerPodV3 pod = poolManager.createPod(profileName, port);

            String apiKey = request.getApiKey() != null && !request.getApiKey().isBlank()
                    ? request.getApiKey() : properties.getDefaultApiKey();
            String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isBlank()
                    ? request.getBaseUrl() : properties.getDefaultBaseUrl();
            String model = request.getModel() != null && !request.getModel().isBlank()
                    ? request.getModel() : properties.getDefaultModel();

            int maxSteps = request.getMaxSteps() != null && request.getMaxSteps() > 0 ? request.getMaxSteps() : 1500;

            poolManager.initTaskContext(Long.valueOf(taskId), pod, request.getInstruction(), apiKey, baseUrl, model, maxSteps);

            // ========== V2 原有逻辑（向容器发 /step 触发 LLM 循环）==========
            // poolManager.triggerNextStepAsync(taskId);
            // ================================================================

            // ========== V3 新逻辑（通过 AI 中台调度 AgentScope Agent）==========
            Integer containerPort = pod.getAssignedPort();
            String sessionId = taskId;
            String agentName = "hr-agent";

            // 绑定 SSE taskId → containerId，使后续 SSE 推送能找到正确的 emitter
            emitterManager.bindTask(taskId, pod.getContainerId());

            boolean invoked = agentBridgeManager.invokeAgent(
                    AgentTaskNotifyDTO.builder()
                            .agentName(agentName)
                            .sessionId(sessionId)
                            .taskId(taskId)
                            .instruction(request.getInstruction())
                            .promptsId(request.getPromptsId())
                            .build()
            );
            if (!invoked) {
                log.error("[V3] 调用 AI 中台失败，taskId: {}", taskId);
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "调用 AI 中台失败",
                        "task_id", taskId
                ));
            }
            log.info("[V3] 已通过 AI 中台调度任务，taskId: {}", taskId);
            // ==================================================================

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "accepted");
            response.put("task_id", taskId);
            response.put("username", request.getUsername());
            response.put("container_id", pod.getContainerId());
            response.put("profile", pod.getProfileName());
            response.put("vnc_port", pod.getVncPort());
            response.put("message", "[V3] 任务已受理，正在后台进行单步回调调度...");

            return ResponseEntity.accepted().body(response);

        } catch (IllegalStateException e) {
            log.error("[V3] 池已耗尽：{}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "服务不可用：" + e.getMessage(),
                    "task_id", taskId,
                    "username", request.getUsername()
            ));

        } catch (Exception e) {
            log.error("[V3] 提交任务失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "内部错误：" + e.getMessage(),
                    "task_id", taskId,
                    "username", request.getUsername()
            ));
        }
    }

    /**
     * 拉起人工登录/养号环境
     * @deprecated V3已改为全部只读模式，不再支持养号，保留接口供前端兼容
     */
    @PostMapping("/task/updateProfile")
    public ResponseEntity<Map<String, Object>> submitUpdateProfileTask(@RequestBody TaskRequest request) {

        String profileName = request.getProfileName();
        String webSite = request.getTargetUrl();

        if (profileName == null || webSite == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "profileName 和 targetUrl 不能为空"));
        }

        String taskId = String.valueOf(System.currentTimeMillis());
        int port = request.getPort();

        try {
            ContainerPodV3 pod = poolManager.createPod(profileName, port);

            String instruction = String.format(
                    "请打开应用 'google-chrome'。然后在地址栏输入 '%s' 并回车访问。页面加载出来后，请立即执行 'terminate' 动作结束任务，千万不要尝试自己去点击登录！将控制权交给人类。",
                    webSite
            );

            String apiKey = properties.getDefaultApiKey();
            String baseUrl = properties.getDefaultBaseUrl();
            String model = properties.getDefaultModel();

            poolManager.initTaskContext(Long.valueOf(taskId), pod, instruction, apiKey, baseUrl, model, 5);

            if (!"等候指令".equals(request.getInstruction())) {
                // ========== V2 原有逻辑（向容器发 /step 触发 LLM 循环）==========
                // poolManager.triggerNextStepAsync(taskId);
                // ================================================================

                // ========== V3 新逻辑（通过 AI 中台调度 AgentScope Agent）==========
                Integer containerPort = pod.getAssignedPort();
                String sessionId = taskId;
                String agentName = "hr-agent";

                boolean invoked = agentBridgeManager.invokeAgent(
                        AgentTaskNotifyDTO.builder()
                                .agentName(agentName)
                                .sessionId(sessionId)
                                .taskId(taskId)
                                .instruction(instruction)
                                .build()
                );
                if (!invoked) {
                    log.error("[V3] 调用 AI 中台失败，taskId: {}", taskId);
                    return ResponseEntity.internalServerError().body(Map.of(
                            "error", "调用 AI 中台失败",
                            "task_id", taskId
                    ));
                }
                log.info("[V3] 已通过 AI 中台调度任务，taskId: {}", taskId);
                // ==================================================================
            } else {
                log.info("[V3] Task {} 仅初始化 VNC 环境，待机中，不触发 AI 回调循环。", taskId);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "env_ready");
            response.put("container_id", pod.getContainerId());
            response.put("vnc_port", pod.getVncPort());
            response.put("message", "[V3] 虚拟浏览器环境已准备就绪，请在 VNC 画面中进行人工登录。");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[V3] 拉起人工环境失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "内部错误：" + e.getMessage()));
        }

    }

    /**
     * 确认人工登录完成
     */
    @PostMapping("/farm/setProfile")
    public ResponseEntity<Map<String, Object>> confirmManualLogin(@RequestBody Map<String, Object> params) {
        String profileName = (String) params.get("profileName");
        String targetUrl = (String) params.get("targetUrl");
        String containerId = (String) params.get("containerId");

        if (profileName == null || targetUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "参数缺失"));
        }

        try {
            authInfoService.updateAuthStatus(profileName, targetUrl, true);

            if (containerId != null) {
                poolManager.stopAndRemoveContainer(containerId);
                log.info("[V3] 用户 {} 已完成手工登录，容器 {} 已销毁", profileName, containerId);
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "[V3] 登录状态已成功保存至数据库，虚拟环境已安全释放。"
            ));

        } catch (Exception e) {
            log.error("[V3] 保存登录态失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "保存失败：" + e.getMessage()));
        }
    }

    /**
     * 获取配置列表
     */
    @GetMapping("/farm/profiles")
    public ResponseEntity<List<Map<String, Object>>> getFarmingProfiles() {
        try {
            List<AuthInfoEntity> flatList = authInfoService.getProfileList();

            Map<String, List<AuthInfoEntity>> groupedProfiles = flatList.stream()
                    .collect(Collectors.groupingBy(
                            entity -> entity.getCloudStorageName() != null ? entity.getCloudStorageName() : "未命名配置"
                    ));

            List<Map<String, Object>> resultList = new ArrayList<>();

            for (Map.Entry<String, List<AuthInfoEntity>> entry : groupedProfiles.entrySet()) {
                String profileName = entry.getKey();
                List<AuthInfoEntity> websites = entry.getValue();

                long loggedInCount = websites.stream()
                        .filter(w -> Boolean.TRUE.equals(w.getIsAvailable()))
                        .count();

                Map<String, Object> profileNode = new LinkedHashMap<>();
                profileNode.put("profileName", profileName);
                profileNode.put("summary", "已登录 " + loggedInCount + "/" + websites.size() + " 个平台");

                List<Map<String, Object>> websiteNodes = websites.stream().map(w -> {
                    Map<String, Object> webNode = new HashMap<>();
                    webNode.put("id", w.getId());
                    webNode.put("url", w.getWebsiteName());
                    webNode.put("status", Boolean.TRUE.equals(w.getIsAvailable()) ? 1 : 0);
                    return webNode;
                }).collect(Collectors.toList());

                profileNode.put("websites", websiteNodes);

                resultList.add(profileNode);
            }

            return ResponseEntity.ok(resultList);

        } catch (Exception e) {
            log.error("[V3] 获取配置列表失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 使用指定配置文件提交GUI自动化任务。
     */
    @PostMapping("/task/{profileName}")
    public ResponseEntity<Map<String, Object>> submitTaskWithProfile(
            @PathVariable String profileName,
            @RequestBody TaskRequest request) {

        request.setProfileName(profileName);
        return submitTask(request);
    }

    /**
     * 获取池统计信息。
     */
    @GetMapping("/pool/stats")
    public ResponseEntity<Map<String, Object>> getPoolStats() {
        Map<String, Object> stats = new LinkedHashMap<>(poolManager.getPoolStats());

        stats.put("maxPoolSize", properties.getMaxPoolSize());
        stats.put("portRange", properties.getPortRangeStart() + "-" + properties.getPortRangeEnd());
        stats.put("idleTimeoutSeconds", properties.getIdleTimeoutSeconds());

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取所有活动容器。
     */
    @GetMapping("/pool/containers")
    public ResponseEntity<List<Map<String, Object>>> getActiveContainers() {
        List<Map<String, Object>> containers = new ArrayList<>();

        for (ContainerPodV3 pod : poolManager.getActivePods()) {
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
     */
    @GetMapping("/container/{containerId}")
    public ResponseEntity<Map<String, Object>> getContainerStatus(@PathVariable String containerId) {
        Optional<ContainerPodV3> podOpt = poolManager.getPod(containerId);

        if (podOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ContainerPodV3 pod = podOpt.get();
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
     * 根据用户名获取容器状态（已废弃：一任务一容器模式下不再维护用户-容器绑定）。
     * 改用 GET /api/v3/agent/container/{containerId} 按容器ID查询。
     */
    @GetMapping("/user/{username}/container")
    @Deprecated
    public ResponseEntity<Map<String, Object>> getContainerByUsername(@PathVariable String username) {
        return ResponseEntity.notFound().build();
    }

    /**
     * 释放容器
     */
    @PostMapping("/container/{containerId}/release")
    public ResponseEntity<Map<String, Object>> releaseContainer(@PathVariable String containerId) {
        Optional<ContainerPodV3> podOpt = poolManager.getPod(containerId);

        if (podOpt.isEmpty()) {
            return ResponseEntity.ofNullable(Map.of(
                    "error", "[V3] 容器未找到",
                    "containerId", containerId
            ));
        }

        ContainerPodV3 pod = podOpt.get();
        poolManager.releasePod(containerId);

        return ResponseEntity.ok(Map.of(
                "status", "released",
                "containerId", containerId,
                "shortId", pod.getShortId()
        ));
    }

    /**
     * 停止并移除容器。
     */
    @DeleteMapping("/container/{containerId}")
    public ResponseEntity<Map<String, Object>> removeContainer(@PathVariable String containerId) {
        Optional<ContainerPodV3> podOpt = poolManager.getPod(containerId);

        if (podOpt.isEmpty()) {
            return ResponseEntity.ofNullable(Map.of(
                    "error", "[V3] 容器未找到",
                    "containerId", containerId
            ));
        }

        poolManager.stopAndRemoveContainer(containerId);

        return ResponseEntity.ok(Map.of(
                "status", "removed",
                "containerId", containerId
        ));
    }

    /**
     * 单任务的多轮调度接口（乒乓回调版）。
     */
    @PostMapping("/task/step")
    public ResponseEntity<Map<String, Object>> sendNextStep(
            @RequestBody TaskRequest request) {
        String taskId = request.getTaskId();

        log.info("[V3] 收到追加指令 (Task: {}): {}", taskId, truncate(request.getInstruction(), 100));

        if (taskId == null || taskId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "taskId 不能为空"));
        }
        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "提示词/指令不能为空"));
        }

        DockerPoolManagerV3.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "[V3] 未找到该任务的上下文记忆。可能任务已结束或被系统清理。"
            ));
        }

        ContainerPodV3 pod = ctx.getPod();

        try {
            ctx.setInstruction(request.getInstruction());

            ctx.setCurrentStep(0);

            ctx.setAborted(false);

            if (request.getMaxSteps() != null && request.getMaxSteps() > 0) {
                ctx.setMaxSteps(request.getMaxSteps());
            }

            // ========== V2 原有逻辑（向容器发 /step 触发 LLM 循环）==========
            // poolManager.triggerNextStepAsync(taskId);
            // ================================================================

            // ========== V3 新逻辑（通过 AI 中台调度 AgentScope Agent）==========
            Integer containerPort = pod.getAssignedPort();
            String sessionId = taskId;
            String agentName = "hr-agent";

            boolean invoked = agentBridgeManager.invokeAgent(
                    AgentTaskNotifyDTO.builder()
                            .agentName(agentName)
                            .sessionId(sessionId)
                            .taskId(taskId)
                            .instruction(request.getInstruction())
                            .build()
            );
            if (!invoked) {
                log.error("[V3] 调用 AI 中台失败，taskId: {}", taskId);
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "调用 AI 中台失败",
                        "task_id", taskId
                ));
            }
            log.info("[V3] 已通过 AI 中台调度任务，taskId: {}", taskId);
            // ==================================================================

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "accepted");
            response.put("task_id", taskId);
            response.put("container_id", pod.getContainerId());
            response.put("vnc_port", pod.getVncPort());
            response.put("message", "[V3] 追加指令已受理，正在后台进行单步回调调度...");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("[V3] 单步执行发生错误：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "内部错误：" + e.getMessage(),
                    "task_id", taskId
            ));
        }
    }

    /**
     * 批量任务的多轮调度接口（队列版）。
     */
    @PostMapping("/task/steps")
    public ResponseEntity<Map<String, Object>> sendNextSteps(
            @RequestBody TaskRequest request) {

        String taskId = request.getTaskId();
        List<String> instructions = request.getInstructions();

        log.info("[V3] 收到批量追加指令 (Task: {}), 指令数量: {}", taskId, instructions != null ? instructions.size() : 0);

        if (taskId == null || taskId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "taskId 不能为空"));
        }
        if (instructions == null || instructions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "指令数组(instructions)不能为空"));
        }

        DockerPoolManagerV3.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "[V3] 未找到该任务的上下文记忆。可能任务已结束或被系统清理。"
            ));
        }

        ContainerPodV3 pod = ctx.getPod();

        try {
            Queue<String> queue = ctx.getInstructionQueue();
            queue.clear();
            queue.addAll(instructions);

            String firstInstruction = queue.poll();
            ctx.setInstruction(firstInstruction);

            ctx.setCurrentStep(0);
            ctx.setAborted(false);

            if (request.getMaxSteps() != null && request.getMaxSteps() > 0) {
                ctx.setMaxSteps(request.getMaxSteps());
            }

            // ========== V2 原有逻辑（向容器发 /step 触发 LLM 循环）==========
            // poolManager.triggerNextStepAsync(taskId);
            // ================================================================

            // ========== V3 新逻辑（通过 AI 中台调度 AgentScope Agent）==========
            Integer containerPort = pod.getAssignedPort();
            String sessionId = taskId;
            String agentName = "hr-agent";

            boolean invoked = agentBridgeManager.invokeAgent(
                    AgentTaskNotifyDTO.builder()
                            .agentName(agentName)
                            .sessionId(sessionId)
                            .taskId(taskId)
                            .instruction(firstInstruction)
                            .build()
            );
            if (!invoked) {
                log.error("[V3] 调用 AI 中台失败，taskId: {}", taskId);
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "调用 AI 中台失败",
                        "task_id", taskId
                ));
            }
            log.info("[V3] 已通过 AI 中台调度批量任务，taskId: {}", taskId);
            // ==================================================================

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "accepted");
            response.put("task_id", taskId);
            response.put("container_id", pod.getContainerId());
            response.put("current_instruction", firstInstruction);
            response.put("remaining_count", queue.size());
            response.put("message", "[V3] 批量指令已受理，正在后台按顺序调度...");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("[V3] 批量执行发生错误：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "内部错误：" + e.getMessage(),
                    "task_id", taskId
            ));
        }
    }

    /**
     * 软中止当前任务（停止任务执行，但保留容器不销毁）
     * <p>
     * 中止后容器会从 BUSY 回退到 READY 状态，可接受新任务。
     * </p>
     */
    @PostMapping("/task/{taskId}/abort")
    public ResponseEntity<Map<String, Object>> abortTask(@PathVariable String taskId) {
        log.info("[V3] 收到软中止任务请求 (Task: {})", taskId);

        DockerPoolManagerV3.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null || ctx.isAborted()) {
            return ResponseEntity.status(404).body(Map.of("error", "[V3] 未找到该任务，或任务已结束/中止。"));
        }

        poolManager.abortTask(taskId);

        return ResponseEntity.ok(Map.of(
                "status", "aborted",
                "task_id", taskId,
                "message", "[V3] 任务已软中止，容器已保留可复用"
        ));
    }

    // ==================== 调试接口（connect + execute）====================

    /**
     * 连接接口：创建容器，返回VNC信息，不调用AI中台。
     * <p>
     * 前端拿到VNC地址后可直接连接查看容器桌面，
     * 后续通过 /task/execute 发送提示词执行任务。
     * </p>
     *
     * @param request 任务请求（只需 profileName，port 可选）
     * @return 容器信息（taskId, containerId, vncPort）
     */
    @PostMapping("/task/connect")
    public ResponseEntity<Map<String, Object>> connectTask(@RequestBody TaskRequest request) {
        log.info("[V3] 收到连接请求：profileName={}", request.getProfileName());

        String taskId = request.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            taskId = String.valueOf(System.currentTimeMillis());
        }

        String profileName = request.getProfileName() != null && !request.getProfileName().isBlank()
                ? request.getProfileName() : "default";
        int port = request.getPort();

        try {
            // 1. 创建容器（只读模式）
            ContainerPodV3 pod = poolManager.createPod(profileName, port);

            // 2. 初始化任务上下文（不调用AI中台）
            String apiKey = request.getApiKey() != null && !request.getApiKey().isBlank()
                    ? request.getApiKey() : properties.getDefaultApiKey();
            String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isBlank()
                    ? request.getBaseUrl() : properties.getDefaultBaseUrl();
            String model = request.getModel() != null && !request.getModel().isBlank()
                    ? request.getModel() : properties.getDefaultModel();
            int maxSteps = request.getMaxSteps() != null && request.getMaxSteps() > 0 ? request.getMaxSteps() : 1500;

            poolManager.initTaskContext(Long.valueOf(taskId), pod, "等候指令", apiKey, baseUrl, model, maxSteps);

            // 3. 绑定 SSE
            emitterManager.bindTask(taskId, pod.getContainerId());

            // 4. 返回容器信息
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "connected");
            response.put("task_id", taskId);
            response.put("container_id", pod.getContainerId());
            response.put("profile", pod.getProfileName());
            response.put("vnc_port", pod.getVncPort());
            response.put("container_url", properties.getCallbackBaseUrl() + ":" + pod.getAssignedPort());
            response.put("message", "[V3] 容器已就绪，请通过VNC连接查看。使用 /task/execute 接口发送提示词执行任务。");

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.error("[V3] 池已耗尽：{}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "服务不可用：" + e.getMessage(),
                    "task_id", taskId
            ));

        } catch (Exception e) {
            log.error("[V3] 连接失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "内部错误：" + e.getMessage(),
                    "task_id", taskId
            ));
        }
    }

    /**
     * 执行接口：接收提示词数组，异步执行任务。
     * <p>
     * 前端调用此接口后立即返回taskId，通过SSE监听执行进度。
     * 后台按顺序执行：系统提示词初始化 → 步骤1 → 步骤2 → ...
     * 每步等待AI中台完成后才执行下一步。
     * </p>
     *
     * @param request 任务请求（需 taskId, sysPrompts, stepPrompts）
     * @return taskId（立即返回，异步执行）
     */
    @PostMapping("/task/execute")
    public ResponseEntity<Map<String, Object>> executeTask(@RequestBody ExecuteRequest request) {
        String taskId = request.getTaskId();
        List<Map<String, Object>> sysPrompts = request.getSysPrompts();
        List<String> stepPrompts = request.getStepPrompts();

        log.info("[V3] 收到执行请求，taskId: {}, sysPrompts数量: {}, stepPrompts数量: {}",
                taskId,
                sysPrompts != null ? sysPrompts.size() : 0,
                stepPrompts != null ? stepPrompts.size() : 0);

        // 1. 参数校验
        if (taskId == null || taskId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "taskId 不能为空"));
        }
        if (stepPrompts == null || stepPrompts.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stepPrompts 不能为空"));
        }

        // 2. 获取任务上下文
        DockerPoolManagerV3.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "未找到该任务的上下文。请先调用 /task/connect 创建容器。"
            ));
        }

        // 3. 格式化系统提示词
        String sysPrompt = assembleSystemPrompt(sysPrompts);

        // 4. 异步执行任务
        String finalTaskId = taskId;
        CompletableFuture.runAsync(() -> {
            try {
                executeStepsAsync(finalTaskId, ctx, sysPrompt, stepPrompts);
            } catch (Exception e) {
                log.error("[V3] 异步执行任务失败，taskId: {}", finalTaskId, e);
                emitterManager.sendJsonEventByTaskId(finalTaskId, Map.of(
                        "taskId", finalTaskId,
                        "status", "FAILED",
                        "errorMessage", e.getMessage()
                ));
            }
        });

        // 5. 立即返回taskId
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "accepted");
        response.put("task_id", taskId);
        response.put("message", "[V3] 任务已受理，正在后台异步执行。请通过SSE监听进度。");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * 异步执行步骤提示词
     */
    private void executeStepsAsync(String taskId, DockerPoolManagerV3.TaskContext ctx,
                                   String sysPrompt, List<String> stepPrompts) {
        log.info("[V3] 开始异步执行任务，taskId: {}, 总步骤数: {}", taskId, stepPrompts.size());

        // 推送开始事件
        emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                "taskId", taskId,
                "status", "RUNNING",
                "totalSteps", stepPrompts.size(),
                "message", "任务开始执行"
        ));

        // 逐个执行步骤
        for (int i = 0; i < stepPrompts.size(); i++) {
            int step = i + 1;
            String stepPrompt = stepPrompts.get(i);

            // 检查是否被中止
            if (ctx.isAborted()) {
                log.info("[V3] 任务被中止，taskId: {}, 当前步骤: {}", taskId, step);
                emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                        "taskId", taskId,
                        "status", "ABORTED",
                        "step", step,
                        "message", "任务已被用户中止"
                ));
                return;
            }

            log.info("[V3] 执行步骤 {}/{}, taskId: {}", step, stepPrompts.size(), taskId);

            // 推送步骤开始事件
            emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                    "taskId", taskId,
                    "status", "STEP_RUNNING",
                    "step", step,
                    "totalSteps", stepPrompts.size(),
                    "instruction", stepPrompt,
                    "message", "开始执行步骤 " + step
            ));

            // 设置当前指令
            ctx.setInstruction(stepPrompt);
            ctx.setCurrentStep(0);

            // 构造DTO
            AgentTaskNotifyDTO dto = AgentTaskNotifyDTO.builder()
                    .agentName("hr-agent")
                    .taskId(taskId)
                    .sessionId(taskId)
                    .sysPrompt(sysPrompt)
                    .instruction(stepPrompt)
                    .step(step)
                    .containerUrl(properties.getCallbackBaseUrl() + ":" + ctx.getPod().getAssignedPort())
                    .build();

            // 调用AI中台
            boolean invoked = agentBridgeManager.invokeAgent(dto);
            if (!invoked) {
                log.error("[V3] 调用AI中台失败，taskId: {}, step: {}", taskId, step);
                emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                        "taskId", taskId,
                        "status", "FAILED",
                        "step", step,
                        "errorMessage", "调用AI中台失败"
                ));
                return;
            }

            // 推送步骤完成事件
            emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                    "taskId", taskId,
                    "status", "STEP_COMPLETED",
                    "step", step,
                    "totalSteps", stepPrompts.size(),
                    "message", "步骤 " + step + " 执行完成"
            ));
        }

        // 全部步骤完成
        log.info("[V3] 所有步骤执行完成，taskId: {}", taskId);
        emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                "taskId", taskId,
                "status", "COMPLETED",
                "totalSteps", stepPrompts.size(),
                "message", "所有步骤执行完成"
        ));
    }

    // ==================== 系统提示词格式化 ====================

    /**
     * 格式化系统提示词
     * <p>
     * 将系统提示词数组按type分类拼装：
     * - type=1 → ## 角色设定
     * - type=2 → ## 物理规则
     * - type=4 → ## 背景知识
     * </p>
     *
     * @param sysPrompts 系统提示词数组 [{type:1, content:"..."}, {type:2, content:"..."}]
     * @return 格式化后的系统提示词
     */
    private String assembleSystemPrompt(List<Map<String, Object>> sysPrompts) {
        if (sysPrompts == null || sysPrompts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        appendByType(sb, sysPrompts, 1, "角色设定");
        appendByType(sb, sysPrompts, 2, "物理规则");
        appendByType(sb, sysPrompts, 4, "背景知识");
        return sb.toString().trim();
    }

    private void appendByType(StringBuilder sb, List<Map<String, Object>> prompts, int type, String sectionTitle) {
        for (Map<String, Object> prompt : prompts) {
            Object typeObj = prompt.get("type");
            Object contentObj = prompt.get("content");

            if (typeObj != null && contentObj != null) {
                int promptType = Integer.parseInt(typeObj.toString());
                String content = contentObj.toString();

                if (promptType == type && !content.isBlank()) {
                    sb.append("## ").append(sectionTitle).append("\n");
                    sb.append(content).append("\n\n");
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    // ==================== 内部类 ====================

    @Data
    public static class TaskRequest {

        private String username;
        private String instruction;
        private List<String> instructions;
        private int port;
        private String taskId;
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer maxSteps;
        private String profileName;
        private Boolean isUpdateProfile;
        private String macAddress;
        private String targetUrl;
        private String promptsId;
        private Map<String, Object> extraParams = new HashMap<>();

    }

    /**
     * execute 接口的请求体
     */
    @Data
    public static class ExecuteRequest {
        /**
         * 任务ID（connect接口返回的）
         */
        private String taskId;

        /**
         * 系统提示词数组
         * 格式：[{"type": 1, "content": "角色设定内容..."}, {"type": 2, "content": "物理规则内容..."}]
         * type: 1=角色设定, 2=物理规则, 4=背景知识
         */
        private List<Map<String, Object>> sysPrompts;

        /**
         * 步骤提示词数组
         * 格式：["步骤1提示词", "步骤2提示词", "步骤3提示词"]
         * 按顺序执行，step从1开始
         */
        private List<String> stepPrompts;
    }
}
