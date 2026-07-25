package com.cyk.DockerTool.V1.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * V1 Agent 的配置属性。
 * 这些属性控制 V1 容器的路径配置、默认参数和运行时配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.v1")
@Data
public class V1AgentProperties {

    private static final Logger log = LoggerFactory.getLogger(V1AgentProperties.class);

    // ==================== 路径配置 ====================

    /**
     * V1 Agent 基础路径（云服务器上的实际路径）
     * 默认值：/usr/local/server/ai/AutoGUI/V1
     */
    private String baseDir = "/usr/local/server/ai/AutoGUI/V1";

    /**
     * 用户配置文件基础路径
     * 完整路径：{baseDir}/profiles/{profileName}
     */
    private String profileBasePath = "/usr/local/server/ai/AutoGUI/V1/profiles";

    /**
     * 工具脚本路径
     */
    private String utilsScriptPath = "/usr/local/server/ai/AutoGUI/V1/utils.py";

    /**
     * Python 运行脚本路径
     */
    private String runnerScriptPath = "/usr/local/server/ai/AutoGUI/V1/run_gui_owl_1_5_for_pc.py";

    /**
     * 输出目录名称（相对于用户配置目录）
     */
    private String outputDirName = "OutPut";

    // ==================== 默认配置 ====================

    /**
     * 默认回调 URL
     */
    private String defaultCallbackUrl = "http://8.129.128.167:8081/api/docker/callback";

    /**
     * Docker 镜像名称
     */
    private String imageName = "gui-agent:v1";

    /**
     * 内存限制（字节）
     */
    private long memoryLimit = 1073741824L;  // 1GB

    /**
     * 内存交换限制（字节）
     */
    private long memorySwap = 2147483648L;   // 2GB

    /**
     * 共享内存大小（字节）
     */
    private long shmSize = 536870912L;       // 512MB

    /**
     * 最大步数
     */
    private Integer maxSteps = 50;

    // ==================== LLM API 配置（新增）====================

    /**
     * LLM API Key
     */
    private String apiKey = "";

    /**
     * LLM API Base URL
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * LLM 模型名称
     */
    private String model = "gpt-4o";

    /**
     * 默认配置文件名称
     */
    private String defaultProfileName = "default_profile";

    // ==================== 目录管理方法 ====================

    /**
     * 初始化时验证并创建基础目录
     */
    @PostConstruct
    public void init() {
        log.info("初始化 V1 Agent 路径配置，基础路径：{}", baseDir);
        validateAndCreateBaseDirectory();
    }

    /**
     * 验证并创建基础目录
     */
    private void validateAndCreateBaseDirectory() {
        try {
            Path basePath = Paths.get(baseDir);

            // 检查基础路径是否存在
            if (!Files.exists(basePath)) {
                log.info("V1 基础目录不存在，正在创建：{}", baseDir);
                Files.createDirectories(basePath);
                log.info("V1 基础目录创建成功：{}", baseDir);
            }

            // 创建 profiles 目录
            Path profilesPath = Paths.get(profileBasePath);
            if (!Files.exists(profilesPath)) {
                log.info("V1 profiles 目录不存在，正在创建：{}", profileBasePath);
                Files.createDirectories(profilesPath);
            }

            // 验证权限
            if (!Files.isReadable(basePath) || !Files.isWritable(basePath)) {
                throw new IllegalStateException("V1 基础目录权限不足，需要读写权限：" + baseDir);
            }

            log.info("V1 基础目录验证通过：{}", baseDir);

        } catch (Exception e) {
            log.error("验证或创建 V1 基础目录失败：{}", e.getMessage(), e);
            // 不抛出异常，允许应用继续启动
        }
    }

    /**
     * 获取指定配置文件的完整路径
     *
     * @param profileName 配置文件名称
     * @return 配置文件完整路径
     */
    public String getProfilePath(String profileName) {
        return Paths.get(profileBasePath, profileName).toString().replace("\\", "/");
    }

    /**
     * 获取指定配置文件的输出目录路径
     *
     * @param profileName 配置文件名称
     * @param taskId      任务 ID
     * @return 输出目录完整路径
     */
    public String getOutputPath(String profileName, String taskId) {
        return Paths.get(profileBasePath, profileName, outputDirName, taskId).toString().replace("\\", "/");
    }

    /**
     * 获取容器内的输出目录路径
     *
     * @param taskId 任务 ID
     * @return 容器内输出目录路径
     */
    public String getContainerOutputPath(String taskId) {
        return "/app/anno/" + taskId;
    }

    /**
     * 获取容器内的 Chrome 配置目录路径
     */
    public String getContainerChromeProfilePath() {
        return "/app/chrome_profile";
    }

    /**
     * 获取容器内的基础配置目录路径（只读模式使用）
     */
    public String getContainerBaseProfilePath() {
        return "/app/base_profile";
    }

    /**
     * 获取容器内的工具脚本路径
     */
    public String getContainerUtilsPath() {
        return "/app/computer_use/utils.py";
    }

    /**
     * 获取容器内的运行脚本路径
     */
    public String getContainerRunnerPath() {
        return "/app/computer_use/run_gui_owl_1_5_for_pc.py";
    }

    /**
     * 获取路径配置的摘要信息
     *
     * @return 路径配置摘要
     */
    public String getPathSummary() {
        return String.format(
                "V1PathConfig{baseDir='%s', profileBasePath='%s', utilsScript='%s', runnerScript='%s'}",
                baseDir, profileBasePath, utilsScriptPath, runnerScriptPath
        );
    }

    // 顺手重写这两个 getter，防止 YAML 读取时带来意外的反斜杠
    public String getUtilsScriptPath() {
        return utilsScriptPath == null ? null : utilsScriptPath.replace("\\", "/");
    }

    public String getRunnerScriptPath() {
        return runnerScriptPath == null ? null : runnerScriptPath.replace("\\", "/");
    }
}
