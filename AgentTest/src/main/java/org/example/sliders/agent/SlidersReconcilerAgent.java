package org.example.sliders.agent;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.sliders.entity.SlidersExtractedRowEntity;
import org.example.sliders.entity.SlidersReconciledTableEntity;
import org.example.sliders.mapper.SlidersExtractedRowMapper;
import org.example.sliders.mapper.SlidersReconciledTableMapper;
import org.example.sliders.service.SlidersTaskService;
import org.example.sliders.skill.SlidersSkillFactory;
import org.example.sliders.tools.BatchReconciler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static org.example.sliders.entity.table.SlidersExtractedRowEntityTableDef.SLIDERS_EXTRACTED_ROW_ENTITY;

/**
 * 数据协调 Agent — 去重、消歧、合并提取数据
 */
@Log4j2
@AgentDefinition(
        name = "SlidersReconciler",
        hooksType = "sliders",
        enableMemory = true,
        enablePersistence = true,
        maxIters = 50,
        description = "数据协调 Agent — 去重、消歧、合并提取数据"
)
public class SlidersReconcilerAgent extends AbstractAgentTemplate {

    @Autowired
    private SlidersTaskService taskService;
    @Autowired
    private SlidersExtractedRowMapper extractedRowMapper;
    @Autowired
    private SlidersReconciledTableMapper reconciledTableMapper;
    @Value("${qwen.apiKey:}")
    private String dashscopeApiKey;
    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String dashscopeModel;

    public SlidersReconcilerAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return """
                你是 SLIDERS 流水线的数据协调 Agent（Reconciler）。
                
                ## 你的职责
                1. 调用 read_extracted_rows 查看所有提取数据概况
                2. 调用 batch_reconcile 并发协调所有表的数据（推荐，速度快 3-5 倍）
                3. batch_reconcile 会自动保存结果，完成后回复"协调完成"
                
                ## 工作流程
                1. 先调用 read_extracted_rows(taskId) 查看提取数据概况
                2. 再调用 batch_reconcile(taskId, question) 并发协调所有表
                3. 完成后回复"协调完成"
                
                ## 重要规则
                - 必须先调用 read_extracted_rows，再调用 batch_reconcile
                - batch_reconcile 的 question 参数从系统消息中的"用户问题"获取
                - batch_reconcile 完成后自动保存，不需要再调用 save_reconciled_table
                """;
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected SkillBox setupSkills() {
        // 创建并发协调器（4 并发）
        BatchReconciler batchReconciler = new BatchReconciler(dashscopeApiKey, dashscopeModel, 4);

        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        SlidersSkillFactory.createReconcilerSkill(),
                        SlidersSkillFactory.createReconcilerTools(
                                // ExtractedRowReader
                                (String taskId) -> extractedRowMapper.selectListByQuery(
                                        QueryWrapper.create().where(SLIDERS_EXTRACTED_ROW_ENTITY.TASK_ID.eq(taskId))),
                                // ReconciledTableSaver
                                (List<SlidersReconciledTableEntity> tables) -> {
                                    for (SlidersReconciledTableEntity t : tables) {
                                        reconciledTableMapper.insert(t);
                                    }
                                },
                                // StatusUpdater
                                (String taskId, String status) -> {
                                    taskService.updateStatus(taskId, status);
                                    return true;
                                },
                                // BatchReconciler
                                batchReconciler
                        )
                )
                .buildSkillBox();
    }
}
