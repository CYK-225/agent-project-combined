package org.example.agentScope.util.tool.DockerTool.Weixin.config;

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
 * 微信 Docker 容器池的配置属性。
 * 与现有的 Chrome 实现完全隔离。
 * 
 * 关键配置：
 * - 微信数据目录：/root/.xwechat（登录状态）
 * - 微信文件目录：/root/xwechat_files（聊天文件）
 * - MAC 地址确定性生成：基于用户名 MD5 哈希
 * @author zzh
 */
@Component
@ConfigurationProperties(prefix = "agent.wechat.pool")
@Data
public class WeChatDockerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WeChatDockerConfiguration.class);

    // ==================== 池配置 ====================

    /**
     * 池中容器的最大数量
     */
    private int maxPoolSize = 10;

    /**
     * 端口范围起始值（应为奇数，用于 FastAPI）
     */
    private int portRangeStart = 9101;

    /**
     * 端口范围结束值
     * 注意：每个容器需要2个端口（FastAPI奇数端口 + VNC偶数端口）
     */
    private int portRangeEnd = portRangeStart + maxPoolSize * 2 - 1;

    /**
     * 容器空闲超时时间（秒），超时后自动清理
     */
    private long idleTimeoutSeconds = 600;  // 微信登录状态保持时间更长

    /**
     * 代理容器的 Docker 镜像名称
     */
    private String imageName = "weixin-agent:v2";

    // ==================== 资源限制 ====================

    /**
     * 容器内存限制（字节），默认：2GB（微信客户端需要更多内存）
     */
    private long memoryLimit = 2147483648L;

    /**
     * 容器内存交换限制（字节），默认：4GB
     */
    private long memorySwap = 4294967296L;

    /**
     * 容器共享内存大小（字节），默认：1GB
     */
    private long shmSize = 1073741824L;

    // ==================== 网络配置 ====================

    /**
     * 容器访问主机的回调基础 URL
     */
    private String callbackBaseUrl = "http://host.docker.internal:8089";

    /**
     * Java 后端基础 URL（用于健康检查）
     * 注意：这是 Java 后端访问 Docker 容器的地址，应使用 localhost
     * 与 callbackBaseUrl（容器访问 Java 后端）不同
     */
    private String javaBaseUrl = "http://localhost";

    // ==================== 路径配置 ====================

    /**
     * 主机上微信数据的基础路径
     * 默认值：/usr/local/server/ai/WeChatData/profiles
     */
    private String profileBasePath = "/usr/local/server/ai/WeChatData/profiles";

    /**
     * 输出目录的相对路径（相对于用户目录）
     */
    private String outputDirName = "OutPut";

    /**
     * 微信登录状态目录名称（容器内路径：/root/.xwechat）
     */
    private String wechatDataDirName = "xwechat_data";

    /**
     * 微信文件目录名称（容器内路径：/root/xwechat_files）
     */
    private String wechatFilesDirName = "xwechat_files";

    // ==================== LLM 默认配置 ====================

    /**
     * LLM API 密钥
     */
    private String defaultApiKey = "sk-e26ef7931f2a480b9d7ec6a2fb56527a";

    /**
     * LLM API 基础 URL
     */
    private String defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * LLM 模型名称
     */
    private String defaultModel = "qwen-vl-max";

    /**
     * LLM 最大步骤数
     */
    private int defaultMaxSteps = 50;  // 微信操作可能需要更多步骤

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        log.info("初始化微信 Docker 配置，基础路径：{}", profileBasePath);
        log.info("端口范围：{} - {}", portRangeStart, portRangeEnd);
        validateAndCreateBaseDirectory();
    }

    /**
     * 验证并创建基础目录
     */
    private void validateAndCreateBaseDirectory() {
        try {
            Path basePath = Paths.get(profileBasePath);

            if (!Files.exists(basePath)) {
                log.info("基础目录不存在，正在创建：{}", profileBasePath);
                Files.createDirectories(basePath);
                log.info("基础目录创建成功：{}", profileBasePath);
            }

            if (!Files.isReadable(basePath) || !Files.isWritable(basePath)) {
                throw new IllegalStateException("基础目录权限不足，需要读写权限：" + profileBasePath);
            }

            log.info("基础目录验证通过：{}", profileBasePath);

        } catch (Exception e) {
            log.error("验证或创建基础目录失败：{}", e.getMessage(), e);
            throw new IllegalStateException("无法初始化基础目录：" + profileBasePath, e);
        }
    }

    // ==================== 路径获取方法 ====================

    /**
     * 获取用户的基础路径
     */
    public String getUserBasePath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username).toString();
    }

    /**
     * 获取用户的输出目录路径
     */
    public String getUserOutputPath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username, outputDirName).toString();
    }

    /**
     * 获取用户的微信登录状态数据目录路径
     * 该目录将挂载到容器的 /root/.xwechat
     */
    public String getUserWeChatDataPath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username, wechatDataDirName).toString();
    }
    
    /**
     * 获取用户的微信聊天文件目录路径
     * 该目录将挂载到容器的 /root/xwechat_files
     */
    public String getUserWeChatFilesPath(String username) {
        validateUsername(username);
        return Paths.get(profileBasePath, username, wechatFilesDirName).toString();
    }



    /**
     * 验证并创建用户目录
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

            // 创建微信登录状态目录
            String wechatDataPath = getUserWeChatDataPath(username);
            Path wechatDataDir = Paths.get(wechatDataPath);
            if (!Files.exists(wechatDataDir)) {
                log.info("创建用户微信数据目录：{}", wechatDataPath);
                Files.createDirectories(wechatDataDir);
            }

            // 创建微信文件目录
            String wechatFilesPath = getUserWeChatFilesPath(username);
            Path wechatFilesDir = Paths.get(wechatFilesPath);
            if (!Files.exists(wechatFilesDir)) {
                log.info("创建用户微信文件目录：{}", wechatFilesPath);
                Files.createDirectories(wechatFilesDir);
            }

            log.info("用户目录验证完成：{}", username);

        } catch (Exception e) {
            log.error("创建用户目录失败：username={}, error={}", username, e.getMessage(), e);
            throw new IllegalStateException("无法创建用户目录：" + username, e);
        }
    }

    /**
     * 验证用户名是否合法
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
     */
    public boolean userDirectoryExists(String username) {
        validateUsername(username);
        return Files.exists(Paths.get(getUserBasePath(username)));
    }

    /**
     * 获取路径配置的摘要信息
     */
    public String getPathSummary() {
        return String.format(
                "WeChatPathConfig{basePath='%s', outputDir='%s', wechatData='%s', wechatFiles='%s'}",
                profileBasePath, outputDirName, wechatDataDirName, wechatFilesDirName
        );
    }
}
