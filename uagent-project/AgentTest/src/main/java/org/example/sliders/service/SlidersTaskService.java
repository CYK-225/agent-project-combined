package org.example.sliders.service;

import com.mybatisflex.core.service.IService;
import org.example.sliders.entity.SlidersTaskEntity;

/**
 * SLIDERS 任务服务
 */
public interface SlidersTaskService extends IService<SlidersTaskEntity> {

    /**
     * 根据 taskId 查找任务
     */
    SlidersTaskEntity getByTaskId(String taskId);

    /**
     * 更新任务状态
     */
    boolean updateStatus(String taskId, String status);

    /**
     * 更新任务答案
     */
    boolean updateAnswer(String taskId, String answer);

    /**
     * 标记任务失败
     */
    boolean markFailed(String taskId, String errorMessage);
}
