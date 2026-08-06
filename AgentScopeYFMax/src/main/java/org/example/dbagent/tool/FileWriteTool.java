package org.example.dbagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 文件写入工具
 * 使用 @Tool 注解模式，兼容 AgentScope Toolkit 框架
 */
@Slf4j
public class FileWriteTool {

    private final ObjectMapper objectMapper;

    public FileWriteTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "file_write",
            description = "将内容写入到指定文件路径。如果父目录不存在会自动创建。用于将生成的Java代码写入文件。"
    )
    public String fileWrite(
            @ToolParam(name = "filePath", description = "文件的完整绝对路径，如 D:/project/src/main/java/org/example/entity/User.java", required = true) String filePath,
            @ToolParam(name = "content", description = "要写入文件的完整内容", required = true) String content
    ) {
        try {
            if (filePath == null || filePath.isBlank()) {
                return objectMapper.writeValueAsString(Map.of(
                        "success", false,
                        "error", "filePath不能为空"
                ));
            }

            if (content == null) {
                content = "";
            }

            Path path = Paths.get(filePath);

            // 确保父目录存在
            Files.createDirectories(path.getParent());

            // 写入文件
            Files.writeString(path, content);

            log.info("文件写入成功: {}", filePath);

            return objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "filePath", filePath,
                    "size", content.length()
            ));

        } catch (IOException e) {
            log.error("文件写入失败: {}", filePath, e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("FileWriteTool执行失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
