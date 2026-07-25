package com.cyk.DockerTool.CookieGet.config;

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
 * CookieGet Agent 的配置属性。
 * 这些属性控制 Cookie 容器的路径配置、内存资源分配和默认参数。
 */
@Component
@ConfigurationProperties(prefix = "agent.cookie")
@Data
public class CookieProperties {

    private static final Logger log = LoggerFactory.getLogger(CookieProperties.class);

    // ==================== 路径配置 ====================

    /**
     * 宿主机Cookie池物理路径
     */
    private String cookiePoolPath = "/usr/local/server/ai/AutoGUI/ENS/Profiles";

    // ==================== 默认配置 ====================

    /**
     * Docker 镜像名称
     */
    private String imageName = "gui-agent:cookie-get";

    /**
     * 默认回调 URL (注意：修正了连续两个端口号的问题)
     */
    private String defaultCallbackUrl = "http://8.129.128.167:8099/api/cookie/callback";

    /**
     * 内存限制（字节）- 1GB
     */
    private Long memoryLimit = 1073741824L;

    /**
     * 内存交换限制（字节）- 2GB
     */
    private Long memorySwap = 2147483648L;

    /**
     * 共享内存大小（字节）- 512MB
     * 【极其重要】：Playwright/Chromium 渲染页面极度依赖 shm，如果不设置，容器内会频繁崩溃！
     */
    private Long shmSize = 536870912L;

    // ==================== V1 镜像 AI 破防配置 ====================

    /**
     * V1 AI 镜像名称
     */
    private String v1ImageName = "gui-agent:v1";

    /**
     * 视觉大模型的 API Key
     */
    private String aiApiKey;

    /**
     * 大模型请求网关
     */
    private String aiBaseUrl;

    /**
     * 使用的具体模型名称
     */
    private String aiModel;

    /**
     * ENScan 配置文件存放的基础路径
     */
    private String ensConfigPath = "/usr/local/server/ai/AutoGUI/ENS/configs";

    // ==================== 目录管理方法 ====================

    /**
     * 初始化时验证并创建 Cookie 池目录
     */
    @PostConstruct
    public void init() {
        log.info("初始化 Cookie Agent 路径配置，Cookie池基础路径：{}", cookiePoolPath);
        validateAndCreateDirectory();
    }

    /**
     * 验证并创建基础目录，赋予读写权限
     */
    private void validateAndCreateDirectory() {
        try {
            Path poolPath = Paths.get(cookiePoolPath);

            // 检查基础路径是否存在
            if (!Files.exists(poolPath)) {
                log.info("Cookie池目录不存在，正在创建：{}", cookiePoolPath);
                Files.createDirectories(poolPath);
                log.info("Cookie池目录创建成功：{}", cookiePoolPath);
            }

            // 验证并强行赋予读写权限，防止 Docker 挂载后因为权限问题无法写入
            File dir = poolPath.toFile();
            if (!dir.canRead() || !dir.canWrite()) {
                log.warn("Cookie池目录权限不足，尝试提升权限：{}", cookiePoolPath);
                dir.setReadable(true, false);
                dir.setWritable(true, false);
                dir.setExecutable(true, false);
            }

            log.info("Cookie池目录验证通过：{}", cookiePoolPath);

        } catch (Exception e) {
            log.error("验证或创建 Cookie池目录失败：{}", e.getMessage(), e);
            // 不抛出异常，允许应用继续启动，但需要留意日志
        }
    }

    // ==================== 容器内路径获取 ====================

    /**
     * 获取容器内读写模式下的 Cookie 挂载路径
     * @return 容器内路径
     */
    public String getContainerCookiePoolPath() {
        return "/app/cookie_pool";
    }

    /**
     * 获取容器内只读模式(心跳检测)下的基础 Cookie 挂载路径
     * @return 容器内路径
     */
    public String getContainerBaseCookiePoolPath() {
        return "/app/base_cookie_pool";
    }
}