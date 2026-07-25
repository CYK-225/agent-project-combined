package org.example.agentScope.util.tool.CLITool.Impl;

import org.example.agentScope.util.tool.CLITool.AbstractCliTool;
import org.example.agentScope.util.tool.CLITool.CliCommandBuilder;
import org.example.agentScope.util.tool.CLITool.CliExecutionRequest;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PingTool extends AbstractCliTool {

    @Override
    public String getName() {
        return "network_ping";
    }

    @Override
    public String getDescription() {
        return "Ping a specific host to check network connectivity.";
    }

    @Override
    public String execute(CliExecutionRequest request) {
        String host = request.getStringParam("host");
        if (host == null || host.isBlank()) {
            return "Error: Missing required parameter 'host'.";
        }

        List<String> command = CliCommandBuilder.create("ping")
                .osSwitch("-n", "-c")
                .add("4")
                .add(host)
                .build();

        return runCommand(command, request);
    }
}