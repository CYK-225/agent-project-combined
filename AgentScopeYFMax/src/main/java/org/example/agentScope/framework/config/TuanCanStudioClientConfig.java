package org.example.agentScope.framework.config;


import io.agentscope.core.studio.StudioClient;
import io.agentscope.core.studio.StudioConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * TuanCan 项目独立 Studio 客户端配置
 * <p>
 * 为 PermissionInjectionAgent 提供独立的 Studio 客户端，
 * 使其消息归属到 TuanCan 项目，而非全局的 AgentCar 项目。
 */
@Slf4j
@Configuration("tuanCanStudioClientConfig")
public class TuanCanStudioClientConfig {

    @Value("${agentscope.studio.tuancan.enabled:false}")
    private boolean tuancanStudioEnabled;

    @Value("${agentscope.studio.url:http://localhost:3000}")
    private String studioUrl;

    private StudioClient tuancanClient;

    @PostConstruct
    public void init() {
        if (tuancanStudioEnabled) {
            log.info("【TuanCan Studio】Initializing TuanCan Studio Client at {}", studioUrl);
            try {
                StudioConfig config = StudioConfig.builder()
                        .studioUrl(studioUrl)
                        .project("TuanCan")
                        .runName(STR."团餐任务_\{LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}")
                        .build();
                tuancanClient = new StudioClient(config);
                tuancanClient.registerRun().block();
                log.info("TuanCan Studio Client connected successfully.");
            } catch (Exception e) {
                log.error("Failed to initialize TuanCan Studio Client", e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (tuancanClient != null) {
            log.info("【TuanCan Studio】Shutting down TuanCan Studio Client...");
            try {
                tuancanClient.shutdown();
            } catch (Exception e) {
                log.error("Error during TuanCan Studio Client shutdown", e);
            }
        }
    }

    /**
     * 获取 TuanCan 项目的 Studio 客户端
     *
     * @return StudioClient 实例，如果未启用则返回 null
     */
    @Bean("tuanCanStudioClient")
    public StudioClient tuanCanStudioClient() {
        return tuancanClient;
    }
}
