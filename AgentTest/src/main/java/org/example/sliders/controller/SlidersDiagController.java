package org.example.sliders.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.log4j.Log4j2;
import org.example.graph.workflow.core.GraphPoolManager;
import org.example.sliders.entity.table.*;
import org.example.sliders.mapper.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/sliders-diag")
public class SlidersDiagController {

    private final GraphPoolManager graphPoolManager;
    private final SlidersTaskMapper taskMapper;
    private final SlidersDocumentMapper documentMapper;
    private final SlidersChunkMapper chunkMapper;
    private final SlidersSchemaMapper schemaMapper;
    private final SlidersExtractedRowMapper extractedRowMapper;
    private final SlidersReconciledTableMapper reconciledTableMapper;
    private final SlidersSqlLogMapper sqlLogMapper;
    private final org.springframework.context.ApplicationContext applicationContext;

    public SlidersDiagController(GraphPoolManager graphPoolManager,
                                 SlidersTaskMapper taskMapper,
                                 SlidersDocumentMapper documentMapper,
                                 SlidersChunkMapper chunkMapper,
                                 SlidersSchemaMapper schemaMapper,
                                 SlidersExtractedRowMapper extractedRowMapper,
                                 SlidersReconciledTableMapper reconciledTableMapper,
                                 SlidersSqlLogMapper sqlLogMapper,
                                 org.springframework.context.ApplicationContext applicationContext) {
        this.graphPoolManager = graphPoolManager;
        this.taskMapper = taskMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.schemaMapper = schemaMapper;
        this.extractedRowMapper = extractedRowMapper;
        this.reconciledTableMapper = reconciledTableMapper;
        this.sqlLogMapper = sqlLogMapper;
        this.applicationContext = applicationContext;
    }

    @GetMapping("/step1")
    public ResponseEntity<Map<String, Object>> step1_stateGraphLoad() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Class<?> clazz = Class.forName("com.alibaba.cloud.ai.graph.StateGraph");
            result.put("ok", true);
            result.put("class", clazz.getName());
            result.put("classLoader", clazz.getClassLoader().getClass().getName());
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
            if (t.getCause() != null) {
                result.put("cause", t.getCause().getClass().getName() + ": " + t.getCause().getMessage());
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/step2")
    public ResponseEntity<Map<String, Object>> step2_overAllState() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            OverAllState state = new OverAllState();
            state.updateState(Map.of("taskId", "test-001"));
            result.put("ok", true);
            result.put("taskId", state.value("taskId").orElse("MISSING"));
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/step3")
    public ResponseEntity<Map<String, Object>> step3_getGraph() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var graph = graphPoolManager.getGraph("sliders-pipeline");
            result.put("ok", true);
            result.put("graphClass", graph.getClass().getName());
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
            if (t.getCause() != null) {
                result.put("cause", t.getCause().getClass().getName() + ": " + t.getCause().getMessage());
            }
            result.put("trace", java.util.Arrays.stream(t.getStackTrace())
                    .limit(20).map(Object::toString).toList());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/step4")
    public ResponseEntity<Map<String, Object>> step4_invokeGraph(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        String taskId = body.getOrDefault("taskId", "diag-test-001");
        try {
            OverAllState initialState = new OverAllState();
            initialState.updateState(Map.of("taskId", taskId));
            log.info("[DIAG] step4 开始，taskId={}", taskId);
            OverAllState output = graphPoolManager.invokeGraph(
                    "sliders-pipeline", initialState, "diag-" + taskId);
            log.info("[DIAG] step4 完毕，taskId={}", taskId);
            result.put("ok", true);
            result.put("taskId", taskId);
            result.put("outputKeys", output.data().keySet());
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
            if (t.getCause() != null) {
                result.put("cause", t.getCause().getClass().getName() + ": " + t.getCause().getMessage());
            }
            result.put("trace", java.util.Arrays.stream(t.getStackTrace())
                    .limit(20).map(Object::toString).toList());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Step 5: 查看指定任务在各表中的数据量
     */
    @GetMapping("/step5/{taskId}")
    public ResponseEntity<Map<String, Object>> step5_checkData(@PathVariable String taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var task = taskMapper.selectOneByQuery(
                    QueryWrapper.create().where(SlidersTaskEntityTableDef.SLIDERS_TASK_ENTITY.TASK_ID.eq(taskId)));
            result.put("task", task != null
                    ? Map.of("status", task.getStatus(),
                            "answer", task.getAnswer() != null
                                    ? task.getAnswer().substring(0, Math.min(200, task.getAnswer().length()))
                                    : "null")
                    : "NOT_FOUND");

            result.put("documents", documentMapper.selectCountByQuery(
                    QueryWrapper.create().where(SlidersDocumentEntityTableDef.SLIDERS_DOCUMENT_ENTITY.TASK_ID.eq(taskId))));
            result.put("chunks", chunkMapper.selectCountByQuery(
                    QueryWrapper.create().where(SlidersChunkEntityTableDef.SLIDERS_CHUNK_ENTITY.TASK_ID.eq(taskId))));
            result.put("schema", schemaMapper.selectCountByQuery(
                    QueryWrapper.create().where(SlidersSchemaEntityTableDef.SLIDERS_SCHEMA_ENTITY.TASK_ID.eq(taskId))));
            result.put("extractedRows", extractedRowMapper.selectCountByQuery(
                    QueryWrapper.create().where(SlidersExtractedRowEntityTableDef.SLIDERS_EXTRACTED_ROW_ENTITY.TASK_ID.eq(taskId))));
            result.put("reconciledTables", reconciledTableMapper.selectCountByQuery(
                    QueryWrapper.create().where(SlidersReconciledTableEntityTableDef.SLIDERS_RECONCILED_TABLE_ENTITY.TASK_ID.eq(taskId))));
            result.put("sqlLogs", sqlLogMapper.selectCountByQuery(
                    QueryWrapper.create().where(SlidersSqlLogEntityTableDef.SLIDERS_SQL_LOG_ENTITY.TASK_ID.eq(taskId))));
            result.put("ok", true);
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Step 6: 查看指定任务的 SQL 日志详情
     */
    @GetMapping("/step6/{taskId}")
    public ResponseEntity<Map<String, Object>> step6_sqlLogs(@PathVariable String taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var logs = sqlLogMapper.selectListByQuery(
                    QueryWrapper.create().where(SlidersSqlLogEntityTableDef.SLIDERS_SQL_LOG_ENTITY.TASK_ID.eq(taskId)));
            result.put("count", logs.size());
            result.put("logs", logs.stream().map(log -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("sql", log.getSqlQuery());
                m.put("purpose", log.getQueryPurpose());
                m.put("result", log.getResultSummary());
                m.put("isError", log.getIsError());
                m.put("createdAt", log.getCreatedAt());
                return m;
            }).toList());
            result.put("ok", true);
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Step 7: 查看指定任务的协调数据详情
     */
    @GetMapping("/step7/{taskId}")
    public ResponseEntity<Map<String, Object>> step7_reconciledData(@PathVariable String taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var tables = reconciledTableMapper.selectListByQuery(
                    QueryWrapper.create().where(SlidersReconciledTableEntityTableDef.SLIDERS_RECONCILED_TABLE_ENTITY.TASK_ID.eq(taskId)));
            result.put("count", tables.size());
            result.put("data", tables.stream().map(t -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("tableName", t.getTableName());
                m.put("rowIndex", t.getRowIndex());
                m.put("rowData", t.getRowData());
                return m;
            }).toList());
            result.put("ok", true);
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Step 8: 修复 JSONB 列类型为 TEXT（MyBatis-Flex 兼容）
     */
    @PostMapping("/step8-fix-jsonb")
    public ResponseEntity<Map<String, Object>> step8_fixJsonb() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            javax.sql.DataSource ds = org.example.sliders.config.DataSourceProvider.getDataSource();
            try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
                String[] sqls = {
                    "ALTER TABLE agent_test.sliders_schema ALTER COLUMN schema_json TYPE TEXT",
                    "ALTER TABLE agent_test.sliders_schema ALTER COLUMN reasoning TYPE TEXT",
                    "ALTER TABLE agent_test.sliders_extracted_row ALTER COLUMN field_data TYPE TEXT",
                    "ALTER TABLE agent_test.sliders_extracted_row ALTER COLUMN metadata TYPE TEXT",
                    "ALTER TABLE agent_test.sliders_reconciled_table ALTER COLUMN row_data TYPE TEXT",
                    "ALTER TABLE agent_test.sliders_reconciled_table ALTER COLUMN provenance TYPE TEXT",
                    "ALTER TABLE agent_test.sliders_chunk ALTER COLUMN metadata TYPE TEXT"
                };
                java.util.List<String> results = new java.util.ArrayList<>();
                for (String sql : sqls) {
                    try {
                        stmt.execute(sql);
                        results.add("✅ " + sql);
                    } catch (Exception e) {
                        results.add("⚠️ " + sql + " → " + e.getMessage());
                    }
                }
                result.put("results", results);
                result.put("ok", true);
            }
        } catch (Throwable t) {
            result.put("ok", false);
            result.put("error", t.getClass().getName());
            result.put("message", t.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * step9 — 诊断 Hook Bean 是否存在
     */
    @GetMapping("/step9-hook-check")
    public ResponseEntity<Map<String, Object>> step9_hookCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 检查 SlidersPipelineTrackerHook Bean
            String[] beanNames = applicationContext.getBeanNamesForType(
                    org.example.sliders.hook.SlidersPipelineTrackerHook.class);
            result.put("hookBeanNames", beanNames);
            result.put("hookBeanCount", beanNames.length);

            if (beanNames.length > 0) {
                var hook = applicationContext.getBean(beanNames[0], org.example.sliders.hook.SlidersPipelineTrackerHook.class);
                result.put("hookClass", hook.getClass().getName());
                result.put("hookActive", true);
            }

            // 列出所有 Hook 相关 Bean
            String[] allHookBeans = applicationContext.getBeanNamesForType(io.agentscope.core.hook.Hook.class);
            result.put("allHookBeans", allHookBeans);
            result.put("allHookBeanCount", allHookBeans.length);

            result.put("ok", true);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getClass().getName());
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
