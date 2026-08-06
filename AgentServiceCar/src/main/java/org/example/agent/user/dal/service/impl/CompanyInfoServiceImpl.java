package org.example.agent.user.dal.service.impl;


import com.mybatisflex.spring.service.impl.ServiceImpl;

import org.example.agent.user.dal.entity.CompanyInfoEntity;
import org.example.agent.user.dal.mapper.CompanyInfoMapper;
import org.example.agent.user.dal.service.ICompanyInfoService;
import org.springframework.stereotype.Service;

/**
 * 公司主体信息表 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service("demoCompanyInfoServiceImpl")
public class CompanyInfoServiceImpl extends ServiceImpl<CompanyInfoMapper, CompanyInfoEntity> implements ICompanyInfoService {

}