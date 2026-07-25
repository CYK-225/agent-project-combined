package org.example.common.stream;



import org.example.common.stream.manager.ResponseBodyEmitterHelper;
import org.example.common.stream.manager.SseEmitterManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/connections")
public class StreamController {
    // 默认超时时间：30分钟 (根据 Nginx/Gateway 配置调整)
    private static final Long DEFAULT_TIMEOUT = 48*30 * 60 * 1000L;

    /**
     * <p>一：建立 SSE (Server-Sent Events) 连接</p>
     * <p>适用场景：文本消息推送、日志监控、简单的进度通知</p>
     * <p>客户端：使用 EventSource API</p>
     */
    @GetMapping(value = "/sse/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse(@PathVariable String taskId) {
        System.out.println(STR."建立SSE连接，taskId=\{taskId}");

        // 1. 获取旧连接
        SseEmitter oldEmitter = SseEmitterManager.getEmitter(taskId);

        // 2. 必须移除旧的！无论它是否存活，因为它绑定的是旧的 HTTP 请求
        if (oldEmitter != null) {
            oldEmitter.complete();
            SseEmitterManager.removeEmitter(taskId);
        }

        // 3. 创建全新的 Emitter
        return SseEmitterManager.createEmitter(taskId, DEFAULT_TIMEOUT);
    }
    /**
     * <p>二：建立通用流 (ResponseBodyEmitter) 连接</p>
     * <p>适用场景：传输 JSON 对象流、文件流、大量数据分块下载</p>
     * <p>客户端：使用 fetch API + ReadableStreamReader</p>
     */
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseBodyEmitter> connectStream(@PathVariable String taskId) {
        System.out.println(STR."建立通用流连接，taskId=\{taskId}");
        // 2. 创建并注册连接
        // 这里的 ResponseBodyEmitterHelper 是上一步编写的类
        ResponseBodyEmitter oldEmitter= ResponseBodyEmitterHelper.getEmitter(taskId);
        if (oldEmitter != null) {
            oldEmitter.complete();
            ResponseBodyEmitterHelper.removeEmitter(taskId);
        }
        ResponseBodyEmitter emitter = ResponseBodyEmitterHelper.createEmitter(taskId, DEFAULT_TIMEOUT);
            // 3. 返回实体
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(emitter);
    }

// ==================== 以下为测试推送逻辑 ====================

    /**
     * 测试 SSE 推送
     * 前端先调用 /sse/{taskId} 监听，再调用此接口触发推送
     */
    @PostMapping("/test/sse/send/{taskId}")
    public String testSseSend(@PathVariable String taskId) {
        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    SseEmitterManager.sendMessageToClient(taskId, "这是第 " + i + " 条测试消息 (SSE)");
                    Thread.sleep(1000); // 模拟耗时任务
                }
                SseEmitterManager.complete(taskId);
            } catch (Exception e) {
                SseEmitterManager.completeWithError(taskId, e);
            }
        });
        return "SSE 异步推送任务已启动，请观察订阅端";
    }

    /**
     * 测试 ResponseBodyEmitter 推送
     * 前端先调用 /stream/{taskId} 监听，再调用此接口触发推送
     */
    @PostMapping("/test/stream/send/{taskId}")
    public String testStreamSend(@PathVariable String taskId) {
        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("step", i);
                    data.put("content", "正在处理分块数据...");
                    data.put("timestamp", System.currentTimeMillis());

                    ResponseBodyEmitterHelper.sendMessageToClient(taskId, data);
                    Thread.sleep(1000);
                }
                ResponseBodyEmitterHelper.complete(taskId);
            } catch (Exception e) {
                ResponseBodyEmitterHelper.completeWithError(taskId, e);
            }
        });
        return "JSON Stream 异步推送任务已启动，请观察订阅端";
    }



}
