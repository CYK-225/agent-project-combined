package org.example.agentScope.framework.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Skill 仓库注册表。
 * <p>
 * 维护 agentName → (repoUrl + skillPatterns) 的映射关系。
 * 数据来源（优先级递减）：
 * <ol>
 *   <li>REST API 动态绑定（POST /api/skill-repo/bind）</li>
 *   <li>{@code @AgentDefinition(skillRepoUrl)} 静态配置（启动时写入）</li>
 * </ol>
 *
 * @author AgentScope-Team
 */
@Slf4j
@Component
public class SkillRepoRegistry {

    /** agentName → 绑定信息 */
    private final ConcurrentHashMap<String, SkillRepoBinding> registry = new ConcurrentHashMap<>();

    /**
     * 绑定仓库地址 + 正则过滤（覆盖已有值）
     */
    public void put(String agentName, String repoUrl, String[] skillPatterns) {
        String normalizedUrl = normalizeUrl(repoUrl);
        registry.put(agentName, new SkillRepoBinding(normalizedUrl, skillPatterns));
        log.info("📌 SkillRepoRegistry bound: {} → {}, patterns: {}",
                agentName, normalizedUrl, skillPatterns != null ? Arrays.toString(skillPatterns) : "[]");
    }

    /**
     * 标准化Git URL：自动添加缺失的协议前缀
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("git@")) {
            return url;
        }
        if (url.matches("^[^/]+@[^:]+:.+$")) {
            return url;
        }
        log.warn("⚠️ Git URL缺少协议前缀，自动添加 http:// : {}", url);
        return "http://" + url;
    }

    /** 仅绑定仓库地址（无过滤） */
    public void put(String agentName, String repoUrl) {
        put(agentName, repoUrl, null);
    }

    /**
     * 仅当不存在时绑定（用于注解静态配置，不覆盖动态值）
     */
    public void putIfAbsent(String agentName, String repoUrl, String[] skillPatterns) {
        registry.putIfAbsent(agentName, new SkillRepoBinding(repoUrl, skillPatterns));
        log.debug("SkillRepoRegistry static bind: {} → {}, patterns: {}",
                agentName, repoUrl, skillPatterns != null ? Arrays.toString(skillPatterns) : "[]");
    }

    /** 获取仓库地址，返回 null 表示未配置 */
    public String getRepoUrl(String agentName) {
        SkillRepoBinding binding = registry.get(agentName);
        return binding != null ? binding.getRepoUrl() : null;
    }

    /** 保留旧方法兼容 */
    public String get(String agentName) {
        return getRepoUrl(agentName);
    }

    /** 获取正则过滤，返回 null 表示未配置（加载全部） */
    public String[] getSkillPatterns(String agentName) {
        SkillRepoBinding binding = registry.get(agentName);
        return binding != null ? binding.getSkillPatterns() : null;
    }

    /** 获取完整绑定信息 */
    public SkillRepoBinding getBinding(String agentName) {
        return registry.get(agentName);
    }

    /** 移除绑定 */
    public void remove(String agentName) {
        registry.remove(agentName);
        log.info("SkillRepoRegistry unbound: {}", agentName);
    }

    /** 查看所有绑定 */
    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        registry.forEach((name, binding) -> result.put(name, binding.getRepoUrl()));
        return Collections.unmodifiableMap(result);
    }

    /** 是否已绑定 */
    public boolean hasRepo(String agentName) {
        return registry.containsKey(agentName);
    }

    // ==================== 绑定信息 ====================

    @Getter
    @AllArgsConstructor
    public static class SkillRepoBinding {
        private final String repoUrl;
        private final String[] skillPatterns;
    }
}
