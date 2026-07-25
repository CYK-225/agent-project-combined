package org.example.sliders.agent;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.sliders.entity.SlidersChunkEntity;
import org.example.sliders.entity.SlidersSchemaEntity;
import org.example.sliders.entity.SlidersTaskEntity;
import org.example.sliders.mapper.SlidersChunkMapper;
import org.example.sliders.mapper.SlidersSchemaMapper;
import org.example.sliders.service.SlidersTaskService;
import org.example.sliders.skill.SlidersSkillFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.example.sliders.entity.table.SlidersChunkEntityTableDef.SLIDERS_CHUNK_ENTITY;

/**
 * Schema 归纳 Agent — 根据问题和文档类型推导关系型 Schema
 */
@Log4j2
@AgentDefinition(
        name = "SlidersSchemaAgent",
        hooksType = "sliders",
        enableMemory = true,
        enablePersistence = true,
        maxIters = 20,
        description = "Schema 归纳 Agent — 根据问题和文档类型推导关系型 Schema"
)
public class SlidersSchemaAgent extends AbstractAgentTemplate {

    @Autowired
    private SlidersTaskService taskService;
    @Autowired
    private SlidersChunkMapper chunkMapper;
    @Autowired
    private SlidersSchemaMapper schemaMapper;

    public SlidersSchemaAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return """
                你是 SLIDERS 流水线的 Schema 归纳 Agent。
                
                ## 你的职责
                1. 调用 read_task_and_chunks 获取任务问题和块摘要
                2. 分析问题类型和文档结构，设计关系型 Schema
                3. **必须调用 save_schema 保存 Schema**（这是你最重要的任务，不保存则后续流程无法进行）
                4. 保存成功后回复"Schema 归纳完成"
                
                ## 重要规则
                - 你必须调用 save_schema 工具保存 Schema，不能只输出文本
                - Schema 设计完成后立即保存，不要跳过这一步
                
                ## Schema 设计原则
                - 每个字段都有 extraction_guideline（提取指南）
                - 主键字段标记 required=true
                - 标注数据类型：STRING / NUMBER / DATE / BOOLEAN
                - Schema 足够精确，避免提取歧义
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
                        SlidersSkillFactory.createSchemaSkill(),
                        SlidersSkillFactory.createSchemaTools(
                                // TaskReader: SlidersTaskEntity read(String taskId)
                                (String taskId) -> taskService.getByTaskId(taskId),
                                // ChunkReader: List<SlidersChunkEntity> read(String taskId)
                                (String taskId) -> chunkMapper.selectListByQuery(
                                        QueryWrapper.create().where(SLIDERS_CHUNK_ENTITY.TASK_ID.eq(taskId))),
                                // SchemaSaver: void save(SlidersSchemaEntity entity)
                                (SlidersSchemaEntity entity) -> schemaMapper.insert(entity),
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
