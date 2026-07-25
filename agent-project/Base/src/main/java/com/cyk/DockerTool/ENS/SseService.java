package com.cyk.DockerTool.ENS;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    // 存放客户端连接: clientId -> SseEmitter
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    // 存放任务与客户端的绑定关系: taskId -> clientId
    private final Map<String, String> taskClientMap = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 连接
     */
    public SseEmitter createConnect(String clientId) {
        // 设置超时时间，0表示不过期，通常设置为 30 分钟 (1800000L) 等
        SseEmitter emitter = new SseEmitter(1800000L);
        emitterMap.put(clientId, emitter);

        emitter.onCompletion(() -> emitterMap.remove(clientId));
        emitter.onTimeout(() -> emitterMap.remove(clientId));
        emitter.onError(e -> emitterMap.remove(clientId));

        log.info("SSE 客户端已连接: {}", clientId);
        return emitter;
    }

    /**
     * 绑定任务和客户端
     */
    public void bindTaskToClient(String taskId, String clientId) {
        taskClientMap.put(taskId, clientId);
    }

    /**
     * 根据 taskId 发送事件给对应的前端
     */
    public void sendEventByTaskId(String taskId, String company, String status, Object data) {
        String clientId = taskClientMap.get(taskId);
        if (clientId == null) return;

        SseEmitter emitter = emitterMap.get(clientId);
        if (emitter != null) {
            try {
                // 构建推送给前端的 JSON 数据结构
                Map<String, Object> eventData = new ConcurrentHashMap<>();
                eventData.put("taskId", taskId);
                eventData.put("company", company);
                eventData.put("status", status); // 未开始、采集中、已完成、失败
                if (data != null) {
                    eventData.put("data", data);
                }

                emitter.send(SseEmitter.event().id(taskId).name("task_update").data(eventData));
            } catch (IOException e) {
                log.error("推送 SSE 事件失败, clientId: {}", clientId, e);
                emitterMap.remove(clientId);
            }
        }
    }

    /**
     * 🌟 全局广播任务状态 (彻底抛弃 taskId 和 clientId 的绑定烦恼)
     */
    public void broadcastTaskUpdate(String company, String status, Object data) {
        log.info("\n📢 [SSE 全局广播] 准备推送状态 | 公司: [{}] | 状态: [{}]", company, status);

        if (emitterMap.isEmpty()) {
            log.warn("📢 [SSE 全局广播] 当前没有任何前端客户端连接，消息被忽略！");
            return;
        }

        // 构建 Payload
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("company", company);
        eventData.put("status", status);
        if (data != null) {
            eventData.put("data", data);
        }

        // 遍历所有连接的客户端，全部发送
        for (Map.Entry<String, SseEmitter> entry : emitterMap.entrySet()) {
            String currentClientId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event().name("task_update").data(eventData));
                log.info("✅ [SSE 全局广播] 成功推送给客户端: {}", currentClientId);
            } catch (IOException e) {
                log.error("❌ [SSE 全局广播] 推送失败，客户端已断开，清理连接: {}", currentClientId);
                emitterMap.remove(currentClientId);
            }
        }
    }
}