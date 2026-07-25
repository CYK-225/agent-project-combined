package org.example.agent.user.dal.service.impl;


import com.mybatisflex.spring.service.impl.ServiceImpl;

import org.example.agent.user.dal.entity.UserCompanyRelationEntity;
import org.example.agent.user.dal.mapper.UserCompanyRelationMapper;
import org.example.agent.user.dal.service.IUserCompanyRelationService;
import org.springframework.stereotype.Service;

/**
 * 员工所属关联表(人事档案) 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service("demoUserCompanyRelationServiceImpl")
public class UserCompanyRelationServiceImpl extends ServiceImpl<UserCompanyRelationMapper, UserCompanyRelationEntity> implements IUserCompanyRelationService {

}