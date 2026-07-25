package org.example.agentScope.framework.config;


import io.agentscope.core.studio.StudioManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@Slf4j
@Configuration("studioIntegrationConfig")
public class StudioIntegrationConfig {

    @Value("${agentscope.studio.enabled:true}")
    private boolean studioEnabled;

    @Value("${agentscope.studio.url:http://localhost:3000}")
    private String studioUrl;

    @PostConstruct
    public void initStudio() {
        String timeSuffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        if (studioEnabled) {
            log.info("【1. Setup】Initializing Studio Server connection at {}", studioUrl);
            try {
                // 对应你的步骤 1
                StudioManager.init()
                        .studioUrl(studioUrl)
                        .project("AgentCar")
                        .runName(STR."分析任务_\{timeSuffix}")
                        .initialize()
                        .block();
                log.info("Studio Server connected successfully.");
            } catch (Exception e) {
                log.error("Failed to connect to Studio Server", e);
            }
        }
    }

    @PreDestroy
    public void shutdownStudio() {
        if (studioEnabled) {
            log.info("【4. Cleanup】Shutting down Studio Server connection...");
            try {
                // 对应你的步骤 4
                StudioManager.shutdown();
            } catch (Exception e) {
                log.error("Error during Studio shutdown", e);
            }
        }
    }
}
