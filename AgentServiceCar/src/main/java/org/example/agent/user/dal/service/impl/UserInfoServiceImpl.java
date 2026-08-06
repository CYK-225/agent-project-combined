package org.example.agent.user.dal.service.impl;


import com.mybatisflex.spring.service.impl.ServiceImpl;

import org.example.agent.user.dal.entity.UserInfoEntity;
import org.example.agent.user.dal.mapper.UserInfoMapper;
import org.example.agent.user.dal.service.IUserInfoService;
import org.springframework.stereotype.Service;

/**
 * 用户信息表 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service("demoUserInfoServiceImpl")
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfoEntity> implements IUserInfoService {

}