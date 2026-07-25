package com.cyk.DockerTool.V2;


import com.cyk.DockerTool.V2.config.AgentPoolProperties;
import com.cyk.DockerTool.V2.model.ContainerPod;
import com.cyk.Utils.V2EmitterManager;
import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.Mapper.AuthInfoMapper;
import com.cyk.task.DAL.Service.impl.AuthInfoServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.yaml.snakeyaml.emitter.Emitter;

import java.util.*;
import java.util.stream.Collectors;



/**
 * GUI代理操作的REST控制器。
 * 提供提交任务和管理代理容器的端点。
 */
@RestController
@RequestMapping("/api/v2/agent")
public class AgentController {

    @Resource
    private V2EmitterManager emitterManager;

    @Resource
    private AuthInfoServiceImpl authInfoService;

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final DockerPoolManager poolManager;
    private final AgentPoolProperties properties;



    public AgentController(DockerPoolManager poolManager, AgentPoolProperties properties) {
        this.poolManager = poolManager;
        this.properties = properties;
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
     * 必须提供用户名以实现用户-容器绑定。
     *
     * @param request 任务请求（必须包含username）
     * @return 包含任务ID和容器信息的响应
     */
    @PostMapping("/task")
    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody TaskRequest request) {
        log.info("收到任务请求：username={}, instruction={}",
                request .getUsername(), truncate(request.getInstruction(), 100));

        // 验证请求
        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
            request.setInstruction("等候指令");
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
        String profileName = request.getProfileName() != null && !request.getProfileName().isBlank()
                ? request.getProfileName() : request.getUsername();
        int port = request.getPort();

        // 获取 isUpdateProfile 参数（默认为 false，即只读模式）
        boolean isUpdateProfile = request.getIsUpdateProfile() != null && request.getIsUpdateProfile();
        log.info("任务配置：isUpdateProfile={}（{}模式）", isUpdateProfile, isUpdateProfile ? "可写/养号" : "只读/搜索");

        try {
            // 获取或创建容器Pod（统一使用profileName进行隔离）
            ContainerPod pod = poolManager.getOrCreatePod(request.getUsername(), profileName, isUpdateProfile,port);

            // 从请求中获取配置，如果未提供则使用默认值
            String apiKey = request.getApiKey() != null && !request.getApiKey().isBlank()
                    ? request.getApiKey() : properties.getDefaultApiKey();
            String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isBlank()
                    ? request.getBaseUrl() : properties.getDefaultBaseUrl();
            String model = request.getModel() != null && !request.getModel().isBlank()
                    ? request.getModel() : properties.getDefaultModel();

            // 为了防止单个指令死循环，设置一个安全上限（默认比如15次点击）
            int maxSteps = request.getMaxSteps() != null && request.getMaxSteps() > 0 ? request.getMaxSteps() : 1500;

            poolManager.initTaskContext(taskId, pod, request.getInstruction(), apiKey, baseUrl, model, isUpdateProfile, maxSteps);

            poolManager.triggerNextStepAsync(taskId);

            // 把容器基础信息拼接到 Python 返回的结果里，一起给前端
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "accepted");
            response.put("task_id", taskId);
            response.put("username", request.getUsername());
            response.put("container_id", pod.getContainerId());
            response.put("vnc_port", pod.getVncPort());
            response.put("message", "任务已受理，正在后台进行单步回调调度...");

            return ResponseEntity.accepted().body(response);

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
     * 1. 拉起人工登录/养号环境
     * 作用：创建一个独占、可写的 V2 容器，控制 AI 打开目标网址后立即挂起，等待人类通过 VNC 操作。
     */
    @PostMapping("/task/updateProfile")
    public ResponseEntity<Map<String, Object>> submitUpdateProfileTask(@RequestBody TaskRequest  request) {

        String profileName = request.getProfileName();
        String webSite = request.getTargetUrl();


        if (profileName == null || webSite == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "profileName 和 targetUrl 不能为空"));
        }

        String taskId = "manual_login_" + System.currentTimeMillis();
        int port = request.getPort();

        try {
            // 核心：强制传入 true (isUpdateProfile=true)，确保人类在 VNC 里的操作能实时落盘到服务器本地
            ContainerPod pod = poolManager.getOrCreatePod(profileName, profileName, true, port);

            // 构造特殊的 Prompt，让大模型只做苦力（打开网页），不越俎代庖
            String instruction = String.format(
                    "请打开应用 'google-chrome'。然后在地址栏输入 '%s' 并回车访问。页面加载出来后，请立即执行 'terminate' 动作结束任务，千万不要尝试自己去点击登录！将控制权交给人类。",
                    webSite
            );

            // 获取默认模型配置
            String apiKey = properties.getDefaultApiKey();
            String baseUrl = properties.getDefaultBaseUrl();
            String model = properties.getDefaultModel();

            // 注册任务并异步发球
            poolManager.initTaskContext(taskId, pod, instruction, apiKey, baseUrl, model, true, 5);

            // 如果是默认的“等候指令”，说明只是连 VNC 占坑，绝对不能发球给 AI！
            if (!"等候指令".equals(request.getInstruction())) {
                poolManager.triggerNextStepAsync(taskId);
            } else {
                log.info("Task {} 仅初始化 VNC 环境，待机中，不触发 AI 回调循环。", taskId);
            }

            // 将 VNC 信息返回给前端，前端拿到 vnc_port 后直接渲染 noVNC 界面给用户看
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "env_ready");
            response.put("container_id", pod.getContainerId());
            response.put("vnc_port", pod.getVncPort());
            response.put("message", "虚拟浏览器环境已准备就绪，请在 VNC 画面中进行人工登录。");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("拉起人工环境失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "内部错误：" + e.getMessage()));
        }

    }


    /**
     * 2. 确认人工登录完成
     * 作用：将登录状态打标到数据库，并安全释放/销毁 Docker 容器
     */
    @PostMapping("/farm/setProfile")
    public ResponseEntity<Map<String, Object>> confirmManualLogin(@RequestBody Map<String, Object> params) {
        String profileName = (String) params.get("profileName");
        String targetUrl = (String) params.get("targetUrl");
        String containerId = (String) params.get("containerId"); // 前端第一步获取到的容器ID

        if (profileName == null || targetUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "参数缺失"));
        }

        try {
            // 1. 调用 Service 层处理数据库的 查/改/增 逻辑
            authInfoService.updateAuthStatus(profileName, targetUrl, true);

            // 2. 清理资源：登录态已经写死在本地硬盘了，这个容器留着也没用了，直接销毁释放服务器内存
            if (containerId != null) {
                poolManager.stopAndRemoveContainer(containerId);
                log.info("用户 {} 已完成手工登录，容器 {} 已销毁", profileName, containerId);
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "登录状态已成功保存至数据库，虚拟环境已安全释放。"
            ));

        } catch (Exception e) {
            log.error("保存登录态失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "保存失败：" + e.getMessage()));
        }
    }


    /**
     * 3. 获取配置列表
     * 作用：获取指定任务结果，并返回给前端
     */
    @GetMapping("/farm/profiles")
    public ResponseEntity<List<Map<String, Object>>> getFarmingProfiles() {
        try {
            // 1. 从 Service 获取扁平的数据库列表
            List<AuthInfoEntity> flatList = authInfoService.getProfileList();

            // 2. 使用 Java 8 Stream API 按配置名 (cloudStorageName) 进行分组
            Map<String, List<AuthInfoEntity>> groupedProfiles = flatList.stream()
                    .collect(Collectors.groupingBy(
                            // 确保 cloudStorageName 不为 null 时才分组
                            entity -> entity.getCloudStorageName() != null ? entity.getCloudStorageName() : "未命名配置"
                    ));

            // 3. 组装前端需要的树状/层级结构
            List<Map<String, Object>> resultList = new ArrayList<>();

            for (Map.Entry<String, List<AuthInfoEntity>> entry : groupedProfiles.entrySet()) {
                String profileName = entry.getKey();
                List<AuthInfoEntity> websites = entry.getValue();

                // 计算该配置下已登录的平台数量
                long loggedInCount = websites.stream()
                        .filter(w -> Boolean.TRUE.equals(w.getIsAvailable()))
                        .count();

                // 构造外层卡片节点
                Map<String, Object> profileNode = new LinkedHashMap<>();
                profileNode.put("profileName", profileName);
                profileNode.put("summary", "已登录 " + loggedInCount + "/" + websites.size() + " 个平台");

                // 将内部的网站列表进行格式化（可选：隐藏掉敏感信息，只传给前端必要字段）
                List<Map<String, Object>> websiteNodes = websites.stream().map(w -> {
                    Map<String, Object> webNode = new HashMap<>();
                    webNode.put("id", w.getId());
                    webNode.put("url", w.getWebsiteName()); // 目标网址
                    webNode.put("status", Boolean.TRUE.equals(w.getIsAvailable()) ? 1 : 0);
                    return webNode;
                }).collect(Collectors.toList());

                profileNode.put("websites", websiteNodes);

                resultList.add(profileNode);
            }

            // 4. 返回给前端
            return ResponseEntity.ok(resultList);

        } catch (Exception e) {
            log.error("获取配置列表失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
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

    /**
     * 单任务的多轮调度接口（乒乓回调版）。
     * 用户观察截图后，随时追加下一步提示词。
     */
    @PostMapping("/task/step")
    public ResponseEntity<Map<String, Object>> sendNextStep(
            @RequestBody TaskRequest request) {
        // 从 JSON 请求体中直接获取 taskId
        String taskId = request.getTaskId();

        log.info("收到追加指令 (Task: {}): {}", taskId, truncate(request.getInstruction(), 100));

        // 1. 校验指令是否为空
        // 1. 校验 taskId 和 指令 是否为空
        if (taskId == null || taskId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "taskId 不能为空"));
        }
        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "提示词/指令不能为空"));
        }

        // 【核心修复】：不再去 Pod 身上找 TaskId，直接从大管家永不失忆的 TaskContext 记忆库里取上下文！
        DockerPoolManager.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "未找到该任务的上下文记忆。可能任务已结束或被系统清理。"
            ));
        }

        ContainerPod pod = ctx.getPod();

        try {
            // 【核心修复】：更新大管家记忆库里的“最新指令”
            ctx.setInstruction(request.getInstruction());

            // 重要：重置当前步数为 0，让这个新指令能够重新获得完整的最大步数额度
            ctx.setCurrentStep(0);

            // 清除中止标记，允许任务重新启动
            ctx.setAborted(false);

            if (request.getMaxSteps() != null && request.getMaxSteps() > 0) {
                ctx.setMaxSteps(request.getMaxSteps());
            }

            // 【核心修复】：打出追加指令的”第一球”！(异步非阻塞发球)
            poolManager.triggerNextStepAsync(taskId);

            // 瞬间返回给前端，等待底层自动去打乒乓球
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "accepted");
            response.put("task_id", taskId);
            response.put("container_id", pod.getContainerId());
            response.put("vnc_port", pod.getVncPort());
            response.put("message", "追加指令已受理，正在后台进行单步回调调度...");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("单步执行发生错误：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "内部错误：" + e.getMessage(),
                    "task_id", taskId
            ));
        }
    }

    /**
     * 批量任务的多轮调度接口（队列版）。
     * 接收一个指令数组，系统会自动在前一条完成后，重置步数并执行下一条。
     */
    @PostMapping("/task/steps")
    public ResponseEntity<Map<String, Object>> sendNextSteps(
            @RequestBody TaskRequest request) {

        String taskId = request.getTaskId();
        List<String> instructions = request.getInstructions();

        log.info("收到批量追加指令 (Task: {}), 指令数量: {}", taskId, instructions != null ? instructions.size() : 0);

        if (taskId == null || taskId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "taskId 不能为空"));
        }
        if (instructions == null || instructions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "指令数组(instructions)不能为空"));
        }

        DockerPoolManager.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "未找到该任务的上下文记忆。可能任务已结束或被系统清理。"
            ));
        }

        ContainerPod pod = ctx.getPod();

        try {
            // 1. 获取上下文的队列，清空旧的未执行任务（如果存在），然后将新指令组加入
            Queue<String> queue = ctx.getInstructionQueue();
            queue.clear();
            queue.addAll(instructions);

            // 2. 取出数组中的第一条指令作为“当前指令”
            String firstInstruction = queue.poll();
            ctx.setInstruction(firstInstruction);

            // 3. 核心机制：重置当前步数为 0
            ctx.setCurrentStep(0);
            ctx.setAborted(false);

            if (request.getMaxSteps() != null && request.getMaxSteps() > 0) {
                ctx.setMaxSteps(request.getMaxSteps());
            }

            // 4. 异步打出第一球！
            poolManager.triggerNextStepAsync(taskId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "accepted");
            response.put("task_id", taskId);
            response.put("container_id", pod.getContainerId());
            response.put("current_instruction", firstInstruction);
            response.put("remaining_count", queue.size());
            response.put("message", "批量指令已受理，正在后台按顺序调度...");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("批量执行发生错误：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "内部错误：" + e.getMessage(),
                    "task_id", taskId
            ));
        }
    }

    /**
     * 强行中止当前任务
     */
    @PostMapping("/task/{taskId}/abort")
    public ResponseEntity<Map<String, Object>> abortTask(@PathVariable String taskId) {
        log.info("收到中止任务请求 (Task: {})", taskId);

        DockerPoolManager.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null || ctx.isAborted()) {
            return ResponseEntity.status(404).body(Map.of("error", "未找到该任务，或任务已结束/中止。"));
        }

        // 调用大管家执行强杀逻辑
        poolManager.abortTask(taskId);

        return ResponseEntity.ok(Map.of(
                "status", "aborted",
                "task_id", taskId,
                "message", "任务已成功中止"
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
        /** 用于接收前端传来的多条指令数组 */
        private List<String> instructions;
        /** 指定端口（可选，不提供则自动分配） */
        private int port;
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
        /** 目标网址 */
        private String targetUrl;
        /** 额外参数 */
        private Map<String, Object> extraParams = new HashMap<>();

    }
}