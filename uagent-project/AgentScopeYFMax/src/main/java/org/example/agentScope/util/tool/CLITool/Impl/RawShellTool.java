package org.example.agentScope.util.tool.CLITool.Impl;

import org.example.agentScope.util.tool.CLITool.AbstractCliTool;
import org.example.agentScope.util.tool.CLITool.CliCommandBuilder;
import org.example.agentScope.util.tool.CLITool.CliExecutionRequest;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RawShellTool extends AbstractCliTool {

    @Override
    public String getName() {
        return "execute_raw_shell";
    }

    @Override
    public String getDescription() {
        return "Execute a raw shell command directly. Warning: Use with caution.";
    }

    @Override
    public String execute(CliExecutionRequest request) {
        String script = request.getStringParam("command");
        if (script == null || script.isBlank()) {
            return "Error: No command provided.";
        }

        List<String> command = CliCommandBuilder.createRaw(script).build();
        return runCommand(command, request);
    }
}
