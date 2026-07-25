package com.cyk.Enity;

import lombok.Data;

import java.util.Map;

@Data
public class V1ActionTo {

    /**
     * 提示词
     */
    private String instruction;
    /**
     * 账号配置名称(账号)
     */
    private String configName;
    /**
     * 是否更新配置
     */
    private Boolean updateIs;
    /**
     * 任务ID
     */
    private Long taskId;
    /**
     * 输出格式
     */
    private Map<String, Object> outputFormat;
}
