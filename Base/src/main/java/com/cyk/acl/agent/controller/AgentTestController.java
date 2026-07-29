package com.cyk.acl.agent.controller;

import com.cyk.acl.agent.AgentBridgeManager;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 中台测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/test/agent")
public class AgentTestController {

    private final AgentBridgeManager agentBridgeManager;

    public AgentTestController(AgentBridgeManager agentBridgeManager) {
        this.agentBridgeManager = agentBridgeManager;
    }

    /**
     * 调用 Agent（测试接口）- 直接透传 DTO
     */
    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invokeAgent(@RequestBody AgentTaskNotifyDTO dto) {
        log.info("[测试] 调用 Agent，agentName: {}, sessionId: {}",
                dto.getAgentName(), dto.getSessionId());

        // taskId 默认生成
        if (dto.getTaskId() == null || dto.getTaskId().isBlank()) {
            dto.setTaskId("task_" + System.currentTimeMillis());
        }

        // sessionId 默认等于 taskId
        if (dto.getSessionId() == null || dto.getSessionId().isBlank()) {
            dto.setSessionId(dto.getTaskId());
        }

        boolean success = agentBridgeManager.invokeAgent(dto);

        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Agent 调用成功",
                "taskId", dto.getTaskId(),
                "sessionId", dto.getSessionId()
            ));
        } else {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Agent 调用失败"));
        }
    }

    /**
     * 恢复 Agent（测试接口）- 直接透传 DTO
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeAgent(@RequestBody AgentTaskNotifyDTO dto) {
        log.info("[测试] 恢复 Agent，taskId: {}", dto.getTaskId());

        boolean success = agentBridgeManager.resumeAgent(dto);

        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Agent 恢复成功"
            ));
        } else {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Agent 恢复失败"));
        }
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        log.info("[测试] 查询任务状态，taskId: {}", taskId);

        try {
            com.alibaba.fastjson2.JSONObject resp = agentBridgeManager.getAgentTaskStatus(taskId);
            if (resp != null && resp.getBooleanValue("success")) {
                return ResponseEntity.ok(resp);
            }

            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "任务不存在: " + taskId
            ));
        } catch (Exception e) {
            log.error("[测试] 查询任务状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 取消任务
     */
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        log.info("[测试] 取消任务，taskId: {}", taskId);

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
            log.error("[测试] 取消任务失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "取消失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查
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
