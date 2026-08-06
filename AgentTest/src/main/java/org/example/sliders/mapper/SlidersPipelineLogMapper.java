package org.example.sliders.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.sliders.entity.SlidersPipelineLogEntity;

@Mapper
@DS("postgresql-agent_test")
public interface SlidersPipelineLogMapper extends BaseMapper<SlidersPipelineLogEntity> {
}
