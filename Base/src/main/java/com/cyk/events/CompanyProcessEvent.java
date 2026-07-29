package com.cyk.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.Map;

@Getter
public class CompanyProcessEvent extends ApplicationEvent {
    private final String companyId;
    private final String companyName;
    private final Map<String, Object> dataMap;
    private final String action; // 操作类型：UPDATE_DATA, UPDATE_STATUS_1, UPDATE_STATUS_2

    public CompanyProcessEvent(Object source, String companyId, String companyName, Map<String, Object> dataMap, String action) {
        super(source);
        this.companyId = companyId;
        this.companyName = companyName;
        this.dataMap = dataMap;
        this.action = action;
    }


}