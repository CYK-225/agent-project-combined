package com.cyk.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * 任务状态变更，通知业务模块通过 SSE 推送给前端的事件
 */

@Getter
public class SsePushEvent extends ApplicationEvent {

    private String taskId;
    private String companyName;
    private String message;
    private Object data;
    private Map<String, Object> payload;

    public SsePushEvent(Object source, String taskId, String companyName, String message, Object data) {
        super(source);
        this.taskId = taskId;
        this.companyName = companyName;
        this.message = message;
        this.data = data;
    }

    public SsePushEvent(Object source, String taskId, Map<String, Object> payload) {
        super(source);
        this.taskId = taskId;
        this.payload = payload;
    }
}