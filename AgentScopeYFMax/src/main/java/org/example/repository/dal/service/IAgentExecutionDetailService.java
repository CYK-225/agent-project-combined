package org.example.repository.dal.service;

import com.mybatisflex.core.service.IService;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;
import org.example.repository.dal.entity.AgentExecutionDetailEntity;

import java.util.List;

/**
 * Agent 执行明细 Service 接口
 * <p>
 * 继承 IService，提供业务方法
 * </p>
 */
public interface IAgentExecutionDetailService extends IService<AgentExecutionDetailEntity> {

    /**
     * 根据任务 ID 查询所有执行明细
     *
     * @param taskId 任务 ID
     * @return 执行明细列表（按 step 排序）
     */
    List<AgentExecutionDetailEntity> selectByTaskId(String taskId);

    /**
     * 根据任务 ID 和步骤编号查询执行明细
     *
     * @param taskId 任务 ID
     * @param step   步骤编号
     * @return 该步骤的所有执行明细
     */
    List<AgentExecutionDetailEntity> selectByTaskIdAndStep(String taskId, Integer step);

    /**
     * 根据任务 ID 删除所有执行明细
     *
     * @param taskId 任务 ID
     * @return 是否成功
     */
    boolean deleteByTaskId(String taskId);

    /**
     * DTO转 Entity
     */
    AgentExecutionDetailEntity toEntity(AgentTaskNotifyDTO dto);
}
