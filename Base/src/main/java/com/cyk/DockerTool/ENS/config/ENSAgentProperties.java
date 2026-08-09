package com.cyk.DockerTool.ENS.config;

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
 * ENS Agent 的配置属性。
 * 这些属性控制 ENS 容器的路径配置和默认参数。
 */
@Component
@ConfigurationProperties(prefix = "agent.ens")
@Data
public class ENSAgentProperties {

    private static final Logger log = LoggerFactory.getLogger(ENSAgentProperties.class);

    // ==================== 路径配置 ====================

    /**
     * ENScan 基础路径（云服务器上的实际路径）
     * 默认值：/usr/local/server/ai/AutoGUI/ENS
     */
    private String baseDir = "/usr/local/server/ai/AutoGUI/ENS";

    /**
     * ENScan 可执行文件路径
     * 完整路径：{baseDir}/enscan
     */
    private String enscanPath = "/usr/local/server/ai/AutoGUI/ENS/enscan";

    /**
     * 输出目录名称（相对于基础目录）
     */
    private String outputDirName = "OutPut";

    // ==================== 默认配置 ====================

    /**
     * 默认回调 URL（由 yml agent.ens.default-callback-url 配置）
     */
    private String defaultCallbackUrl;

    /**
     * Docker 镜像名称
     */
    private String imageName = "gui-agent:ENS";

    /**
     * 内存限制（字节）
     */
    private long memoryLimit = 536870912L;  // 512MB

    /**
     * 内存交换限制（字节）
     */
    private long memorySwap = 1073741824L;   // 1GB

    /**
     * 共享内存大小（字节）
     */
    private long shmSize = 268435456L;       // 256MB

    /**
     * ENScan API 端口
     */
    private int apiPort = 8100;

    // ==================== Cookies 配置 ====================

    /**
     * 爱企查 (AQC) Cookie
     */
    private String aqcCookie;

    /**
     * 天眼查 (TYC) Cookie
     */
    private String tycCookie;

    /**
     * 天眼查 tycid
     */
    private String tycId;

    /**
     * 天眼查 auth_token
     */
    private String tycAuthToken;

    /**
     * 快查 (KC) Cookie
     */
    private String kcCookie;

    /**
     * 风鸟 (RB) Cookie
     */
    private String rbCookie;

    // ==================== 目录管理方法 ====================

    /**
     * 初始化时验证并创建基础目录
     */
    @PostConstruct
    public void init() {
        log.info("初始化 ENS Agent 路径配置，基础路径：{}", baseDir);
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
                log.info("ENS 基础目录不存在，正在创建：{}", baseDir);
                Files.createDirectories(basePath);
                log.info("ENS 基础目录创建成功：{}", baseDir);
            }

            // 创建输出目录
            Path outputPath = Paths.get(baseDir, outputDirName);
            if (!Files.exists(outputPath)) {
                log.info("ENS 输出目录不存在，正在创建：{}", outputPath);
                Files.createDirectories(outputPath);
            }

            // 验证权限
            if (!Files.isReadable(basePath) || !Files.isWritable(basePath)) {
                throw new IllegalStateException("ENS 基础目录权限不足，需要读写权限：" + baseDir);
            }

            log.info("ENS 基础目录验证通过：{}", baseDir);

        } catch (Exception e) {
            log.error("验证或创建 ENS 基础目录失败：{}", e.getMessage(), e);
            // 不抛出异常，允许应用继续启动
        }
    }

    /**
     * 获取指定任务的输出目录路径
     * 返回 Docker 兼容的正斜杠路径格式
     *
     * @param taskId 任务 ID
     * @return 输出目录完整路径（正斜杠格式）
     */
    public String getOutputPath(String taskId) {
        String path = Paths.get(baseDir, outputDirName, taskId).toString();
        // Docker 需要正斜杠路径，Windows 路径需要转换
        return path.replace("\\", "/");
    }

    /**
     * 获取容器内的输出目录路径
     *
     * @param taskId 任务 ID
     * @return 容器内输出目录路径
     */
    public String getContainerOutputPath(String taskId) {
        return "/app/output/" + taskId;
    }

    /**
     * 获取容器内的 ENScan 路径
     */
    public String getContainerENScanPath() {
        return "/app/enscan";
    }

    /**
     * 获取路径配置的摘要信息
     *
     * @return 路径配置摘要
     */
    public String getPathSummary() {
        return String.format(
                "ENSPathConfig{baseDir='%s', enscanPath='%s', outputDir='%s'}",
                baseDir, enscanPath, outputDirName
        );
    }

    // ==================== Config.yaml 生成 ====================

    /**
     * 生成 ENScan_GO 的 config.yaml 内容
     *
     * @return YAML 格式的配置内容
     */
    public String generateConfigYaml() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# ENScan_GO 配置文件\n");
        yaml.append("# 由 ENSAgentProperties 自动生成\n\n");

        // 爱企查配置
        if (isNotEmpty(aqcCookie)) {
            yaml.append("aqc:\n");
            yaml.append("  cookie: \"").append(escapeYaml(aqcCookie)).append("\"\n");
        }

        // 天眼查配置
        if (isNotEmpty(tycCookie) || isNotEmpty(tycId) || isNotEmpty(tycAuthToken)) {
            yaml.append("tyc:\n");
            if (isNotEmpty(tycCookie)) {
                yaml.append("  cookie: \"").append(escapeYaml(tycCookie)).append("\"\n");
            }
            if (isNotEmpty(tycId)) {
                yaml.append("  tycid: \"").append(escapeYaml(tycId)).append("\"\n");
            }
            if (isNotEmpty(tycAuthToken)) {
                yaml.append("  auth_token: \"").append(escapeYaml(tycAuthToken)).append("\"\n");
            }
        }

        // 快查配置
        if (isNotEmpty(kcCookie)) {
            yaml.append("kc:\n");
            yaml.append("  cookie: \"").append(escapeYaml(kcCookie)).append("\"\n");
        }

        // 风鸟配置
        if (isNotEmpty(rbCookie)) {
            yaml.append("rb:\n");
            yaml.append("  cookie: \"").append(escapeYaml(rbCookie)).append("\"\n");
        }

        return yaml.toString();
    }

    /**
     * 获取 config.yaml 文件的宿主机路径 (支持多账号动态路径)
     *
     * @param configName 账号名称 (例如: 13145739225)
     * @return config.yaml 完整路径
     */
    public String getConfigYamlPath(String configName) {
        // 如果传入了 configName，则拼接多租户路径
        if (configName != null && !configName.trim().isEmpty()) {
            // 拼接格式: /usr/local/server/ai/AutoGUI/ENS/configs/13145739225/config_13145739225.yaml
            return Paths.get(baseDir, "configs", configName, "config_" + configName + ".yaml")
                    .toString().replace("\\", "/");
        }

        // 默认回退路径 (兼容老逻辑)
        return Paths.get(baseDir, "config.yaml").toString().replace("\\", "/");
    }
    /**
     * 获取容器内的 config.yaml 路径
     *
     * @return 容器内 config.yaml 路径
     */
    public String getContainerConfigYamlPath() {
        return "/app/config.yaml";
    }

    /**
     * 判断字符串是否非空
     */
    private boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 转义 YAML 字符串中的特殊字符
     */
    private String escapeYaml(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
