package org.example.repository.dal.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;
import org.example.repository.dal.entity.AgentExecutionDetailEntity;
import org.example.repository.dal.mapper.AgentExecutionDetailMapper;
import org.example.repository.dal.service.IAgentExecutionDetailService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 执行明细 Service 实现类
 * <p>
 * 继承 ServiceImpl，实现业务方法
 * </p>
 */
@Slf4j
@Service
public class AgentExecutionDetailServiceImpl
        extends ServiceImpl<AgentExecutionDetailMapper, AgentExecutionDetailEntity>
        implements IAgentExecutionDetailService {

    /**
     * 根据任务 ID 查询所有执行明细（按 step 排序）
     */
    @Override
    public List<AgentExecutionDetailEntity> selectByTaskId(String taskId) {
        log.debug("[AgentExecutionDetailService] 根据任务 ID 查询执行明细，taskId: {}", taskId);

        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(AgentExecutionDetailEntity.class)
                .where(AgentExecutionDetailEntity::getTaskId).eq(taskId)
                .orderBy(AgentExecutionDetailEntity::getStep).asc()
                .orderBy(AgentExecutionDetailEntity::getId).asc();

        return list(queryWrapper);
    }

    /**
     * 根据任务 ID 和步骤编号查询执行明细
     */
    @Override
    public List<AgentExecutionDetailEntity> selectByTaskIdAndStep(String taskId, Integer step) {
        log.debug("[AgentExecutionDetailService] 根据任务 ID 和步骤查询，taskId: {}, step: {}", taskId, step);

        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(AgentExecutionDetailEntity.class)
                .where(AgentExecutionDetailEntity::getTaskId).eq(taskId)
                .and(AgentExecutionDetailEntity::getStep).eq(step)
                .orderBy(AgentExecutionDetailEntity::getId).asc();

        return list(queryWrapper);
    }

    /**
     * 根据任务 ID 删除所有执行明细
     */
    @Override
    public boolean deleteByTaskId(String taskId) {
        log.info("[AgentExecutionDetailService] 删除任务的所有执行明细，taskId: {}", taskId);

        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(AgentExecutionDetailEntity.class)
                .where(AgentExecutionDetailEntity::getTaskId).eq(taskId);

        return remove(queryWrapper);
    }

    @Override
    public AgentExecutionDetailEntity toEntity(AgentTaskNotifyDTO dto) {
        AgentExecutionDetailEntity entity = AgentExecutionDetailEntity.builder()
                .taskId(dto.getTaskId())
                .sessionId(dto.getSessionId())
                .step(dto.getStep())
                .toolStep(dto.getToolStep())
                .modelOutput(dto.getModelOutput())
                .toolName(dto.getToolName())
                .toolInput(dto.getToolInput())
                .screenshotPath(dto.getScreenshotPath())
                .outputResult(dto.getOutputResult())
                .build();
        return entity;
    }
}
