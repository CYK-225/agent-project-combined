package org.example.sliders.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.example.sliders.entity.SlidersTaskEntity;
import org.example.sliders.mapper.SlidersTaskMapper;
import org.example.sliders.service.SlidersTaskService;
import org.springframework.stereotype.Service;

import static org.example.sliders.entity.table.SlidersTaskEntityTableDef.SLIDERS_TASK_ENTITY;

@Service
@DS("postgresql-agent_test")
public class SlidersTaskServiceImpl extends ServiceImpl<SlidersTaskMapper, SlidersTaskEntity>
        implements SlidersTaskService {

    @Override
    public SlidersTaskEntity getByTaskId(String taskId) {
        return getOne(QueryWrapper.create().where(SLIDERS_TASK_ENTITY.TASK_ID.eq(taskId)));
    }

    @Override
    public boolean updateStatus(String taskId, String status) {
        SlidersTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setStatus(status);
        return updateById(entity);
    }

    @Override
    public boolean updateAnswer(String taskId, String answer) {
        SlidersTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setAnswer(answer);
        entity.setStatus("COMPLETED");
        return updateById(entity);
    }

    @Override
    public boolean markFailed(String taskId, String errorMessage) {
        SlidersTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setStatus("FAILED");
        entity.setErrorMessage(errorMessage);
        return updateById(entity);
    }
}
