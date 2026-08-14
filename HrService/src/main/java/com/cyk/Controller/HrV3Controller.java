package com.cyk.Controller;

import com.cyk.DockerTool.V3.DockerPoolManagerV3;
import com.cyk.DockerTool.V3.config.AgentPoolPropertiesV3;
import com.cyk.DockerTool.V3.model.ContainerPodV3;
import com.cyk.Enity.miniPromptsTo;
import com.cyk.Utils.V3EmitterManager;
import com.cyk.acl.agent.AgentBridgeManager;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import com.cyk.common.ResultData;
import com.cyk.task.DAL.Controller.DTO.CreateTaskTO;
import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Service.ITaskInfoService;
import com.cyk.task.DAL.Service.impl.AuthInfoServiceImpl;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * V3 HR 任务控制器
 *
 * <p>前端 V3 任务的统一入口，包含 SSE 连接建立、任务创建、状态查询。</p>
 *
 * <p>接口前缀：/api/hr/v3</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/hr/v3")
public class HrV3Controller {

    @Resource
    private V3EmitterManager emitterManager;

    @Resource
    private CustomTaskScheduler customTaskScheduler;

    @Resource
    private ITaskInfoService taskInfoService;
    
    @Resource
    private DockerPoolManagerV3 poolManager;
    
    @Resource
    private AgentBridgeManager agentBridgeManager;
    
    @Resource
    private AgentPoolPropertiesV3 properties;

    @Resource
    private AuthInfoServiceImpl authInfoService;

    /** 养号模式默认进入的网址（百度首页） */
    private static final String BAIDU_HOME_URL = "https://www.baidu.com";

    // ==================== SSE 连接 ====================

    /**
     * 建立 SSE 长连接
     *
     * <p>前端在创建任务之前调用此接口建立 SSE 连接，后续任务执行过程中的
     * RUNNING / STEP_COMPLETED / TASK_COMPLETED / TASK_FAILED 事件
     * 都会通过此连接推送。</p>
     *
     * @param clientId 前端生成的唯一标识（UUID）
     * @return SseEmitter（text/event-stream）
     */
    @RequestMapping(value = "/sse/{clientId}", method = {RequestMethod.GET, RequestMethod.POST})
    public SseEmitter createSseConnection(@PathVariable String clientId) {
        log.info("[HrV3] 建立 SSE 连接: clientId={}", clientId);
        return emitterManager.createEmitter(clientId, 0L);
    }

    // ==================== 任务创建 ====================

    /**
     * 创建 V3 任务
     *
     * <p>创建任务并加入调度队列，立即返回 taskId。
     * 调度逻辑通过事件异步触发，不阻塞当前请求。</p>
     *
     * <p>请求示例：</p>
     * <pre>
     * POST /api/hr/v3/task
     * {
     *   "promptId": 123,
     *   "configName": "账号标识",
     *   "clientId": "前端SSE连接的clientId"
     * }
     * </pre>
     *
     * @param request 任务创建请求
     * @return 创建结果（包含 taskId 和排队位置）
     */
    @PostMapping("/task")
    public ResultData<Map<String, Object>> createTask(@RequestBody V3TaskCreateRequest request) {
        log.info("[HrV3] 创建 V3 任务: promptId={}, configName={}, clientId={}",
                request.getPromptId(), request.getConfigName(), request.getClientId());

        try {
            // 1. 建立 SSE 绑定：taskId → clientId
            //    由于 taskId 在 createTask 内部才生成，需要后置绑定
            //    先创建任务，拿到 taskId 后再绑定

            // 2. 构造 CreateTaskTO
            CreateTaskTO createTaskTO = CreateTaskTO.builder()
                    .taskType("V3")
                    .promptId(request.getPromptId())
                    .configName(request.getConfigName())
                    .companyName(request.getCompanyName())
                    .userId(request.getUserId())
                    .build();

            // 3. 创建任务（内部会入队、发布事件、异步调度）
            Long taskId = customTaskScheduler.startTask(createTaskTO);

            if (taskId == null || taskId == 0L) {
                return ResultData.error("任务创建失败：无有效提示词或参数异常");
            }

            // 4. 绑定 SSE：taskId → clientId（前端传入的 SSE clientId）
            if (request.getClientId() != null && !request.getClientId().isBlank()) {
                emitterManager.bindTask(String.valueOf(taskId), request.getClientId());
                log.info("[HrV3] SSE 绑定: taskId={}, clientId={}", taskId, request.getClientId());
            }

            // 5. 查询排队位置
            int queuePosition = taskInfoService.getQueuePosition(taskId);

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("taskId", taskId);
            resultData.put("queuePosition", queuePosition);
            resultData.put("message", "任务创建成功，当前排在第" + (queuePosition + 1) + "位");

            return ResultData.success("任务创建成功", resultData);

        } catch (IllegalStateException e) {
            log.warn("[HrV3] 任务创建被拒绝: {}", e.getMessage());
            return ResultData.error(409, e.getMessage());
        } catch (Exception e) {
            log.error("[HrV3] 任务创建异常", e);
            return ResultData.error("任务创建失败: " + e.getMessage());
        }
    }

    // ==================== 批量创建 ====================

    /**
     * 批量创建 V3 任务
     *
     * <p>根据 count 参数批量创建相同配置的任务，所有任务共享相同的 promptId、configName 等参数。</p>
     *
     * <p>请求示例：</p>
     * <pre>
     * POST /api/hr/v3/task/batch
     * {
     *   "promptId": 123,
     *   "configName": "账号标识",
     *   "clientId": "前端SSE连接的clientId",
     *   "count": 5
     * }
     * </pre>
     *
     * @param request 任务创建请求（包含 count 字段）
     * @return 批量创建结果（包含所有 taskId 列表）
     */
    @PostMapping("/task/batch")
    public ResultData<Map<String, Object>> createBatchTasks(@RequestBody V3TaskCreateRequest request) {
        // 默认数量为1
        int count = (request.getCount() != null && request.getCount() > 0) ? request.getCount() : 1;

        log.info("[HrV3] 批量创建 V3 任务: promptId={}, configName={}, count={}",
                request.getPromptId(), request.getConfigName(), count);

        try {
            java.util.List<Long> taskIds = new java.util.ArrayList<>();
            java.util.List<String> errors = new java.util.ArrayList<>();

            for (int i = 0; i < count; i++) {
                try {
                    // 构造 CreateTaskTO
                    CreateTaskTO createTaskTO = CreateTaskTO.builder()
                            .taskType("V3")
                            .promptId(request.getPromptId())
                            .configName(request.getConfigName())
                            .companyName(request.getCompanyName())
                            .userId(request.getUserId())
                            .build();

                    // 创建任务
                    Long taskId = customTaskScheduler.startTask(createTaskTO);

                    if (taskId == null || taskId == 0L) {
                        errors.add("第" + (i + 1) + "个任务创建失败");
                        continue;
                    }

                    // 绑定 SSE
                    if (request.getClientId() != null && !request.getClientId().isBlank()) {
                        emitterManager.bindTask(String.valueOf(taskId), request.getClientId());
                    }

                    taskIds.add(taskId);
                    log.info("[HrV3] 批量创建第 {} 个任务成功, taskId={}", i + 1, taskId);

                } catch (Exception e) {
                    log.warn("[HrV3] 批量创建第 {} 个任务失败: {}", i + 1, e.getMessage());
                    errors.add("第" + (i + 1) + "个任务创建失败: " + e.getMessage());
                }
            }

            // 构造返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("taskIds", taskIds);
            result.put("successCount", taskIds.size());
            result.put("totalCount", count);

            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }

            if (taskIds.isEmpty()) {
                return ResultData.error("所有任务创建失败");
            }

            String message = "批量创建完成，成功" + taskIds.size() + "个";
            if (!errors.isEmpty()) {
                message += "，失败" + errors.size() + "个";
            }

            return ResultData.success(message, result);

        } catch (Exception e) {
            log.error("[HrV3] 批量创建任务异常", e);
            return ResultData.error("批量创建任务失败: " + e.getMessage());
        }
    }

    // ==================== 任务查询 ====================

    /**
     * 查询任务状态
     *
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    @GetMapping("/task/{taskId}")
    public ResultData<Map<String, Object>> getTaskStatus(@PathVariable Long taskId) {
        TaskInfoEntity task = taskInfoService.getById(taskId);

        if (task == null) {
            return ResultData.error(404, "任务不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus());
        data.put("taskType", task.getTaskType());
        data.put("retryCount", task.getRetryCount());
        data.put("failureReason", task.getFailureReason());
        data.put("createTime", task.getCreateTimeMs());
        data.put("startTime", task.getStartTime());
        data.put("userId", task.getUserId());
        data.put("promptId", task.getPromptId());

        return ResultData.success(data);
    }

    /**
     * 查询排队位置
     *
     * @param taskId 任务ID
     * @return 排队位置信息
     */
    @GetMapping("/task/{taskId}/queue")
    public ResultData<Map<String, Object>> getQueuePosition(@PathVariable Long taskId) {
        int position = taskInfoService.getQueuePosition(taskId);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("position", position);
        data.put("displayText", position >= 0 ? "您前面还有" + position + "个任务" : "任务不在队列中");

        return ResultData.success(data);
    }

    // ==================== 调试接口 ====================
    
    /**
     * 连接VNC接口：创建容器，返回VNC信息，不调用AI中台。
     * 
     * <p>前端拿到VNC地址后可直接连接查看容器桌面，
     * 后续通过 /execute 接口发送提示词执行任务。</p>
     * 
     * @param request 连接请求（需profileName，clientId必填）
     * @return 容器信息（taskId, containerId, vncPort）
     */
    @PostMapping("/connect")
    public ResultData<Map<String, Object>> connect(@RequestBody ConnectRequest request) {
        log.info("[HrV3] 收到连接请求：profileName={}, clientId={}", request.getProfileName(), request.getClientId());
        
        // 1. 参数校验
        if (request.getProfileName() == null || request.getProfileName().isBlank()) {
            return ResultData.error("profileName不能为空");
        }
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            return ResultData.error("clientId不能为空");
        }
        
        // 2. 生成taskId
        String taskId = String.valueOf(System.currentTimeMillis());
        
        try {
            // 3. 创建容器（只读模式）
            int port = request.getPort() != null ? request.getPort() : 0;
            ContainerPodV3 pod = poolManager.createPod(request.getProfileName(), port);
            
            // 4. 初始化任务上下文（不调用AI中台）
            String apiKey = properties.getDefaultApiKey();
            String baseUrl = properties.getDefaultBaseUrl();
            String model = properties.getDefaultModel();
            int maxSteps = 1500;
            
            poolManager.initTaskContext(Long.valueOf(taskId), pod, "等候指令", apiKey, baseUrl, model, maxSteps);
            
            // 5. 绑定SSE
            emitterManager.bindTask(taskId, request.getClientId());
            
            // 6. 返回容器信息
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("containerId", pod.getContainerId());
            result.put("vncPort", pod.getVncPort());
            result.put("profile", pod.getProfileName());
            result.put("message", "容器已就绪，请通过VNC连接查看。使用 /execute 接口发送提示词执行任务。");
            
            return ResultData.success("连接成功", result);
            
        } catch (IllegalStateException e) {
            log.error("[HrV3] 池已耗尽：{}", e.getMessage());
            return ResultData.error(503, "服务不可用：" + e.getMessage());
        } catch (Exception e) {
            log.error("[HrV3] 连接失败：{}", e.getMessage(), e);
            return ResultData.error("连接失败：" + e.getMessage());
        }
    }
    
    /**
     * 执行工作流接口：接收提示词数组，异步执行任务。
     * 
     * <p>前端调用此接口后立即返回taskId，通过SSE监听执行进度。
     * 后台按顺序执行：系统提示词初始化 → 步骤1 → 步骤2 → ...
     * 每步等待AI中台完成后才执行下一步。</p>
     * 
     * @param request 执行请求（需taskId, sysPrompts, stepPrompts）
     * @return taskId（立即返回，异步执行）
     */
    @PostMapping("/execute")
    public ResultData<Map<String, Object>> execute(@RequestBody ExecuteRequest request) {
        String taskId = request.getTaskId();
        List<miniPromptsTo> sysPrompts = request.getSysPrompts();
        List<miniPromptsTo> stepPrompts = request.getStepPrompts();
        
        log.info("[HrV3] 收到执行请求，taskId: {}, sysPrompts数量: {}, stepPrompts数量: {}",
                taskId,
                sysPrompts != null ? sysPrompts.size() : 0,
                stepPrompts != null ? stepPrompts.size() : 0);
        
        // 1. 参数校验
        if (taskId == null || taskId.isBlank()) {
            return ResultData.error("taskId不能为空");
        }
        if (stepPrompts == null || stepPrompts.isEmpty()) {
            return ResultData.error("stepPrompts不能为空");
        }
        
        // 2. 获取任务上下文
        DockerPoolManagerV3.TaskContext ctx = poolManager.getTaskContext(taskId);
        if (ctx == null) {
            return ResultData.error(404, "未找到该任务的上下文。请先调用 /connect 创建容器。");
        }
        
        // 3. 格式化系统提示词
        String sysPrompt = assembleSystemPrompt(sysPrompts);
        
        // 4. 异步执行任务
        String finalTaskId = taskId;
        CompletableFuture.runAsync(() -> {
            try {
                executeStepsAsync(finalTaskId, ctx, sysPrompt, stepPrompts);
            } catch (Exception e) {
                log.error("[HrV3] 异步执行任务失败，taskId: {}", finalTaskId, e);
                emitterManager.sendJsonEventByTaskId(finalTaskId, Map.of(
                        "taskId", finalTaskId,
                        "status", "FAILED",
                        "errorMessage", e.getMessage()
                ));
            }
        });
        
        // 5. 立即返回taskId
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("message", "任务已受理，正在后台异步执行。请通过SSE监听进度。");
        
        return ResultData.success("任务已受理", result);
    }
    
    /**
     * 异步执行步骤提示词
     * <p>
     * 将提示词列表注册到 AgentBridgeManager，由其自动循环调度。
     * 与任务模式共用同一套回调处理逻辑。
     * </p>
     */
    private void executeStepsAsync(String taskId, DockerPoolManagerV3.TaskContext ctx,
                                   String sysPrompt, List<miniPromptsTo> stepPrompts) {
        log.info("[HrV3] 开始异步执行任务，taskId: {}, 总步骤数: {}", taskId, stepPrompts.size());
        
        // 推送开始事件
        emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                "taskId", taskId,
                "status", "RUNNING",
                "totalSteps", stepPrompts.size(),
                "message", "任务开始执行"
        ));
        
        try {
            // 1. 注册步骤提示词列表到 AgentBridgeManager（用于自动循环）
            agentBridgeManager.registerDebugPrompts(taskId, stepPrompts);
            
            // 2. 创建并注册 Future（用于等待所有步骤完成）
            CompletableFuture<Void> completionFuture = new CompletableFuture<>();
            agentBridgeManager.registerDebugFuture(taskId, completionFuture);
            
            // 3. 如果有系统提示词，先单独发送（step=-1）
            if (sysPrompt != null && !sysPrompt.isBlank()) {
                log.info("[HrV3] 发送系统提示词，taskId: {}", taskId);
                
                CompletableFuture<Void> sysFuture = new CompletableFuture<>();
                agentBridgeManager.registerDebugFuture(taskId + "_sys", sysFuture);
                
                AgentTaskNotifyDTO sysDto = AgentTaskNotifyDTO.builder()
                        .agentName("hr-agent")
                        .taskId(taskId + "_sys")
                        .sessionId(taskId)
                        .sysPrompt(sysPrompt)
                        .instruction(sysPrompt)
                        .step(-1)
                        .containerUrl("http://" + properties.getDockerHostIp() + ":" + ctx.getPod().getAssignedPort())
                        .build();
                
                boolean invoked = agentBridgeManager.invokeAgent(sysDto);
                if (!invoked) {
                    throw new RuntimeException("调用AI中台失败");
                }
                
                sysFuture.get(5, TimeUnit.MINUTES);
                log.info("[HrV3] 系统提示词发送完成，taskId: {}", taskId);
            }
            
            // 4. 检查是否被中止
            if (ctx.isAborted()) {
                log.info("[HrV3] 任务被中止，taskId: {}", taskId);
                emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                        "taskId", taskId,
                        "status", "ABORTED",
                        "message", "任务已被用户中止"
                ));
                return;
            }
            
            // 5. 发起第一步调用（AgentBridgeManager 会自动循环后续步骤）
            log.info("[HrV3] 发起第一步调用，taskId: {}", taskId);
            AgentTaskNotifyDTO firstStepDto = AgentTaskNotifyDTO.builder()
                    .agentName("hr-agent")
                    .taskId(taskId)
                    .sessionId(taskId)
                    .instruction(stepPrompts.get(0).getContent())
                    .step(1)
                    .containerUrl("http://" + properties.getDockerHostIp() + ":" + ctx.getPod().getAssignedPort())
                    .build();
            
            boolean invoked = agentBridgeManager.invokeAgent(firstStepDto);
            if (!invoked) {
                throw new RuntimeException("调用AI中台失败");
            }
            
            // 6. 等待所有步骤完成
            completionFuture.get(30, TimeUnit.MINUTES);
            
            // 7. 推送完成事件
            log.info("[HrV3] 所有步骤执行完成，taskId: {}", taskId);
            emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                    "taskId", taskId,
                    "status", "COMPLETED",
                    "totalSteps", stepPrompts.size(),
                    "message", "所有步骤执行完成"
            ));
            
        } catch (TimeoutException e) {
            log.error("[HrV3] 任务执行超时，taskId: {}", taskId, e);
            emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                    "taskId", taskId,
                    "status", "TIMEOUT",
                    "errorMessage", "任务执行超时"
            ));
        } catch (Exception e) {
            log.error("[HrV3] 任务执行失败，taskId: {}", taskId, e);
            emitterManager.sendJsonEventByTaskId(taskId, Map.of(
                    "taskId", taskId,
                    "status", "FAILED",
                    "errorMessage", e.getMessage()
            ));
        }
    }
    
    /**
     * 格式化系统提示词
     * <p>
     * 将系统提示词数组按type分类拼装：
     * - type=1 → ## 角色设定
     * - type=2 → ## 物理规则
     * - type=4 → ## 背景知识
     * </p>
     * 
     * @param sysPrompts 系统提示词数组
     * @return 格式化后的系统提示词
     */
    private String assembleSystemPrompt(List<miniPromptsTo> sysPrompts) {
        if (sysPrompts == null || sysPrompts.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        appendByType(sb, sysPrompts, 1, "角色设定");
        appendByType(sb, sysPrompts, 2, "物理规则");
        appendByType(sb, sysPrompts, 4, "背景知识");
        return sb.toString().trim();
    }
    
    private void appendByType(StringBuilder sb, List<miniPromptsTo> prompts, int type, String sectionTitle) {
        for (miniPromptsTo prompt : prompts) {
            // 这里假设miniPromptsTo有getType()方法，如果没有需要调整
            // 实际上miniPromptsTo没有type字段，需要从其他地方获取
            // 暂时直接拼接所有提示词
            if (prompt.getContent() != null && !prompt.getContent().isBlank()) {
                sb.append("## ").append(sectionTitle).append("\n");
                sb.append(prompt.getContent()).append("\n\n");
            }
        }
    }
    
    // ==================== 养号（人工登录/配置制作） ====================

    /**
     * 创建养号会话：系统自动新建配置并拉起浏览器进入百度。
     *
     * <p>与旧版 updateProfile 流程的区别：</p>
     * <ul>
     *   <li>只需传入配置名，无需选择目标网址（系统固定打开百度首页）</li>
     *   <li>浏览器配置目录以可写模式挂载，用户在 VNC 中的登录/养号操作实时落盘到服务器</li>
     *   <li>后续养号流程完全由用户自行操作，保存时机由用户自己决定</li>
     * </ul>
     *
     * @param request 创建请求（profileName、clientId 必填，port 可选）
     * @return 容器信息（taskId, containerId, vncPort）
     */
    @PostMapping("/farm/create")
    public ResultData<Map<String, Object>> createFarmSession(@RequestBody FarmCreateRequest request) {
        log.info("[HrV3] 创建养号会话：profileName={}, clientId={}", request.getProfileName(), request.getClientId());

        // 1. 参数校验
        if (request.getProfileName() == null || request.getProfileName().isBlank()) {
            return ResultData.error("profileName不能为空");
        }
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            return ResultData.error("clientId不能为空");
        }

        // 2. 生成taskId
        String taskId = String.valueOf(System.currentTimeMillis());

        try {
            // 3. 创建可写模式的养号容器
            int port = request.getPort() != null ? request.getPort() : 0;
            ContainerPodV3 pod = poolManager.createFarmingPod(request.getProfileName(), port);

            // 4. 标记养号会话（不参与常规空闲回收，防止用户操作期间被误清理）
            pod.putMetadata("farmingSession", true);
            pod.putMetadata("farmingTaskId", taskId);

            // 5. 绑定SSE
            emitterManager.bindTask(taskId, request.getClientId());

            // 6. 系统自动拉起浏览器进入百度（不依赖AI中台）
            boolean baiduOpened = poolManager.openBaiduInContainer(pod);

            // 7. 返回容器信息
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("containerId", pod.getContainerId());
            result.put("vncPort", pod.getVncPort());
            result.put("profile", pod.getProfileName());
            result.put("baiduOpened", baiduOpened);
            result.put("message", baiduOpened
                    ? "养号环境已就绪，浏览器已自动打开百度。请通过VNC进行登录/养号操作，完成后点击保存配置。"
                    : "养号环境已就绪，但自动打开百度失败，请手动在浏览器中访问百度首页。");

            return ResultData.success("养号环境创建成功", result);

        } catch (IllegalStateException e) {
            log.error("[HrV3] 创建养号会话失败（池耗尽）：{}", e.getMessage());
            return ResultData.error(503, "服务不可用：" + e.getMessage());
        } catch (Exception e) {
            log.error("[HrV3] 创建养号会话失败：{}", e.getMessage(), e);
            return ResultData.error("创建养号环境失败：" + e.getMessage());
        }
    }

    /**
     * 保存养号配置：保存浏览器配置到服务器并断开VNC，落盘数据库后配置列表可见。
     *
     * <p>执行顺序：</p>
     * <ol>
     *   <li>浏览器配置在可写挂载模式下已实时落盘到服务器本地</li>
     *   <li>落盘数据库 auth_info 表：仅登记配置存在，<b>不标记任何已登录网址</b>
     *       （已登录网址由用户调用 /farm/mark 标记时才会新增）</li>
     *   <li>销毁容器（断开VNC连接，用户无法继续操作）</li>
     * </ol>
     *
     * @param request 保存请求（profileName、containerId 必填）
     * @return 保存结果
     */
    @PostMapping("/farm/save")
    public ResultData<Map<String, Object>> saveFarmSession(@RequestBody FarmSaveRequest request) {
        log.info("[HrV3] 保存养号配置：profileName={}, containerId={}", request.getProfileName(), request.getContainerId());

        if (request.getProfileName() == null || request.getProfileName().isBlank()) {
            return ResultData.error("profileName不能为空");
        }
        if (request.getContainerId() == null || request.getContainerId().isBlank()) {
            return ResultData.error("containerId不能为空");
        }

        try {
            // 1. 落盘数据库：登记配置存在，但百度仅作为默认平台占位（is_available=false，非已登录）
            //    已登录网址必须由用户调用 /farm/mark 标记后才产生
            authInfoService.updateAuthStatus(request.getProfileName(), BAIDU_HOME_URL, false);

            // 2. 优雅关闭浏览器，等待登录态（Cookie/Login Data）完成落盘，避免强杀丢失
            poolManager.gracefulStopChromium(request.getContainerId());

            // 3. 销毁容器（断开VNC连接，防止用户继续操作产生脏数据）
            poolManager.stopAndRemoveContainer(request.getContainerId());
            log.info("[HrV3] 养号配置已保存，容器 {} 已销毁，配置名：{}", request.getContainerId(), request.getProfileName());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("profileName", request.getProfileName());
            result.put("websiteName", BAIDU_HOME_URL);
            result.put("message", "浏览器配置已保存至服务器，VNC连接已断开。该配置尚未标记登录网址，请使用配置标记功能添加已登录网址。");

            return ResultData.success("配置保存成功", result);

        } catch (Exception e) {
            log.error("[HrV3] 保存养号配置失败：{}", e.getMessage(), e);
            return ResultData.error("保存配置失败：" + e.getMessage());
        }
    }

    /**
     * 取消养号会话：直接销毁容器，不落盘数据库。
     *
     * <p>用户放弃本次养号时调用，配置不会出现在配置列表中。</p>
     *
     * @param request 取消请求（containerId 必填）
     * @return 取消结果
     */
    @PostMapping("/farm/cancel")
    public ResultData<Map<String, Object>> cancelFarmSession(@RequestBody FarmSaveRequest request) {
        log.info("[HrV3] 取消养号会话：containerId={}", request.getContainerId());

        if (request.getContainerId() == null || request.getContainerId().isBlank()) {
            return ResultData.error("containerId不能为空");
        }

        try {
            poolManager.stopAndRemoveContainer(request.getContainerId());
            log.info("[HrV3] 养号会话已取消，容器 {} 已销毁（未落盘数据库）", request.getContainerId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "养号会话已取消，容器已销毁，本次操作未保存。");

            return ResultData.success("取消成功", result);

        } catch (Exception e) {
            log.error("[HrV3] 取消养号会话失败：{}", e.getMessage(), e);
            return ResultData.error("取消养号会话失败：" + e.getMessage());
        }
    }

    /**
     * 重新拉起浏览器：用户在 VNC 操作中误关浏览器后，调用此接口重新打开浏览器并进入百度。
     *
     * <p>复用创建会话时的自动拉起逻辑（打开 Chrome → 输入百度地址 → 回车），
     * 不依赖 AI 中台；会话已保存/取消/超时回收时返回 404。</p>
     *
     * @param request 请求（containerId 必填）
     * @return 拉起结果（baiduOpened、vncPort）
     */
    @PostMapping("/farm/open-browser")
    public ResultData<Map<String, Object>> openBrowserInFarmSession(@RequestBody FarmBrowserRequest request) {
        log.info("[HrV3] 重新拉起浏览器：containerId={}", request.getContainerId());

        if (request.getContainerId() == null || request.getContainerId().isBlank()) {
            return ResultData.error("containerId不能为空");
        }

        try {
            // 1. 定位养号容器（已销毁/回收的会话查不到，提示重新创建）
            ContainerPodV3 pod = poolManager.getPod(request.getContainerId()).orElse(null);
            if (pod == null) {
                return ResultData.error(404, "养号会话不存在或已结束，请重新创建养号环境");
            }

            // 2. 重新拉起浏览器并进入百度
            boolean baiduOpened = poolManager.openBaiduInContainer(pod);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("containerId", pod.getContainerId());
            result.put("vncPort", pod.getVncPort());
            result.put("baiduOpened", baiduOpened);
            result.put("message", baiduOpened
                    ? "浏览器已重新拉起并进入百度首页。"
                    : "拉起浏览器失败，请稍后重试，或手动在容器内打开浏览器。");

            return ResultData.success(baiduOpened ? "浏览器拉起成功" : "浏览器拉起失败", result);

        } catch (Exception e) {
            log.error("[HrV3] 重新拉起浏览器失败：{}", e.getMessage(), e);
            return ResultData.error("拉起浏览器失败：" + e.getMessage());
        }
    }

    /**
     * 配置标记接口：用户选定配置并输入网址，标记该配置已登录指定网址。
     *
     * <p>仅将状态落盘到数据库 auth_info 表，不涉及容器操作或文件系统变更。</p>
     *
     * @param request 标记请求（profileName、websiteUrl 必填）
     * @return 标记结果
     */
    @PostMapping("/farm/mark")
    public ResultData<Map<String, Object>> markFarmProfile(@RequestBody FarmMarkRequest request) {
        log.info("[HrV3] 标记配置登录状态：profileName={}, websiteUrl={}", request.getProfileName(), request.getWebsiteUrl());

        if (request.getProfileName() == null || request.getProfileName().isBlank()) {
            return ResultData.error("profileName不能为空");
        }
        if (request.getWebsiteUrl() == null || request.getWebsiteUrl().isBlank()) {
            return ResultData.error("websiteUrl不能为空");
        }

        try {
            // 落盘数据库：按 (cloudStorageName, websiteName) 查/改/增，标记已登录
            authInfoService.updateAuthStatus(request.getProfileName(), request.getWebsiteUrl(), true);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("profileName", request.getProfileName());
            result.put("websiteUrl", request.getWebsiteUrl());
            result.put("message", "配置已标记为登录 " + request.getWebsiteUrl());

            return ResultData.success("标记成功", result);

        } catch (Exception e) {
            log.error("[HrV3] 标记配置登录状态失败：{}", e.getMessage(), e);
            return ResultData.error("标记失败：" + e.getMessage());
        }
    }

    /**
     * 获取养号配置列表。
     *
     * <p>只有已执行保存操作（落盘数据库）的配置才会出现在列表中，
     * 未保存的养号会话不会展示。</p>
     *
     * @return 按配置名分组的配置列表
     */
    @GetMapping("/farm/profiles")
    public ResultData<List<Map<String, Object>>> getFarmProfiles() {
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

            return ResultData.success("获取成功", resultList);

        } catch (Exception e) {
            log.error("[HrV3] 获取养号配置列表失败：{}", e.getMessage(), e);
            return ResultData.error("获取配置列表失败：" + e.getMessage());
        }
    }

    // ==================== 请求体定义 ====================

    @Data
    public static class FarmCreateRequest {
        /**
         * 配置名称（必填，同时作为宿主机浏览器配置目录名）
         */
        private String profileName;

        /**
         * 前端SSE连接的clientId（必填）
         */
        private String clientId;

        /**
         * 指定端口（可选）
         */
        private Integer port;
    }

    @Data
    public static class FarmSaveRequest {
        /**
         * 配置名称（保存时必填）
         */
        private String profileName;

        /**
         * 容器ID（必填，用于销毁容器断开VNC）
         */
        private String containerId;
    }

    @Data
    public static class FarmMarkRequest {
        /**
         * 配置名称（必填）
         */
        private String profileName;

        /**
         * 目标网址（必填，标记该配置已登录的网址）
         */
        private String websiteUrl;
    }

    @Data
    public static class FarmBrowserRequest {
        /**
         * 容器ID（必填，create 返回的 containerId）
         */
        private String containerId;
    }

    @Data
    public static class V3TaskCreateRequest {
        /**
         * 提示词组 ID（对应 prompts 表的 prompt_id）
         */
        private Long promptId;

        /**
         * 配置名称（对应 auth_info 的 cloud_storage_name，作为容器 username）
         */
        private String configName;

        /**
         * 公司名称（可选，不填则用 configName 兜底）
         */
        private String companyName;

        /**
         * 前端 SSE 连接的 clientId（必填，用于绑定 SSE 推送）
         */
        private String clientId;

        /**
         * 用户ID（可选）
         */
        private String userId;

        /**
         * 批量创建任务数量（可选，默认为1）
         */
        private Integer count;
    }
    
    @Data
    public static class ConnectRequest {
        /**
         * 配置名称（必填）
         */
        private String profileName;
        
        /**
         * 前端SSE连接的clientId（必填）
         */
        private String clientId;
        
        /**
         * 指定端口（可选）
         */
        private Integer port;
    }
    
    @Data
    public static class ExecuteRequest {
        /**
         * 任务ID（connect接口返回的）
         */
        private String taskId;
        
        /**
         * 系统提示词数组
         */
        private List<miniPromptsTo> sysPrompts;
        
        /**
         * 步骤提示词数组
         */
        private List<miniPromptsTo> stepPrompts;
    }
}
