package org.example.sliders.agent;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.sliders.entity.SlidersChunkEntity;
import org.example.sliders.entity.SlidersDocumentEntity;
import org.example.sliders.mapper.SlidersChunkMapper;
import org.example.sliders.mapper.SlidersDocumentMapper;
import org.example.sliders.service.SlidersTaskService;
import org.example.sliders.skill.SlidersSkillFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.example.sliders.entity.table.SlidersDocumentEntityTableDef.SLIDERS_DOCUMENT_ENTITY;

/**
 * 文档分块 Agent — 将文档切分为语义完整的块
 */
@Log4j2
@AgentDefinition(
        name = "SlidersChunker",
        hooksType = "sliders",
        enableMemory = true,
        enablePersistence = true,
        maxIters = 20,
        description = "文档分块 Agent — 将文档切分为语义完整的块"
)
public class SlidersChunkerAgent extends AbstractAgentTemplate {

    @Autowired
    private SlidersTaskService taskService;
    @Autowired
    private SlidersDocumentMapper documentMapper;
    @Autowired
    private SlidersChunkMapper chunkMapper;

    public SlidersChunkerAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return """
                你是 SLIDERS 流水线的文档分块 Agent（Chunker）。

                ## 执行步骤（必须严格按顺序执行，不可跳过）
                1. 调用 read_task_documents 读取任务文档
                2. 对每个文档，必须调用 chunk_document 工具执行程序化分块（targetChunkSize=3000）
                3. 确认分块结果后回复"分块完成"

                ## 重要规则
                - 你必须调用 chunk_document 工具，不能手动分块或跳过分块步骤
                - 默认块大小 3000 字符（targetChunkSize=3000）
                - 每块保持局部自包含，表格不跨块切割
                - 如果只有一个文档，对它调用一次 chunk_document 即可
                - 如果有多个文档，对每个文档分别调用 chunk_document
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
                        SlidersSkillFactory.createChunkerSkill(),
                        SlidersSkillFactory.createChunkerTools(
                                // DocumentReader: List<SlidersDocumentEntity> readByTaskId(String)
                                (String taskId) -> documentMapper.selectListByQuery(
                                        QueryWrapper.create().where(SLIDERS_DOCUMENT_ENTITY.TASK_ID.eq(taskId))),
                                // ChunkSaver: void saveAll(String taskId, List<SlidersChunkEntity>)
                                (String taskId, List<SlidersChunkEntity> chunks) -> {
                                    for (SlidersChunkEntity chunk : chunks) {
                                        chunkMapper.insert(chunk);
                                    }
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
