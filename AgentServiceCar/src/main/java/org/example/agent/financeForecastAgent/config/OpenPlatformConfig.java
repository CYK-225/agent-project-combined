package org.example.agent.financeForecastAgent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 开放平台接口配置
 * author: zhilin
 * 2026.04.14
 */
@Component
@Data
@ConfigurationProperties(prefix = "open-platform")
public class OpenPlatformConfig {

    /**
     * 接口域名
     */
    private String baseUrl = "https://api.youfantech.cn";

    /**
     * 应用ID（测试环境使用 "1111" 可绕过签名校验）
     */
    private String appId = "cli_79b4340a2e9142e";

    /**
     * 应用密钥
     */
    private String appSecret = "d94a35f311b4482b949042a83b4cc6f6";

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 10000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 30000;
}
