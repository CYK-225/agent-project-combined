package org.example.sliders.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.sliders.entity.SlidersChunkEntity;
import org.example.sliders.entity.SlidersSchemaEntity;
import org.example.sliders.entity.SlidersTaskEntity;

import java.util.List;

/**
 * Schema Agent 专用工具 — 读取任务信息+块摘要、保存 Schema
 */
@Slf4j
public class SchemaTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TaskReader taskReader;
    private final ChunkReader chunkReader;
    private final SchemaSaver schemaSaver;
    private final StatusUpdater statusUpdater;

    @FunctionalInterface public interface TaskReader { SlidersTaskEntity read(String taskId); }
    @FunctionalInterface public interface ChunkReader { List<SlidersChunkEntity> read(String taskId); }
    @FunctionalInterface public interface SchemaSaver { void save(SlidersSchemaEntity entity); }
    @FunctionalInterface public interface StatusUpdater { boolean update(String taskId, String status); }

    public SchemaTools(TaskReader taskReader, ChunkReader chunkReader,
                       SchemaSaver schemaSaver, StatusUpdater statusUpdater) {
        this.taskReader = taskReader;
        this.chunkReader = chunkReader;
        this.schemaSaver = schemaSaver;
        this.statusUpdater = statusUpdater;
    }

    @Tool(
            name = "read_task_and_chunks",
            description = "读取任务的问题和所有文档块的摘要信息，用于分析问题类型和文档结构、设计关系型 Schema。"
                    + "返回每个块的前 300 字符预览。"
    )
    public String readTaskAndChunks(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        statusUpdater.update(taskId, "SCHEMA_INDUCING");

        SlidersTaskEntity task = taskReader.read(taskId);
        if (task == null) {
            return "❌ 未找到任务: " + taskId;
        }

        List<SlidersChunkEntity> chunks = chunkReader.read(taskId);

        StringBuilder sb = new StringBuilder();
        sb.append("## 任务信息\n");
        sb.append("- 任务 ID: ").append(task.getTaskId()).append("\n");
        sb.append("- 问题: ").append(task.getQuestion()).append("\n\n");

        sb.append("## 文档块摘要（共 ").append(chunks.size()).append(" 块）\n\n");
        for (var chunk : chunks) {
            String preview = chunk.getContent().length() > 300
                    ? chunk.getContent().substring(0, 300) + "..."
                    : chunk.getContent();

            sb.append("### 块 ").append(chunk.getChunkIndex())
                    .append(" [").append(chunk.getDocumentName()).append("]");
            if (chunk.getChunkHeader() != null && !chunk.getChunkHeader().isEmpty()) {
                sb.append(" — ").append(chunk.getChunkHeader());
            }
            sb.append("\n");
            sb.append("```\n").append(preview).append("\n```\n\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "save_schema",
            description = "保存设计好的关系型 Schema。"
                    + "Schema JSON 格式：{\"tables\":[{\"name\":\"表名\",\"description\":\"描述\","
                    + "\"fields\":[{\"name\":\"字段名\",\"data_type\":\"STRING|NUMBER|DATE|BOOLEAN\","
                    + "\"description\":\"描述\",\"required\":true,\"extraction_guideline\":\"提取指南\"}]}]}"
    )
    public String saveSchema(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "schemaJson", description = "Schema JSON") String schemaJson,
            @ToolParam(name = "reasoning", description = "Schema 设计的推理过程") String reasoning
    ) {
        try {
            // 校验 JSON 格式
            var tree = MAPPER.readTree(schemaJson);

            schemaSaver.save(SlidersSchemaEntity.builder()
                    .taskId(taskId)
                    .schemaVersion(1)
                    .schemaJson(schemaJson)
                    .reasoning(reasoning)
                    .build());

            // 统计表和字段数
            int tableCount = tree.has("tables") ? tree.get("tables").size() : 0;
            int fieldCount = 0;
            if (tree.has("tables")) {
                for (var table : tree.get("tables")) {
                    if (table.has("fields")) fieldCount += table.get("fields").size();
                }
            }

            log.info("Schema saved: taskId={}, tables={}, fields={}", taskId, tableCount, fieldCount);

            return String.format("""
                    ✅ Schema 保存成功！
                    - 任务 ID: %s
                    - 表数量: %d
                    - 字段总数: %d
                    
                    Schema 归纳完成，请回复"Schema 归纳完成"。
                    """, taskId, tableCount, fieldCount);

        } catch (Exception e) {
            log.error("Failed to save schema", e);
            return "❌ Schema 保存失败: " + e.getMessage() + "\n请检查 JSON 格式是否正确。";
        }
    }
}
