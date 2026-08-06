package org.example.sliders.agent;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.sliders.entity.SlidersChunkEntity;
import org.example.sliders.entity.SlidersExtractedRowEntity;
import org.example.sliders.entity.SlidersSchemaEntity;
import org.example.sliders.mapper.SlidersChunkMapper;
import org.example.sliders.mapper.SlidersExtractedRowMapper;
import org.example.sliders.mapper.SlidersSchemaMapper;
import org.example.sliders.service.SlidersTaskService;
import org.example.sliders.skill.SlidersSkillFactory;
import org.example.sliders.tools.BatchExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static org.example.sliders.entity.table.SlidersChunkEntityTableDef.SLIDERS_CHUNK_ENTITY;
import static org.example.sliders.entity.table.SlidersSchemaEntityTableDef.SLIDERS_SCHEMA_ENTITY;

/**
 * 结构化提取 Agent — 按 Schema 从文档块中提取结构化数据
 */
@Log4j2
@AgentDefinition(
        name = "SlidersExtractor",
        hooksType = "sliders",
        enableMemory = true,
        enablePersistence = true,
        maxIters = 50,
        description = "结构化提取 Agent — 按 Schema 从文档块中提取结构化数据"
)
public class SlidersExtractorAgent extends AbstractAgentTemplate {

    @Autowired
    private SlidersTaskService taskService;
    @Autowired
    private SlidersSchemaMapper schemaMapper;
    @Autowired
    private SlidersChunkMapper chunkMapper;
    @Autowired
    private SlidersExtractedRowMapper extractedRowMapper;
    @Value("${qwen.apiKey:}")
    private String dashscopeApiKey;
    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String dashscopeModel;

    public SlidersExtractorAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return """
                你是 SLIDERS 流水线的结构化提取 Agent（Extractor）。
                
                ## 你的职责
                1. 调用 read_schema_and_chunks 获取 Schema 和文档块信息
                2. 调用 batch_extract 并发提取所有文档块的数据（推荐，速度快 4-8 倍）
                3. batch_extract 会自动保存结果，完成后回复"提取完成"
                
                ## 工作流程
                1. 先调用 read_schema_and_chunks(taskId) 查看 Schema 和文档概况
                2. 再调用 batch_extract(taskId, question) 并发提取所有数据
                3. 完成后回复"提取完成"
                
                ## 重要规则
                - 必须先调用 read_schema_and_chunks，再调用 batch_extract
                - batch_extract 的 question 参数从系统消息中的"用户问题"获取
                - batch_extract 完成后自动保存，不需要再调用 save_extracted_rows
                """;
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected SkillBox setupSkills() {
        // 创建并发提取器（4 并发）
        BatchExtractor batchExtractor = new BatchExtractor(dashscopeApiKey, dashscopeModel, 4);

        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        SlidersSkillFactory.createExtractorSkill(),
                        SlidersSkillFactory.createExtractorTools(
                                // SchemaReader
                                (String taskId) -> schemaMapper.selectListByQuery(
                                        QueryWrapper.create().where(SLIDERS_SCHEMA_ENTITY.TASK_ID.eq(taskId))),
                                // ChunkReader
                                (String taskId) -> chunkMapper.selectListByQuery(
                                        QueryWrapper.create().where(SLIDERS_CHUNK_ENTITY.TASK_ID.eq(taskId))),
                                // ExtractedRowSaver
                                (List<SlidersExtractedRowEntity> rows) -> {
                                    for (SlidersExtractedRowEntity row : rows) {
                                        extractedRowMapper.insert(row);
                                    }
                                },
                                // StatusUpdater
                                (String taskId, String status) -> {
                                    taskService.updateStatus(taskId, status);
                                    return true;
                                },
                                // BatchExtractor
                                batchExtractor
                        )
                )
                .buildSkillBox();
    }
}
