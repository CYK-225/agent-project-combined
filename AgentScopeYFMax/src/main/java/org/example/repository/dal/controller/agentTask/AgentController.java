package org.example.repository.dal.controller.agentTask;

import lombok.extern.slf4j.Slf4j;
import org.example.repository.dal.entity.AgentTaskEntity;
import org.example.repository.dal.service.IAgentExecutionDetailService;
import org.example.acl.apiClient.AgentTaskExecutorService;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;
import org.example.repository.dal.service.IAgentTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 中台 API 控制器
 * <p>
 * 提供 Agent 调用、恢复、查询等接口，供工具平台调用。
 * Controller 只做参数接收和返回，业务逻辑在 Service 层。
 * </p>
 *
 * <h3>职责划分：</h3>
 * <ul>
 *   <li>Controller：参数校验、默认值生成、任务创建/更新、调 Service、返回结果</li>
 *   <li>{@link AgentTaskExecutorService}：Agent 执行编排（创建 Agent、调用、恢复、缓存管理）</li>
 * </ul>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li><b>POST /api/agent/invoke</b> - 调用 Agent（工具平台触发）</li>
 *   <li><b>POST /api/agent/cache/remove</b> - 移除 Agent 会话缓存</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Value("${ai-url.ai-callback}")
    private String aiCallbackUrl;

    @Resource
    private AgentTaskExecutorService executorService;

    @Resource
    private IAgentExecutionDetailService executionDetailService;

    @Resource
    private IAgentTaskService agentTaskService;

    /**
     * 调用 Agent（创建或恢复会话）
     * <p>
     * 工具平台调用此接口触发 Agent 执行。流程：
     * 1. 参数校验（agentName 必填）
     * 2. 生成 taskId / sessionId 默认值（不传则自动生成）
     * 3. 创建/更新任务记录（DB）
     * 4. 启动 Agent 异步执行
     *
     * @param request 调用请求（agentName, sessionId, taskId, containerPort, instruction, callbackUrl）
     * @return 操作结果（success, taskId, sessionId）
     *         <p>containerUrl 由配置 container.host + 端口自动拼接，不在代码中硬编码 IP</p>
     */
    @PostMapping("/invoke")
    public Map<String, Object> invokeAgent(@RequestBody AgentTaskNotifyDTO request) {
        log.info("[AgentController] 调用 Agent，agentName: {}, sessionId: {}, taskId: {}， step:{}",
                 request.getAgentName(), request.getSessionId(), request.getTaskId(), request.getStep());

        // 参数校验
        if (request.getAgentName() == null || request.getAgentName().isBlank()) {
            return Map.of("success", false, "message", "agentName 不能为空");
        }

        // 生成 taskId（如果未提供）
        String taskId = request.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            return Map.of("success", false, "message", "taskId 不能为空");
        }

        // sessionId 默认等于 taskId（flowID = sessionId = taskId）
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = taskId;
        }

        // 创建/更新任务记录（DB 层）
        // containerUrl 不落盘，仅运行时使用
        AgentTaskEntity existingTask = agentTaskService.selectByTaskId(taskId);

        if (request.getAiCallbackUrl() == null || request.getAiCallbackUrl().isBlank()){
            request.setAiCallbackUrl(aiCallbackUrl);
        }

        if (existingTask != null) {
            // 任务已存在 → 更新指令 + 同步刷新 AI 中台回调地址（以当前配置为准），
            // 避免历史落盘的旧回调地址残留，导致容器回调失效、故障难排查
            existingTask.setInstruction(request.getInstruction());
            existingTask.setAiCallbackUrl(request.getAiCallbackUrl());
            existingTask.setUpdatedAt(new Date());
            agentTaskService.updateById(existingTask);
            log.info("[AgentController] 任务已存在，更新指令与回调地址，taskId: {}", taskId);
        } else {
            // 创建新任务
            AgentTaskEntity task = agentTaskService.toEntity(request);
            task.setStatus(AgentTaskEntity.Status.RUNNING);
            task.setCreatedBy("SYSTEM");
            agentTaskService.save(task);
            log.info("[AgentController] 新任务已创建，taskId: {}, agentName: {}", taskId, request.getAgentName());
        }

        // 启动 Agent 异步执行（Agent 层）
        executorService.invokeAgent(request);

        return Map.of("success", true, "message", "Agent 调用成功",
                "taskId", taskId, "sessionId", sessionId);
    }


    /**
     * 移除 Agent 会话缓存
     * <p>
     * 手动清理 AgentSessionCache 中的 Agent 实例。
     * 适用于任务异常终止、缓存残留等场景。
     *
     * @param flowId    会话 ID（对应 AgentSessionCache 的 threadId）
     * @param agentName Agent 名称（如 "hr-agent"）
     * @return 操作结果
     */
    @PostMapping("/cache/remove")
    public Map<String, Object> removeCache(@RequestParam String flowId, @RequestParam String agentName) {
        log.info("[AgentController] 移除缓存，flowId: {}, agentName: {}", flowId, agentName);
        executorService.removeCache(flowId, agentName);
        return Map.of("success", true, "message", "缓存已移除", "flowId", flowId, "agentName", agentName);
    }

}
