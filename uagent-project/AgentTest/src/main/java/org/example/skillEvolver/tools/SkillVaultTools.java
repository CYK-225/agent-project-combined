package org.example.skillEvolver.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill 持久化工具 — 版本管理 + 文件写入，兼容 AgentScope LocalSkillLoader 格式。
 * <p>
 * 写出的目录结构：
 * <pre>
 * {skillRootDir}/{skillName}/
 * ├── SKILL.md              ← AgentScope LocalSkillLoader 直接加载
 * └── versions/
 *     ├── v0.md
 *     ├── v1.md
 *     └── v2.md
 * </pre>
 * <p>
 * 遵循函数式注入模式，SkillRootDir 通过 lambda 注入，可在测试中替换。
 *
 * @author zhilin
 */
@Slf4j
public class SkillVaultTools {

    @FunctionalInterface
    public interface SkillRootProvider {
        String getRootDir();
    }

    private final SkillRootProvider skillRootProvider;

    public SkillVaultTools(SkillRootProvider skillRootProvider) {
        this.skillRootProvider = skillRootProvider;
    }

    @Tool(
            name = "persist_skill_to_file",
            description = "将 SKILL.md 写入文件系统，格式兼容 AgentScope LocalSkillLoader。"
                    + "同时保存一份版本快照到 versions/ 子目录。"
                    + "写入成功后，其他 Agent 可通过 LocalSkillLoader 自动加载该 Skill。"
    )
    public String persistSkillToFile(
            @ToolParam(name = "skillName", description = "Skill 名称（将作为目录名）") String skillName,
            @ToolParam(name = "skillMarkdown", description = "SKILL.md 完整内容（含 frontmatter）") String skillMarkdown,
            @ToolParam(name = "versionLabel", description = "版本标签（如 v0, v1, v2-best）") String versionLabel
    ) {
        try {
            String rootDir = skillRootProvider.getRootDir();
            Path skillDir = Paths.get(rootDir, skillName);
            Files.createDirectories(skillDir);

            // 写主文件 SKILL.md（LocalSkillLoader 扫描此文件）
            Path skillMd = skillDir.resolve("SKILL.md");
            Files.writeString(skillMd, skillMarkdown, StandardCharsets.UTF_8);

            // 写版本快照
            Path versionsDir = skillDir.resolve("versions");
            Files.createDirectories(versionsDir);
            Path versionFile = versionsDir.resolve(versionLabel + ".md");
            Files.writeString(versionFile, skillMarkdown, StandardCharsets.UTF_8);

            log.info("[SkillVault] Skill 已持久化: {} (版本 {})", skillName, versionLabel);
            return "✅ Skill 已持久化\n"
                    + "- 目录: " + skillDir.toAbsolutePath() + "\n"
                    + "- 主文件: SKILL.md\n"
                    + "- 版本快照: versions/" + versionLabel + ".md\n"
                    + "- 可通过 LocalSkillLoader 加载";
        } catch (IOException e) {
            log.error("[SkillVault] 持久化失败", e);
            return "❌ 持久化失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "list_persisted_skills",
            description = "列出已持久化的所有 Skill。返回每个 Skill 的名称、最后修改时间、大小。"
    )
    public String listPersistedSkills() {
        try {
            String rootDir = skillRootProvider.getRootDir();
            Path root = Paths.get(rootDir);
            if (!Files.exists(root)) {
                return "📁 Skill 目录尚不存在: " + rootDir;
            }

            List<String> skills = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        Path skillMd = entry.resolve("SKILL.md");
                        if (Files.exists(skillMd)) {
                            long size = Files.size(skillMd);
                            var mtime = Files.getLastModifiedTime(skillMd);
                            skills.add(String.format("- **%s** (%d 字符, 最后修改: %s)",
                                    entry.getFileName(), size, mtime));
                        }
                    }
                }
            }

            if (skills.isEmpty()) {
                return "📭 暂无已持久化的 Skill";
            }
            return "## 已持久化的 Skill（共 " + skills.size() + " 个）\n\n" + String.join("\n", skills);
        } catch (IOException e) {
            return "❌ 列出 Skill 失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "read_persisted_skill",
            description = "读取已持久化的 Skill 内容。用于在迭代中加载上一版本的 SKILL.md 作为基础。"
    )
    public String readPersistedSkill(
            @ToolParam(name = "skillName", description = "Skill 名称") String skillName
    ) {
        try {
            String rootDir = skillRootProvider.getRootDir();
            Path skillMd = Paths.get(rootDir, skillName, "SKILL.md");
            if (!Files.exists(skillMd)) {
                return "❌ 未找到 Skill: " + skillName;
            }
            return Files.readString(skillMd, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "❌ 读取 Skill 失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "read_skill_version",
            description = "读取指定版本的 Skill 内容。用于回溯对比不同版本。"
    )
    public String readSkillVersion(
            @ToolParam(name = "skillName", description = "Skill 名称") String skillName,
            @ToolParam(name = "versionLabel", description = "版本标签（如 v0, v1）") String versionLabel
    ) {
        try {
            String rootDir = skillRootProvider.getRootDir();
            Path versionFile = Paths.get(rootDir, skillName, "versions", versionLabel + ".md");
            if (!Files.exists(versionFile)) {
                return "❌ 未找到版本: " + versionLabel + " (Skill: " + skillName + ")";
            }
            return Files.readString(versionFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "❌ 读取版本失败: " + e.getMessage();
        }
    }
}
