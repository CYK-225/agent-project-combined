package org.example.dbagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.mas.agentHub.BaseTool;
import org.example.dbagent.model.TableSchema;
import org.example.dbagent.service.SchemaReaderService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 表结构读取工具
 * 用于读取MySQL数据库的表结构信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaReaderTool extends BaseTool {

    private final SchemaReaderService schemaReaderService;
    private final ObjectMapper objectMapper;

    @Override
    public String getToolName() {
        return "schema_reader";
    }

    @Override
    public String execute(Map<String, Object> input) {
        try {
            String action = (String) input.getOrDefault("action", "list");

            switch (action) {
                case "list":
                    List<String> tables = schemaReaderService.listTables();
                    return objectMapper.writeValueAsString(Map.of(
                            "success", true,
                            "tables", tables
                    ));

                case "detail":
                    String tableName = (String) input.get("tableName");
                    if (tableName == null || tableName.isBlank()) {
                        return "{\"success\":false,\"error\":\"tableName不能为空\"}";
                    }
                    TableSchema schema = schemaReaderService.getTableSchema(tableName);
                    return objectMapper.writeValueAsString(Map.of(
                            "success", true,
                            "schema", schema
                    ));

                case "test":
                    boolean connected = schemaReaderService.testConnection();
                    return objectMapper.writeValueAsString(Map.of(
                            "success", true,
                            "connected", connected
                    ));

                default:
                    return "{\"success\":false,\"error\":\"未知action: " + action + "\"}";
            }
        } catch (Exception e) {
            log.error("SchemaReaderTool执行失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
