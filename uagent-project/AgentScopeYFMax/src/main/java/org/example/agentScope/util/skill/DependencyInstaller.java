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
 * Python 依赖安装工具
 * <p>
 * 支持自动检测并安装 requirements.txt 中的依赖。
 * 使用 uv（优先）或 pip 作为包管理器。
 * <p>
 * 查找顺序：
 * 1. 根目录 requirements.txt
 * 2. skills/requirements.txt（模块级依赖）
 *
 * @author AgentScope-Team
 */
@Slf4j
public class DependencyInstaller {

    private static final int TIMEOUT_SECONDS = 300; // 5 分钟超时

    /**
     * 检查并安装 Python 依赖
     *
     * @param workDir 工作目录（仓库根目录）
     * @return 安装是否成功
     */
    public static boolean installIfNeeded(Path workDir) {
        log.info("Checking dependencies for workDir: {}", workDir);

        // 查找所有 requirements.txt 文件
        List<Path> requirementsFiles = findRequirementsFiles(workDir);

        if (requirementsFiles.isEmpty()) {
            log.warn("No requirements.txt found in {} or skills/, skipping dependency installation", workDir);
            return true;
        }

        log.info("Found {} requirements.txt files: {}", requirementsFiles.size(), requirementsFiles);

        // 检查虚拟环境是否存在
        Path venvPath = workDir.resolve(".venv");
        if (Files.exists(venvPath)) {
            log.info("Virtual environment already exists at: {}", venvPath);
            // 合并所有 requirements.txt 并检查是否需要更新
            return verifyAndInstall(workDir, requirementsFiles);
        }

        // 首次安装
        log.info("Installing Python dependencies from {} requirements.txt files...", requirementsFiles.size());
        return installAll(workDir, requirementsFiles);
    }

    /**
     * 查找所有 requirements.txt 文件
     * 查找顺序：根目录 + skills/requirements.txt
     */
    private static List<Path> findRequirementsFiles(Path workDir) {
        List<Path> files = new ArrayList<>();

        // 1. 根目录 requirements.txt
        Path rootReq = workDir.resolve("requirements.txt");
        log.debug("Checking root requirements.txt: {} (exists: {})", rootReq, Files.exists(rootReq));
        if (Files.exists(rootReq)) {
            files.add(rootReq);
            log.info("Found requirements.txt at: {}", rootReq);
        }

        // 2. skills/requirements.txt 和 skills/*/requirements.txt
        Path skillsDir = workDir.resolve("skills");
        log.debug("Checking skills directory: {} (exists: {})", skillsDir, Files.isDirectory(skillsDir));
        if (Files.isDirectory(skillsDir)) {
            // 直接检查 skills/requirements.txt
            Path skillsReq = skillsDir.resolve("requirements.txt");
            log.debug("Checking skills/requirements.txt: {} (exists: {})", skillsReq, Files.exists(skillsReq));
            if (Files.exists(skillsReq) && !files.contains(skillsReq)) {
                files.add(skillsReq);
                log.info("Found requirements.txt at: {}", skillsReq);
            }

            // 扫描 skills/*/requirements.txt（模块级依赖）
            try (Stream<Path> stream = Files.walk(skillsDir, 2)) {
                stream.filter(p -> p.getFileName().toString().equals("requirements.txt"))
                        .filter(p -> !files.contains(p)) // 避免重复
                        .forEach(f -> {
                            files.add(f);
                            log.info("Found requirements.txt at: {}", f);
                        });
            } catch (IOException e) {
                log.warn("Failed to scan skills directory for requirements.txt", e);
            }
        }

        log.info("Total requirements.txt files found: {}", files.size());
        return files;
    }

    /**
     * 验证并安装（如果需要）
     */
    private static boolean verifyAndInstall(Path workDir, List<Path> requirementsFiles) {
        // 合并所有 requirements.txt 内容
        String mergedContent = mergeRequirements(requirementsFiles);

        // 创建临时合并文件
        Path mergedFile = workDir.resolve(".requirements_merged.txt");
        try {
            Files.writeString(mergedFile, mergedContent);
        } catch (IOException e) {
            log.warn("Failed to create merged requirements file", e);
            return installAll(workDir, requirementsFiles);
        }

        // 检查是否需要更新（通过比较文件修改时间）
        Path lockFile = workDir.resolve(".deps_installed");
        if (Files.exists(lockFile)) {
            try {
                long lastInstall = Files.getLastModifiedTime(lockFile).toMillis();
                boolean needsUpdate = requirementsFiles.stream()
                        .anyMatch(f -> {
                            try {
                                return Files.getLastModifiedTime(f).toMillis() > lastInstall;
                            } catch (IOException e) {
                                return true;
                            }
                        });

                if (!needsUpdate) {
                    log.info("Dependencies are up-to-date, skipping installation");
                    return true;
                }

                log.info("Requirements files changed, reinstalling dependencies...");
            } catch (IOException e) {
                log.warn("Failed to check lock file", e);
            }
        }

        return installDependencies(workDir, mergedFile);
    }

    /**
     * 安装所有依赖
     */
    private static boolean installAll(Path workDir, List<Path> requirementsFiles) {
        // 合并所有 requirements.txt
        String mergedContent = mergeRequirements(requirementsFiles);
        Path mergedFile = workDir.resolve(".requirements_merged.txt");
        try {
            Files.writeString(mergedFile, mergedContent);
        } catch (IOException e) {
            log.error("Failed to create merged requirements file", e);
            return false;
        }

        return installDependencies(workDir, mergedFile);
    }

    /**
     * 合并多个 requirements.txt 内容
     */
    private static String mergeRequirements(List<Path> files) {
        StringBuilder sb = new StringBuilder();
        for (Path file : files) {
            try {
                String content = Files.readString(file);
                sb.append("# From: ").append(file.getFileName()).append("\n");
                sb.append(content).append("\n");
            } catch (IOException e) {
                log.warn("Failed to read requirements file: {}", file, e);
            }
        }
        return sb.toString();
    }

    /**
     * 安装依赖
     */
    private static boolean installDependencies(Path workDir, Path requirementsFile) {
        // 优先使用 uv
        if (isCommandAvailable("uv")) {
            return installWithUv(workDir, requirementsFile);
        }
        // 降级到 pip
        if (isCommandAvailable("pip")) {
            return installWithPip(workDir, requirementsFile);
        }

        log.error("Neither uv nor pip is available. Please install Python package manager.");
        return false;
    }

    /**
     * 使用 uv 安装依赖
     */
    private static boolean installWithUv(Path workDir, Path requirementsFile) {
        log.info("Using uv to install dependencies...");

        // 检查并清理已存在的 .venv 目录（如果损坏）
        Path venvPath = workDir.resolve(".venv");
        if (Files.exists(venvPath)) {
            log.info("Found existing .venv directory at: {}", venvPath);
            // 验证是否为有效的虚拟环境
            boolean isValidVenv = isWindows()
                    ? Files.exists(venvPath.resolve("Scripts").resolve("python.exe"))
                    : Files.exists(venvPath.resolve("bin").resolve("python"));
            
            if (!isValidVenv) {
                log.warn("Existing .venv is not a valid virtual environment, will recreate...");
                try {
                    // 删除无效的 .venv 目录
                    deleteDirectory(venvPath);
                    log.info("Deleted invalid .venv directory");
                } catch (IOException e) {
                    log.error("Failed to delete invalid .venv directory", e);
                    return false;
                }
            }
        }

        // 创建虚拟环境（如果已存在则清除）
        log.info("Creating virtual environment with uv...");
        if (!executeCommand(workDir, "uv", "venv", ".venv", "--python", "3.11", "--clear")) {
            log.warn("Failed to create venv with uv using Python 3.11, trying without --python flag...");
            if (!executeCommand(workDir, "uv", "venv", ".venv", "--clear")) {
                log.error("Failed to create virtual environment with uv. Please check if uv is installed and Python 3.11 is available.");
                return false;
            }
        }

        // 验证虚拟环境是否成功创建
        if (!Files.exists(venvPath)) {
            log.error("Virtual environment was not created at: {}", venvPath);
            return false;
        }
        
        boolean isValidVenv = isWindows()
                ? Files.exists(venvPath.resolve("Scripts").resolve("python.exe"))
                : Files.exists(venvPath.resolve("bin").resolve("python"));
        
        if (!isValidVenv) {
            log.error("Virtual environment created but Python executable not found. This may indicate a corrupted venv.");
            return false;
        }
        
        log.info("✅ Virtual environment created successfully at: {}", venvPath);

        // Windows 上需要等待文件系统释放锁
        if (isWindows()) {
            try {
                log.info("Waiting for Windows file system to release locks...");
                Thread.sleep(2000); // 等待 2 秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 安装依赖
        log.info("Installing dependencies from: {}", requirementsFile);
        if (!executeCommand(workDir, "uv", "pip", "install", "-r", requirementsFile.toString())) {
            // 如果失败，再等待一次并重试
            log.warn("First install attempt failed, retrying after delay...");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!executeCommand(workDir, "uv", "pip", "install", "-r", requirementsFile.toString())) {
                log.error("Failed to install dependencies after retry. Please check requirements.txt for errors.");
                return false;
            }
        }

        // 创建安装完成标记
        createInstallLock(workDir);
        log.info("✅ Dependencies installed successfully");
        return true;
    }

    /**
     * 使用 pip 安装依赖
     */
    private static boolean installWithPip(Path workDir, Path requirementsFile) {
        log.info("Using pip to install dependencies...");

        // 创建虚拟环境（如果已存在则清除）
        if (!executeCommand(workDir, "python", "-m", "venv", ".venv", "--clear")) {
            return false;
        }

        // 安装依赖
        String pipCommand = isWindows()
                ? workDir.resolve(".venv").resolve("Scripts").resolve("pip").toString()
                : workDir.resolve(".venv").resolve("bin").resolve("pip").toString();

        if (!executeCommand(workDir, pipCommand, "install", "-r", requirementsFile.toString())) {
            return false;
        }

        // 创建安装完成标记
        createInstallLock(workDir);
        return true;
    }

    /**
     * 创建安装完成标记文件
     */
    private static void createInstallLock(Path workDir) {
        try {
            Path lockFile = workDir.resolve(".deps_installed");
            Files.writeString(lockFile, "Dependencies installed at: " + java.time.Instant.now());
        } catch (IOException e) {
            log.warn("Failed to create install lock file", e);
        }
    }

    /**
     * 执行命令
     */
    private static boolean executeCommand(Path workDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            log.info("Executing: {} in {}", String.join(" ", command), workDir);
            Process process = pb.start();

            // 读取输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("  {}", line);
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Command timed out after {} seconds", TIMEOUT_SECONDS);
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Command failed with exit code: {}", exitCode);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to execute command: {}", String.join(" ", command), e);
            return false;
        }
    }

    /**
     * 检查命令是否可用
     */
    private static boolean isCommandAvailable(String command) {
        try {
            String checkCommand = isWindows() ? "where" : "which";
            ProcessBuilder pb = new ProcessBuilder(checkCommand, command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否为 Windows 系统
     */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 递归删除目录及其所有内容
     *
     * @param directory 要删除的目录
     * @throws IOException 如果删除失败
     */
    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (java.util.stream.Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", path, e);
                        }
                    });
        }
    }
}
