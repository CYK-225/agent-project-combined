package org.example.agent.user.dal.mapper;


import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.example.agent.user.dal.entity.CompanyInfoEntity;
import org.springframework.stereotype.Repository;

/**
 * 公司主体信息表 映射层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Mapper
@Repository("demoCompanyInfoMapper")
//TODO 等一下改
public interface CompanyInfoMapper extends BaseMapper<CompanyInfoEntity> {


}
