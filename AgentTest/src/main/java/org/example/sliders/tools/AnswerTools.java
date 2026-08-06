package org.example.sliders.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.sliders.entity.SlidersReconciledTableEntity;
import org.example.sliders.entity.SlidersSqlLogEntity;
import org.example.sliders.entity.SlidersTaskEntity;

import java.util.List;

/**
 * Answer Agent 专用工具 — 读取协调数据、保存答案、记录 SQL 查询
 */
@Slf4j
public class AnswerTools {

    private final TaskReader taskReader;
    private final ReconciledDataReader dataReader;
    private final SqlLogSaver sqlLogSaver;
    private final AnswerSaver answerSaver;
    private final StatusUpdater statusUpdater;

    @FunctionalInterface public interface TaskReader { SlidersTaskEntity read(String taskId); }
    @FunctionalInterface public interface ReconciledDataReader { List<SlidersReconciledTableEntity> read(String taskId); }
    @FunctionalInterface public interface SqlExecutor { List<java.util.Map<String, Object>> execute(String sql); }
    @FunctionalInterface public interface SqlLogSaver { void save(SlidersSqlLogEntity entity); }
    @FunctionalInterface public interface AnswerSaver { boolean save(String taskId, String answer); }
    @FunctionalInterface public interface StatusUpdater { boolean update(String taskId, String status); }

    private final SqlExecutor sqlExecutor;

    public AnswerTools(TaskReader taskReader, ReconciledDataReader dataReader,
                       SqlExecutor sqlExecutor, SqlLogSaver sqlLogSaver,
                       AnswerSaver answerSaver, StatusUpdater statusUpdater) {
        this.taskReader = taskReader;
        this.dataReader = dataReader;
        this.sqlExecutor = sqlExecutor;
        this.sqlLogSaver = sqlLogSaver;
        this.answerSaver = answerSaver;
        this.statusUpdater = statusUpdater;
    }

    @Tool(
            name = "read_task_question",
            description = "读取用户的原始问题。用于确保回答紧扣问题。"
    )
    public String readTaskQuestion(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        SlidersTaskEntity task = taskReader.read(taskId);
        if (task == null) {
            return "❌ 未找到任务: " + taskId;
        }
        return "📝 用户问题：" + task.getQuestion();
    }

    @Tool(
            name = "read_reconciled_data",
            description = "读取协调后的干净数据，按表名分组展示。"
                    + "注意：数据量大时会很慢，建议优先用 describe_tables + execute_sql 组合查询。"
    )
    public String readReconciledData(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        statusUpdater.update(taskId, "ANSWERING");

        List<SlidersReconciledTableEntity> tables = dataReader.read(taskId);
        if (tables.isEmpty()) {
            return "❌ 未找到任务 " + taskId + " 的协调数据";
        }

        // 按表名分组
        java.util.Map<String, java.util.List<SlidersReconciledTableEntity>> byTable = new java.util.LinkedHashMap<>();
        for (var row : tables) {
            byTable.computeIfAbsent(row.getTableName(), k -> new java.util.ArrayList<>()).add(row);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 协调后的数据（共 ").append(tables.size()).append(" 行）：\n\n");

        for (var entry : byTable.entrySet()) {
            String tableName = entry.getKey();
            var rows = entry.getValue();

            sb.append("## 表: ").append(tableName)
                    .append("（").append(rows.size()).append(" 行）\n\n");

            // 输出为 Markdown 表格格式，方便 LLM 理解
            sb.append("```json\n");
            for (var row : rows) {
                sb.append(String.format("[%d] %s", row.getRowIndex(), row.getRowData()));
                if (row.getReconciliationContext() != null) {
                    sb.append("  // 协调: ").append(row.getReconciliationContext());
                }
                sb.append("\n");
            }
            sb.append("```\n\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "describe_tables",
            description = "查看任务关联的协调表结构（表名、行数、样例行），无需加载全部数据。"
                    + "用于快速了解可用数据，再用 execute_sql 精确查询。"
    )
    public String describeTables(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        List<SlidersReconciledTableEntity> tables = dataReader.read(taskId);
        if (tables.isEmpty()) {
            return "❌ 未找到任务 " + taskId + " 的协调数据";
        }

        // 按表名分组
        java.util.Map<String, java.util.List<SlidersReconciledTableEntity>> byTable = new java.util.LinkedHashMap<>();
        for (var t : tables) {
            byTable.computeIfAbsent(t.getTableName(), k -> new java.util.ArrayList<>()).add(t);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 任务 ").append(taskId).append(" 协调数据概览：\n\n");

        for (var entry : byTable.entrySet()) {
            String tableName = entry.getKey();
            var rows = entry.getValue();
            sb.append("### 表: ").append(tableName).append("（").append(rows.size()).append(" 行）\n");

            // 从第一行解析字段名
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.findAndRegisterModules();
                var firstRow = mapper.readTree(rows.get(0).getRowData());
                sb.append("字段: ");
                java.util.Iterator<String> fields = firstRow.fieldNames();
                boolean first = true;
                while (fields.hasNext()) {
                    if (!first) sb.append(", ");
                    sb.append(fields.next());
                    first = false;
                }
                sb.append("\n");
            } catch (Exception e) {
                sb.append("（字段解析失败）\n");
            }

            // 样例行（最多 2 行）
            sb.append("样例:\n```json\n");
            int count = 0;
            for (var row : rows) {
                if (count >= 2) break;
                sb.append(row.getRowData()).append("\n");
                count++;
            }
            sb.append("```\n\n");
        }

        sb.append("💡 使用 execute_sql 工具查询数据，SQL 示例：\n");
        sb.append("```sql\n");
        sb.append("SELECT row_data::jsonb->>'name' AS name, row_data::jsonb->>'role' AS role\n");
        sb.append("FROM agent_test.sliders_reconciled_table\n");
        sb.append("WHERE task_id = '").append(taskId).append("' AND table_name = 'extracted_data'\n");
        sb.append("```\n");

        return sb.toString();
    }

    @Tool(
            name = "log_sql_query",
            description = "记录你设计的 SQL 查询，用于审计和调试。"
                    + "每次设计 SQL 查询后都应调用此工具记录。"
    )
    public String logSqlQuery(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "sqlQuery", description = "SQL 查询语句") String sqlQuery,
            @ToolParam(name = "purpose", description = "查询目的") String purpose,
            @ToolParam(name = "resultSummary", description = "查询结果摘要") String resultSummary
    ) {
        sqlLogSaver.save(SlidersSqlLogEntity.builder()
                .taskId(taskId)
                .sqlQuery(sqlQuery)
                .queryPurpose(purpose)
                .resultSummary(resultSummary)
                .isError(false)
                .build());

        return "📝 SQL 查询已记录";
    }

    @Tool(
            name = "execute_sql",
            description = "执行 SQL 查询协调后的结构化数据（直接调用，不需要先查表结构）。\n"
                    + "表：agent_test.sliders_reconciled_table\n"
                    + "字段：task_id, table_name, row_index, row_data(TEXT JSON), reconciliation_context\n"
                    + "row_data 是 TEXT 类型，必须先转 JSONB：row_data::jsonb->>'key'\n"
                    + "常用 table_name: extracted_data（主数据表）\n"
                    + "row_data 中的字段取决于 Schema，常见: name, role, technical_specialties, responsibilities_summary 等\n"
                    + "示例: SELECT row_data::jsonb->>'name' AS name, row_data::jsonb->>'role' AS role "
                    + "FROM agent_test.sliders_reconciled_table WHERE task_id = '{taskId}' AND table_name = 'extracted_data'"
    )
    public String executeSql(
            @ToolParam(name = "sql", description = "要执行的 SQL 查询语句") String sql,
            @ToolParam(name = "taskId", description = "任务 ID，用于日志记录") String taskId
    ) {
        log.info("[AnswerTools] execute_sql: {}", sql);

        // 安全校验：只允许 SELECT
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT")) {
            return "❌ 安全限制：只允许 SELECT 查询";
        }

        try {
            List<java.util.Map<String, Object>> rows = sqlExecutor.execute(sql);

            // 记录 SQL 日志（暂时关闭，减少数据库写入开销）
            // sqlLogSaver.save(SlidersSqlLogEntity.builder()
            //         .taskId(taskId)
            //         .sqlQuery(sql)
            //         .queryPurpose("数据查询")
            //         .resultSummary("返回 " + rows.size() + " 行")
            //         .isError(false)
            //         .build());

            if (rows.isEmpty()) {
                return "查询返回 0 行结果。";
            }

            // 截断大结果集
            List<java.util.Map<String, Object>> display = rows.size() > 100
                    ? rows.subList(0, 100) : rows;

            StringBuilder sb = new StringBuilder();
            sb.append("查询返回 ").append(rows.size()).append(" 行");
            if (rows.size() > 100) sb.append("（仅显示前 100 行）");
            sb.append("：\n\n");

            // 表格格式
            if (!display.isEmpty()) {
                java.util.Map<String, Object> first = display.get(0);
                List<String> cols = new java.util.ArrayList<>(first.keySet());
                sb.append("| ").append(String.join(" | ", cols)).append(" |\n");
                sb.append("| ").append(cols.stream().map(c -> "---").collect(java.util.stream.Collectors.joining(" | "))).append(" |\n");
                for (java.util.Map<String, Object> row : display) {
                    sb.append("| ");
                    for (int i = 0; i < cols.size(); i++) {
                        if (i > 0) sb.append(" | ");
                        Object val = row.get(cols.get(i));
                        sb.append(val == null ? "NULL" : String.valueOf(val));
                    }
                    sb.append(" |\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[AnswerTools] execute_sql 失败: {}", e.getMessage());

            // 记录错误日志（暂时关闭）
            // sqlLogSaver.save(SlidersSqlLogEntity.builder()
            //         .taskId(taskId)
            //         .sqlQuery(sql)
            //         .queryPurpose("数据查询")
            //         .resultSummary("错误: " + e.getMessage())
            //         .isError(true)
            //         .build());

            return "❌ SQL 执行失败: " + e.getMessage() + "\n请检查 SQL 语法。注意 row_data 是 TEXT 类型，必须用 row_data::jsonb->>'key' 格式查询。";
        }
    }

    @Tool(
            name = "save_answer",
            description = "保存最终答案到任务表。"
    )
    public String saveAnswer(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "answer", description = "最终答案（自然语言，附关键数据引用）") String answer
    ) {
        boolean success = answerSaver.save(taskId, answer);

        if (success) {
            log.info("Answer saved: taskId={}", taskId);

            return String.format("""
                    ✅ 答案已保存！
                    - 任务 ID: %s
                    任务完成。
                    """, taskId);
        } else {
            return "❌ 保存答案失败，任务可能不存在: " + taskId;
        }
    }
}
