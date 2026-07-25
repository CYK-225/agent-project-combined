package org.example.repository.dal.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;
import org.example.repository.dal.entity.AgentTaskEntity;
import org.example.repository.dal.mapper.AgentTaskMapper;
import org.example.repository.dal.service.IAgentTaskService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

import static org.example.repository.dal.entity.AgentTaskEntity.Status;

/**
 * Agent任务 Service 实现类
 * <p>
 * 继承 ServiceImpl，实现业务方法
 * </p>
 */
@Slf4j
@Service
public class AgentTaskServiceImpl extends ServiceImpl<AgentTaskMapper, AgentTaskEntity> 
        implements IAgentTaskService {

    /**
     * 根据任务ID查询
     */
    @Override
    public AgentTaskEntity selectByTaskId(String taskId) {
        log.debug("[AgentTaskService] 根据任务ID查询，taskId: {}", taskId);
        
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(AgentTaskEntity.class)
                .where(AgentTaskEntity::getTaskId).eq(taskId);
        
        return getOne(queryWrapper);
    }

    /**
     * 根据状态查询任务列表
     */
    @Override
    public List<AgentTaskEntity> selectByStatus(String status) {
        log.debug("[AgentTaskService] 根据状态查询，status: {}", status);
        
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(AgentTaskEntity.class)
                .where(AgentTaskEntity::getStatus).eq(status)
                .orderBy(AgentTaskEntity::getCreatedAt).desc();
        
        return list(queryWrapper);
    }

    /**
     * 更新状态
     */
    @Override
    public boolean updateStatus(String taskId, String status, String updatedBy) {
        log.info("[AgentTaskService] 更新状态，taskId: {}, status: {}", taskId, status);
        
        AgentTaskEntity task = selectByTaskId(taskId);
        if (task == null) {
            log.warn("[AgentTaskService] 任务不存在，taskId: {}", taskId);
            return false;
        }
        
        task.setStatus(status);
        task.setUpdatedBy(updatedBy);
        task.setUpdatedAt(new Date());
        
        return updateById(task);
    }

    // updateModelOutput()、updateLog() 已废弃（字段迁移至 agent_execution_detail 表）

    /**
     * 标记任务开始
     */
    @Override
    public boolean markTaskStarted(String taskId, String updatedBy) {
        log.info("[AgentTaskService] 标记任务开始，taskId: {}", taskId);
        
        AgentTaskEntity task = selectByTaskId(taskId);
        if (task == null) {
            log.warn("[AgentTaskService] 任务不存在，taskId: {}", taskId);
            return false;
        }
        
        task.setStatus(Status.RUNNING);
        task.setStartedAt(new Date());
        task.setUpdatedBy(updatedBy);
        task.setUpdatedAt(new Date());
        
        return updateById(task);
    }

    /**
     * 标记任务完成（状态 → SUCCESS）
     */
    @Override
    public boolean markTaskCompleted(String taskId, String updatedBy) {
        log.info("[AgentTaskService] 标记任务完成，taskId: {}", taskId);
        
        AgentTaskEntity task = selectByTaskId(taskId);
        if (task == null) {
            log.warn("[AgentTaskService] 任务不存在，taskId: {}", taskId);
            return false;
        }
        
        task.setStatus(Status.SUCCESS);
        task.setCompletedAt(new Date());
        task.setUpdatedBy(updatedBy);
        task.setUpdatedAt(new Date());
        
        return updateById(task);
    }

    /**
     * 标记任务失败
     */
    @Override
    public boolean markTaskFailed(String taskId, String errorMessage, String updatedBy) {
        log.info("[AgentTaskService] 标记任务失败，taskId: {}", taskId);
        
        AgentTaskEntity task = selectByTaskId(taskId);
        if (task == null) {
            log.warn("[AgentTaskService] 任务不存在，taskId: {}", taskId);
            return false;
        }
        
        task.setStatus(Status.FAILED);
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(new Date());
        task.setUpdatedBy(updatedBy);
        task.setUpdatedAt(new Date());
        
        return updateById(task);
    }

    @Override
    public AgentTaskEntity toEntity(AgentTaskNotifyDTO dto) {
        AgentTaskEntity entity = AgentTaskEntity.builder()
                .agentName(dto.getAgentName())
                .taskId(dto.getTaskId())
                .sessionId(dto.getSessionId())
                .instruction(dto.getInstruction())
                .promptsId(dto.getPromptsId())
                .promptType(dto.getPromptType())
                .callbackUrl(dto.getCallbackUrl())
                .aiCallbackUrl(dto.getAiCallbackUrl())
                .status(dto.getStatus())
                .errorMessage(dto.getErrorMessage())
                .success(dto.getSuccess())
                .result(dto.getResult())
                .build();
        return entity;
    }

    // markTaskSuspended / markTaskResumed / markTaskCancelled 已废弃
    // 状态只有 RUNNING / SUCCESS / FAILED，挂起是 Agent 内部行为，不体现在 agent_task 状态中

    /**
     * 更新工具执行结果
     */
    // updateToolResult 已废弃（字段迁移至 agent_execution_detail 表）
}
