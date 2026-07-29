package com.cyk.task.DAL.Service;



import com.cyk.task.DAL.DO.TaskLogEntity;
import com.mybatisflex.core.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 任务执行过程日志表-记录执行器每一步的细节 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface ITaskLogService extends IService<TaskLogEntity> {

    List<TaskLogEntity> selectByTaskId(Long taskId);

    List<TaskLogEntity> selectByTaskIdAndContainerType(Long taskId, String containerType);

    int countByTaskId(Long taskId);

    List<TaskLogEntity> selectRecentErrors(int minutes, int limit);

    List<Map<String, Object>> countByTaskType(java.util.Date startTime, java.util.Date endTime);
}