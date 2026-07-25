package org.example.skillEvolver.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.skillEvolver.entity.EvolverSkillVersionEntity;

/**
 * Skill 版本快照 Mapper
 *
 * @author zhilin
 */
@Mapper
@DS("postgresql-agent_test")
public interface EvolverSkillVersionMapper extends BaseMapper<EvolverSkillVersionEntity> {
}
