package org.example.sliders.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.sliders.entity.SlidersExtractedRowEntity;
import org.example.sliders.entity.SlidersReconciledTableEntity;

import java.util.List;

/**
 * Reconciler Agent 专用工具 — 读取原始提取数据、并发协调、保存协调结果
 */
@Slf4j
public class ReconcilerTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExtractedRowReader rowReader;
    private final ReconciledTableSaver tableSaver;
    private final StatusUpdater statusUpdater;
    private final BatchReconciler batchReconciler;

    @FunctionalInterface
    public interface ExtractedRowReader {
        List<SlidersExtractedRowEntity> read(String taskId);
    }

    @FunctionalInterface
    public interface ReconciledTableSaver {
        void saveAll(List<SlidersReconciledTableEntity> tables);
    }

    @FunctionalInterface
    public interface StatusUpdater {
        boolean update(String taskId, String status);
    }

    public ReconcilerTools(ExtractedRowReader rowReader,
                           ReconciledTableSaver tableSaver,
                           StatusUpdater statusUpdater,
                           BatchReconciler batchReconciler) {
        this.rowReader = rowReader;
        this.tableSaver = tableSaver;
        this.statusUpdater = statusUpdater;
        this.batchReconciler = batchReconciler;
    }

    @Tool(
            name = "read_extracted_rows",
            description = "读取任务的所有提取行数据，按表名分组展示。"
                    + "用于分析数据重叠、冲突，规划协调策略。"
    )
    public String readExtractedRows(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        statusUpdater.update(taskId, "RECONCILING");

        List<SlidersExtractedRowEntity> rows = rowReader.read(taskId);
        if (rows.isEmpty()) {
            return "❌ 未找到任务 " + taskId + " 的提取数据";
        }

        // 按表名分组
        java.util.Map<String, java.util.List<SlidersExtractedRowEntity>> byTable = new java.util.LinkedHashMap<>();
        for (var row : rows) {
            byTable.computeIfAbsent(row.getTableName(), k -> new java.util.ArrayList<>()).add(row);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 任务 ").append(taskId).append(" 共有 ")
                .append(rows.size()).append(" 行提取数据，分布如下：\n\n");

        for (var entry : byTable.entrySet()) {
            String tableName = entry.getKey();
            var tableRows = entry.getValue();

            sb.append("## 表: ").append(tableName)
                    .append("（").append(tableRows.size()).append(" 行）\n\n");

            for (var row : tableRows) {
                sb.append("### 行 ").append(row.getRowIndex())
                        .append(" [来源: ").append(row.getChunkId()).append("]\n");
                sb.append("```json\n").append(row.getFieldData()).append("\n```\n\n");
            }
        }

        // 统计潜在重复
        long duplicates = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.getTableName() + ":" + r.getFieldData(), java.util.stream.Collectors.counting()))
                .values().stream().filter(c -> c > 1).count();

        if (duplicates > 0) {
            sb.append("⚠️ 检测到 ").append(duplicates).append(" 组可能的重复数据\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "save_reconciled_table",
            description = "保存协调（去重/合并）后的干净数据。\n"
                    + "每行含: table_name, row_index, row_data (字段值 JSON), "
                    + "provenance (溯源 JSON), reconciliation_context (协调上下文说明)"
    )
    public String saveReconciledTable(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "rowsJson", description = "协调后的行数据 JSON 数组。\n"
                    + "每个元素含: table_name, row_index, row_data (JSON object), "
                    + "provenance (可选 JSON object), reconciliation_context (可选字符串)\n"
                    + "例如: [{\"table_name\":\"Trial\",\"row_index\":0,"
                    + "\"row_data\":{\"trial_name\":\"Study A\",\"population\":\"100人\"},"
                    + "\"provenance\":{\"source_chunks\":[\"report_chunk0\",\"report_chunk3\"]},"
                    + "\"reconciliation_context\":\"合并了 chunk0 和 chunk3 的同名试验数据\"}]")
            String rowsJson
    ) {
        try {
            var array = MAPPER.readTree(rowsJson);
            java.util.List<SlidersReconciledTableEntity> entities = new java.util.ArrayList<>();

            for (var row : array) {
                String rowData = row.has("row_data") ? MAPPER.writeValueAsString(row.get("row_data")) : "{}";
                String provenance = row.has("provenance") ? MAPPER.writeValueAsString(row.get("provenance")) : null;
                String context = row.has("reconciliation_context") ? row.get("reconciliation_context").asText() : null;

                entities.add(SlidersReconciledTableEntity.builder()
                        .taskId(taskId)
                        .tableName(row.path("table_name").asText(""))
                        .rowIndex(row.path("row_index").asInt(0))
                        .rowData(rowData)
                        .provenance(provenance)
                        .reconciliationContext(context)
                        .build());
            }

            tableSaver.saveAll(entities);

            // 统计表名
            java.util.Set<String> tableNames = entities.stream()
                    .map(SlidersReconciledTableEntity::getTableName)
                    .collect(java.util.stream.Collectors.toSet());

            log.info("Reconciled table saved: taskId={}, tables={}, rows={}",
                    taskId, tableNames, entities.size());

            return String.format("""
                    ✅ 协调数据保存成功！
                    - 任务 ID: %s
                    - 表数量: %d
                    - 总行数: %d
                    - 表列表: %s
                    
                    协调完成，请回复[协调完成]。
                    """, taskId, tableNames.size(), entities.size(), tableNames);

        } catch (Exception e) {
            log.error("Failed to save reconciled table", e);
            return "❌ 保存协调数据失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "batch_reconcile",
            description = "并发协调所有表的数据。一次调用完成所有表的去重、合并、冲突解决，自动保存结果。"
                    + "比逐表协调快 3-5 倍。完成后请回复[协调完成]。"
    )
    public String batchReconcile(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "question", description = "用户问题（用于指导协调策略）") String question
    ) {
        statusUpdater.update(taskId, "RECONCILING");

        // 读取所有提取行
        List<SlidersExtractedRowEntity> rows = rowReader.read(taskId);
        if (rows.isEmpty()) {
            return "❌ 未找到提取数据";
        }

        // 按表名分组，构建 tableData: tableName -> rowsJson
        java.util.Map<String, java.util.List<SlidersExtractedRowEntity>> byTable = new java.util.LinkedHashMap<>();
        for (var row : rows) {
            byTable.computeIfAbsent(row.getTableName(), k -> new java.util.ArrayList<>()).add(row);
        }

        java.util.Map<String, String> tableData = new java.util.LinkedHashMap<>();
        for (var entry : byTable.entrySet()) {
            try {
                // 序列化该表的所有行
                java.util.List<java.util.Map<String, Object>> rowList = new java.util.ArrayList<>();
                for (var row : entry.getValue()) {
                    java.util.Map<String, Object> rowMap = new java.util.LinkedHashMap<>();
                    rowMap.put("chunk_id", row.getChunkId());
                    rowMap.put("row_index", row.getRowIndex());
                    rowMap.put("field_data", MAPPER.readValue(row.getFieldData(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}));
                    rowList.add(rowMap);
                }
                tableData.put(entry.getKey(), MAPPER.writeValueAsString(rowList));
            } catch (Exception e) {
                log.warn("[batch_reconcile] 序列化表 {} 失败: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("[batch_reconcile] 开始并发协调: taskId={}, tables={}, concurrency={}",
                taskId, tableData.size(), 4);

        // 并发协调
        java.util.Map<String, java.util.List<java.util.Map<String, Object>>> results =
                batchReconciler.batchReconcile(question, tableData);

        if (results.isEmpty()) {
            return "⚠️ 并发协调完成，但未产生协调结果。请检查提取数据是否正确。";
        }

        // 转换为 SlidersReconciledTableEntity 并保存
        java.util.List<SlidersReconciledTableEntity> entities = new java.util.ArrayList<>();
        for (var entry : results.entrySet()) {
            String tableName = entry.getKey();
            java.util.List<java.util.Map<String, Object>> reconciledRows = entry.getValue();

            for (int i = 0; i < reconciledRows.size(); i++) {
                java.util.Map<String, Object> row = reconciledRows.get(i);
                try {
                    entities.add(SlidersReconciledTableEntity.builder()
                            .taskId(taskId)
                            .tableName(tableName)
                            .rowIndex(i)
                            .rowData(MAPPER.writeValueAsString(row))
                            .reconciliationContext("batch_reconcile 自动协调")
                            .build());
                } catch (Exception e) {
                    log.warn("[batch_reconcile] 序列化失败: table={}, row={}", tableName, i);
                }
            }
        }

        tableSaver.saveAll(entities);

        java.util.Set<String> tableNames = entities.stream()
                .map(SlidersReconciledTableEntity::getTableName)
                .collect(java.util.stream.Collectors.toSet());

        log.info("[batch_reconcile] 完成: {}/{} 表, {} rows saved", results.size(), tableData.size(), entities.size());

        return String.format("""
                ✅ 并发协调完成！
                - 成功: %d/%d 个表
                - 协调行数: %d
                - 表列表: %s
                - 已自动保存
                
                请回复"协调完成"。
                """, results.size(), tableData.size(), entities.size(), tableNames);
    }
}
