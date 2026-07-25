package org.example.agent.Sp.dal.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.agent.Sp.dal.entity.DishAttributesEntity;


/**
 * 菜品属性表 映射层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Mapper
@DS("postgresql-prediction")
public interface DishAttributesMapper extends BaseMapper<DishAttributesEntity> {


}
