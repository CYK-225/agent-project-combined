package com.cyk.DockerTool.V1.cmd;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * V1 Agent 运行配置
 * 注意：默认值已移至 V1AgentProperties 配置类，通过 application.yml 进行配置
 */
@Data

public class GuiAgentRunConfig {
    // 基础配置（从 V1AgentProperties 读取）
    private String imageName;

    // 资源限制 (单位: Bytes)
    // 1.2g ≈ 1288490188L, 2g = 2147483648L, 512m = 536870912L
    private Long memoryBytes;
    private Long memorySwapBytes;
    private Long shmSizeBytes;


    // Agent 运行参数
    private String apiKey;
    private String baseUrl;
    private String model;
    private Integer maxSteps;

    // AI 具体的 Instruction (Prompt)
    private String instruction;
    /**
     * 用户配置的浏览器数据目录名称（可选），如果不提供则使用默认值 "default_profile"
      * 例如，用户 "zjhtest" 可以传入 "user_zjhtest"，系统会在配置的 profileBasePath 目录下创建对应用户目录
      * 这样做的好处是可以为不同用户隔离浏览器数据，避免冲突，并且方便管理和清理
      */
    private String profileName;
    /**
     * 是否更新浏览器配置数据（可选），默认为 false
     */
    private Boolean isUpdateProfile = false;
    /**
     * 伪装的mac地址
     */
    private String macAddress;
    /**
     * 期望的输出格式模板
     * Value 可以是 String（描述要求），也可以是嵌套的 Map 或 List，用于定义复杂结构
     */
    private Map<String, Object> outputFormat;
    /**
     * 任务id
     */
    private String taskId;
    /**
     * 回调 URL（从配置读取）
     */
    private String callbackUrl;
}
