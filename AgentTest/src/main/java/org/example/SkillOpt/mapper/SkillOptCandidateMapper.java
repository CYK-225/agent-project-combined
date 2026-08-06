package org.example.skillOpt.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.skillOpt.entity.SkillOptCandidateEntity;

/**
 * @author zhilin
 */
@Mapper
@DS("postgresql-agent_test")
public interface SkillOptCandidateMapper extends BaseMapper<SkillOptCandidateEntity> {
}
