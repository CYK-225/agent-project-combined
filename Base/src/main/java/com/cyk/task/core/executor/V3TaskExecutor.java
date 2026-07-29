package com.cyk.task.core.executor;

import com.cyk.DockerTool.V3.DockerPoolManagerV3;
import com.cyk.DockerTool.V3.config.AgentPoolPropertiesV3;
import com.cyk.DockerTool.V3.model.ContainerPodV3;
import com.cyk.Enity.table.PromptsEntity;
import com.cyk.Service.IPromptsService;
import com.cyk.Utils.V3EmitterManager;
import com.cyk.acl.agent.AgentBridgeManager;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import com.cyk.common.ResultData;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.core.constants.TaskConstants;
import com.cyk.task.core.enums.TaskType;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * V3任务执行器（异步回调模式）
 *
 * <p>工作流提示词分两大类：</p>
 * <ul>
 *   <li><b>系统提示词</b>（step=-1）：角色(type=1)、物理规则(type=2)、背景知识(type=4)等，
 *       启动时拼装后通过DTO的sysPrompt字段一次性注入Agent</li>
 *   <li><b>执行提示词</b>（step=1,2,3...）：步骤(type=3)、步骤输出(type=5)，
 *       通过DTO的promptsId+step字段标识，AgentBridgeManager自动循环调度</li>
 * </ul>
 *
 * <p>调用链路：</p>
 * <pre>
 * start() → 拼装DTO(sysPrompt + promptsId + step=1) → invokeAgent
 *                                               ↓
 *                              AgentBridgeManager自动处理回调循环：
 *                              COMPLETED → 查DB下一步 → 有则invokeAgent(step+1)
 *                                                     → 无则onTaskCompleted
 *                              FAILED → onTaskCompleted(false)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class V3TaskExecutor implements TaskExecutor {

    private final CustomTaskScheduler customTaskScheduler;
    private final DockerPoolManagerV3 poolManager;
    private final AgentPoolPropertiesV3 properties;
    private final IPromptsService promptsService;
    private final AgentBridgeManager agentBridgeManager;
    private final V3EmitterManager emitterManager;

    @PostConstruct
    public void init() {
        customTaskScheduler.registerExecutor(TaskType.V3.getCode(), this);
        log.info("[V3执行器] 已注册到调度器");
    }

    @Override
    public boolean start(TaskInfoEntity task) throws Exception {
        log.info("[V3执行器] 准备启动任务: ID={}, 类型={}, configName={}, promptId={}",
                task.getId(), task.getTaskType(), task.getConfigName(), task.getPromptId());

        Long promptId = task.getPromptId();
        if (promptId == null) {
            throw new IllegalArgumentException("V3任务缺少promptId");
        }

        // 1. 查DB: 加载该工作流下所有提示词记录
        ResultData<List<PromptsEntity>> result = promptsService.selectListByPromptId(String.valueOf(promptId));
        if (result == null || result.getData() == null || result.getData().isEmpty()) {
            throw new IllegalArgumentException("未找到promptId=" + promptId + "对应的提示词记录");
        }

        List<PromptsEntity> allPrompts = result.getData();

        // 2. 分类：系统提示词(step=-1) 和 执行步骤(step>0)
        List<PromptsEntity> systemPrompts = allPrompts.stream()
                .filter(p -> p.getStep() != null && p.getStep() == -1)
                .collect(Collectors.toList());

        List<PromptsEntity> executionSteps = allPrompts.stream()
                .filter(p -> p.getStep() != null && p.getStep() > 0)
                .sorted(Comparator.comparingInt(PromptsEntity::getStep))
                .collect(Collectors.toList());

        log.info("[V3执行器] 提示词加载完成: promptId={}, 系统提示词={}条, 执行步骤={}条",
                promptId, systemPrompts.size(), executionSteps.size());

        // 3. 创建全新容器（一任务一容器）
        String profileName = task.getConfigName();
        if (profileName == null || profileName.isBlank()) {
            profileName = "default";
        }

        ContainerPodV3 pod = poolManager.createPod(profileName, 0);
        log.info("[V3执行器] 容器就绪: podId={}, port={}, profile={}",
                pod.getShortId(), pod.getAssignedPort(), profileName);

        // 4. 构造v3TaskId
        Long v3TaskId = task.getId();

        // 5. 初始化任务上下文（V3全部只读模式，无需isUpdateProfile）
        poolManager.initTaskContext(
                v3TaskId, pod,
                executionSteps.isEmpty() ? "" : executionSteps.get(0).getContent(),
                properties.getDefaultApiKey(), properties.getDefaultBaseUrl(),
                properties.getDefaultModel(), properties.getDefaultMaxSteps());

        // 6. 计算动态超时（基于执行步骤数）
        int totalSteps = executionSteps.size();
        long estimatedTotalMs = (long) Math.max(totalSteps, 1) * TaskConstants.V3_PER_STEP_ESTIMATED_MS;
        long timeoutMs = (long) (estimatedTotalMs * TaskConstants.V3_TIMEOUT_SAFETY_FACTOR);
        timeoutMs = Math.max(timeoutMs, TaskConstants.V3_MIN_TIMEOUT_MS);
        timeoutMs = Math.min(timeoutMs, TaskConstants.V3_MAX_TIMEOUT_MS);
        customTaskScheduler.setTaskTimeout(task.getId(), timeoutMs);
        log.info("[V3执行器] 动态超时: steps={}, 预估={}ms, 最终={}ms ({}分钟)",
                totalSteps, estimatedTotalMs, timeoutMs, timeoutMs / 60_000);

        // 7. 拼装系统提示词
        String sysPrompt = assembleSystemPrompt(systemPrompts);

        // 8. 构造DTO，一次invokeAgent发送系统提示词+第一步
        AgentTaskNotifyDTO dto = AgentTaskNotifyDTO.builder()
                .agentName("hr-agent")
                .taskId(String.valueOf(v3TaskId))
                .sessionId(String.valueOf(v3TaskId))
                .sysPrompt(sysPrompt)
                .promptsId(String.valueOf(promptId))
                .step(1)
                .containerUrl(properties.getCallbackBaseUrl() + ":" + pod.getAssignedPort())
                .build();

        log.info("[V3执行器] 发起Agent调用: v3TaskId={}, promptsId={}, step=1, sysPrompt前80字={}",
                v3TaskId, promptId,
                sysPrompt.isEmpty() ? "(无)" : (sysPrompt.length() > 80 ? sysPrompt.substring(0, 80) + "..." : sysPrompt));

        boolean success = agentBridgeManager.invokeAgent(dto);

        if (!success) {
            throw new RuntimeException("Agent调用失败: v3TaskId=" + v3TaskId);
        }

        return true;
    }

    // ==================== 系统提示词拼装 ====================

    /**
     * 拼装系统提示词
     *
     * <p>将所有step=-1的提示词按类型分块拼装：</p>
     * <pre>
     * ## 角色设定
     * {角色提示词内容}
     * ## 物理规则
     * {物理规则提示词内容}
     * ## 背景知识
     * {背景知识提示词内容}
     * </pre>
     */
    private String assembleSystemPrompt(List<PromptsEntity> systemPrompts) {
        if (systemPrompts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        appendByType(sb, systemPrompts, 1, "角色设定");
        appendByType(sb, systemPrompts, 2, "物理规则");
        appendByType(sb, systemPrompts, 4, "背景知识");
        return sb.toString().trim();
    }

    private void appendByType(StringBuilder sb, List<PromptsEntity> prompts, int type, String sectionTitle) {
        List<PromptsEntity> filtered = prompts.stream()
                .filter(p -> p.getType() != null && p.getType() == type)
                .collect(Collectors.toList());

        for (PromptsEntity p : filtered) {
            if (p.getContent() != null && !p.getContent().isBlank()) {
                sb.append("## ").append(sectionTitle).append("\n");
                sb.append(p.getContent()).append("\n\n");
            }
        }
    }

    @Override
    public String getTaskType() {
        return TaskType.V3.getCode();
    }

    @Override
    public String getName() {
        return "V3任务执行器";
    }
}
