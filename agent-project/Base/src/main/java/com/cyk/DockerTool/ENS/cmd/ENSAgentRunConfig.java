package com.cyk.DockerTool.ENS.cmd;

import lombok.Data;

/**
 * ENS Agent 运行配置
 * 注意：默认值已移至 ENSAgentProperties 配置类，通过 application.yml 进行配置
 */
@Data
public class ENSAgentRunConfig {

    // 基础配置（从 ENSAgentProperties 读取）
    private String imageName;

    // 资源限制 (单位: Bytes)
    private Long memoryBytes;
    private Long memorySwapBytes;
    private Long shmSizeBytes;

    /**
     * 查询类型：aqc (爱企查), qcc (企查查), tianyan (天眼查)
     */
    private String type = "rb";

    /**
     * 企业名称或 PID
     */
    private String companyName;

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 回调 URL（从配置读取）
     */
    private String callbackUrl;

    /**
     * 伪装的 MAC 地址
     */
    private String macAddress = "02:42:ac:11:00:03";

    /**
     * 用户配置
     */
    private String configName;
}
