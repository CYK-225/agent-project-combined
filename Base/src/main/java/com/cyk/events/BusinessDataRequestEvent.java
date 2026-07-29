package com.cyk.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class BusinessDataRequestEvent extends ApplicationEvent {
    // === 请求参数 (Base模块提供给业务模块) ===
    private final String companyName;
    private final String taskType;
    private final List<String> fieldList;
    private final boolean isRetry;

    // === 响应数据 (由 BaiduService 模块监听并 set 填充) ===
    private String companyId;
    private Map<String, Object> needFind;
    private String mission;
    private String webAddress;

    public BusinessDataRequestEvent(Object source, String companyName, String taskType, List<String> fieldList, boolean isRetry) {
        super(source);
        this.companyName = companyName;
        this.taskType = taskType;
        this.fieldList = fieldList;
        this.isRetry = isRetry;
    }


}