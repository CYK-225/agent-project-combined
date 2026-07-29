package com.cyk.task.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步配置类
 *
 * <p>启用 Spring 异步功能和定时任务调度。</p>
 *
 * @author system
 * @since 1.0
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    // 使用 Spring 默认的异步线程池
    // 如需自定义，可覆盖 taskExecutor Bean
}
