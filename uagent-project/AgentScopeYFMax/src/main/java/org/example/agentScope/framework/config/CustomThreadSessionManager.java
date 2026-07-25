package org.example.agentScope.framework.config;


import io.agentscope.core.agent.Agent;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 自定义 ThreadSessionManager
 * - 支持 Thread-Aware Factory，允许在创建 Agent 时注入 threadId
 * - 自动管理会话缓存，确保每个 threadId 对应独立 Agent 实例
 * - 支持清除指定 Agent 的所有会话（用于动态绑定后刷新）
 */
@Slf4j
@Component
@Primary // 确保覆盖框架默认的 ThreadSessionManager
public class CustomThreadSessionManager extends ThreadSessionManager {

    // Thread-Aware Factory 映射：agentId -> (threadId -> Agent)
    private final Map<String, Function<String, Agent>> threadAwareFactories = new ConcurrentHashMap<>();

    // 缓存 sessions 字段的反射访问，避免重复查找
    private volatile Field sessionsField;

    public CustomThreadSessionManager(
            @Value("${agentscope.agui.max-sessions:1000}") int maxSessions,
            @Value("${agentscope.agui.session-timeout-minutes:30}") int sessionTimeoutMinutes) {
        super(maxSessions, sessionTimeoutMinutes);
        log.info("CustomThreadSessionManager initialized. maxSessions={}, timeout={}min",
                maxSessions, sessionTimeoutMinutes);
    }

    /**
     * 注册 Thread-Aware Factory
     * @param agentId Agent ID
     * @param factory 接收 threadId 并返回 Agent 的函数
     */
    public void registerThreadAwareFactory(String agentId, Function<String, Agent> factory) {
        threadAwareFactories.put(agentId, factory);
        log.info("Registered thread-aware factory for agentId: {}", agentId);
    }

    /**
     * 清除指定 Agent 的所有会话缓存。
     * <p>
     * 用于动态绑定后刷新，确保下次请求时重建 Agent 并加载新的 Skill。
     *
     * @param agentId Agent ID
     * @return 清除的会话数量
     */
    public int removeAllSessionsForAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            log.warn("⚠️ Cannot remove sessions: agentId is null or blank");
            return 0;
        }

        log.info("🗑️ Starting session cache cleanup for agentId: {}", agentId);

        try {
            // 通过反射获取父类的 sessions Map
            Field field = getSessionsField();
            if (field == null) {
                log.error("❌ Cannot access sessions field via reflection - cleanup aborted");
                return 0;
            }

            @SuppressWarnings("unchecked")
            Map<String, ?> sessions = (Map<String, ?>) field.get(this);
            if (sessions == null) {
                log.info("ℹ️ Sessions map is null - nothing to clean");
                return 0;
            }

            if (sessions.isEmpty()) {
                log.info("ℹ️ Sessions map is empty - nothing to clean");
                return 0;
            }

            log.info("📊 Total sessions before cleanup: {}", sessions.size());

            // 遍历并删除匹配的会话
            int removedCount = 0;
            var iterator = sessions.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Object session = entry.getValue();

                // ThreadSession 是内部类，通过反射检查 agentId
                try {
                    Field agentIdField = session.getClass().getDeclaredField("agentId");
                    agentIdField.setAccessible(true);
                    String sessionAgentId = (String) agentIdField.get(session);

                    if (agentId.equals(sessionAgentId)) {
                        String threadId = entry.getKey();
                        iterator.remove();
                        removedCount++;
                        log.debug("   🗑️ Removed session: threadId={}, agentId={}", threadId, agentId);
                    }
                } catch (Exception e) {
                    // 如果无法检查 agentId，跳过此会话
                    log.debug("   ⚠️ Failed to check session agentId: {}", e.getMessage());
                }
            }

            if (removedCount > 0) {
                log.info("✅ Session cache cleanup completed: removed {} sessions for agentId: {}", removedCount, agentId);
                log.info("📊 Total sessions after cleanup: {}", sessions.size());
            } else {
                log.info("ℹ️ No sessions found for agentId: {} - cache already clean", agentId);
            }

            return removedCount;
        } catch (Exception e) {
            log.error("❌ Failed to remove sessions for agentId: {}", agentId, e);
            return 0;
        }
    }

    private Field getSessionsField() {
        if (sessionsField != null) {
            return sessionsField;
        }
        try {
            // ThreadSessionManager 的 sessions 字段
            sessionsField = ThreadSessionManager.class.getDeclaredField("sessions");
            sessionsField.setAccessible(true);
            return sessionsField;
        } catch (NoSuchFieldException e) {
            log.error("Cannot find 'sessions' field in ThreadSessionManager", e);
            return null;
        }
    }

    @Override
    public Agent getOrCreateAgent(String threadId, String agentId, Supplier<Agent> agentFactory) {
        // 1. 检查是否有自定义的 Thread-Aware Factory
        Function<String, Agent> customFactory = threadAwareFactories.get(agentId);

        if (customFactory != null) {
            log.debug("Using thread-aware factory for agentId: {}, threadId: {}", agentId, threadId);

            // 2. 包装为 Supplier，注入 threadId
            // super.getOrCreateAgent 会处理缓存逻辑，如果已缓存则不会调用此 Supplier
            Supplier<Agent> injectedFactory = () -> customFactory.apply(threadId);

            return super.getOrCreateAgent(threadId, agentId, injectedFactory);
        }

        // 3. 降级到默认逻辑
        return super.getOrCreateAgent(threadId, agentId, agentFactory);
    }
}
