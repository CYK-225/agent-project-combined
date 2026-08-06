package org.example.agentScope.util.skill;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Python 路径配置工具。
 * <p>
 * 解决 Git 仓库技能目录嵌套导致的 {@code No module named xxx} 问题。
 * <p>
 * 当仓库结构为 {@code skills/module_name/skill_name/} 时，
 * Python 脚本在仓库根目录执行时无法找到 {@code module_name} 包。
 * 本工具通过创建 Python {@code .pth} 文件将模块目录路径加入 {@code sys.path}。
 * <p>
 * 工作原理：
 * <ul>
 *   <li>检测 {@code skills/} 目录下是否包含"模块目录"（含子技能目录而非 SKILL.md 的目录）</li>
 *   <li>为每个模块目录创建 {@code .pth} 文件，路径写入 Python user site-packages</li>
 *   <li>Python 启动时自动读取 {@code .pth} 文件，将路径加入 {@code sys.path}</li>
 * </ul>
 *
 * @author AgentScope-Team
 */
@Slf4j
public class PythonPathSetup {

    private static final String PTH_FILE_NAME = "agentscope_skills.pth";

    /**
     * 检测并配置 Python 路径。
     * <p>
     * 扫描 {@code workDir/skills/} 目录，如果发现模块目录（包含子技能目录的容器目录），
     * 则创建 {@code .pth} 文件将其路径加入 Python 的 {@code sys.path}。
     *
     * @param workDir Git 仓库根目录（代码执行的 workDir）
     */
    public static void setupIfNeeded(Path workDir) {
        if (workDir == null || !Files.isDirectory(workDir)) {
            return;
        }

        Path skillsDir = workDir.resolve("skills");
        if (!Files.isDirectory(skillsDir)) {
            return;
        }

        // 检测模块目录：skills/ 下的子目录中，如果某个子目录包含更深的子目录（含 SKILL.md），
        // 但没有自己的 SKILL.md，则它是一个"模块目录"
        List<Path> moduleDirs = detectModuleDirs(skillsDir);
        if (moduleDirs.isEmpty()) {
            log.debug("No module directories found under skills/, skipping PYTHONPATH setup");
            return;
        }

        log.info("Detected {} module directories under skills/: {}", moduleDirs.size(), moduleDirs);
        createPthFile(workDir, moduleDirs);
    }

    /**
     * 检测 skills/ 目录下的模块目录。
     * <p>
     * 模块目录的特征：
     * 1. 是 skills/ 的直接子目录
     * 2. 包含子目录，且子目录中有 SKILL.md（传统嵌套结构）
     * 3. 或者包含 scripts/ 子目录（扁平结构，技能直接在skills下）
     * <p>
     * 无论模块目录自身是否有 SKILL.md，都会被检测。
     * 因为即使框架把它当作一个技能加载，Python 仍然需要 .pth 文件来找到模块。
     */
    private static List<Path> detectModuleDirs(Path skillsDir) {
        List<Path> moduleDirs = new ArrayList<>();
        // 使用数组包装以便在lambda中修改
        boolean[] hasFlatStructure = {false};

        try (Stream<Path> stream = Files.list(skillsDir)) {
            stream.filter(Files::isDirectory)
                    .forEach(dir -> {
                        // 检查子目录中是否包含 SKILL.md（即子技能）
                        boolean hasSubSkills = false;
                        boolean hasScriptsDir = false;
                        try (Stream<Path> subStream = Files.list(dir)) {
                            hasSubSkills = subStream
                                    .filter(Files::isDirectory)
                                    .anyMatch(sub -> Files.exists(sub.resolve("SKILL.md")));

                            // 检查是否包含 scripts/ 子目录（扁平结构特征）
                            hasScriptsDir = Files.isDirectory(dir.resolve("scripts"));
                        } catch (IOException e) {
                            log.warn("Failed to scan directory: {}", dir, e);
                        }

                        if (hasSubSkills) {
                            moduleDirs.add(dir);
                            log.info("Detected module directory (nested): {} (relative: {})",
                                    dir, skillsDir.getParent().relativize(dir));
                        } else if (hasScriptsDir) {
                            // 扁平结构：技能目录直接包含 scripts/ 子目录
                            moduleDirs.add(dir);
                            hasFlatStructure[0] = true;
                            log.info("Detected skill directory (flat): {} (relative: {})",
                                    dir, skillsDir.getParent().relativize(dir));
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan skills directory: {}", skillsDir, e);
        }

        // 如果是扁平结构，还需要添加 skills 目录本身，以便找到 database 等共享模块
        if (hasFlatStructure[0] && !moduleDirs.contains(skillsDir)) {
            moduleDirs.add(skillsDir);
            log.info("Detected flat structure, adding skills directory itself: {}", skillsDir);
        }

        return moduleDirs;
    }

    /**
     * 创建 .pth 文件将模块路径加入 Python sys.path。
     * <p>
     * 同时在两个位置创建：
     * 1. venv 的 site-packages（如果存在 .venv）
     * 2. Python user site-packages（系统 Python）
     * <p>
     * 这样无论是通过 venv 还是系统 Python 执行脚本，都能找到模块。
     */
    private static void createPthFile(Path workDir, List<Path> moduleDirs) {
        // 构建 .pth 文件内容：每行一个绝对路径
        StringBuilder pthContent = new StringBuilder();
        pthContent.append("# AgentScope skills PYTHONPATH - auto-generated\n");
        for (Path moduleDir : moduleDirs) {
            String absPath = moduleDir.toAbsolutePath().normalize().toString();
            pthContent.append(absPath).append("\n");
            
            // 同时添加父目录（skills/）以支持不同的导入方式
            Path parentDir = moduleDir.getParent();
            if (parentDir != null) {
                String parentPath = parentDir.toAbsolutePath().normalize().toString();
                pthContent.append(parentPath).append("\n");
            }
        }
        String content = pthContent.toString();
        boolean created = false;

        // 1. 在 venv 的 site-packages 中创建 .pth 文件
        Path venvSitePackages = getVenvSitePackagesPath(workDir);
        if (venvSitePackages != null && Files.isDirectory(venvSitePackages)) {
            created |= writePthFile(venvSitePackages, content);
            log.info("Created .pth file in venv site-packages: {}", venvSitePackages);
        }

        // 2. 在 Python user site-packages 中创建 .pth 文件
        Path userSitePackages = getUserSitePackagesPath();
        if (userSitePackages != null && Files.isDirectory(userSitePackages)) {
            created |= writePthFile(userSitePackages, content);
            log.info("Created .pth file in user site-packages: {}", userSitePackages);
        }

        // 3. 降级：放在 workDir 下
        if (!created) {
            Path fallbackDir = workDir;
            writePthFile(fallbackDir, content);
            log.warn("Could not determine Python site-packages path. " +
                    "Created .pth file at workDir: {}. " +
                    "If Python cannot find modules, set PYTHONPATH manually: export PYTHONPATH={}",
                    fallbackDir,
                    moduleDirs.stream()
                            .map(p -> p.toAbsolutePath().normalize().toString())
                            .reduce((a, b) -> a + System.getProperty("path.separator") + b)
                            .orElse(""));
        }
    }

    /**
     * 将 .pth 文件写入指定目录。
     *
     * @return 写入是否成功
     */
    private static boolean writePthFile(Path dir, String content) {
        Path pthFile = dir.resolve(PTH_FILE_NAME);
        try {
            Files.writeString(pthFile, content);
            log.info("Written .pth file: {}", pthFile);
            return true;
        } catch (IOException e) {
            log.error("Failed to write .pth file at: {}", pthFile, e);
            return false;
        }
    }

    /**
     * 获取 venv 的 site-packages 路径（如果 .venv 存在）。
     *
     * @param workDir 仓库根目录（可能包含 .venv）
     * @return venv site-packages 路径，null 表示不存在
     */
    private static Path getVenvSitePackagesPath(Path workDir) {
        Path venvPath = workDir.resolve(".venv");
        if (!Files.isDirectory(venvPath)) {
            return null;
        }

        // Windows: .venv/Lib/python3.X/site-packages
        // Linux:   .venv/lib/python3.X/site-packages
        Path venvLib = venvPath.resolve(isWindows() ? "Lib" : "lib");
        if (!Files.isDirectory(venvLib)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(venvLib)) {
            Path sitePackages = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("python"))
                    .map(p -> p.resolve("site-packages"))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);

            if (sitePackages != null) {
                log.debug("Found venv site-packages: {}", sitePackages);
            }
            return sitePackages;
        } catch (IOException e) {
            log.debug("Failed to scan venv lib directory", e);
            return null;
        }
    }

    /**
     * 通过 Python 命令获取 user site-packages 路径。
     *
     * @return user site-packages 路径，null 表示获取失败
     */
    private static Path getUserSitePackagesPath() {
        String[] pythonCommands = {"python3", "python"};
        for (String python : pythonCommands) {
            Path result = tryGetSitePackages(python);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 尝试通过指定 Python 可执行文件获取 user site-packages 路径。
     */
    private static Path tryGetSitePackages(String pythonCmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd, "-c",
                    "import site; print(site.getusersitepackages())"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);

                if (finished && process.exitValue() == 0 && line != null && !line.isBlank()) {
                    Path sitePackages = Path.of(line.trim());
                    // 确保目录存在
                    if (!Files.isDirectory(sitePackages)) {
                        Files.createDirectories(sitePackages);
                    }
                    log.debug("Python user site-packages ({}): {}", pythonCmd, sitePackages);
                    return sitePackages;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get site-packages from {}: {}", pythonCmd, e.getMessage());
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
