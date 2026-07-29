package com.cyk.Utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service // 🌟 1. 改为 Spring 管理的单例 Bean
public class V2EmitterManager {

    // 🌟 2. 核心：使用 clientId 而不是 taskId，避免浏览器连接数超限
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    // 🌟 3. 辅助字典：将后端生成的 taskId 绑定到前端的 clientId
    private final Map<String, String> taskToClientMap = new ConcurrentHashMap<>();

    /**
     * 创建并保存 SSE 连接
     */
    public SseEmitter createEmitter(String clientId, Long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);

        emitter.onCompletion(() -> emitterMap.remove(clientId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时，clientId: {}", clientId);
            emitterMap.remove(clientId);
        });
        emitter.onError((ex) -> {
            log.error("SSE 连接异常，clientId: {}", clientId, ex);
            emitterMap.remove(clientId);
        });

        emitterMap.put(clientId, emitter);
        log.info("SSE 客户端已连接: {}", clientId);
        return emitter;
    }

    /**
     * 绑定任务与客户端 (在下发批量任务时调用)
     */
    public void bindTask(String taskId, String clientId) {
        taskToClientMap.put(taskId, clientId);
    }


    /**
     * 推送任务更新信息到前端 (根据容器id)
     */
    public void sendMessageByContainerId(String containerId, Object messagePayload) {
        SseEmitter emitter = emitterMap.get(containerId);
        if (emitter != null) {
            try {
                // 🌟 4. 必须指定 name("task_update")，否则前端 addEventListener 收不到
                emitter.send(SseEmitter.event()
                        .name("task_update")
                        .data(messagePayload));
            } catch (IOException e) {
                log.error("SSE 推送异常，主动断开客户端: {}", containerId);
                emitter.complete();
                emitterMap.remove(containerId);
            }
        }
    }

    /**
     * 推送任务更新信息到前端 (核心推送逻辑)
     */
    public void sendMessageByTaskId(String taskId, Object messagePayload) {
        String clientId = taskToClientMap.get(taskId);
        if (clientId == null) {
            log.warn("未找到 taskId: {} 对应的 clientId，无法推送", taskId);
            return;
        }

        SseEmitter emitter = emitterMap.get(clientId);
        if (emitter != null) {
            try {
                // 🌟 4. 必须指定 name("task_update")，否则前端 addEventListener 收不到
                emitter.send(SseEmitter.event()
                        .name("task_update")
                        .data(messagePayload));
            } catch (IOException e) {
                log.error("SSE 推送异常，主动断开客户端: {}", clientId);
                emitter.complete();
                emitterMap.remove(clientId);
            }
        }
    }
    /**
     * 发送包含公司名称、状态、数据的普通事件
     *
     * @param taskId      任务ID
     * @param companyName 公司名称
     * @param message     状态信息（如：采集中、已完成）
     * @param data        附加数据（可为null）
     */
    public void sendEventByTaskId(String taskId, String companyName, String message, Object data) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("company", companyName);
        payload.put("status", message);
        if (data != null) {
            payload.put("data", data);
        }
        // 复用你已有的底层发送方法
        sendMessageByTaskId(taskId, payload);
    }
    /**
     * 发送自定义 JSON 载荷事件
     *
     * @param taskId  任务ID
     * @param payload JSON Map 数据包
     */
    public void sendJsonEventByTaskId(String taskId, Map<String, Object> payload) {
        // 直接复用你已有的底层发送方法
        sendMessageByTaskId(taskId, payload);
    }
    public void removeEmitter(String clientId) {
        emitterMap.remove(clientId);
    }

    /**
     * 任务完成时，清理 Task 映射关系
     */
    public void clearTask(String taskId) {
        taskToClientMap.remove(taskId);
    }
}