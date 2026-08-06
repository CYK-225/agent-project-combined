package org.example.acl.callBack;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.acl.apiClient.AgentTaskExecutorService;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 手机操控回调 — 接收容器推送的工具执行结果 + 新截图
 * <p>
 * 本控制器只负责接收 HTTP 回调，所有业务逻辑（缓存校验、工具结果构建、Agent 恢复、
 * 任务状态管理、完成后通知）全部委托给
 * {@link AgentTaskExecutorService#resumeTask(String, String, String)}。
 * <p>
 * 容器每次回调只带一个结果 + 一张截图。
 * toolUseId 由 {@link org.example.agentScope.mas.phone.hook.ScreenshotInjectionHook#handlePreActing}
 * 在每次工具调用前自动缓存。
 * <p>
 * 通知容器执行工具调用由工具本身负责，本接口只负责接收回调。
 */
@Slf4j
@RestController
@RequestMapping("/api/phone")
public class PhoneCallbackController {

    @Resource
    private AgentTaskExecutorService agentTaskExecutorService;

    /**
     * 接收容器回调 — 单个工具结果 + 单张截图，驱动挂起-恢复循环
     * <p>
     * 仅做 HTTP 层面的接收，业务逻辑全部在
     * {@link AgentTaskExecutorService#resumeTask} 中处理：
     * <ol>
     *   <li>从 DB 查询任务 → 校验存在性</li>
     *   <li>从 AgentSessionCache 获取 Agent 实例 → 校验缓存</li>
     *   <li>更新截图 → 获取 toolUseId → 构建 ToolResultBlock</li>
     *   <li>异步恢复 Agent 执行（subscribe）</li>
     *   <li>处理再次挂起 / 正常完成（含任务状态更新 + 通知工具平台） / 异常</li>
     * </ol>
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody AgentTaskNotifyDTO payload) {
        String threadId = payload.getSessionId();
        String agentName = payload.getAgentName();
        log.info("[PhoneCallback] 收到回调: threadId={}, agentName={}, taskId={}", threadId, agentName, payload.getTaskId());

        // 委托给 AgentTaskExecutorService，由它统一处理：
        //   DB 查询 → 缓存校验 → 截图更新 → toolUseId 获取 → 执行明细写入 → Agent 恢复 → 状态管理 → 通知
        agentTaskExecutorService.resumeTask(payload);

        return ResponseEntity.ok(Map.of("status", "accepted"));
    }
}
