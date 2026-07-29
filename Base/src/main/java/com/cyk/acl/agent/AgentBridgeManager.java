package com.cyk.acl.agent;

import com.alibaba.fastjson2.JSONObject;
import com.cyk.DockerTool.V3.DockerPoolManagerV3;
import com.cyk.DockerTool.V3.config.AgentPoolPropertiesV3;
import com.cyk.Enity.miniPromptsTo;
import com.cyk.Enity.table.PromptsEntity;
import com.cyk.Service.IPromptsService;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Service.impl.TaskInfoServiceImpl;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import com.cyk.Utils.V3EmitterManager;
import com.cyk.acl.agent.client.AgentPlatformClient;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import com.cyk.DockerTool.V3.model.ContainerPodV3;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cyk.Enity.table.table.PromptsEntityTableDef.PROMPTS_ENTITY;

/**
 * AI 中台通信管理器（编排层）
 * <p>
 * 所有公共方法统一使用 {@link AgentTaskNotifyDTO} 作为通信载体。
 * </p>
 *
 * <h3>回调状态语义：</h3>
 * <p>
 * DTO 中的 status 是步骤状态（SUSPENDED/COMPLETED/FAILED），不是工作流状态。
 * 工作流状态通过检查是否存在下一步提示词来判断：
 * - 步骤 COMPLETED + 有下一步提示词 → 工作流继续，重新 invokeAgent
 * - 步骤 COMPLETED + 无下一步提示词 → 工作流完成，获取聚合结果，调用任务系统完成
 * - 步骤 FAILED → 工作流直接失败，调用任务系统失败
 * </p>
 *
 * <h3>步骤提示词机制：</h3>
 * <p>
 * 调用方可传入 promptsId（对应 prompts 表的 prompt_id），通过 step 字段追踪当前步骤。
 * prompts 表中同一 prompt_id 下按 step 编号组织多步提示词，Manager 通过查询下一步是否存在来决定继续或结束。
 * </p>
 */
@Slf4j
@Component
public class AgentBridgeManager {

    @Resource
    private AgentPlatformClient platformClient;

    @Resource
    private DockerPoolManagerV3 poolManager;

    @Resource
    private AgentPoolPropertiesV3 properties;

    @Resource
    private V3EmitterManager emitterManager;

    @Resource
    private CustomTaskScheduler customTaskScheduler;

    @Resource
    private IPromptsService promptsService;

    @Resource
    private TaskInfoServiceImpl taskInfoService;
    
    // ==================== 调试接口支持 ====================
    
    /**
     * 存储调试任务的Future，用于同步等待
     */
    private final Map<String, CompletableFuture<Void>> debugTaskFutures = new ConcurrentHashMap<>();
    
    /**
     * 存储调试任务的步骤提示词列表，用于自动循环调度
     */
    private final Map<String, List<miniPromptsTo>> debugTaskPrompts = new ConcurrentHashMap<>();
    
    // ==================== toolStep 本地计数器 ====================
    
    /**
     * 存储每个任务每个步骤的本地 toolStep 计数器
     * key: taskId_step, value: 本地计数
     */
    private final Map<String, AtomicInteger> localToolStepCounters = new ConcurrentHashMap<>();
    
    /**
     * 存储每个任务上一次回调的 AI 中台 toolStep 值
     * key: taskId_step, value: AI中台返回的toolStep
     */
    private final Map<String, Integer> lastAiToolSteps = new ConcurrentHashMap<>();
    
    /**
     * 获取本地 toolStep 计数器的 key
     */
    private String getToolStepKey(String taskId, Integer step) {
        return taskId + "_" + (step != null ? step : 0);
    }
    
    /**
     * 注册调试任务的Future
     * @param taskId 任务ID
     * @param future CompletableFuture
     */
    public void registerDebugFuture(String taskId, CompletableFuture<Void> future) {
        debugTaskFutures.put(taskId, future);
    }
    
    /**
     * 注册调试任务的步骤提示词列表
     * @param taskId 任务ID
     * @param stepPrompts 步骤提示词列表
     */
    public void registerDebugPrompts(String taskId, List<miniPromptsTo> stepPrompts) {
        debugTaskPrompts.put(taskId, stepPrompts);
    }

    // ==================== invokeAgent ====================

    /**
     * 调用 AI 中台的 Agent
     * <p>
     * 从 DTO 中读取 agentName、sessionId、taskId、instruction、promptsId 等字段。
     * 如果指定了 promptsId，则从 prompts 表查询对应步骤的提示词内容并覆盖 instruction，然后发送给 AI 中台。
     * </p>
     *
     * @param dto invoke 场景的 DTO（需填充 agentName、sessionId、taskId，可选 promptsId 和 instruction）
     * @return 是否调用成功
     */
    public boolean invokeAgent(AgentTaskNotifyDTO dto) {
        String taskId = dto.getTaskId();
        String promptsId = dto.getPromptsId();
        log.info("[AgentBridgeManager] 调用 Agent，agentName: {}, taskId: {}, promptsId: {}, debugMode: {}",
                dto.getAgentName(), taskId, promptsId, dto.isDebugMode());

        try {
            // 如果是调试接口任务，不查询prompts表，直接使用DTO中的instruction
            if (dto.isDebugMode()) {
                log.info("[AgentBridgeManager] 调试接口任务，直接使用DTO中的instruction，taskId: {}", taskId);
                return platformClient.invokeAgent(dto);
            }
            
            // 如果指定了 promptsId，从 DB 查询提示词内容覆盖 instruction
            if (promptsId != null && !promptsId.isBlank()) {
                int step = dto.getStep() != null ? dto.getStep() : -1;
                if(step<0){throw new RuntimeException("步骤编号 step 未指定或无效或为null，无法查询提示词，step实际为："+dto.getStep());}
                String promptContent = getPromptContent(promptsId, step);
                if (promptContent != null && !promptContent.isBlank()) {
                    log.info("[AgentBridgeManager] 从提示词表获取到步骤 {} 的内容，覆盖 instruction，promptsId: {}", step, promptsId);
                    dto.setInstruction(promptContent);
                } else {
                    log.warn("[AgentBridgeManager] promptsId: {}, step: {} 未找到提示词内容，使用原始 instruction", promptsId, step);
                }
            }

            // 委托给 ACL 层客户端调用
            return platformClient.invokeAgent(dto);

        } catch (Exception e) {
            log.error("[AgentBridgeManager] invokeAgent 失败", e);
            return false;
        }
    }

    // ==================== handleAgentCallback ====================

    /**
     * 处理 AI 中台的回调（步骤状态回调）
     *
     * @param dto 回调 DTO（callback 场景）
     * @return 处理结果
     */
    public Map<String, Object> handleAgentCallback(AgentTaskNotifyDTO dto) {
        String taskId = dto.getTaskId();
        String status = dto.getStatus();

        log.info("[AgentBridgeManager] 收到 AI 中台回调，taskId: {}, status: {}, step: {}, toolStep: {}",
                taskId, status, dto.getStep(), dto.getToolStep());


        if ("SUCCESS".equals(status)) {
            handleAgentCompleted(dto);
        } else if("RUNNING".equals(status)){
            handleStepLog(dto);
        }
        else if ("FAILED".equals(status)) {
            handleAgentFailed(dto);
        }

        return Map.of("success", true, "message", "回调已处理");
    }

    // ==================== handleAgentSuspended ====================

    private void handleAgentSuspended(AgentTaskNotifyDTO dto) {
        String taskId = dto.getTaskId();
        String toolName = dto.getToolName();
        String toolInput = dto.getToolInput();
        Integer step = dto.getStep() != null ? dto.getStep() : 0;

        log.info("[AgentBridgeManager] Agent 挂起，taskId: {}, toolName: {}", taskId, toolName);

        try {
            // 1. 推送 SSE
            Map<String, Object> ssePayload = new LinkedHashMap<>();
            ssePayload.put("taskId", taskId);
            ssePayload.put("status", "SUSPENDED");
            ssePayload.put("toolName", toolName != null ? toolName : "");
            ssePayload.put("step", step);
            emitterManager.sendJsonEventByTaskId(taskId, ssePayload);

            // 2. 查找容器 Pod
            Optional<ContainerPodV3> podOpt = poolManager.getPodByTaskId(taskId);
            if (podOpt.isEmpty()) {
                log.error("[AgentBridgeManager] 未找到 taskId: {} 对应的容器 Pod", taskId);
                resumeAgent(AgentTaskNotifyDTO.builder()
                        .taskId(taskId)
                        .result("未找到关联的容器 Pod")
                        .success(false)
                        .step(step)
                        .build());
                return;
            }

            ContainerPodV3 pod = podOpt.get();

            // 3. 向容器发送 GUI 操作指令
            String callbackUrl = properties.getCallbackBaseUrl()
                    + "/api/v3/container/callback/" + pod.getContainerId();
            poolManager.executeToolAction(pod, taskId,
                    toolName != null ? toolName : "unknown",
                    toolInput != null ? toolInput : "{}",
                    callbackUrl);

            log.info("[AgentBridgeManager] 已向容器 {} 发送 GUI 操作指令，taskId: {}, toolName: {}",
                    pod.getShortId(), taskId, toolName);

        } catch (Exception e) {
            log.error("[AgentBridgeManager] 处理 Agent 挂起失败，taskId: {}", taskId, e);
            try {
                resumeAgent(AgentTaskNotifyDTO.builder()
                        .taskId(taskId)
                        .result("工具平台处理挂起失败: " + e.getMessage())
                        .success(false)
                        .step(step)
                        .build());
            } catch (Exception ex) {
                log.error("[AgentBridgeManager] 通知 AI 中台失败也失败，taskId: {}", taskId, ex);
            }
        }
    }

    // ==================== handleAgentCompleted ====================

    /**
     * 步骤执行完成 → 查询下一步提示词是否存在 → 决定继续还是结束
     */
    private void handleAgentCompleted(AgentTaskNotifyDTO dto) {
        String taskId = dto.getTaskId();
        String promptsId = dto.getPromptsId();
        Integer step = dto.getStep();

        log.info("[AgentBridgeManager] 步骤执行完成，taskId: {}", taskId);

        // 清理当前步骤的 toolStep 计数器
        String toolStepKey = getToolStepKey(taskId, step);
        localToolStepCounters.remove(toolStepKey);
        lastAiToolSteps.remove(toolStepKey);
        log.debug("[AgentBridgeManager] 清理步骤 toolStep 计数器，taskId: {}, step: {}", taskId, step);

        // 推送 SSE（步骤完成）
        pushSse(taskId, "STEP_COMPLETED",
                Map.of("step", step != null ? step : 0));

        // ==================== 调试模式：从内存获取下一步提示词 ====================
        List<miniPromptsTo> debugPrompts = debugTaskPrompts.get(taskId);
        if (debugPrompts != null) {
            int currentStep = dto.getStep() != null ? dto.getStep() : 1;
            int nextIndex = currentStep; // nextIndex 是下一个步骤在数组中的索引
            
            if (nextIndex < debugPrompts.size()) {
                // 有下一步提示词 → 继续执行
                miniPromptsTo nextPrompt = debugPrompts.get(nextIndex);
                int nextStep = currentStep + 1;
                log.info("[AgentBridgeManager] 调试模式：存在下一步提示词 (step={})，继续执行，taskId: {}", nextStep, taskId);
                
                dto.setStep(nextStep);
                dto.setInstruction(nextPrompt.getContent());
                invokeAgent(dto);
            } else {
                // 无下一步提示词 → 调试任务完成
                log.info("[AgentBridgeManager] 调试模式：所有步骤执行完成，taskId: {}", taskId);
                debugTaskPrompts.remove(taskId);
                CompletableFuture<Void> future = debugTaskFutures.remove(taskId);
                if (future != null) {
                    future.complete(null);
                }
            }
            return;
        }

        // ==================== 任务模式：从DB获取下一步提示词 ====================
        // 检查是否有关联的步骤提示词组
        if (promptsId == null || promptsId.isBlank()) {
            log.warn("[AgentBridgeManager] promptsId为null且非调试模式，taskId: {}", taskId);
            finishTask(taskId, true, null);
            return;
        }

        try {
            // 查询下一步提示词
            int currentStep = dto.getStep() != null ? dto.getStep() : 1;
            int nextStep = currentStep + 1;
            String nextPrompt = getPromptContent(promptsId, nextStep);

            if (nextPrompt != null && !nextPrompt.isBlank()) {
                // 有下一步提示词 → 继续执行
                log.info("[AgentBridgeManager] 存在下一步提示词 (step={})，继续执行，taskId: {}", nextStep, taskId);
                dto.setStep(nextStep);
                dto.setInstruction(nextPrompt);
                invokeAgent(dto);
            } else {
                // 无下一步提示词 → 任务完成
                log.info("[AgentBridgeManager] 无下一步提示词 (step={})，任务完成，taskId: {}", nextStep, taskId);
                finishTask(taskId, true, null);
            }
        } catch (Exception e) {
            log.error("[AgentBridgeManager] 查询下一步提示词失败，taskId: {}", taskId, e);
            finishTask(taskId, false, "查询下一步提示词异常: " + e.getMessage());
        }
    }

    // ==================== handleAgentFailed ====================

    /**
     * 步骤执行失败 → 任务直接失败 → 调用任务系统失败方法
     */
    private void handleAgentFailed(AgentTaskNotifyDTO dto) {
        String taskId = dto.getTaskId();
        String errorMessage = dto.getErrorMessage();
        Integer step = dto.getStep();

        log.error("[AgentBridgeManager] 步骤执行失败，taskId: {}, errorMessage: {}", taskId, errorMessage);

        // 清理当前步骤的 toolStep 计数器
        String toolStepKey = getToolStepKey(taskId, step);
        localToolStepCounters.remove(toolStepKey);
        lastAiToolSteps.remove(toolStepKey);
        
        // 清理该任务的所有步骤计数器
        String taskIdPrefix = taskId + "_";
        localToolStepCounters.keySet().removeIf(k -> k.startsWith(taskIdPrefix));
        lastAiToolSteps.keySet().removeIf(k -> k.startsWith(taskIdPrefix));
        log.debug("[AgentBridgeManager] 清理任务所有 toolStep 计数器，taskId: {}", taskId);

        pushSse(taskId, "FAILED", Map.of(
                "errorMessage", errorMessage != null ? errorMessage : "",
                "step", step != null ? step : 0
        ));

        // 检查是否为调试任务（通过内存中的提示词列表判断）
        if (debugTaskPrompts.remove(taskId) != null) {
            log.error("[AgentBridgeManager] 调试任务步骤失败，taskId: {}", taskId);
            // 通知等待线程异常
            CompletableFuture<Void> future = debugTaskFutures.remove(taskId);
            if (future != null) {
                future.completeExceptionally(new RuntimeException(errorMessage));
            }
            return;
        }

        finishTask(taskId, false, errorMessage);
    }

    // ==================== handleStepLog ====================

    /**
     * 步骤执行日志（实时推送）
     * <p>
     * AI 中台在每个工具步骤完成后，通过 notifyStepLog() 实时推送执行明细。
     * status = "EXECUTING" 区分于任务结束时的 COMPLETED/FAILED。
     * </p>
     */
    private void handleStepLog(AgentTaskNotifyDTO dto) {
        String taskId = dto.getTaskId();
        Integer step = dto.getStep();
        Integer aiToolStep = dto.getToolStep();
        
        // 计算本地 toolStep（每个步骤从1开始）
        String key = getToolStepKey(taskId, step);
        AtomicInteger localCounter = localToolStepCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        Integer lastAiToolStep = lastAiToolSteps.get(key);
        
        // 如果 AI 中台的 toolStep 变化了，本地计数器加1
        if (lastAiToolStep == null || !lastAiToolStep.equals(aiToolStep)) {
            localCounter.incrementAndGet();
            lastAiToolSteps.put(key, aiToolStep);
        }
        
        int localToolStep = localCounter.get();
        
        log.info("[AgentBridgeManager] 步骤日志，taskId: {}, step: {}, toolStep: {} (本地: {}), toolName: {}",
                taskId, step, aiToolStep, localToolStep, dto.getToolName());

        // 推送 SSE 给前端（使用本地计数的 toolStep）
        Map<String, Object> ssePayload = new LinkedHashMap<>();
        ssePayload.put("taskId", taskId);
        ssePayload.put("status", "RUNNING");
        ssePayload.put("step", step);
        ssePayload.put("toolStep", localToolStep);
        ssePayload.put("toolName", dto.getToolName());
        ssePayload.put("toolInput", dto.getToolInput());
        ssePayload.put("outputResult", dto.getOutputResult());
        ssePayload.put("screenshotPath", dto.getScreenshotPath());
        ssePayload.put("timestamp", dto.getTimestamp());
        emitterManager.sendJsonEventByTaskId(taskId, ssePayload);
    }

    // ==================== resumeAgent ====================

    /**
     * 恢复 Agent 执行
     * <p>
     * 容器执行完 GUI 操作后，调用此方法恢复 Agent。
     * </p>
     *
     * @param dto resume 场景的 DTO（需填充 taskId、result、success、step）
     * @return 是否恢复成功
     */
    public boolean resumeAgent(AgentTaskNotifyDTO dto) {
        log.info("[AgentBridgeManager] 恢复 Agent，taskId: {}, step: {}, toolStep: {}",
                dto.getTaskId(), dto.getStep(), dto.getToolStep());

        try {
            pushSse(dto.getTaskId(), "RESUMED", Map.of(
                    "success", dto.getSuccess() != null ? dto.getSuccess() : false,
                    "step", dto.getStep() != null ? dto.getStep() : 0,
                    "toolStep", dto.getToolStep() != null ? dto.getToolStep() : 0
            ));

            return platformClient.resumeAgent(dto);

        } catch (Exception e) {
            log.error("[AgentBridgeManager] resumeAgent 失败", e);
            return false;
        }
    }

    // ==================== finishTask ====================

    /**
     * 任务结束时统一处理：获取聚合结果，调用任务系统完成回调
     */
    private void finishTask(String taskId, boolean success, String errorMessage) {
        try {
            Long longTaskId = parseLongTaskId(taskId);

            // 从数据库获取任务的真实 taskType，避免硬编码错误类型导致并发计数器偏移
            String taskType = "AI"; // 默认兜底
            if (longTaskId != null) {
                TaskInfoEntity taskEntity = taskInfoService.getById(longTaskId);
                if (taskEntity != null && taskEntity.getTaskType() != null) {
                    taskType = taskEntity.getTaskType();
                    log.info("[AgentBridgeManager] 从DB获取任务类型，taskId: {}, taskType: {}", taskId, taskType);
                }
            }

            if (success) {
                // 获取 AI 中台的任务详情（含执行明细）
                JSONObject taskDetail = getAccumulatedResults(taskId);
                Map<String, Object> resultMap = new HashMap<>();
                if (taskDetail != null && taskDetail.getBooleanValue("success")) {
                    resultMap.put("accumulatedResults", taskDetail);
                    log.info("[AgentBridgeManager] 成功获取任务详情，taskId: {}", taskId);
                } else {
                    log.warn("[AgentBridgeManager] 获取任务详情失败或为空，taskId: {}", taskId);
                }

                pushSse(taskId, "TASK_COMPLETED", Map.of("results", resultMap));

                if (longTaskId != null) {
                    customTaskScheduler.onTaskCompleted(longTaskId, taskType, true, resultMap);
                }
            } else {
                pushSse(taskId, "TASK_FAILED",
                        Map.of("errorMessage", errorMessage != null ? errorMessage : ""));

                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("error", errorMessage);

                if (longTaskId != null) {
                    customTaskScheduler.onTaskCompleted(longTaskId, taskType, false, resultMap);
                }
            }

            // 任务结束后清理 AI 中台的 Agent 会话缓存
            try {
                platformClient.removeAgentCache(taskId, null);
            } catch (Exception e) {
                log.warn("[AgentBridgeManager] 清理 Agent 会话缓存失败（不影响任务结果），taskId: {}", taskId, e);
            }
        } catch (Exception e) {
            log.error("[AgentBridgeManager] finishTask 失败，taskId: {}", taskId, e);
        }
    }

    private Long parseLongTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) return null;
        try {
            return Long.parseLong(taskId);
        } catch (NumberFormatException e) {
            String digits = taskId.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    return Long.parseLong(digits);
                } catch (NumberFormatException ex) {
                    log.warn("[AgentBridgeManager] taskId {} 无法转换为 Long", taskId);
                    return null;
                }
            }
            return null;
        }
    }

    // ==================== 提示词查询 ====================

    /**
     * 查询指定提示词组的指定步骤内容
     *
     * @param promptsId prompt_id 值
     * @param step      步骤号
     * @return 提示词内容，不存在返回 null
     */
    private String getPromptContent(String promptsId, int step) {
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .from(PROMPTS_ENTITY)
                    .where(PROMPTS_ENTITY.PROMPT_ID.eq(Long.parseLong(promptsId)))
                    .and(PROMPTS_ENTITY.STEP.eq(step))
                    .limit(1);
            PromptsEntity prompt = promptsService.getOne(queryWrapper);
            return prompt != null ? prompt.getContent() : null;
        } catch (NumberFormatException e) {
            log.error("[AgentBridgeManager] promptsId 格式错误: {}", promptsId, e);
            return null;
        }
    }

    // ==================== SSE 辅助 ====================

    private void pushSse(String taskId, String status, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", status);
        payload.putAll(extra);
        emitterManager.sendJsonEventByTaskId(taskId, payload);
    }

    // ==================== 查询 / 管理 ====================

    public JSONObject getAgentTaskStatus(String taskId) {
        return platformClient.getAgentTaskStatus(taskId);
    }

    public boolean cancelAgentTask(String taskId) {
        return platformClient.cancelAgentTask(taskId);
    }

    public JSONObject getAccumulatedResults(String taskId) {
        log.info("[AgentBridgeManager] 查询任务详情（含执行明细），taskId: {}", taskId);
        return platformClient.getAgentTaskResults(taskId);
    }

    public JSONObject getAgentTaskDetail(String taskId) {
        log.info("[AgentBridgeManager] 查询任务详情，taskId: {}", taskId);
        return platformClient.getAgentTaskDetail(taskId);
    }

    public boolean removeAgentCache(String flowId, String agentName) {
        log.info("[AgentBridgeManager] 移除 Agent 会话缓存，flowId: {}, agentName: {}", flowId, agentName);
        return platformClient.removeAgentCache(flowId, agentName);
    }

    public boolean healthCheck() {
        return platformClient.healthCheck();
    }

}
