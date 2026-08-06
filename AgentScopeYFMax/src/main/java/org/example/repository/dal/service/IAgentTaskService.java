package org.example.repository.dal.service;

import com.mybatisflex.core.service.IService;
import org.example.repository.dal.entity.AgentTaskEntity;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;

import java.util.List;

/**
 * Agent任务 Service 接口
 * <p>
 * 继承 IService，提供业务方法
 * </p>
 */
public interface IAgentTaskService extends IService<AgentTaskEntity> {

    /**
     * 根据任务ID查询
     * 
     * @param taskId 任务ID
     * @return 任务实体
     */
    AgentTaskEntity selectByTaskId(String taskId);

    /**
     * 根据状态查询任务列表
     * 
     * @param status 状态
     * @return 任务列表
     */
    List<AgentTaskEntity> selectByStatus(String status);

    /**
     * 更新状态
     * 
     * @param taskId 任务ID
     * @param status 状态
     * @param updatedBy 修改者
     * @return 是否成功
     */
    boolean updateStatus(String taskId, String status, String updatedBy);

    // 以下方法已废弃（字段迁移至 agent_execution_detail 表）：
    // - updateModelOutput()  → model_output 存在明细表
    // - updateLog()          → log 已废弃（明细表即日志）

    /**
     * 标记任务开始
     * 
     * @param taskId 任务ID
     * @param updatedBy 修改者
     * @return 是否成功
     */
    boolean markTaskStarted(String taskId, String updatedBy);

    /**
     * 标记任务完成（状态 → SUCCESS）
     * 
     * @param taskId 任务ID
     * @param updatedBy 修改者
     * @return 是否成功
     */
    boolean markTaskCompleted(String taskId, String updatedBy);

    /**
     * 标记任务失败
     * 
     * @param taskId 任务ID
     * @param errorMessage 错误信息
     * @param updatedBy 修改者
     * @return 是否成功
     */
    boolean markTaskFailed(String taskId, String errorMessage, String updatedBy);

    /**
     * 更新工具执行结果
     */
    // updateToolResult 已废弃（字段迁移至 agent_execution_detail 表）

    /**
     * DTO转 Entity
     */
    AgentTaskEntity toEntity(AgentTaskNotifyDTO dto);
}
