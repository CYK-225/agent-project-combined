package com.cyk.Service.impl;


import com.cyk.Enity.table.CategoriesEntity;
import com.cyk.Mapper.CategoriesMapper;
import com.cyk.Service.ICategoriesService;

import org.springframework.stereotype.Service;

import com.mybatisflex.spring.service.impl.ServiceImpl;

/**
 * 提示词分类表 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service
public class CategoriesServiceImpl extends ServiceImpl<CategoriesMapper, CategoriesEntity> implements ICategoriesService {

    @Override
    public String getCategoryByName(String name) {
        // 使用 MyBatis-Flex 构建查询条件
        com.mybatisflex.core.query.QueryWrapper queryWrapper = com.mybatisflex.core.query.QueryWrapper.create()
                .where(CategoriesEntity::getName).eq(name); // 假设通过 name 字段查询

        CategoriesEntity category = this.getMapper().selectOneByQuery(queryWrapper);

        // 返回提示词内容（如果找不到返回空字符串，避免空指针）
        return category != null ? category.getDescription() : "";
    }

}