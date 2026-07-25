package com.cyk.Listeners;

import com.cyk.Utils.SseEmitterManager;
import com.cyk.events.CompanyProcessEvent;
import com.cyk.events.SsePushEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * HrService任务事件监听器
 *
 * <p>监听Base模块发布的任务系统事件，处理V3任务相关的业务逻辑。</p>
 */
@Slf4j
@Component
public class HrTaskEventListener {

    @Resource
    private SseEmitterManager sseEmitterManager;

    // =====================================================================
    // 1. 监听: SSE 统一推送
    // =====================================================================
    @Async
    @EventListener
    public void handleSsePushEvent(SsePushEvent event) {
        log.info("[HrService] 收到SSE推送事件: taskId={}", event.getTaskId());
        if (event.getPayload() != null) {
            sseEmitterManager.sendJsonEventByTaskId(event.getTaskId(), event.getPayload());
        } else {
            sseEmitterManager.sendEventByTaskId(
                    event.getTaskId(), event.getCompanyName(), event.getMessage(), event.getData());
        }
    }

    // =====================================================================
    // 2. 监听: 企业状态更新事件（预留）
    // =====================================================================
    @Async
    @EventListener
    public void handleCompanyProcessEvent(CompanyProcessEvent event) {
        String action = event.getAction();
        log.info("[HrService] 收到企业处理事件: companyId={}, action={}", event.getCompanyId(), action);
        // V3业务逻辑后续补充
    }
}
