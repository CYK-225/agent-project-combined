package org.example.sliders.service;

import com.mybatisflex.core.service.IService;
import org.example.sliders.entity.SlidersPipelineLogEntity;

/**
 * 流水线日志服务
 */
public interface SlidersPipelineLogService extends IService<SlidersPipelineLogEntity> {

    /**
     * 记录流水线事件
     */
    void log(String taskId, String agentName, String stage, String action, String detail, Long durationMs);
}
