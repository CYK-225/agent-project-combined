package org.example.sliders.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.sliders.entity.SlidersTaskEntity;

@Mapper
@DS("postgresql-agent_test")
public interface SlidersTaskMapper extends BaseMapper<SlidersTaskEntity> {
}
