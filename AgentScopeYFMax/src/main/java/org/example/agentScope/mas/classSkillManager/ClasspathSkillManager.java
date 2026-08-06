package org.example.agentScope.mas.classSkillManager;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classpath Skill 资源管理器。
 * <p>
 * 封装 {@link ClasspathSkillRepository} 的生命周期，提供 resourcePath 级缓存。
 * 从 classpath 资源目录加载预打包的 Skill（只读），自动兼容标准 JAR 和 Spring Boot Fat JAR。
 * <p>
 * 资源目录结构示例：
 * <pre>
 * src/main/resources/skills/
 * ├── data-analysis/
 * │   └── SKILL.md
 * ├── code-review/
 * │   └── SKILL.md
 * └── report-gen/
 *     └── SKILL.md
 * </pre>
 *
 * @author AgentScope-Team
 */
@Slf4j
@Component
public class ClasspathSkillManager {

    /** resourcePath → ClasspathSkillRepository */
    private final ConcurrentHashMap<String, ClasspathSkillRepository> repoCache = new ConcurrentHashMap<>();

    /** resourcePath → 已加载的 AgentSkill 列表 */
    private final ConcurrentHashMap<String, List<AgentSkill>> skillCache = new ConcurrentHashMap<>();

    /**
     * 加载资源目录中所有 Skill（带缓存）。
     *
     * @param resourcePath classpath 资源路径，如 "skills"
     * @return AgentSkill 列表，不会返回 null
     */
    public List<AgentSkill> loadSkills(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return Collections.emptyList();
        }
        return skillCache.computeIfAbsent(resourcePath, path -> {
            try {
                ClasspathSkillRepository repo = getOrCreateRepo(path);
                List<AgentSkill> skills = repo.getAllSkills();
                log.info("📦 Loaded {} skills from classpath: {}", skills.size(), path);
                return skills;
            } catch (IOException e) {
                log.error("❌ Failed to load skills from classpath: {}", path, e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * 按正则表达式过滤加载 Skill。
     *
     * @param resourcePath classpath 资源路径
     * @param patterns     正则表达式数组，null 或空则加载全部
     * @return 匹配的 AgentSkill 列表
     */
    public List<AgentSkill> loadSkillsByPatterns(String resourcePath, String... patterns) {
        if (resourcePath == null || resourcePath.isBlank() || patterns == null || patterns.length == 0) {
            return Collections.emptyList();
        }

        List<AgentSkill> allSkills = loadSkills(resourcePath);
        List<AgentSkill> result = new ArrayList<>();

        for (AgentSkill skill : allSkills) {
            String name = skill.getName();
            if (name == null) continue;

            for (String pattern : patterns) {
                if (pattern == null || pattern.isBlank()) continue;
                try {
                    if (name.matches(pattern)) {
                        result.add(skill);
                        break;
                    }
                } catch (java.util.regex.PatternSyntaxException e) {
                    log.warn("⚠️ Invalid regex pattern '{}': {}", pattern, e.getMessage());
                }
            }
        }

        if (result.isEmpty()) {
            log.warn("⚠️ No skills matched patterns {} in classpath: {}", List.of(patterns), resourcePath);
        } else {
            log.info("🎯 Matched {} skills by patterns {} from classpath: {}",
                    result.size(), List.of(patterns), resourcePath);
        }
        return result;
    }

    /**
     * 获取资源目录中所有 Skill 名称。
     */
    public List<String> listSkillNames(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ClasspathSkillRepository repo = getOrCreateRepo(resourcePath);
            return repo.getAllSkillNames();
        } catch (IOException e) {
            log.error("❌ Failed to list skill names from classpath: {}", resourcePath, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取 ClasspathSkillRepository 中 skills 的本地路径。
     * <p>
     * 标准 JAR 环境下返回 null（资源在 JAR 内部，无真实文件系统路径）。
     * 开发环境（IDE/非 Fat JAR）下返回 src/main/resources 下的真实路径。
     *
     * @param resourcePath classpath 资源路径
     * @return 本地路径，JAR 环境下为 null
     */
    public Path getLocalPath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        try {
            ClasspathSkillRepository repo = getOrCreateRepo(resourcePath);
            return extractLocalPath(repo);
        } catch (IOException e) {
            log.error("❌ Failed to get local path for classpath: {}", resourcePath, e);
            return null;
        }
    }

    /**
     * 通过反射从 ClasspathSkillRepository 获取 skillBasePath（private Path 类型）。
     * <p>
     * 注意：Fat JAR 环境下返回的是 ZipFileSystem 内部路径，不是真实文件系统路径。
     */
    private Path extractLocalPath(ClasspathSkillRepository repo) {
        try {
            Field f = ClasspathSkillRepository.class.getDeclaredField("skillBasePath");
            f.setAccessible(true);
            return (Path) f.get(repo);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.warn("⚠️ Failed to extract local path from ClasspathSkillRepository: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 刷新缓存（重新加载资源目录）。
     *
     * @param resourcePath classpath 资源路径
     * @return 刷新后的 AgentSkill 列表
     */
    public List<AgentSkill> refreshSkills(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return Collections.emptyList();
        }
        // Classpath 资源不会变，清除缓存后重新加载
        skillCache.remove(resourcePath);
        // 关闭旧 repo
        closeRepo(resourcePath);
        return loadSkills(resourcePath);
    }

    /**
     * 获取或创建 ClasspathSkillRepository 实例
     */
    private ClasspathSkillRepository getOrCreateRepo(String resourcePath) throws IOException {
        ClasspathSkillRepository existing = repoCache.get(resourcePath);
        if (existing != null) {
            return existing;
        }
        log.info("📂 Creating ClasspathSkillRepository for: {}", resourcePath);
        ClasspathSkillRepository repo = new ClasspathSkillRepository(resourcePath);
        repoCache.put(resourcePath, repo);
        return repo;
    }

    /**
     * 关闭指定 resourcePath 的 repo
     */
    private void closeRepo(String resourcePath) {
        ClasspathSkillRepository repo = repoCache.remove(resourcePath);
        if (repo != null) {
            try {
                repo.close();
            } catch (Exception e) {
                log.debug("Error closing classpath repo: {}", resourcePath, e);
            }
        }
    }

    /**
     * 应用关闭时清理所有 repo
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up {} classpath skill repositories...", repoCache.size());
        repoCache.forEach((path, repo) -> {
            try {
                repo.close();
            } catch (Exception e) {
                log.debug("Error closing classpath repo: {}", path, e);
            }
        });
        repoCache.clear();
        skillCache.clear();
    }
}
