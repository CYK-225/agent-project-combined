package org.example.agentScope.framework.core;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.tool.ToolExecutionContext;
import org.example.agentScope.mas.reActAgent.AgentConfigPo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Agent 实例会话缓存
 * <p>
 * 以 {@code threadId + agentName} 为 key 缓存 Agent 实例，避免重复构建。
 * 缓存穿透时自动调用 {@link AgentPoolManager#getAgentWithSession(String, String)} 创建。
 * <p>
 * <h3>配置项（application.yml）</h3>
 * <pre>
 * agentscope:
 *   session-cache:
 *     expire-after-access-minutes: 30   # 最后访问后多久过期
 *     max-size: 1000                    # 最大缓存条目数
 * </pre>
 * <p>
 * <h3>Fallback 机制</h3>
 * JetCache 2.7.5 的 {@code @CreateCache} 已废弃，2.8+ 的 {@code QuickConfig} 尚不可用。
 * 当前版本使用 {@link ConcurrentHashMap} + 惰性过期检查实现缓存。
 * 升级 JetCache 2.8+ 后可引入 {@code CacheManager.getOrCreateCache(QuickConfig)} 替代。
 *
 * @author AgentScope-Team
 */
@Slf4j
@Component
public class AgentSessionCache {

    @Value("${agentscope.session-cache.expire-after-access-minutes:30}")
    private long expireAfterAccessMinutes;

    @Value("${agentscope.session-cache.max-size:1000}")
    private int maxSize;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final AgentPoolManager agentPoolManager;

    public AgentSessionCache(AgentPoolManager agentPoolManager) {
        this.agentPoolManager = agentPoolManager;
    }

    /**
     * 缓存条目
     */
    public record CacheEntry(
            ReActAgent agent,
            String threadId,
            String agentName,
            long createdAt,
            long lastAccessedAt
    ) {
        public static CacheEntry of(ReActAgent agent, String threadId, String agentName) {
            long now = System.currentTimeMillis();
            return new CacheEntry(agent, threadId, agentName, now, now);
        }

        public CacheEntry withAccessedAt(long timestamp) {
            return new CacheEntry(agent, threadId, agentName, createdAt, timestamp);
        }
    }

    // ==================== 核心方法 ====================

    /**
     * 获取缓存的 Agent，未命中时自动创建并缓存
     * <p>
     * 这是最常用的入口——一次调用搞定"取/建"。
     *
     * @param threadId  会话 ID（由外部 API 传入）
     * @param agentName Agent 名称（对应 {@code @AgentDefinition} 的 name）
     * @return Agent 实例（保证不为 null）
     */
    public ReActAgent getOrCreate(String threadId, String agentName) {
        String key = buildKey(threadId, agentName);

        // 1. 尝试从缓存获取（惰性过期检查）
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (isExpired(entry)) {
                cache.remove(key);
                log.info("[SessionCache] 条目已过期，移除: key={}", key);
            } else {
                // 续命：刷新 lastAccessedAt
                cache.put(key, entry.withAccessedAt(System.currentTimeMillis()));
                log.debug("[SessionCache] 命中: key={}", key);
                return entry.agent();
            }
        }

        // 2. 缓存未命中 → 调用 AgentPoolManager 构建（带会话持久化）
        log.info("[SessionCache] 未命中，创建 Agent: key={}", key);
        ReActAgent agent = agentPoolManager.getAgentWithSession(agentName, threadId);

        // 3. 存入缓存（顺便做 maxSize 淘汰）
        evictIfNecessary();
        cache.put(key, CacheEntry.of(agent, threadId, agentName));

        return agent;
    }

    /**
     * 仅传入 override 获取缓存的 Agent（无 context 和 hooks）
     *
     * @param threadId   会话 ID
     * @param agentName  Agent 名称
     * @param override   外部配置覆盖（可选）
     * @return Agent 实例（保证不为 null）
     */
    public ReActAgent getOrCreate(String threadId, String agentName, AgentConfigPo override) {
        return getOrCreate(threadId, agentName, null, null, override);
    }

    /**
     * 初次创建的时候允许传入工具上下文和hook
     * @param threadId
     * @param agentName
     * @return
     */
    public ReActAgent getOrCreate(String threadId, String agentName, ToolExecutionContext context, List<Hook> hooks) {
        return getOrCreate(threadId, agentName, context, hooks, null);
    }

    /**
     * 获取缓存的 Agent，未命中时自动创建并缓存（支持 AgentConfigPo override）
     * <p>
     * override 仅在缓存未命中、首次创建 Agent 时生效。
     * 缓存命中时返回已缓存的 Agent 实例，override 不生效（方案 A）。
     * 如需强制重建，先调用 {@link #remove} 再调用本方法。
     *
     * @param threadId   会话 ID（由外部 API 传入）
     * @param agentName  Agent 名称（对应 {@code @AgentDefinition} 的 name）
     * @param context    工具执行上下文（可选）
     * @param hooks      动态 Hook（可选）
     * @param override   外部配置覆盖（可选），为 null 时使用模板默认值
     * @return Agent 实例（保证不为 null）
     */
    public ReActAgent getOrCreate(String threadId, String agentName,
                                  ToolExecutionContext context, List<Hook> hooks,
                                  AgentConfigPo override) {
        String key = buildKey(threadId, agentName);

        // 1. 尝试从缓存获取（惰性过期检查）
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (isExpired(entry)) {
                cache.remove(key);
                log.info("[SessionCache] 条目已过期，移除: key={}", key);
            } else {
                // 续命：刷新 lastAccessedAt
                cache.put(key, entry.withAccessedAt(System.currentTimeMillis()));
                log.debug("[SessionCache] 命中: key={}", key);
                return entry.agent();
            }
        }

        // 2. 缓存未命中 → 调用 AgentPoolManager 构建（带会话持久化 + override）
        log.info("[SessionCache] 未命中，创建 Agent: key={}", key);
        ReActAgent agent = agentPoolManager.getAgentWithSession(agentName, threadId, context, hooks, override);

        // 3. 存入缓存（顺便做 maxSize 淘汰）
        evictIfNecessary();
        cache.put(key, CacheEntry.of(agent, threadId, agentName));

        return agent;
    }
    /**
     * 手动注册已有 Agent 到缓存
     *
     * @param threadId  会话 ID
     * @param agentName Agent 名称
     * @param agent     已创建的 Agent 实例
     */
    public void put(String threadId, String agentName, ReActAgent agent) {
        String key = buildKey(threadId, agentName);
        evictIfNecessary();
        cache.put(key, CacheEntry.of(agent, threadId, agentName));
        log.info("[SessionCache] C: key={}", key);
    }

    /**
     * 获取缓存的 Agent（不自动创建，惰性过期检查）
     *
     * @return Agent 实例，未命中或已过期返回 null
     */
    public ReActAgent get(String threadId, String agentName) {
        String key = buildKey(threadId, agentName);
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (isExpired(entry)) {
                cache.remove(key);
                return null;
            }
            // 续命
            cache.put(key, entry.withAccessedAt(System.currentTimeMillis()));
            return entry.agent();
        }
        return null;
    }

    /**
     * 获取缓存条目（含元数据，惰性过期检查）
     */
    public CacheEntry getEntry(String threadId, String agentName) {
        String key = buildKey(threadId, agentName);
        //打印threadId，agentName
        log.info("threadId:{}", threadId);
        log.info("agentName:{}", agentName);
        CacheEntry entry = cache.get(key);
        log.info("[SessionCache] 获取条目: key={}", key);
        log.info("[SessionCache] 获取entry: entry={}", entry);
        if (entry != null && isExpired(entry)) {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    /**
     * 移除单个缓存条目
     *
     * @return 被移除的 Agent，不存在返回 null
     */
    public ReActAgent remove(String threadId, String agentName) {
        String key = buildKey(threadId, agentName);
        CacheEntry removed = cache.remove(key);
        if (removed != null) {
            log.info("[SessionCache] 已移除: key={}", key);
            return removed.agent();
        }
        return null;
    }

    /**
     * 移除某个 threadId 下的所有缓存条目
     * <p>
     * 会话结束时批量清理。
     *
     * @param threadId 会话 ID
     */
    public void removeByThread(String threadId) {
        String prefix = threadId + ":";
        int count = 0;

        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> e = it.next();
            if (e.getKey().startsWith(prefix)) {
                it.remove();
                count++;
            }
        }

        if (count > 0) {
            log.info("[SessionCache] 批量移除 threadId={}, count={}", threadId, count);
        }
    }

    /**
     * 检查缓存中是否存在（惰性过期检查）
     */
    public boolean contains(String threadId, String agentName) {
        return getEntry(threadId, agentName) != null;
    }

    /**
     * 当前缓存条目数
     */
    public int size() {
        return cache.size();
    }

    // ==================== 内部方法 ====================

    private String buildKey(String threadId, String agentName) {
        return threadId + ":" + agentName;
    }

    /**
     * 惰性过期检查：最后访问时间超过阈值即过期
     */
    private boolean isExpired(CacheEntry entry) {
        return System.currentTimeMillis() - entry.lastAccessedAt() > expireAfterAccessMinutes * 60_000;
    }

    /**
     * 超出 maxSize 时，移除最旧的条目（LRU 策略）
     */
    private void evictIfNecessary() {
        if (cache.size() >= maxSize) {
            // 找到 lastAccessedAt 最小的 key
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
                if (e.getValue().lastAccessedAt() < oldestTime) {
                    oldestTime = e.getValue().lastAccessedAt();
                    oldestKey = e.getKey();
                }
            }

            if (oldestKey != null) {
                cache.remove(oldestKey);
                log.info("[SessionCache] maxSize 淘汰最旧条目: key={}", oldestKey);
            }
        }
    }
}
