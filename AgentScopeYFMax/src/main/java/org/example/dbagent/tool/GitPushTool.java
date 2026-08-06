package org.example.dbagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.mas.agentHub.BaseTool;
import org.example.dbagent.service.GitService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Git推送工具
 * 用于Git仓库操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitPushTool extends BaseTool {

    private final GitService gitService;
    private final ObjectMapper objectMapper;

    @Override
    public String getToolName() {
        return "git_push";
    }

    @Override
    public String execute(Map<String, Object> input) {
        try {
            String action = (String) input.getOrDefault("action", "pull");

            switch (action) {
                case "pull":
                    gitService.pullLatest();
                    return objectMapper.writeValueAsString(Map.of(
                            "success", true,
                            "message", "拉取成功"
                    ));

                case "test":
                    boolean connected = gitService.testConnection();
                    return objectMapper.writeValueAsString(Map.of(
                            "success", true,
                            "connected", connected
                    ));

                default:
                    return "{\"success\":false,\"error\":\"未知action: " + action + "\"}";
            }
        } catch (Exception e) {
            log.error("GitPushTool执行失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
