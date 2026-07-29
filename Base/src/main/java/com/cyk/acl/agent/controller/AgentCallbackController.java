package com.cyk.acl.agent.controller;

import com.cyk.acl.agent.AgentBridgeManager;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

/**
 * Agent 中台回调控制器
 * <p>
 * 接收 AI 中台的回调，处理 Agent 状态变化：
 * 1. Agent 挂起（SUSPENDED）- 需要工具平台执行 GUI 操作
 * 2. Agent 完成（COMPLETED）- 任务结束
 * 3. Agent 失败（FAILED）- 任务异常
 * </p>
 *
 * <h3>回调地址：</h3>
 * <pre>
 * POST /api/v3/agent/callback - AI 中台回调地址
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v3/agent")
public class AgentCallbackController {

    private final AgentBridgeManager agentBridgeManager;

    public AgentCallbackController(AgentBridgeManager agentBridgeManager) {
        this.agentBridgeManager = agentBridgeManager;
    }

    /**
     * 接收 AI 中台的回调
     * <p>
     * AI 中台在以下情况会调用此接口：
     * 1. Agent 调用了工具（挂起），需要工具平台执行 GUI 操作
     * 2. Agent 执行完成
     * 3. Agent 执行失败
     * </p>
     *
     * @param notifyDTO 回调通知
     * @return 处理结果
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleAgentCallback(
            @RequestBody AgentTaskNotifyDTO notifyDTO) {

        log.info("[AgentCallbackController] 收到 AI 中台回调，taskId: {}, status: {}",
                notifyDTO.getTaskId(), notifyDTO.getStatus());

        try {
            Map<String, Object> result = agentBridgeManager.handleAgentCallback(notifyDTO);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AgentCallbackController] 处理回调失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "处理回调失败: " + e.getMessage()));
        }
    }

    /**
     * 查询 Agent 任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        log.info("[AgentCallbackController] 查询任务状态，taskId: {}", taskId);

        try {
            // 查询 AI 中台
            com.alibaba.fastjson2.JSONObject resp = agentBridgeManager.getAgentTaskStatus(taskId);
            if (resp != null && resp.getBooleanValue("success")) {
                return ResponseEntity.ok(resp);
            }

            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "任务不存在: " + taskId
            ));
        } catch (Exception e) {
            log.error("[AgentCallbackController] 查询任务状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 取消 Agent 任务
     *
     * @param taskId 任务 ID
     * @return 取消结果
     */
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        log.info("[AgentCallbackController] 取消任务，taskId: {}", taskId);

        try {
            boolean success = agentBridgeManager.cancelAgentTask(taskId);

            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "任务取消成功"
                ));
            } else {
                return ResponseEntity.internalServerError()
                        .body(Map.of("success", false, "message", "任务取消失败"));
            }
        } catch (Exception e) {
            log.error("[AgentCallbackController] 取消任务失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "取消失败: " + e.getMessage()));
        }
    }

    /**
     * 查询任务的多步骤聚合结果
     * <p>
     * 从 AI 中台获取指定 taskId 下所有步骤的 modelOutput，
     * 用于前端展示完整的多步骤执行报告或业务数据落盘
     * </p>
     *
     * @param taskId 任务 ID
     * @return 所有步骤的聚合结果
     */
    @GetMapping("/results/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskResults(@PathVariable String taskId) {
        log.info("[AgentCallbackController] 查询任务聚合结果，taskId: {}", taskId);

        try {
            com.alibaba.fastjson2.JSONObject results = agentBridgeManager.getAccumulatedResults(taskId);
            return ResponseEntity.ok(Objects.requireNonNullElseGet(results, () -> Map.of(
                    "success", false,
                    "message", "查询聚合结果失败，AI 中台无响应"
            )));

        } catch (Exception e) {
            log.error("[AgentCallbackController] 查询任务聚合结果失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查
     *
     * @return AI 中台是否可达
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean healthy = agentBridgeManager.healthCheck();

        return ResponseEntity.ok(Map.of(
            "success", healthy,
            "message", healthy ? "AI 中台可达" : "AI 中台不可达"
        ));
    }
}
