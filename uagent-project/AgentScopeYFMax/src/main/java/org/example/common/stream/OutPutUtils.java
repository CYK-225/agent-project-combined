package org.example.common.stream;

import lombok.extern.slf4j.Slf4j;


import org.example.common.stream.manager.ResponseBodyEmitterHelper;
import org.example.common.stream.manager.SseEmitterManager;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class OutPutUtils {

    /**
     * @param type           "answer" 或 "data" 或"status"
     * @param taskId         任务ID
     * @param status         枚举类型
     * @param statusName     阶段子描述
     * @param content        String 或 Flux<String>
     */
    public static CompletableFuture<String> send(String type, String taskId, Status status, String statusName, Object content) {
        String statusChannel = STR."\{taskId}_status";
        String contentChannel = STR."\{taskId}_\{type}";
        CompletableFuture<String> future = new CompletableFuture<>();

        // 记录器：如果是 Flux 会用到它累积，如果是 String 稍后直接赋值
        final StringBuilder answerBuilder = new StringBuilder();
        Flux<String> sourceFlux;

        if (content instanceof Flux<?> flux) {
            sourceFlux = flux.map(String::valueOf)
                    .doOnNext(answerBuilder::append) // 累积 Flux 的每一片内容
                    .delayElements(Duration.ofMillis(10));
        } else {
            String strContent = String.valueOf(content);
            answerBuilder.append(strContent); // String 直接存入

            if ("data".equals(type)) {
                // data 类型通常是 JSON 字符串，直接发，不拆分
                System.out.println(STR."data content:\{strContent}");
                sourceFlux = Flux.just(strContent);
            } else {
                // answer 类型按字符拆分模拟流式
                sourceFlux = Flux.fromArray(strContent.split(""))
                        .delayElements(Duration.ofMillis(20));
            }
        }

        AtomicBoolean isFirst = new AtomicBoolean(true);

        sourceFlux.subscribe(
                chunk -> {
                    if (isFirst.compareAndSet(true, false)) {
                        SseEmitterManager.sendMessageToClient(statusChannel, status.getStartTag(statusName));
                        try {
                            Thread.sleep(100); // 确保开始标签先到达
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if ("data".equals(type)) {
                        System.out.println(STR."data chunk:\{chunk}");
                        ResponseBodyEmitterHelper.sendMessageToClient(contentChannel, chunk);
                    } else {
                        SseEmitterManager.sendMessageToClient(contentChannel, chunk);
                    }
                },
                error -> {
                    SseEmitterManager.sendMessageToClient(statusChannel, status.getErrorTag(error.getMessage()));
                    future.completeExceptionally(error);
                },
                () -> {
                    try {
                        Thread.sleep(100); // 确保开始不黏包
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    SseEmitterManager.sendMessageToClient(statusChannel, status.getEndTag(statusName));

                    // 【核心】流结束后，通过回调把累积完整的字符串传回去
                    future.complete(answerBuilder.toString());
                }
        );
        return future; // 立即返回这个“凭证”
    }
    public static void end(String taskId){
        String statusChannel = STR."\{taskId}_status";
        SseEmitterManager.sendMessageToClient(statusChannel, "<彻底结束>:END");
    }

    public static void erro(String taskId,String msg){
        String statusChannel = STR."\{taskId}_status";
        SseEmitterManager.sendMessageToClient(statusChannel, STR."<对话异常>:\{msg}");
    }

}