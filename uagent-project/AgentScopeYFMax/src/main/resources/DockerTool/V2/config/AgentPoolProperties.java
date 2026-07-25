package org.example.agentScope.util.tool.DockerTool.V2.config;

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
 * 代理池的配置属性。
 * 这些属性控制容器池行为、资源限制和外部服务URL。
 */
@Component
@ConfigurationProperties(prefix = "agent.pool")
@Data
public class AgentPoolProperties {

    private static final Logger log = LoggerFactory.getLogger(AgentPoolProperties.class);



    /**
     * 池中容器的最大数量。
     */
    private int maxPoolSize = 10;

    /**
     * 容器代理服务器的端口范围起始值（应为奇数，用于FastAPI）
     */
    private int portRangeStart = 9001;

    /**
     * 容器代理服务器的端口范围结束值
     * 注意：每个容器需要2个端口（FastAPI奇数端口 + VNC偶数端口）
     */
    private int portRangeEnd = portRangeStart + maxPoolSize * 2 - 1;
    /**
     * 容器空闲超时时间（秒），超时后自动清理)
        */
    private long idleTimeoutSeconds = 300;

    /**
     * 代理容器的Docker镜像名称
        */
    private String imageName = "gui-agent:v2";

    /**
     * 容器内存限制（字节)，默认：1GB
        */
    private long memoryLimit = 1073741824L;

    /**
     * 容器内存交换限制(字节)，默认：5GB
        */
    private long memorySwap = 2147483648L;

    /**
     * 容器共享内存大小（字节)，默认：512MB
        */
    private long shmSize = 536870912L;

    /**
     * 容器访问主机的回调基础URL
     * 使用host.docker.internal实现容器到主机的通信
        */
    private String callbackBaseUrl = "http://47.119.39.86:8089";

    // ==================== 路径配置 ====================

    /**
     * 主机上用户数据的基础路径（从 yml 配置读取）
     * 默认值：/root/AutoGUIV2/profiles
     */
    private String profileBasePath = "/usr/local/server/ai/AutoGUI/V2/profiles";

    /**
     * 输出目录的相对路径（相对于用户目录）
     * 默认值：OutPut
     * 完整路径：{profileBasePath}/{username}/OutPut
     */
    private String outputDirName = "OutPut";

    /**
     * 浏览器状态目录的相对路径（相对于用户目录）
     * 默认值：chrome_data
     * 完整路径：{profileBasePath}/{username}/chrome_data
     */
    private String chromeDataDirName = "chrome_data";

    // ==================== LLM默认配置 ====================

    /**
     * LLM API密钥（从配置获取，如未配置则使用默认值）
        */
    private String defaultApiKey = "sk-e26ef7931f2a480b9d7ec6a2fb56527a";

    /**
     * LLM API基础URL（从配置获取，如未配置则使用默认值）
        */
    private String defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * LLM模型名称（从配置获取，如未配置则使用默认值）
        */
    private String defaultModel = "qwen3.5-plus";
    /**
     * LLM最大步骤数（从配置获取，如未配置则使用默认值）
        */
            private int defaultMaxSteps = 40;

    // ==================== 目录管理方法 ====================

    /**
     * 初始化时验证并创建基础目录
     */
    @PostConstruct
    public void init() {
        log.info("初始化路径配置，基础路径：{}", profileBasePath);
        validateAndCreateBaseDirectory();
    }

    /**
     * 验证并创建基础目录
     */
    private void validateAndCreateBaseDirectory() {
        try {
            Path basePath = Paths.get(profileBasePath);

            // 检查基础路径是否存在
            if (!Files.exists(basePath)) {
                log.info("基础目录不存在，正在创建：{}", profileBasePath);
                Files.createDirectories(basePath);
                log.info("基础目录创建成功：{}", profileBasePath);
            }

            // 验证权限
            if (!Files.isReadable(basePath) || !Files.isWritable(basePath)) {
                throw new IllegalStateException("基础目录权限不足，需要读写权限：" + profileBasePath);
            }

            log.info("基础目录验证通过：{}", profileBasePath);

        } catch (Exception e) {
            log.error("验证或创建基础目录失败：{}", e.getMessage(), e);
            throw new IllegalStateException("无法初始化基础目录：" + profileBasePath, e);
        }
    }

    /**
     * 获取用户的完整基础路径
     *
     * @param username 用户名
     * @return 用户基础路径
     */
    public String getUserBasePath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username).toString();
    }

    /**
     * 获取用户的输出目录路径
     *
     * @param username 用户名
     * @return 输出目录路径
     */
    public String getUserOutputPath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username, outputDirName).toString();
    }

    /**
     * 获取用户的浏览器数据目录路径
     *
     * @param username 用户名
     * @return 浏览器数据目录路径
     */
    public String getUserChromeDataPath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username, chromeDataDirName).toString();
    }

    /**
     * 验证并创建用户目录（包括输出目录和浏览器数据目录）
     *
     * @param username 用户名
     */
    public void validateAndCreateUserDirectories(String username) {
        validateUsername(username);

        try {
            // 创建用户基础目录
            String userBasePath = getUserBasePath(username);
            Path userBaseDir = Paths.get(userBasePath);
            if (!Files.exists(userBaseDir)) {
                log.info("创建用户目录：{}", userBasePath);
                Files.createDirectories(userBaseDir);
            }

            // 创建输出目录
            String outputPath = getUserOutputPath(username);
            Path outputDir = Paths.get(outputPath);
            if (!Files.exists(outputDir)) {
                log.info("创建用户输出目录：{}", outputPath);
                Files.createDirectories(outputDir);
            }

            // 创建浏览器数据目录
            String chromeDataPath = getUserChromeDataPath(username);
            Path chromeDataDir = Paths.get(chromeDataPath);
            if (!Files.exists(chromeDataDir)) {
                log.info("创建用户浏览器数据目录：{}", chromeDataPath);
                Files.createDirectories(chromeDataDir);
            }

            log.info("用户目录验证完成：{}", username);

        } catch (Exception e) {
            log.error("创建用户目录失败：username={}, error={}", username, e.getMessage(), e);
            throw new IllegalStateException("无法创建用户目录：" + username, e);
        }
    }

    /**
     * 验证用户名是否合法
     *
     * @param username 用户名
     */
    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        // 防止路径遍历攻击
        if (username.contains("..") || username.contains("/") || username.contains("\\")) {
            throw new IllegalArgumentException("用户名包含非法字符：" + username);
        }
    }

    /**
     * 检查用户目录是否存在
     *
     * @param username 用户名
     * @return 如果用户目录存在返回 true
     */
    public boolean userDirectoryExists(String username) {
        validateUsername(username);
        return Files.exists(Paths.get(getUserBasePath(username)));
    }

    /**
     * 获取路径配置的摘要信息
     *
     * @return 路径配置摘要
     */
    public String getPathSummary() {
        return String.format(
                "PathConfig{basePath='%s', outputDir='%s', chromeDataDir='%s'}",
                profileBasePath, outputDirName, chromeDataDirName
        );
    }
}
