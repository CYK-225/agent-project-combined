package com.cyk.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.Map;

/**
 * ENS数据抓取完成，通知业务模块进行数据同步入库的事件
 */

@Getter
public class EnsDataSyncEvent extends ApplicationEvent {
    private final String companyName;
    private final Map<String, Object> dataMap;

    public EnsDataSyncEvent(Object source, String companyName, Map<String, Object> dataMap) {
        super(source);
        this.companyName = companyName;
        this.dataMap = dataMap;
    }


}