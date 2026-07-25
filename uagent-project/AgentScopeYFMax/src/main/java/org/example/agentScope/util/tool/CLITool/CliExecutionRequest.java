package org.example.agentScope.util.tool.CLITool;

import lombok.Data;
import lombok.experimental.Accessors;
import java.util.HashMap;
import java.util.Map;

/**
 * CLI 工具执行请求参数
 */
@Data
@Accessors(chain = true) // 开启链式 setter 支持
public class CliExecutionRequest {

    // 接收大模型提取的业务参数 (如 {"host": "127.0.0.1", "command": "ls -l"})
    private Map<String, Object> parameters = new HashMap<>();

    // 运行上下文（可选配置）
    private String workingDirectory;
    private Long timeoutSecondsOverride;

    /**
     * 便捷获取字符串参数的辅助方法
     */
    public String getStringParam(String key) {
        Object val = parameters.get(key);
        return val != null ? val.toString() : null;
    }
}
