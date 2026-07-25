package org.example.agentScope.util.tool.CLITool;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CliToolManager {

    private final Map<String, AbstractCliTool> toolRegistry = new ConcurrentHashMap<>();

    @Resource
    private List<AbstractCliTool> tools;

    @PostConstruct
    public void init() {
        for (AbstractCliTool tool : tools) {
            if (toolRegistry.containsKey(tool.getName())) {
                throw new IllegalStateException(STR."Duplicate CLI tool name: \{tool.getName()}");
            }
            toolRegistry.put(tool.getName(), tool);
            log.info("Registered CLI Tool: {}", tool.getName());
        }
    }

    public AbstractCliTool getTool(String name) {
        return toolRegistry.get(name);
    }

    public List<String> getAllToolNames() {
        return List.copyOf(toolRegistry.keySet());
    }
}
