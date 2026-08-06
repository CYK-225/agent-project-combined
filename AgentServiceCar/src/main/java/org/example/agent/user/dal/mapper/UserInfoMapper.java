package org.example.agent.user.dal.mapper;


import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.example.agent.user.dal.entity.UserInfoEntity;
import org.springframework.stereotype.Repository;

/**
 * 用户信息表 映射层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Mapper
@Repository("demoUserInfoMapper")
public interface UserInfoMapper extends BaseMapper<UserInfoEntity> {


}
