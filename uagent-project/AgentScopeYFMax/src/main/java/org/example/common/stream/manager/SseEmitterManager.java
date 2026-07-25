package org.example.common.stream.manager;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SseEmitterManager {

    // 使用任务ID关联SseEmitter
    private static final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public static SseEmitter createEmitter(String taskId,Long timeout) {
        // 设置超时时间
        SseEmitter emitter = new SseEmitter(timeout);
        emitter.onCompletion(() -> emitterMap.remove(taskId));
        emitter.onTimeout(() -> {
            emitterMap.remove(taskId);
            // 可以在这里补充超时后的特定逻辑，例如记录日志
        });
        emitter.onError((ex) -> {
            emitterMap.remove(taskId);
            // 可以在这里补充错误处理逻辑，例如记录日志
        });
        addEmitter(taskId, emitter);
        return emitter;
    }

    public static void addEmitter(String taskId, SseEmitter emitter) {
        emitterMap.put(taskId, emitter);
    }

    public static void removeEmitter(String taskId) {
        emitterMap.remove(taskId);
    }

    public void clear() {
        emitterMap.forEach((id, emitter) -> emitter.complete());
        emitterMap.clear();
    }

    public static SseEmitter getEmitter(String taskId) {

        return emitterMap.get(taskId);
    }

    public static void sendMessageToClient(String taskId, String message) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            System.out.println("taskId"+taskId);
            System.out.println("message"+message);
            try {
                emitter.send(message);
            } catch (IOException e) {
                emitter.complete();
                emitterMap.remove(taskId);
            }
        }
    }

    /**
     * 以标准 SSE event 格式发送 JSON 数据
     * <p>
     * Spring SseEmitter.event().data(json) 会自动生成：
     * <pre>data:{"id":"chatcmpl-...","choices":[...]}</pre>
     * </p>
     *
     * @param taskId 任务ID
     * @param json   OpenAI chunk 的 JSON 字符串
     */
    public static void sendEvent(String taskId, String json) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(json));
            } catch (IOException e) {
                emitter.complete();
                emitterMap.remove(taskId);
            }
        }
    }

    /**
     * 发送 SSE 流结束标记 [DONE]
     * <p>
     * OpenAI 规范要求流式响应最后发送：<pre>data: [DONE]</pre>
     * </p>
     *
     * @param taskId 任务ID
     */
    public static void sendDone(String taskId) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data("[DONE]"));
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * 异常时中断SSE流
     * @param taskId 任务ID
     * @param ex 异常信息
     */
    public static void completeWithError(String taskId, Throwable ex) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            emitter.completeWithError(ex);
            emitterMap.remove(taskId);
        }
    }
    /**
     * 任务完成时候SSE中断
     * @param taskId 任务ID
     */
    public static void complete(String taskId) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            emitter.complete();
            emitterMap.remove(taskId);
        }
    }
}
