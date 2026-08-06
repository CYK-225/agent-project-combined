package org.example.agentScope.mas.skillManager.core;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.GitSkillRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.example.agentScope.util.skill.SkillEnvConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git Skill 仓库管理器。
 * <p>
 * 封装 {@link GitSkillRepository} 的生命周期，提供仓库级缓存。
 * 同一个 repoUrl 只会 clone 一次，后续读操作自动 pull 检测。
 * <p>
 * 职责：
 * <ul>
 *   <li>创建/缓存 GitSkillRepository 实例</li>
 *   <li>加载仓库中所有 AgentSkill</li>
 *   <li>刷新仓库（pull + 重新解析）</li>
 *   <li>获取仓库本地路径（供 codeExecution 使用）</li>
 *   <li>应用关闭时清理临时目录</li>
 * </ul>
 *
 * @author AgentScope-Team
 */
@Slf4j
@Component
public class GitSkillManager {

    @Autowired(required = false)
    private SkillEnvConfigManager envConfigManager;

    /** repoUrl → GitSkillRepository */
    private final ConcurrentHashMap<String, GitSkillRepository> repoCache = new ConcurrentHashMap<>();

    /** repoUrl → 已加载的 AgentSkill 列表 */
    private final ConcurrentHashMap<String, List<AgentSkill>> skillCache = new ConcurrentHashMap<>();

    /** repoUrl → 仓库本地路径（供 codeExecution workDir 使用） */
    private final ConcurrentHashMap<String, Path> localPathCache = new ConcurrentHashMap<>();

    /** sessionId → (repoUrl → GitSkillRepository) - 会话级隔离缓存 */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, GitSkillRepository>> sessionRepoCache = new ConcurrentHashMap<>();

    /** sessionId → (repoUrl → 已加载的 AgentSkill 列表) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<AgentSkill>>> sessionSkillCache = new ConcurrentHashMap<>();

    /** sessionId → (repoUrl → 仓库本地路径) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Path>> sessionLocalPathCache = new ConcurrentHashMap<>();

    /**
     * 加载仓库中所有 Skill（带缓存）。
     * 首次调用会 clone，后续调用会做轻量远端检查，有更新才 pull。
     *
     * @param repoUrl Git 仓库地址
     * @return AgentSkill 列表，不会返回 null
     */
    public List<AgentSkill> loadSkills(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return Collections.emptyList();
        }
        return skillCache.computeIfAbsent(repoUrl, url -> {
            GitSkillRepository repo = getOrCreateRepo(url);
            List<AgentSkill> skills = repo.getAllSkills();
            log.info("📦 Loaded {} skills from Git repo: {}", skills.size(), url);
            return skills;
        });
    }

    /**
     * 加载仓库中所有 Skill（会话隔离版本）。
     * 为每个会话创建独立的仓库实例和临时目录。
     *
     * @param repoUrl   Git 仓库地址
     * @param sessionId 会话标识
     * @return AgentSkill 列表，不会返回 null
     */
    public List<AgentSkill> loadSkillsWithSession(String repoUrl, String sessionId) {
        if (repoUrl == null || repoUrl.isBlank() || sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }

        ConcurrentHashMap<String, List<AgentSkill>> sessionSkills = sessionSkillCache.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        return sessionSkills.computeIfAbsent(repoUrl, url -> {
            GitSkillRepository repo = getOrCreateRepoWithSession(url, sessionId);
            List<AgentSkill> skills = repo.getAllSkills();
            log.info("📦 Loaded {} skills from Git repo for session {}: {}", skills.size(), sessionId, url);
            return skills;
        });
    }

    /**
     * 加载仓库中指定名称的 Skill（精确匹配）。
     *
     * @param repoUrl   Git 仓库地址
     * @param skillName Skill 名称
     * @return AgentSkill，不存在返回 null
     */
    public AgentSkill loadSkill(String repoUrl, String skillName) {
        if (repoUrl == null || repoUrl.isBlank() || skillName == null || skillName.isBlank()) {
            return null;
        }
        loadSkills(repoUrl);
        GitSkillRepository repo = getOrCreateRepo(repoUrl);
        return repo.getSkill(skillName);
    }

    /**
     * 按正则表达式过滤加载 Skill。
     * <p>
     * 每个 pattern 对仓库中所有 Skill 名称做 {@link String#matches(String)} 匹配。
     * 精确名称也能匹配（如 "data-analysis" 等价于正则 ^data-analysis$）。
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code "data-analysis"} — 精确匹配</li>
     *   <li>{@code "data-.*"} — 匹配所有 data- 开头的 Skill</li>
     *   <li>{@code ".*security.*"} — 匹配名称含 security 的 Skill</li>
     * </ul>
     *
     * @param repoUrl  Git 仓库地址
     * @param patterns 正则表达式数组，null 或空则返回空列表
     * @return 匹配的 AgentSkill 列表
     */
    public List<AgentSkill> loadSkillsByPatterns(String repoUrl, String... patterns) {
        if (repoUrl == null || repoUrl.isBlank() || patterns == null || patterns.length == 0) {
            return Collections.emptyList();
        }

        List<AgentSkill> allSkills = loadSkills(repoUrl);
        List<AgentSkill> result = new ArrayList<>();

        for (AgentSkill skill : allSkills) {
            String name = skill.getName();
            if (name == null) continue;

            for (String pattern : patterns) {
                if (pattern == null || pattern.isBlank()) continue;
                try {
                    if (name.matches(pattern)) {
                        result.add(skill);
                        break; // 一个 Skill 只加一次
                    }
                } catch (java.util.regex.PatternSyntaxException e) {
                    log.warn("⚠️ Invalid regex pattern '{}': {}", pattern, e.getMessage());
                }
            }
        }

        if (result.isEmpty()) {
            log.warn("⚠️ No skills matched patterns {} in repo: {}", List.of(patterns), repoUrl);
        } else {
            log.info("🎯 Matched {} skills by patterns {} from repo: {}",
                    result.size(), List.of(patterns), repoUrl);
        }
        return result;
    }

    /**
     * 按正则表达式过滤加载 Skill（会话隔离版本）。
     *
     * @param repoUrl   Git 仓库地址
     * @param sessionId 会话标识
     * @param patterns  正则表达式数组，null 或空则返回空列表
     * @return 匹配的 AgentSkill 列表
     */
    public List<AgentSkill> loadSkillsByPatternsWithSession(String repoUrl, String sessionId, String... patterns) {
        if (repoUrl == null || repoUrl.isBlank() || sessionId == null || sessionId.isBlank() || patterns == null || patterns.length == 0) {
            return Collections.emptyList();
        }

        List<AgentSkill> allSkills = loadSkillsWithSession(repoUrl, sessionId);
        List<AgentSkill> result = new ArrayList<>();

        for (AgentSkill skill : allSkills) {
            String name = skill.getName();
            if (name == null) continue;

            for (String pattern : patterns) {
                if (pattern == null || pattern.isBlank()) continue;
                try {
                    if (name.matches(pattern)) {
                        result.add(skill);
                        break; // 一个 Skill 只加一次
                    }
                } catch (java.util.regex.PatternSyntaxException e) {
                    log.warn("⚠️ Invalid regex pattern '{}': {}", pattern, e.getMessage());
                }
            }
        }

        if (result.isEmpty()) {
            log.warn("⚠️ No skills matched patterns {} in repo for session {}: {}", List.of(patterns), sessionId, repoUrl);
        } else {
            log.info("🎯 Matched {} skills by patterns {} from repo for session {}: {}",
                    result.size(), List.of(patterns), sessionId, repoUrl);
        }
        return result;
    }

    /**
     * 刷新仓库（清缓存 + pull + 重新解析）。
     *
     * @param repoUrl Git 仓库地址
     * @return 刷新后的 AgentSkill 列表
     */
    public List<AgentSkill> refreshSkills(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return Collections.emptyList();
        }
        skillCache.remove(repoUrl);
        // GitSkillRepository 内部每次 read 都会检查远端 HEAD
        // 清除 skillCache 后下次 loadSkills 会触发 pull
        return loadSkills(repoUrl);
    }

    /**
     * 获取仓库中所有 Skill 名称。
     */
    public List<String> listSkillNames(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return Collections.emptyList();
        }
        GitSkillRepository repo = getOrCreateRepo(repoUrl);
        return repo.getAllSkillNames();
    }

    /**
     * 获取仓库的本地路径（供 codeExecution workDir 使用）。
     * <p>
     * 通过反射从 GitSkillRepository 内部读取，因为框架未暴露此 API。
     *
     * @param repoUrl Git 仓库地址
     * @return 本地仓库路径，null 表示无法获取
     */
    public Path getLocalPath(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }
        return localPathCache.computeIfAbsent(repoUrl, url -> {
            try {
                GitSkillRepository repo = getOrCreateRepo(url);
                // GitSkillRepository 内部字段名为 localPath
                java.lang.reflect.Field field = repo.getClass().getDeclaredField("localPath");
                field.setAccessible(true);
                Path path = (Path) field.get(repo);
                log.debug("Git repo local path for {}: {}", url, path);
                return path;
            } catch (Exception e) {
                log.warn("Failed to get local path for repo: {}", url, e);
                return null;
            }
        });
    }

    /**
     * 获取仓库的本地路径（会话隔离版本）。
     *
     * @param repoUrl   Git 仓库地址
     * @param sessionId 会话标识
     * @return 本地仓库路径，null 表示无法获取
     */
    public Path getLocalPathWithSession(String repoUrl, String sessionId) {
        if (repoUrl == null || repoUrl.isBlank() || sessionId == null || sessionId.isBlank()) {
            return null;
        }

        ConcurrentHashMap<String, Path> sessionPaths = sessionLocalPathCache.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        return sessionPaths.computeIfAbsent(repoUrl, url -> {
            try {
                GitSkillRepository repo = getOrCreateRepoWithSession(url, sessionId);
                // GitSkillRepository 内部字段名为 localPath
                java.lang.reflect.Field field = repo.getClass().getDeclaredField("localPath");
                field.setAccessible(true);
                Path path = (Path) field.get(repo);
                log.debug("Git repo local path for session {} {}: {}", sessionId, url, path);
                return path;
            } catch (Exception e) {
                log.warn("Failed to get local path for repo in session {}: {}", sessionId, url, e);
                return null;
            }
        });
    }

    /**
     * 获取或创建 GitSkillRepository 实例
     */
    private GitSkillRepository getOrCreateRepo(String repoUrl) {
        // 自动修复URL：如果缺少协议前缀，添加 http://
        String normalizedUrl = normalizeUrl(repoUrl);

        // 从URL中提取凭证并设置全局 CredentialsProvider
        setupCredentialsFromUrl(normalizedUrl);

        // 如果缓存中已有，直接返回
        GitSkillRepository cached = repoCache.get(normalizedUrl);
        if (cached != null) {
            return cached;
        }

        // 缓存中没有，创建新实例（会进行验证）
        try {
            GitSkillRepository repo = repoCache.computeIfAbsent(normalizedUrl, url -> {
                log.info("🔄 Creating GitSkillRepository for: {}", url);
                return new GitSkillRepository(url);
            });
            
            // ⚠️ 重要：仓库创建后生成 .env 配置文件
            generateEnvFileForRepo(normalizedUrl, repo, null);
            
            return repo;
        } catch (Exception e) {
            log.error("❌ Failed to create GitSkillRepository for: {}", normalizedUrl, e);
            // 提供更详细的错误信息
            if (e instanceof TransportException) {
                log.error("💡 提示: 请检查以下可能的原因:");
                log.error("   1. 仓库 URL 是否正确: {}", normalizedUrl);
                log.error("   2. 网络连接是否正常");
                log.error("   3. 如果是私有仓库，是否已配置认证凭证");
                log.error("   4. SSL 证书是否有效");
            }
            // 从缓存中移除失败的条目
            repoCache.remove(normalizedUrl);
            throw new RuntimeException("Failed to create GitSkillRepository for: " + normalizedUrl, e);
        }
    }

    /**
     * 获取或创建 GitSkillRepository 实例（会话隔离版本）。
     * 为每个会话创建独立的仓库实例，使用会话ID作为临时目录的一部分。
     *
     * @param repoUrl   Git 仓库地址
     * @param sessionId 会话标识
     * @return GitSkillRepository 实例
     */
    private GitSkillRepository getOrCreateRepoWithSession(String repoUrl, String sessionId) {
        // 自动修复URL：如果缺少协议前缀，添加 http://
        String normalizedUrl = normalizeUrl(repoUrl);

        // 从URL中提取凭证并设置全局 CredentialsProvider
        setupCredentialsFromUrl(normalizedUrl);

        // 获取会话级缓存
        ConcurrentHashMap<String, GitSkillRepository> sessionRepos = sessionRepoCache.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());

        // 如果会话级缓存中已有，直接返回
        GitSkillRepository cached = sessionRepos.get(normalizedUrl);
        if (cached != null) {
            return cached;
        }

        // 会话级缓存中没有，创建新实例（每个会话独立的临时目录）
        try {
            GitSkillRepository repo = sessionRepos.computeIfAbsent(normalizedUrl, url -> {
                log.info("🔄 Creating GitSkillRepository for session {}: {}", sessionId, url);
                // GitSkillRepository 会自动创建临时目录，每个实例都有独立的临时目录
                return new GitSkillRepository(url);
            });
            
            // ⚠️ 重要：仓库创建后生成 .env 配置文件
            generateEnvFileForRepo(normalizedUrl, repo, sessionId);
            
            return repo;
        } catch (Exception e) {
            log.error("❌ Failed to create GitSkillRepository for session {}: {}", sessionId, normalizedUrl, e);
            // 提供更详细的错误信息
            if (e instanceof TransportException) {
                log.error("💡 提示: 请检查以下可能的原因:");
                log.error("   1. 仓库 URL 是否正确: {}", normalizedUrl);
                log.error("   2. 网络连接是否正常");
                log.error("   3. 如果是私有仓库，是否已配置认证凭证");
                log.error("   4. SSL 证书是否有效");
            }
            // 从缓存中移除失败的条目
            sessionRepos.remove(normalizedUrl);
            throw new RuntimeException("Failed to create GitSkillRepository for session " + sessionId + ": " + normalizedUrl, e);
        }
    }

    /**
     * 标准化Git URL：自动添加缺失的协议前缀
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        // 如果URL已经包含协议前缀，直接返回
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("git@")) {
            return url;
        }
        // 如果URL看起来像 git@host:path 格式，返回原样
        if (url.matches("^[^/]+@[^:]+:.+$")) {
            return url;
        }
        // 否则添加 http:// 前缀
        log.warn("⚠️ Git URL缺少协议前缀，自动添加 http:// : {}", url);
        return "http://" + url;
    }

    /**
     * 从URL中提取凭证并设置全局 CredentialsProvider
     * <p>
     * 支持格式：http://username:password@host/path
     */
    private void setupCredentialsFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            // 匹配 http(s)://user:pass@host 格式
            Pattern pattern = Pattern.compile("^https?://([^:]+):([^@]+)@.+");
            Matcher matcher = pattern.matcher(url);
            if (matcher.matches()) {
                String username = matcher.group(1);
                String password = matcher.group(2);
                CredentialsProvider cp = new UsernamePasswordCredentialsProvider(username, password);
                CredentialsProvider.setDefault(cp);
                log.info("✅ 设置全局 CredentialsProvider，用户名: {}", username);
            }
        } catch (Exception e) {
            log.warn("⚠️ 从URL提取凭证失败: {}", e.getMessage());
        }
    }

    /**
     * 验证 Git 仓库 URL 是否可访问
     * <p>
     * 注意：此方法会创建临时连接来验证仓库，仅用于诊断目的。
     * 正式使用请通过 loadSkills 等方法。
     *
     * @param repoUrl Git 仓库地址
     * @return true 如果仓库可访问，false 否则
     */
    public boolean isRepoAccessible(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return false;
        }
        try {
            Git.lsRemoteRepository()
                    .setRemote(repoUrl)
                    .call();
            log.debug("✅ Git repo is accessible: {}", repoUrl);
            return true;
        } catch (Exception e) {
            log.warn("⚠️ Git repo is not accessible: {}", repoUrl, e);
            return false;
        }
    }

    /**
     * 为 Skill 仓库生成 .env 配置文件
     *
     * @param repoUrl   Git 仓库地址
     * @param repo      GitSkillRepository 实例
     * @param sessionId 会话 ID（null 表示非会话模式）
     */
    private void generateEnvFileForRepo(String repoUrl, GitSkillRepository repo, String sessionId) {
        if (envConfigManager == null) {
            log.debug("SkillEnvConfigManager not available, skipping .env generation");
            return;
        }

        try {
            // 获取仓库本地路径
            Path localPath = null;
            try {
                java.lang.reflect.Field field = repo.getClass().getDeclaredField("localPath");
                field.setAccessible(true);
                localPath = (Path) field.get(repo);
            } catch (Exception e) {
                log.warn("Failed to get local path from GitSkillRepository", e);
                return;
            }

            if (localPath == null) {
                log.warn("Local path is null for repo: {}", repoUrl);
                return;
            }

            // 确定环境名称
            String envName = System.getProperty("skill.env", "default");
            
            // 生成 .env 文件
            boolean success = envConfigManager.generateEnvFile(repoUrl, localPath, envName);
            
            if (success) {
                log.info("✅ Generated .env file for repo: {} (session: {})", repoUrl, sessionId != null ? sessionId : "global");
            } else {
                log.warn("⚠️ Failed to generate .env file for repo: {}", repoUrl);
            }

        } catch (Exception e) {
            log.error("❌ Error generating .env file for repo: {}", repoUrl, e);
            // 不抛出异常，避免影响 Skill 加载流程
        }
    }

    /**
     * 关闭所有仓库，释放资源
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up {} Git repositories...", repoCache.size());
        repoCache.values().forEach(repo -> {
            try {
                repo.close();
            } catch (Exception e) {
                log.warn("Failed to close GitSkillRepository", e);
            }
        });
        repoCache.clear();
        skillCache.clear();
        localPathCache.clear();

        // 清理会话级缓存
        log.info("Cleaning up {} session Git repositories...", sessionRepoCache.size());
        sessionRepoCache.values().forEach(sessionRepos -> {
            sessionRepos.values().forEach(repo -> {
                try {
                    repo.close();
                } catch (Exception e) {
                    log.warn("Failed to close session GitSkillRepository", e);
                }
            });
        });
        sessionRepoCache.clear();
        sessionSkillCache.clear();
        sessionLocalPathCache.clear();
    }
}
