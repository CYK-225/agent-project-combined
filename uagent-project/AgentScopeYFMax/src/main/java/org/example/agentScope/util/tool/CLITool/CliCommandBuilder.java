package org.example.agentScope.util.tool.CLITool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 跨平台 CLI 命令链式构建器 (线程安全)
 */
public class CliCommandBuilder {

    private final List<String> commandList = new ArrayList<>();
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private CliCommandBuilder() {}

    public static CliCommandBuilder create(String baseCommand) {
        return new CliCommandBuilder().add(baseCommand);
    }

    /**
     * 直接执行原生整句命令（系统 Shell 代理）
     */
    public static CliCommandBuilder createRaw(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }
        return new CliCommandBuilder()
                .add(IS_WINDOWS ? "cmd.exe" : "sh")
                .add(IS_WINDOWS ? "/c" : "-c")
                .add(rawCommand);
    }

    public CliCommandBuilder add(String arg) {
        if (arg != null && !arg.trim().isEmpty()) {
            this.commandList.add(arg);
        }
        return this;
    }

    public CliCommandBuilder addAll(String... args) {
        if (args != null) {
            this.commandList.addAll(Arrays.asList(args));
        }
        return this;
    }

    public CliCommandBuilder osSwitch(String winArg, String unixArg) {
        return add(IS_WINDOWS ? winArg : unixArg);
    }

    public List<String> build() {
        return new ArrayList<>(this.commandList);
    }
}
