package org.example.sliders.agent;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.sliders.entity.SlidersReconciledTableEntity;
import org.example.sliders.entity.SlidersSqlLogEntity;
import org.example.sliders.entity.SlidersTaskEntity;
import org.example.sliders.mapper.SlidersReconciledTableMapper;
import org.example.sliders.mapper.SlidersSqlLogMapper;
import org.example.sliders.service.SlidersTaskService;
import org.example.sliders.skill.SlidersSkillFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.example.sliders.entity.table.SlidersReconciledTableEntityTableDef.SLIDERS_RECONCILED_TABLE_ENTITY;

/**
 * SQL 问答 Agent — 基于协调数据回答用户问题
 */
@Log4j2
@AgentDefinition(
        name = "SlidersAnswer",
        hooksType = "sliders",
        enableMemory = true,
        enablePersistence = true,
        maxIters = 30,
        description = "SQL 问答 Agent — 基于协调数据回答用户问题"
)
public class SlidersAnswerAgent extends AbstractAgentTemplate {

    @Autowired
    private SlidersTaskService taskService;
    @Autowired
    private SlidersReconciledTableMapper reconciledTableMapper;
    @Autowired
    private SlidersSqlLogMapper sqlLogMapper;

    public SlidersAnswerAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return """
                你是 SLIDERS 流水线的最终问答 Agent（Answer）。
                
                ## 工作流程（2 步完成）
                1. execute_sql(sql, taskId) — 直接写 SQL 查询数据（问题已在 prompt 中给出，表结构已在工具描述中）
                2. save_answer(taskId, answer) — 保存最终答案
                
                ## 关键规则
                - **禁止调用 read_task_question**（问题已在 prompt 中）
                - **禁止调用 describe_tables**（表结构已在 execute_sql 工具描述中）
                - **禁止调用 read_reconciled_data**（太慢）
                - 一次 SQL 搞定，不要分多次查询
                
                ## SQL 查询指南
                - 表：agent_test.sliders_reconciled_table
                - 字段：task_id, table_name, row_index, row_data(TEXT JSON), reconciliation_context
                - row_data 是 TEXT 类型，查询时必须先转 JSONB：row_data::jsonb->>'字段名'
                - 常用 table_name: extracted_data
                
                ## 回答原则
                - 回答紧扣原始问题，引用具体数据
                - 使用结构化格式（列表/表格）组织答案
                """;
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected SkillBox setupSkills() {
        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        SlidersSkillFactory.createAnswerSkill(),
                        SlidersSkillFactory.createAnswerTools(
                                // TaskReader: SlidersTaskEntity read(String taskId)
                                (String taskId) -> taskService.getByTaskId(taskId),
                                // ReconciledDataReader: List<SlidersReconciledTableEntity> read(String taskId)
                                (String taskId) -> reconciledTableMapper.selectListByQuery(
                                        QueryWrapper.create().where(SLIDERS_RECONCILED_TABLE_ENTITY.TASK_ID.eq(taskId))),
                                // SqlExecutor: List<Map<String,Object>> execute(String sql)
                                (String sql) -> {
                                    var ds = org.example.sliders.config.DataSourceProvider.getDataSource();
                                    try (var conn = ds.getConnection();
                                         var stmt = conn.createStatement();
                                         var rs = stmt.executeQuery(sql)) {
                                        var meta = rs.getMetaData();
                                        int colCount = meta.getColumnCount();
                                        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
                                        while (rs.next()) {
                                            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                                            for (int i = 1; i <= colCount; i++) {
                                                row.put(meta.getColumnLabel(i), rs.getObject(i));
                                            }
                                            results.add(row);
                                        }
                                        return results;
                                    } catch (java.sql.SQLException e) {
                                        throw new RuntimeException(e.getMessage(), e);
                                    }
                                },
                                // SqlLogSaver: void save(SlidersSqlLogEntity entity)
                                (SlidersSqlLogEntity entity) -> sqlLogMapper.insert(entity),
                                // AnswerSaver: boolean save(String taskId, String answer)
                                (String taskId, String answer) -> {
                                    taskService.updateAnswer(taskId, answer);
                                    return true;
                                },
                                // StatusUpdater: boolean update(String taskId, String status)
                                (String taskId, String status) -> {
                                    taskService.updateStatus(taskId, status);
                                    return true;
                                }
                        )
                )
                .buildSkillBox();
    }
}
