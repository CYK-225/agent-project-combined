package org.example.agentScope.util.tool.CLITool;

import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractCliTool {

    protected static final long DEFAULT_TIMEOUT_SECONDS = 30;

    public abstract String getName();
    public abstract String getDescription();
    public abstract String execute(CliExecutionRequest request);

    /**
     * 核心命令行执行引擎
     */
    protected String runCommand(List<String> command, CliExecutionRequest request) {
        StringBuilder output = new StringBuilder();
        Process process = null;
        long timeout = request.getTimeoutSecondsOverride() != null ?
                request.getTimeoutSecondsOverride() : DEFAULT_TIMEOUT_SECONDS;

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // 合并标准流与错误流防死锁

            if (request.getWorkingDirectory() != null) {
                pb.directory(new File(request.getWorkingDirectory()));
            }

            process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Command timed out: {}", command);
                return "Error: Command timed out after " + timeout + " seconds.";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("Command exited with code {}: {}", exitCode, command);
                output.append("\n[Process exited with code: ").append(exitCode).append("]");
            }

        } catch (Exception e) {
            log.error("Execution exception for command {}: ", command, e);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            return "Error: Exception occurred - " + e.getMessage();
        }
        return output.toString();
    }
}
