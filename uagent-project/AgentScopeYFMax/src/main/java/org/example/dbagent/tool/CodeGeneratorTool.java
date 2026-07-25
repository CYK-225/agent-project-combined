package org.example.dbagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.mas.agentHub.BaseTool;
import org.example.dbagent.merger.MergeResult;
import org.example.dbagent.model.TableSchema;
import org.example.dbagent.service.CodeGenerationService;
import org.example.dbagent.service.GitService;
import org.example.dbagent.service.SchemaReaderService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 代码生成工具
 * 用于生成或更新Entity、Mapper、Service代码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGeneratorTool extends BaseTool {

    private final CodeGenerationService codeGenerationService;
    private final SchemaReaderService schemaReaderService;
    private final GitService gitService;
    private final ObjectMapper objectMapper;

    @Override
    public String getToolName() {
        return "code_generator";
    }

    @Override
    public String execute(Map<String, Object> input) {
        try {
            String tableName = (String) input.get("tableName");
            if (tableName == null || tableName.isBlank()) {
                return "{\"success\":false,\"error\":\"tableName不能为空\"}";
            }

            String mode = (String) input.getOrDefault("mode", "full");

            // 1. 拉取最新代码
            gitService.pullLatest();

            // 2. 读取表结构
            TableSchema schema = schemaReaderService.getTableSchema(tableName);

            // 3. 生成/更新代码
            MergeResult result = codeGenerationService.generateOrUpdateCode(tableName, schema, mode);

            // 4. 提交并推送
            if (result.isSuccess()) {
                gitService.commitAndPush(tableName, result.getChanges());
            }

            return objectMapper.writeValueAsString(Map.of(
                    "success", result.isSuccess(),
                    "changes", result.getChanges() != null ? result.getChanges() : List.of(),
                    "error", result.getErrorMessage() != null ? result.getErrorMessage() : ""
            ));

        } catch (Exception e) {
            log.error("CodeGeneratorTool执行失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
