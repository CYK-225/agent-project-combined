package org.example.repository.dal.service.impl;


import com.baomidou.dynamic.datasource.annotation.DS;
import org.example.repository.dal.entity.UserSessionMappingEntity;
import org.example.repository.dal.mapper.UserSessionMappingMapper;
import org.example.repository.dal.service.IUserSessionMappingService;
import org.springframework.stereotype.Service;

import com.mybatisflex.spring.service.impl.ServiceImpl;

/**
 * 用户会话关联表 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service
@DS("postgresql-session") // 确保使用正确的数据源

public class UserSessionMappingServiceImpl extends ServiceImpl<UserSessionMappingMapper, UserSessionMappingEntity> implements IUserSessionMappingService {

}