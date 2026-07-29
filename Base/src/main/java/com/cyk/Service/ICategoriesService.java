package com.cyk.Service;


import com.cyk.Enity.table.CategoriesEntity;
import com.mybatisflex.core.service.IService;

/**
 * 提示词分类表 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface ICategoriesService extends IService<CategoriesEntity> {

    String getCategoryByName(String name);
}