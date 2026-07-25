package org.example.agent.user.dal.mapper;


import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.example.agent.user.dal.entity.UserCompanyRelationEntity;
import org.springframework.stereotype.Repository;

/**
 * 员工所属关联表(人事档案) 映射层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Mapper
@Repository("demoUserCompanyRelationMapper")
public interface UserCompanyRelationMapper extends BaseMapper<UserCompanyRelationEntity> {


}
