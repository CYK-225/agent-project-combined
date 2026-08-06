package com.cyk.DockerTool.V3.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * V3代理池的配置属性。
 * 配置前缀：agent.v3.pool
 */
@Component
@ConfigurationProperties(prefix = "agent.v3.pool")
@Data
public class AgentPoolPropertiesV3 {

    private static final Logger log = LoggerFactory.getLogger(AgentPoolPropertiesV3.class);

    private int maxPoolSize = 5;

    private int portRangeStart = 9301;

    private int portRangeEnd = portRangeStart + maxPoolSize * 2 - 1;

    private long idleTimeoutSeconds = 300;

    private String imageName = "autogui-v3:latest";

    private long memoryLimit = 1073741824L;

    private long memorySwap = 2147483648L;

    private long shmSize = 536870912L;

    private String callbackBaseUrl = "http://8.163.67.126";

    private String dockerHostIp = "8.163.67.126";

    // ==================== 路径配置 ====================

    private String profileBasePath = "/usr/local/server/ai/AutoGUI/V3/profiles";

    private String outputDirName = "OutPut";

    private String chromeDataDirName = "chrome_data";

    // ==================== LLM默认配置 ====================

    private String defaultApiKey = "sk-e26ef7931f2a480b9d7ec6a2fb56527a";

    private String defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String defaultModel = "qwen3.5-plus";

    private int defaultMaxSteps = 40;

    // ==================== 目录管理方法 ====================

    @PostConstruct
    public void init() {
        log.info("[V3] 初始化路径配置，基础路径：{}", profileBasePath);
        validateAndCreateBaseDirectory();
    }

    private void validateAndCreateBaseDirectory() {
        try {
            Path basePath = Paths.get(profileBasePath);

            if (!Files.exists(basePath)) {
                log.info("[V3] 基础目录不存在，正在创建：{}", profileBasePath);
                Files.createDirectories(basePath);
                log.info("[V3] 基础目录创建成功：{}", profileBasePath);
            }

            if (!Files.isReadable(basePath) || !Files.isWritable(basePath)) {
                throw new IllegalStateException("[V3] 基础目录权限不足，需要读写权限：" + profileBasePath);
            }

            log.info("[V3] 基础目录验证通过：{}", profileBasePath);

        } catch (Exception e) {
            log.error("[V3] 验证或创建基础目录失败：{}", e.getMessage(), e);
            throw new IllegalStateException("[V3] 无法初始化基础目录：" + profileBasePath, e);
        }
    }

    public String getUserBasePath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username).toString();
    }

    public String getUserOutputPath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username, outputDirName).toString();
    }

    public String getUserChromeDataPath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username, chromeDataDirName).toString();
    }

    public void validateAndCreateUserDirectories(String username) {
        validateUsername(username);

        try {
            String userBasePath = getUserBasePath(username);
            Path userBaseDir = Paths.get(userBasePath);
            if (!Files.exists(userBaseDir)) {
                log.info("[V3] 创建用户目录：{}", userBasePath);
                Files.createDirectories(userBaseDir);
            }

            String outputPath = getUserOutputPath(username);
            Path outputDir = Paths.get(outputPath);
            if (!Files.exists(outputDir)) {
                log.info("[V3] 创建用户输出目录：{}", outputPath);
                Files.createDirectories(outputDir);
            }

            String chromeDataPath = getUserChromeDataPath(username);
            Path chromeDataDir = Paths.get(chromeDataPath);
            if (!Files.exists(chromeDataDir)) {
                log.info("[V3] 创建用户浏览器数据目录：{}", chromeDataPath);
                Files.createDirectories(chromeDataDir);
            }

            log.info("[V3] 用户目录验证完成：{}", username);

        } catch (Exception e) {
            log.error("[V3] 创建用户目录失败：username={}, error={}", username, e.getMessage(), e);
            throw new IllegalStateException("[V3] 无法创建用户目录：" + username, e);
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        if (username.contains("..") || username.contains("/") || username.contains("\\")) {
            throw new IllegalArgumentException("用户名包含非法字符：" + username);
        }
    }

    public boolean userDirectoryExists(String username) {
        validateUsername(username);
        return Files.exists(Paths.get(getUserBasePath(username)));
    }

    public String getPathSummary() {
        return String.format(
                "V3PathConfig{basePath='%s', outputDir='%s', chromeDataDir='%s'}",
                profileBasePath, outputDirName, chromeDataDirName
        );
    }
}
