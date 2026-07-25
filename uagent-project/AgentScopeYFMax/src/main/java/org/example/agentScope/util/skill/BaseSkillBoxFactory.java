package org.example.agentScope.util.skill;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.util.SkillUtil;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.coding.ShellCommandTool;
import io.agentscope.core.tool.mcp.McpClientWrapper;

import org.example.agentScope.mas.skillManager.core.GitSkillManager;
import org.example.agentScope.mas.classSkillManager.ClasspathSkillManager;
import org.example.agentScope.util.tool.SubAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * SkillBox 构建工厂。
 * <p>
 * 提供链式构建器 {@link SkillBoxBuilder}，支持：
 * <ul>
 *   <li>{@link #addSkillWithTools} — 本地 Skill + Tool 注册</li>
 *   <li>{@link #addMcpToolWithFilters} — MCP 工具高级装配</li>
 *   <li>{@link #addGitSkills} — Git 远程仓库 Skill 自动加载 + 代码执行</li>
 *   <li>{@link #addOnlySkill} — 纯 Skill（无 Tool）注册</li>
 * </ul>
 *
 * @author AgentScope-Team
 */
@Component
public class BaseSkillBoxFactory {

    private static final Logger log = LoggerFactory.getLogger(BaseSkillBoxFactory.class);

    private final GitSkillManager gitSkillManager;
    private final ClasspathSkillManager classpathSkillManager;
    private final SkillEnvGenerator skillEnvGenerator;

    public BaseSkillBoxFactory(GitSkillManager gitSkillManager,
                               ClasspathSkillManager classpathSkillManager,
                               SkillEnvGenerator skillEnvGenerator) {
        this.gitSkillManager = gitSkillManager;
        this.classpathSkillManager = classpathSkillManager;
        this.skillEnvGenerator = skillEnvGenerator;
    }

    /**
     * 入口方法：开始创建一个 SkillBox 的构建流程
     */
    public SkillBoxBuilder create() {
        return new SkillBoxBuilder(new Toolkit(), gitSkillManager, classpathSkillManager, skillEnvGenerator);
    }

    public SkillBoxBuilder create(Toolkit toolkit) {
        return new SkillBoxBuilder(toolkit, gitSkillManager, classpathSkillManager, skillEnvGenerator);
    }

    // ================== 静态工具方法保持不变 ==================

    /**
     * Windows 路径安全字符清洗正则：匹配 Windows 文件系统禁止的字符
     * <code>: * ? " < > |</code> 以及控制字符（0x00-0x1F）
     */
    private static final java.util.regex.Pattern UNSAFE_PATH_CHARS =
            java.util.regex.Pattern.compile("[:*?\"<>|\\x00-\\x1F]");

    /**
     * 清洗 AgentSkill 的 source 字段中 Windows 不兼容的路径字符。
     * <p>
     * SDK 的 {@code SkillBox.uploadSkillFiles()} 会将 skillId（格式: name_source）
     * 直接作为 {@code Path.resolve()} 的参数来创建上传目录，
     * 但 Windows 文件系统禁止路径中包含 {@code : * ? " < > |} 等字符。
     * Git source 通常形如 {@code git:owner/repo}，其中的冒号在 Linux/macOS 合法但在 Windows 非法。
     * <p>
     * 此方法将非法字符替换为 {@code -}，确保 skillId 在所有操作系统上都可作为路径使用。
     *
     * @param skill 原始 AgentSkill
     * @return source 已清洗的 AgentSkill（如果 source 无非法字符则原样返回）
     */
    private static AgentSkill sanitizeSkillSource(AgentSkill skill) {
        String originalSource = skill.getSource();
        String sanitizedSource = UNSAFE_PATH_CHARS.matcher(originalSource).replaceAll("-");
        if (sanitizedSource.equals(originalSource)) {
            return skill;
        }
        log.info("   🔧 Sanitized skill source for Windows: '{}' → '{}'", originalSource, sanitizedSource);
        return skill.toBuilder()
                .source(sanitizedSource)
                .build();
    }

    /**
     * 创建AgentSkill
     */
    public static AgentSkill createBaseAgentSkill(String skillName,
                                                  String description,
                                                  String skillContent) {
        return AgentSkill.builder()
                .name(skillName)
                .description(description)
                .skillContent(skillContent)
                .build();
    }

    /**
     * 创建AgentSkill通过md文档
     */
    public static AgentSkill createBaseAgentSkillFromMd(String skill) {
        return SkillUtil.createFrom(skill, null);
    }

    /**
     * 自动检测操作系统并返回虚拟环境中的 Python 解释器路径。
     * <p>
     * Windows: .venv\Scripts\python.exe
     * Linux/Mac: .venv/bin/python
     *
     * @param workDir 工作目录（仓库根目录）
     * @return 虚拟环境 Python 解释器的相对路径
     */
    private static String getVenvPythonPath(Path workDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return ".venv\\Scripts\\python.exe";
        } else {
            return ".venv/bin/python";
        }
    }

    // ================== 内部构建器类 ==================

    /**
     * 专门用于链式构建 SkillBox 的内部类
     */
    public static class SkillBoxBuilder {
        private static final Logger log = LoggerFactory.getLogger(SkillBoxBuilder.class);
        private final SkillBox skillBox;
        private final Toolkit sharedToolkit;
        private final GitSkillManager gitSkillManager;
        private final ClasspathSkillManager classpathSkillManager;
        private final SkillEnvGenerator skillEnvGenerator;

        public SkillBoxBuilder(Toolkit sharedToolkit, GitSkillManager gitSkillManager,
                               ClasspathSkillManager classpathSkillManager,
                               SkillEnvGenerator skillEnvGenerator) {
            this.sharedToolkit = sharedToolkit;
            this.skillBox = new SkillBox(sharedToolkit);
            this.gitSkillManager = gitSkillManager;
            this.classpathSkillManager = classpathSkillManager;
            this.skillEnvGenerator = skillEnvGenerator;
        }

        public SkillBoxBuilder addOnlySkill(AgentSkill skill) {
            this.skillBox.registerSkill(skill);
            return this;
        }

        /**
         * 【1】常规工具组装：支持 POJO (@Tool)、AgentTool、SubAgent
         */
        public SkillBoxBuilder addSkillWithTools(AgentSkill skill, Object... tools) {
            if (tools == null) throw new NullPointerException("tools cannot be null");

            for (Object item : tools) {
                if (item == null) continue;

                var registration = this.skillBox.registration().skill(skill);

                switch (item) {
                    case io.agentscope.core.tool.AgentTool agentTool -> registration.agentTool(agentTool).apply();
                    case SubAgent agent ->
                            registration.subAgent(agent::getAgent, agent.toImplementationConfig()).apply();
                    case McpClientWrapper mcpClientWrapper -> registration.mcpClient(mcpClientWrapper).apply();
                    default -> registration.tool(item).apply();
                }
            }
            return this;
        }

        /**
         * 【2】高级 MCP 工具装配（支持 enableTools / disableTools 过滤）
         */
        public SkillBoxBuilder addMcpToolWithFilters(AgentSkill skill,
                                                     McpClientWrapper mcp,
                                                     List<String> enableTools,
                                                     List<String> disableTools) {
            var registration = this.skillBox.registration()
                    .skill(skill)
                    .mcpClient(mcp);

            if (enableTools != null && !enableTools.isEmpty()) {
                registration.enableTools(enableTools);
            }
            if (disableTools != null && !disableTools.isEmpty()) {
                registration.disableTools(disableTools);
            }

            registration.apply();
            return this;
        }

        /**
         * 【3】Git Skill 仓库集成（默认开启代码执行）。
         * <p>
         * 从 Git 仓库加载所有 Skill 并注册到 SkillBox，同时开启代码执行能力
         * （Shell/Read/Write），使 LLM 能执行仓库中的脚本。
         * <p>
         * repoUrl 为 null 或空时直接跳过，不影响现有逻辑。
         * <p>
         * <h3>使用方式</h3>
         * <pre>{@code
         * // 在 Agent 子类的 setupSkills() 中：
         * @Override
         * protected SkillBox setupSkills() {
         *     return skillBoxFactory.create(getToolkit())
         *         .addSkillWithTools(mailSkill, mailTools)
         *         .addGitSkills(getSkillRepoUrl())   // ← 追加 Git Skill
         *         .buildSkillBox();
         * }
         * }</pre>
         *
         * @param repoUrl Git 仓库地址，null 或空则跳过
         * @return this（链式调用）
         */
        /**
         * Git Skill 仓库集成（加载全部 Skill）。
         *
         * @param repoUrl Git 仓库地址，null 或空则跳过
         * @return this（链式调用）
         */
        public SkillBoxBuilder addGitSkills(String repoUrl) {
            return addGitSkills(repoUrl, true, (String[]) null);
        }

        /**
         * Git Skill 仓库集成（加载全部 Skill，可选代码执行）。
         *
         * @param repoUrl       Git 仓库地址
         * @param codeExecution 是否开启代码执行
         * @return this
         */
        public SkillBoxBuilder addGitSkills(String repoUrl, boolean codeExecution) {
            return addGitSkills(repoUrl, codeExecution, (String[]) null);
        }

        /**
         * Git Skill 仓库集成（正则过滤 + 可选代码执行）。
         * <p>
         * 使用示例：
         * <pre>{@code
         * // 加载全部
         * .addGitSkills(repoUrl)
         *
         * // 精确匹配
         * .addGitSkills(repoUrl, true, "data-analysis")
         *
         * // 正则匹配所有 data- 开头的 Skill
         * .addGitSkills(repoUrl, true, "data-.*")
         *
         * // 多个正则（任一匹配即加载）
         * .addGitSkills(repoUrl, true, "data-.*", ".*security.*")
         * }</pre>
         *
         * @param repoUrl       Git 仓库地址，null 或空则跳过
         * @param codeExecution 是否开启代码执行（Shell/Read/Write）
         * @param skillPatterns 正则表达式过滤，null 或空则加载全部
         * @return this（链式调用）
         */
        public SkillBoxBuilder addGitSkills(String repoUrl, boolean codeExecution, String... skillPatterns) {
            return addGitSkillsWithSession(repoUrl, null, codeExecution, skillPatterns);
        }

        /**
         * Git Skill 仓库集成（会话隔离版本）。
         * 为每个会话创建独立的临时目录和虚拟环境。
         *
         * @param repoUrl       Git 仓库地址，null 或空则跳过
         * @param sessionId     会话标识，null 表示不使用会话隔离
         * @param codeExecution 是否开启代码执行（Shell/Read/Write）
         * @param skillPatterns 正则表达式过滤，null 或空则加载全部
         * @return this（链式调用）
         */
        public SkillBoxBuilder addGitSkillsWithSession(String repoUrl, String sessionId, boolean codeExecution, String... skillPatterns) {
            if (repoUrl == null || repoUrl.isBlank()) {
                return this;
            }


            // 加载 Skill（按正则过滤或全部）
            List<AgentSkill> skills;
            if (skillPatterns != null && skillPatterns.length > 0) {
                if (sessionId != null && !sessionId.isBlank()) {
                    skills = gitSkillManager.loadSkillsByPatternsWithSession(repoUrl, sessionId, skillPatterns);
                } else {
                    skills = gitSkillManager.loadSkillsByPatterns(repoUrl, skillPatterns);
                }
                log.info("📋 Loading Git skills with patterns: {}", Arrays.toString(skillPatterns));
            } else {
                if (sessionId != null && !sessionId.isBlank()) {
                    skills = gitSkillManager.loadSkillsWithSession(repoUrl, sessionId);
                } else {
                    skills = gitSkillManager.loadSkills(repoUrl);
                }
                log.info("📋 Loading all Git skills from repo: {}", repoUrl);
            }

            if (skills.isEmpty()) {
                log.warn("⚠️ No skills found in repo: {}", repoUrl);
                return this;
            }

            // 逐个注册到 SkillBox
            log.info("✅ Found {} skills to inject:", skills.size());
            for (AgentSkill skill : skills) {
                // ⚠️ Windows 兼容：sanitize source 中的非法路径字符
                // SDK 的 SkillBox.uploadSkillFiles() 会将 skillId (name_source) 用作 Path.resolve() 的参数，
                // Windows 不允许路径中包含 : * ? " < > | 等字符，需替换为 -
                AgentSkill sanitizedSkill = sanitizeSkillSource(skill);
                this.skillBox.registration()
                        .skill(sanitizedSkill)
                        .apply();
                log.info("   ✅ Injected skill: {} (source: {}, description: {})",
                        sanitizedSkill.getName(),
                        sanitizedSkill.getSource(),
                        sanitizedSkill.getDescription() != null ? sanitizedSkill.getDescription().substring(0, Math.min(50, sanitizedSkill.getDescription().length())) : "none");
            }

            // 开启代码执行能力
            if (codeExecution) {
                Path localPath;
                if (sessionId != null && !sessionId.isBlank()) {
                    localPath = gitSkillManager.getLocalPathWithSession(repoUrl, sessionId);
                } else {
                    localPath = gitSkillManager.getLocalPath(repoUrl);
                }

                if (localPath != null) {
                    // ⚠️ 重要：先安装 Python 依赖（如果存在 requirements.txt）
                    // 必须在配置 ShellCommandTool 之前，确保 .venv 目录已创建
                    boolean depsInstalled = DependencyInstaller.installIfNeeded(localPath);
                    if (!depsInstalled) {
                        log.error("❌ Failed to install dependencies for repo: {}. Code execution may not work properly.", repoUrl);
                    }

                    // ⚠️ 重要：生成 .env 配置文件
                    // 将 Spring 配置中的数据库配置传递给 Python 脚本
                    boolean envGenerated = skillEnvGenerator.generateEnvFile(localPath);
                    if (!envGenerated) {
                        log.warn("⚠️ Failed to generate .env file for repo: {}. Python scripts may not have database config.", repoUrl);
                    }

                    // 自动检测操作系统并获取虚拟环境 Python 路径
                    String venvPython = getVenvPythonPath(localPath);
                    log.info("🔍 Detected OS: {}, venv Python: {}", System.getProperty("os.name"), venvPython);

                    // 验证虚拟环境是否存在
                    Path venvPath = localPath.resolve(".venv");
                    if (!java.nio.file.Files.exists(venvPath)) {
                        log.error("❌ Virtual environment not found at: {}. This may cause Python script execution failures.", venvPath);
                    } else {
                        log.info("✅ Virtual environment exists at: {}", venvPath);
                    }

                    ShellCommandTool customShell = new ShellCommandTool(

                            null,  // baseDir 会被自动覆盖为 workDir

                            Set.of("uv", "node", "npm", "python", "python3", venvPython),

                            command -> {
                                log.info("Approval callback for command: {}", command);
                                return true;  // 自动批准所有命令
                            }
                    );
                    // ⚠️ 重要：设置 workDir 为仓库根目录（LLM 执行 Shell 命令的默认目录）
                    this.skillBox.codeExecution()
                            .workDir(localPath.toString())
                            .withShell(customShell)
                            .withRead()
                            .withWrite()
                            .enable();

                    // 配置 Python 路径：检测 skills/ 下的模块目录，创建 .pth 文件
                    // 解决嵌套目录结构导致的 `python -m xxx` 找不到模块问题
                    PythonPathSetup.setupIfNeeded(localPath);

                    // ⚠️ 重要：启用 Skill 资源自动上传
                    // 这会将 Skill 中的资源文件（包括 scripts/ 目录）上传到 uploadDir
                    // LLM 可以通过 Read 工具读取这些文件，或通过 Shell 工具执行脚本
                    // uploadDir 默认为 workDir/skills/
                    this.skillBox.setAutoUploadSkill(true);

                    log.info("✅ Code execution ENABLED for Git Skill repo: {}", repoUrl);
                    log.info("   📂 workDir: {}", localPath);
                    log.info("   📤 Skill resources (scripts/, references/) will be auto-uploaded to: {}/skills/", localPath);
                    log.info("   🔧 LLM tools enabled: Shell, Read, Write");
                    log.info("   🎯 LLM can now execute Python/Shell scripts from Skills");
                } else {
                    log.warn("⚠️ Code execution skipped: no local path found for repo: {}", repoUrl);
                }
            }

            return this;
        }

        // ==================== Classpath Skill 集成 ====================

        /**
         * Classpath Skill 加载（全部 Skill，开启代码执行）。
         *
         * @param resourcePath classpath 资源路径，如 "skills"
         * @return this
         */
        public SkillBoxBuilder addClasspathSkills(String resourcePath) {
            return addClasspathSkills(resourcePath, true, (String[]) null);
        }

        /**
         * Classpath Skill 加载（可选代码执行）。
         *
         * @param resourcePath  classpath 资源路径
         * @param codeExecution 是否开启代码执行（Shell/Read/Write）
         * @return this
         */
        public SkillBoxBuilder addClasspathSkills(String resourcePath, boolean codeExecution) {
            return addClasspathSkills(resourcePath, codeExecution, (String[]) null);
        }

        /**
         * Classpath Skill 加载（正则过滤 + 可选代码执行）。
         * <p>
         * 从 classpath 资源目录加载预打包的 Skill（只读，Skill 文件不可修改），
         * 但可开启代码执行能力（Shell/Read/Write），使 LLM 能执行仓库中的脚本。
         * 自动兼容标准 JAR 和 Spring Boot Fat JAR。
         * <p>
         * 注意：Fat JAR 环境下代码执行的 workDir 无效（资源在 JAR 内部），会自动跳过。
         *
         * @param resourcePath  classpath 资源路径，如 "skills"
         * @param codeExecution 是否开启代码执行（Shell/Read/Write）
         * @param skillPatterns 正则表达式过滤，null 或空则加载全部
         * @return this
         */
        public SkillBoxBuilder addClasspathSkills(String resourcePath, boolean codeExecution, String... skillPatterns) {
            if (resourcePath == null || resourcePath.isBlank()) {
                return this;
            }

            List<AgentSkill> skills;
            if (skillPatterns != null && skillPatterns.length > 0) {
                skills = classpathSkillManager.loadSkillsByPatterns(resourcePath, skillPatterns);
            } else {
                skills = classpathSkillManager.loadSkills(resourcePath);
            }

            if (skills.isEmpty()) {
                return this;
            }

            for (AgentSkill skill : skills) {
                // ⚠️ Windows 兼容：sanitize source 中的非法路径字符（同 addGitSkills）
                AgentSkill sanitizedSkill = sanitizeSkillSource(skill);
                this.skillBox.registration()
                        .skill(sanitizedSkill)
                        .apply();
            }

            // 开启代码执行能力
            if (codeExecution) {
                Path localPath = classpathSkillManager.getLocalPath(resourcePath);
                if (localPath != null) {
                    this.skillBox.codeExecution()
                            .workDir(localPath.toString())
                            .withShell()
                            .withRead()
                            .withWrite()
                            .enable();
                } else {
                    log.warn("⚠️ Classpath skill codeExecution skipped: " +
                            "resource '{}' is inside a JAR (no real filesystem path)", resourcePath);
                }
            }

            return this;
        }

        // ==================== 构建完成方法 ====================

        public SkillBoxBuilder enableSkillLoadTool() {
            this.skillBox.registerSkillLoadTool();
            return this;
        }

        public SkillBox buildSkillBox() {
            return this.skillBox;
        }

        public Toolkit buildToolkit() {
            return this.sharedToolkit;
        }
    }
}
