package org.example.skillEvolver.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Trial 执行工具 — 在隔离工作区中加载策略变体执行任务、收集 trace、验证结果。
 * <p>
 * 每个 TrialTestAgent 实例持有自己的工作区路径和 trial 上下文，
 * 通过函数式接口注入依赖，不依赖 Spring 容器。
 *
 * @author zhilin
 */
@Slf4j
public class TrialTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== 函数式接口 ====================

    /** 执行一段 shell 命令，返回 {exitCode, stdout, stderr} */
    @FunctionalInterface
    public interface CommandRunner {
        CommandResult run(String workDir, String command, int timeoutSeconds);
    }

    /** 验证 trial 结果，返回 {passed, score, message} */
    @FunctionalInterface
    public interface Verifier {
        VerifyResult verify(String workDir, String taskData);
    }

    /** 将 trial trace 写入任务记录 */
    @FunctionalInterface
    public interface TraceCollector {
        void collect(String taskId, int iteration, int variantIndex,
                      boolean passed, double reward, String traceSummary);
    }

    // ==================== 结果 DTO ====================

    public record CommandResult(int exitCode, String stdout, String stderr) {}
    public record VerifyResult(boolean passed, double score, String message) {}

    // ==================== 实例字段 ====================

    private final CommandRunner commandRunner;
    private final Verifier verifier;
    private final TraceCollector traceCollector;

    public TrialTools(CommandRunner commandRunner,
                      Verifier verifier,
                      TraceCollector traceCollector) {
        this.commandRunner = commandRunner;
        this.verifier = verifier;
        this.traceCollector = traceCollector;
    }

    // ==================== @Tool 方法 ====================

    @Tool(
            name = "execute_trial_task",
            description = "在隔离工作区中执行一次 trial 任务。"
                    + "Agent 先用 write_trial_file 写好执行脚本，再调用此工具执行。"
                    + "返回执行结果：exit code + stdout + stderr。"
                    + "超时默认 300 秒。"
    )
    public String executeTrialTask(
            @ToolParam(name = "workDir", description = "隔离工作区目录路径") String workDir,
            @ToolParam(name = "command", description = "要执行的命令（通常是 python 脚本或 shell 命令）") String command,
            @ToolParam(name = "timeoutSeconds", description = "超时秒数（默认 300）") int timeoutSeconds
    ) {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 300;
        }

        log.info("[TrialRunner] 执行 trial: workDir={}, cmd={}, timeout={}s",
                workDir, command, timeoutSeconds);

        CommandResult result = commandRunner.run(workDir, command, timeoutSeconds);

        // 自动保存执行结果到 .trial_result.json，供 verify_trial_result 读取
        try {
            java.nio.file.Path resultFile = java.nio.file.Paths.get(workDir, ".trial_result.json");
            java.nio.file.Files.createDirectories(resultFile.getParent());
            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("exitCode", result.exitCode());
            resultData.put("stdout", result.stdout() != null ? result.stdout() : "");
            resultData.put("stderr", result.stderr() != null ? result.stderr() : "");
            resultData.put("timestamp", java.time.Instant.now().toString());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(resultFile.toFile(), resultData);
            log.info("[TrialRunner] 执行结果已保存: {}", resultFile);
        } catch (Exception e) {
            log.warn("[TrialRunner] 保存执行结果失败: {}", e.getMessage());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Trial 执行结果\n\n");
        sb.append("- **Exit Code**: ").append(result.exitCode()).append("\n");

        if (result.exitCode() == 0) {
            sb.append("- **状态**: ✅ 执行成功\n\n");
        } else {
            sb.append("- **状态**: ❌ 执行失败\n\n");
        }

        if (result.stdout() != null && !result.stdout().isBlank()) {
            String stdout = result.stdout();
            if (stdout.length() > 3000) {
                stdout = stdout.substring(0, 3000) + "\n... (截断，共 " + result.stdout().length() + " 字符)";
            }
            sb.append("### stdout\n```\n").append(stdout).append("\n```\n\n");
        }

        if (result.stderr() != null && !result.stderr().isBlank()) {
            String stderr = result.stderr();
            if (stderr.length() > 2000) {
                stderr = stderr.substring(0, 2000) + "\n... (截断)";
            }
            sb.append("### stderr\n```\n").append(stderr).append("\n```\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "verify_trial_result",
            description = "验证 trial 执行结果是否符合预期。"
                    + "根据任务配置的 verifier 规则检查输出文件、关键词、exit code 等。"
                    + "返回验证结果：passed + score + 诊断信息。"
    )
    public String verifyTrialResult(
            @ToolParam(name = "workDir", description = "trial 工作区目录") String workDir,
            @ToolParam(name = "taskData", description = "任务输入数据（JSON）") String taskData,
            @ToolParam(name = "taskId", description = "进化任务 ID") String taskId,
            @ToolParam(name = "iteration", description = "当前迭代号") int iteration,
            @ToolParam(name = "variantIndex", description = "策略变体序号") int variantIndex
    ) {
        VerifyResult result = verifier.verify(workDir, taskData);

        // 收集 trace
        String traceSummary = String.format(
                "trial-%d-variant-%d: passed=%s, score=%.3f, msg=%s",
                iteration, variantIndex, result.passed(), result.score(), result.message()
        );
        traceCollector.collect(taskId, iteration, variantIndex,
                result.passed(), result.score(), traceSummary);

        // 同时直接通过 ConcurrentHashMap 注册验证结果（避免 ThreadLocal 跨线程丢失）
        org.example.skillEvolver.agent.TrialTestAgent.registerVerifyResult(
                workDir, result.passed(), result.score(), result.message());

        StringBuilder sb = new StringBuilder();
        sb.append("## 验证结果\n\n");
        sb.append("- **通过**: ").append(result.passed() ? "✅ 是" : "❌ 否").append("\n");
        sb.append("- **分数**: ").append(String.format("%.3f", result.score())).append("\n");
        sb.append("- **诊断**: ").append(result.message()).append("\n");

        return sb.toString();
    }

    @Tool(
            name = "write_trial_file",
            description = "在 trial 工作区中写入文件（执行脚本、配置文件、数据文件等）。"
                    + "用于准备 trial 执行环境。"
    )
    public String writeTrialFile(
            @ToolParam(name = "workDir", description = "工作区目录") String workDir,
            @ToolParam(name = "fileName", description = "文件名（如 run.py, config.json）") String fileName,
            @ToolParam(name = "content", description = "文件内容") String content
    ) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(workDir);
            java.nio.file.Files.createDirectories(dir);

            java.nio.file.Path filePath = dir.resolve(fileName);
            java.nio.file.Files.writeString(filePath, content, java.nio.charset.StandardCharsets.UTF_8);

            log.info("[TrialRunner] 文件已写入: {}", filePath);
            return "✅ 文件已写入: " + filePath.toAbsolutePath();
        } catch (Exception e) {
            log.error("[TrialRunner] 写入文件失败", e);
            return "❌ 写入文件失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "read_trial_file",
            description = "读取 trial 工作区中的文件内容。用于检查输出文件、日志等。"
    )
    public String readTrialFile(
            @ToolParam(name = "workDir", description = "工作区目录") String workDir,
            @ToolParam(name = "fileName", description = "文件名") String fileName,
            @ToolParam(name = "maxChars", description = "最大读取字符数（默认 5000）") int maxChars
    ) {
        if (maxChars <= 0) maxChars = 5000;
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(workDir, fileName);
            if (!java.nio.file.Files.exists(filePath)) {
                return "❌ 文件不存在: " + fileName;
            }
            String content = java.nio.file.Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
            if (content.length() > maxChars) {
                content = content.substring(0, maxChars) + "\n... (截断，共 " + content.length() + " 字符)";
            }
            return content;
        } catch (Exception e) {
            return "❌ 读取文件失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "list_trial_files",
            description = "列出 trial 工作区中的所有文件。用于了解执行环境。"
    )
    public String listTrialFiles(
            @ToolParam(name = "workDir", description = "工作区目录") String workDir
    ) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(workDir);
            if (!java.nio.file.Files.exists(dir)) {
                return "📁 工作区不存在: " + workDir;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 工作区文件\n\n");
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.list(dir)) {
                paths.forEach(p -> {
                    try {
                        long size = java.nio.file.Files.size(p);
                        sb.append("- ").append(p.getFileName())
                                .append(" (").append(size).append(" bytes)\n");
                    } catch (Exception ignored) {}
                });
            }
            return sb.toString();
        } catch (Exception e) {
            return "❌ 列出文件失败: " + e.getMessage();
        }
    }
}
