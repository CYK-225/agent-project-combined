package com.cyk.acl.agent.controller;

import com.cyk.acl.agent.AgentBridgeManager;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 容器回调控制器（桥接版）
 * <p>
 * 专门处理容器的回调，并桥接到 AI 中台：
 * 1. 收到容器回调（GUI 操作完成）
 * 2. 调用 AgentBridgeManager.resumeAgent() 恢复 AI 中台的 Agent
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v3/container")
public class ContainerBridgeController {

    private final AgentBridgeManager agentBridgeManager;

    public ContainerBridgeController(AgentBridgeManager agentBridgeManager) {
        this.agentBridgeManager = agentBridgeManager;
    }

    /**
     * 处理容器回调（桥接到 AI 中台）
     * <p>
     * 容器执行完 GUI 操作后，调用此接口。
     * 容器回调的 DTO 直接透传给 AgentBridgeManager.resumeAgent()。
     * </p>
     *
     * @param containerId 容器 ID
     * @param dto 回调 DTO（resume 场景）
     * @return 处理结果
     */
    @PostMapping("/callback/{containerId}")
    public ResponseEntity<Map<String, Object>> handleContainerCallback(
            @PathVariable String containerId,
            @RequestBody AgentTaskNotifyDTO dto) {

        log.info("[ContainerBridgeController] 收到容器回调，containerId: {}, taskId: {}",
                containerId, dto.getTaskId());

        try {
            String taskId = dto.getTaskId();
            if (taskId == null) {
                log.warn("[ContainerBridgeController] 回调缺少 taskId");
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "缺少 taskId"));
            }

            Boolean success = dto.getSuccess();
            log.info("[ContainerBridgeController] 容器执行结果，taskId: {}, success: {}, step: {}",
                     taskId, success, dto.getStep());

            if (!Boolean.TRUE.equals(success)) {
                // 容器执行失败，补充错误信息后透传
                dto.setResult("容器执行失败: " + dto.getResult());
            }

            boolean resumed = agentBridgeManager.resumeAgent(dto);

            if (resumed) {
                log.info("[ContainerBridgeController] 已恢复 AI 中台 Agent，taskId: {}", taskId);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已恢复 AI 中台 Agent"
                ));
            } else {
                log.error("[ContainerBridgeController] 恢复 AI 中台 Agent 失败，taskId: {}", taskId);
                return ResponseEntity.internalServerError()
                        .body(Map.of("success", false, "message", "恢复 AI 中台 Agent 失败"));
            }

        } catch (Exception e) {
            log.error("[ContainerBridgeController] 处理容器回调失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "处理失败: " + e.getMessage()));
        }
    }

    /**
     * 查询容器任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        log.info("[ContainerBridgeController] 查询任务状态，taskId: {}", taskId);

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
            log.error("[ContainerBridgeController] 查询任务状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "查询失败: " + e.getMessage()));
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
