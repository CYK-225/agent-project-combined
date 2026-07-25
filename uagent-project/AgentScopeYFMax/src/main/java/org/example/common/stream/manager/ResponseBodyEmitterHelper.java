package org.example.common.stream.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ResponseBodyEmitter 管理工具类
 * 用于通用异步请求处理，支持流式发送 JSON、文件等数据
 */
@Slf4j
public class ResponseBodyEmitterHelper {

    // 使用任务ID关联 ResponseBodyEmitter
    private static final Map<String, ResponseBodyEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 创建并注册 Emitter
     * @param taskId 任务ID
     * @param timeout 超时时间 (毫秒)，传入 null 则使用 Spring Boot 默认配置
     * @return ResponseBodyEmitter
     */
    public static ResponseBodyEmitter createEmitter(String taskId, Long timeout) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(timeout);
        addEmitter(taskId, emitter);
        return emitter;
    }

    /**
     * 手动添加 Emitter 并设置回调
     */
    public static void addEmitter(String taskId, ResponseBodyEmitter emitter) {
        emitterMap.put(taskId, emitter);
        // 注册回调：完成或超时后移除引用，防止内存泄漏
        emitter.onCompletion(() -> emitterMap.remove(taskId));
        emitter.onTimeout(() -> {
            emitterMap.remove(taskId);
            // 可以在这里补充超时后的特定逻辑，例如记录日志
        });
    }

    /**
     * 移除 Emitter (不触发完成操作，仅从 Map 移除)
     */
    public static void removeEmitter(String taskId) {
        emitterMap.remove(taskId);
    }

    /**
     * 清空所有连接
     */
    public void clear() {
        emitterMap.forEach((id, emitter) -> emitter.complete());
        emitterMap.clear();
    }

    /**
     * 获取 Emitter 实例
     */
    public static ResponseBodyEmitter getEmitter(String taskId) {

        return emitterMap.get(taskId);
    }

    /**
     * 发送数据给客户端
     * @param taskId 任务ID
     * @param data 要发送的数据对象 (会被 HttpMessageConverter 自动转换，如转为 JSON)
     */
    public static void sendMessageToClient(String taskId, Object data) {
        ResponseBodyEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            try {
                // ResponseBodyEmitter 可以发送任意对象，不仅仅是 String
                log.info("Json发送的信息 {}: {}", taskId, data);
                emitter.send(data);

            } catch (IOException e) {
                // 发送失败（如客户端断开连接），移除该 emitter
                emitter.completeWithError(e); // 建议显式调用 completeWithError
                emitterMap.remove(taskId);
            }
        }
    }

    /**
     * 异常时中断流
     * @param taskId 任务ID
     * @param ex 异常信息
     */
    public static void completeWithError(String taskId, Throwable ex) {
        ResponseBodyEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            emitter.completeWithError(ex);
            emitterMap.remove(taskId);
        }
    }

    /**
     * 任务正常完成时关闭流
     * @param taskId 任务ID
     */
    public static void complete(String taskId) {
        ResponseBodyEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            emitter.complete();
            emitterMap.remove(taskId);
        }
    }
}
