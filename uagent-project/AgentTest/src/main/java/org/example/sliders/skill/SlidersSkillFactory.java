package org.example.sliders.skill;

import io.agentscope.core.skill.AgentSkill;
import org.example.sliders.service.SlidersTaskService;
import org.example.sliders.tools.*;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;

/**
 * SLIDERS 各 Agent 的 Skill + Tool 工厂。
 * <p>
 * 纯静态工厂，产出 {@code AgentSkill + Object[]}，
 * 由 Agent Template 的 {@code addSkillWithTools} 消费。
 * <p>
 * 使用示例（在 Agent Template 中）：
 * <pre>
 * // Chunker Agent
 * components.skillBox().create(getToolkit())
 *     .addSkillWithTools(
 *         SlidersSkillFactory.createChunkerSkill(),
 *         SlidersSkillFactory.createChunkerTools(...)
 *     )
 *     .buildSkillBox()
 * </pre>
 */
public class SlidersSkillFactory {

    private SlidersSkillFactory() {
    }

    // ======================== Master ========================

    public static AgentSkill createMasterSkill() {
        return createBaseAgentSkill(
                "sliders_master",
                "SLIDERS 流水线任务管理",
                "你是 SLIDERS 流水线协调者，负责任务创建、状态查询、Agent 路由。"
        );
    }

    public static Object[] createMasterTools(
            MasterTools.DocumentSaver documentSaver,
            SlidersTaskService taskService
    ) {
        return new Object[]{new MasterTools(taskService, documentSaver)};
    }

    // ======================== Chunker ========================

    public static AgentSkill createChunkerSkill() {
        return createBaseAgentSkill(
                "sliders_chunker",
                "文档分块",
                "你是文档分块 Agent，负责读取文档、按语义边界切分、保存分块结果。"
        );
    }

    public static Object[] createChunkerTools(
            ChunkerTools.DocumentReader documentReader,
            ChunkerTools.ChunkSaver chunkSaver,
            ChunkerTools.StatusUpdater statusUpdater
    ) {
        return new Object[]{new ChunkerTools(documentReader, chunkSaver, statusUpdater)};
    }

    // ======================== Schema ========================

    public static AgentSkill createSchemaSkill() {
        return createBaseAgentSkill(
                "sliders_schema",
                "Schema 归纳",
                "你是 Schema 归纳 Agent，负责分析问题类型和文档结构，设计关系型 Schema。"
        );
    }

    public static Object[] createSchemaTools(
            SchemaTools.TaskReader taskReader,
            SchemaTools.ChunkReader chunkReader,
            SchemaTools.SchemaSaver schemaSaver,
            SchemaTools.StatusUpdater statusUpdater
    ) {
        return new Object[]{new SchemaTools(taskReader, chunkReader, schemaSaver, statusUpdater)};
    }

    // ======================== Extractor ========================

    public static AgentSkill createExtractorSkill() {
        return createBaseAgentSkill(
                "sliders_extractor",
                "结构化提取",
                "你是结构化提取 Agent，按 Schema 从文档块提取结构化数据，带相关性门控。"
        );
    }

    public static Object[] createExtractorTools(
            ExtractorTools.SchemaReader schemaReader,
            ExtractorTools.ChunkReader chunkReader,
            ExtractorTools.ExtractedRowSaver rowSaver,
            ExtractorTools.StatusUpdater statusUpdater,
            BatchExtractor batchExtractor
    ) {
        return new Object[]{new ExtractorTools(schemaReader, chunkReader, rowSaver, statusUpdater, batchExtractor)};
    }

    // ======================== Reconciler ========================

    public static AgentSkill createReconcilerSkill() {
        return createBaseAgentSkill(
                "sliders_reconciler",
                "数据协调",
                "你是数据协调 Agent，负责去重、消歧、合并提取数据为干净的结构化表。"
        );
    }

    public static Object[] createReconcilerTools(
            ReconcilerTools.ExtractedRowReader rowReader,
            ReconcilerTools.ReconciledTableSaver tableSaver,
            ReconcilerTools.StatusUpdater statusUpdater,
            BatchReconciler batchReconciler
    ) {
        return new Object[]{new ReconcilerTools(rowReader, tableSaver, statusUpdater, batchReconciler)};
    }

    // ======================== Answer ========================

    public static AgentSkill createAnswerSkill() {
        return createBaseAgentSkill(
                "sliders_answer",
                "SQL 问答",
                "你是 SQL 问答 Agent，基于协调数据生成 SQL 查询，输出自然语言答案。"
        );
    }

    public static Object[] createAnswerTools(
            AnswerTools.TaskReader taskReader,
            AnswerTools.ReconciledDataReader dataReader,
            AnswerTools.SqlExecutor sqlExecutor,
            AnswerTools.SqlLogSaver sqlLogSaver,
            AnswerTools.AnswerSaver answerSaver,
            AnswerTools.StatusUpdater statusUpdater
    ) {
        return new Object[]{new AnswerTools(taskReader, dataReader, sqlExecutor, sqlLogSaver, answerSaver, statusUpdater)};
    }
}
