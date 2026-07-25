package org.example.sliders.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.sliders.entity.SlidersChunkEntity;
import org.example.sliders.entity.SlidersExtractedRowEntity;
import org.example.sliders.entity.SlidersSchemaEntity;

import java.util.List;

/**
 * Extractor Agent 专用工具 — 读取 Schema+块、并发提取数据、保存提取结果
 */
@Slf4j
public class ExtractorTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SchemaReader schemaReader;
    private final ChunkReader chunkReader;
    private final ExtractedRowSaver rowSaver;
    private final StatusUpdater statusUpdater;
    private final BatchExtractor batchExtractor;

    @FunctionalInterface public interface SchemaReader { List<SlidersSchemaEntity> read(String taskId); }
    @FunctionalInterface public interface ChunkReader { List<SlidersChunkEntity> read(String taskId); }
    @FunctionalInterface public interface ExtractedRowSaver { void saveAll(List<SlidersExtractedRowEntity> rows); }
    @FunctionalInterface public interface StatusUpdater { boolean update(String taskId, String status); }

    public ExtractorTools(SchemaReader schemaReader, ChunkReader chunkReader,
                          ExtractedRowSaver rowSaver, StatusUpdater statusUpdater,
                          BatchExtractor batchExtractor) {
        this.schemaReader = schemaReader;
        this.chunkReader = chunkReader;
        this.rowSaver = rowSaver;
        this.statusUpdater = statusUpdater;
        this.batchExtractor = batchExtractor;
    }

    @Tool(
            name = "read_schema_and_chunks",
            description = "读取任务的 Schema 定义和所有文档块。用于了解需要提取什么数据以及数据来源。"
                    + "返回完整 Schema + 每个块的内容。"
    )
    public String readSchemaAndChunks(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        statusUpdater.update(taskId, "EXTRACTING");

        List<SlidersSchemaEntity> schemas = schemaReader.read(taskId);
        if (schemas.isEmpty()) {
            return "❌ 未找到任务 " + taskId + " 的 Schema";
        }

        List<SlidersChunkEntity> chunks = chunkReader.read(taskId);
        if (chunks.isEmpty()) {
            return "❌ 未找到任务 " + taskId + " 的文档块";
        }

        SlidersSchemaEntity latestSchema = schemas.getLast();

        StringBuilder sb = new StringBuilder();
        sb.append("## Schema 定义\n```json\n");
        sb.append(latestSchema.getSchemaJson());
        sb.append("\n```\n\n");

        sb.append("## 文档块（共 ").append(chunks.size()).append(" 块）\n\n");
        for (var chunk : chunks) {
            sb.append("### 块 ").append(chunk.getChunkId())
                    .append(" [").append(chunk.getDocumentName()).append("]");
            if (chunk.getChunkHeader() != null && !chunk.getChunkHeader().isEmpty()) {
                sb.append(" — ").append(chunk.getChunkHeader());
            }
            sb.append("\n```\n").append(chunk.getContent()).append("\n```\n\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "save_extracted_rows",
            description = "保存从文档块中提取的结构化数据。"
                    + "每行数据包含 table_name（归属表名）、chunk_id（来源块）、field_data（字段值 JSON）。\n"
                    + "field_data 格式：{\"字段名\": {\"value\": \"值\", \"quote\": \"原文引用\", \"rationale\": \"提取理由\", \"confidence\": \"High\"}}"
    )
    public String saveExtractedRows(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "rowsJson", description = "提取的行数据 JSON 数组。\n"
                    + "每个元素含: table_name, chunk_id, document_name, row_index, field_data (JSON object)\n"
                    + "例如: [{\"table_name\":\"Trial\",\"chunk_id\":\"report_chunk0\","
                    + "\"document_name\":\"report.md\",\"row_index\":0,"
                    + "\"field_data\":{\"trial_name\":{\"value\":\"Study A\",\"quote\":\"...\",\"rationale\":\"...\",\"confidence\":\"High\"}}}]")
            String rowsJson
    ) {
        try {
            var array = MAPPER.readTree(rowsJson);
            java.util.List<SlidersExtractedRowEntity> entities = new java.util.ArrayList<>();

            for (var row : array) {
                String fieldData = row.has("field_data") ? MAPPER.writeValueAsString(row.get("field_data")) : "{}";
                String metadata = row.has("metadata") ? MAPPER.writeValueAsString(row.get("metadata")) : null;

                entities.add(SlidersExtractedRowEntity.builder()
                        .taskId(taskId)
                        .tableName(row.path("table_name").asText(""))
                        .chunkId(row.path("chunk_id").asText(""))
                        .documentName(row.path("document_name").asText(""))
                        .rowIndex(row.path("row_index").asInt(0))
                        .fieldData(fieldData)
                        .metadata(metadata)
                        .build());
            }

            rowSaver.saveAll(entities);

            log.info("Extracted rows saved: taskId={}, rows={}", taskId, entities.size());

            return String.format("""
                    ✅ 提取数据保存成功！
                    - 任务 ID: %s
                    - 行数: %d
                    
                    提取完成，请回复"提取完成"。
                    """, taskId, entities.size());

        } catch (Exception e) {
            log.error("Failed to save extracted rows", e);
            return "❌ 保存提取数据失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "batch_extract",
            description = "并发提取所有文档块的数据。一次调用完成所有 chunk 的提取，自动保存结果。"
                    + "比逐个调用 extract_chunk 快 4-8 倍。完成后请回复[提取完成]。"
    )
    public String batchExtract(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "question", description = "用户问题（用于指导提取方向）") String question
    ) {
        statusUpdater.update(taskId, "EXTRACTING");

        // 读取 Schema
        List<SlidersSchemaEntity> schemas = schemaReader.read(taskId);
        if (schemas.isEmpty()) {
            return "❌ 未找到 Schema";
        }
        String schemaJson = schemas.getLast().getSchemaJson();

        // 读取所有 chunks
        List<SlidersChunkEntity> chunks = chunkReader.read(taskId);
        if (chunks.isEmpty()) {
            return "❌ 未找到文档块";
        }

        log.info("[batch_extract] 开始并发提取: taskId={}, chunks={}, concurrency={}",
                taskId, chunks.size(), 4);

        // 构建 chunkMap: chunkId -> content
        java.util.Map<String, String> chunkMap = new java.util.LinkedHashMap<>();
        for (var chunk : chunks) {
            chunkMap.put(chunk.getChunkId(), chunk.getContent());
        }

        // 并发提取
        java.util.Map<String, List<java.util.Map<String, Object>>> results =
                batchExtractor.batchExtract(schemaJson, question, chunkMap);

        if (results.isEmpty()) {
            return "⚠️ 并发提取完成，但未从任何 chunk 中提取到数据。请检查 Schema 和文档是否匹配。";
        }

        // 转换为 SlidersExtractedRowEntity 并保存
        List<SlidersExtractedRowEntity> entities = new java.util.ArrayList<>();
        for (var entry : results.entrySet()) {
            String chunkId = entry.getKey();
            List<java.util.Map<String, Object>> rows = entry.getValue();

            // 找到对应的文档名
            String docName = chunks.stream()
                    .filter(c -> c.getChunkId().equals(chunkId))
                    .findFirst().map(SlidersChunkEntity::getDocumentName).orElse("unknown");

            for (int i = 0; i < rows.size(); i++) {
                java.util.Map<String, Object> row = rows.get(i);
                // 构建 field_data: 每个字段包装为 {value, quote, rationale, confidence}
                java.util.Map<String, java.util.Map<String, Object>> fieldData = new java.util.LinkedHashMap<>();
                for (var field : row.entrySet()) {
                    java.util.Map<String, Object> fieldEntry = new java.util.LinkedHashMap<>();
                    fieldEntry.put("value", field.getValue());
                    fieldEntry.put("confidence", "High");
                    fieldEntry.put("rationale", "batch_extract 自动提取");
                    fieldData.put(field.getKey(), fieldEntry);
                }
                try {
                    entities.add(SlidersExtractedRowEntity.builder()
                            .taskId(taskId)
                            .tableName(extractTableName(row))
                            .chunkId(chunkId)
                            .documentName(docName)
                            .rowIndex(i)
                            .fieldData(MAPPER.writeValueAsString(fieldData))
                            .build());
                } catch (Exception e) {
                    log.warn("[batch_extract] 序列化失败: chunk={}, row={}", chunkId, i);
                }
            }
        }

        rowSaver.saveAll(entities);

        int successChunks = results.size();
        int totalChunks = chunks.size();
        log.info("[batch_extract] 完成: {}/{} chunks, {} rows saved", successChunks, totalChunks, entities.size());

        return String.format("""
                ✅ 并发提取完成！
                - 成功: %d/%d 个 chunk
                - 提取行数: %d
                - 已自动保存
                
                请回复"提取完成"。
                """, successChunks, totalChunks, entities.size());
    }

    /** 从行数据中推断表名（取第一个字段名或 table_name 字段） */
    private String extractTableName(java.util.Map<String, Object> row) {
        if (row.containsKey("table_name")) {
            return String.valueOf(row.get("table_name"));
        }
        if (row.containsKey("_table")) {
            return String.valueOf(row.get("_table"));
        }
        return "extracted_data";
    }
}
