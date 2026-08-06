package org.example.repository.dal.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.repository.dal.entity.UserSessionMappingEntity;

/**
 * 用户会话关联表 映射层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Mapper
@DS("postgresql-session") // 确保使用正确的数据源

public interface UserSessionMappingMapper extends BaseMapper<UserSessionMappingEntity> {


}
