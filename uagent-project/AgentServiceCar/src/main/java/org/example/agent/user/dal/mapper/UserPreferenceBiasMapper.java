package org.example.agent.user.dal.mapper;


import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.example.agent.user.dal.entity.UserPreferenceBiasEntity;
import org.springframework.stereotype.Repository;

/**
 * 用户喜好偏差表 映射层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Mapper
@Repository("demoUserPreferenceBiasMapper")
public interface UserPreferenceBiasMapper extends BaseMapper<UserPreferenceBiasEntity> {


}
