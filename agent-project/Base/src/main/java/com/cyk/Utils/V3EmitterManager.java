package com.cyk.Utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class V3EmitterManager {

    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    private final Map<String, String> taskToClientMap = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String clientId, Long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);

        emitter.onCompletion(() -> emitterMap.remove(clientId));
        emitter.onTimeout(() -> {
            log.warn("[V3] SSE 连接超时，clientId: {}", clientId);
            emitterMap.remove(clientId);
        });
        emitter.onError((ex) -> {
            log.error("[V3] SSE 连接异常，clientId: {}", clientId, ex);
            emitterMap.remove(clientId);
        });

        emitterMap.put(clientId, emitter);
        log.info("[V3] SSE 客户端已连接: {}", clientId);
        return emitter;
    }

    public void bindTask(String taskId, String clientId) {
        taskToClientMap.put(taskId, clientId);
    }

    public void sendMessageByContainerId(String containerId, Object messagePayload) {
        SseEmitter emitter = emitterMap.get(containerId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("task_update")
                        .data(messagePayload));
            } catch (IOException e) {
                log.error("[V3] SSE 推送异常，主动断开客户端: {}", containerId);
                emitter.complete();
                emitterMap.remove(containerId);
            }
        }
    }

    public void sendMessageByTaskId(String taskId, Object messagePayload) {
        String clientId = taskToClientMap.get(taskId);
        if (clientId == null) {
            log.warn("[V3] 未找到 taskId: {} 对应的 clientId，无法推送", taskId);
            return;
        }

        SseEmitter emitter = emitterMap.get(clientId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("task_update")
                        .data(messagePayload));
            } catch (IOException e) {
                log.error("[V3] SSE 推送异常，主动断开客户端: {}", clientId);
                emitter.complete();
                emitterMap.remove(clientId);
            }
        }
    }

    public void sendEventByTaskId(String taskId, String companyName, String message, Object data) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("company", companyName);
        payload.put("status", message);
        if (data != null) {
            payload.put("data", data);
        }
        sendMessageByTaskId(taskId, payload);
    }

    public void sendJsonEventByTaskId(String taskId, Map<String, Object> payload) {
        sendMessageByTaskId(taskId, payload);
    }

    public void removeEmitter(String clientId) {
        emitterMap.remove(clientId);
    }

    public void clearTask(String taskId) {
        taskToClientMap.remove(taskId);
    }
}
