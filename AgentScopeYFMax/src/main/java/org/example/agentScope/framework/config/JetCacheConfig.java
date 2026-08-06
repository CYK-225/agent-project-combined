package org.example.agentScope.framework.config;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.context.annotation.Configuration;

/**
 * JetCache 配置
 * <p>
 * {@code @CreateCache} 在 JetCache 2.7.5 已废弃，2.8+ 的 {@code QuickConfig} 尚不可用。
 * 当前版本仅启用 {@code @EnableMethodCache} 以支持方法级缓存注解。
 * <p>
 * Agent 实例缓存使用 YAML 驱动的 {@link org.example.agentScope.framework.core.AgentSessionCache}，
 * 配置项在 {@code agentscope.session-cache.*} 下。
 * <p>
 * 升级 JetCache 2.8+ 后可引入 {@code CacheManager.getOrCreateCache(QuickConfig)} 替代。
 */
@Configuration
@EnableMethodCache(basePackages = "org.example")
public class JetCacheConfig {
}
