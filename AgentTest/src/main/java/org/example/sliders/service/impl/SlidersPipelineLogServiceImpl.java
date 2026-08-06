package org.example.sliders.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.example.sliders.entity.SlidersPipelineLogEntity;
import org.example.sliders.mapper.SlidersPipelineLogMapper;
import org.example.sliders.service.SlidersPipelineLogService;
import org.springframework.stereotype.Service;

@Service
@DS("postgresql-agent_test")
public class SlidersPipelineLogServiceImpl extends ServiceImpl<SlidersPipelineLogMapper, SlidersPipelineLogEntity>
        implements SlidersPipelineLogService {

    @Override
    public void log(String taskId, String agentName, String stage, String action, String detail, Long durationMs) {
        save(SlidersPipelineLogEntity.builder()
                .taskId(taskId)
                .agentName(agentName)
                .stage(stage)
                .action(action)
                .detail(detail)
                .durationMs(durationMs)
                .build());
    }
}
