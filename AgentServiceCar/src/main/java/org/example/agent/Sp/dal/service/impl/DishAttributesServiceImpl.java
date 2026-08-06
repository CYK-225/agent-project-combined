package org.example.agent.Sp.dal.service.impl;


import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.example.agent.Sp.dal.entity.DishAttributesEntity;
import org.example.agent.Sp.dal.mapper.DishAttributesMapper;
import org.example.agent.Sp.dal.service.DishAttributesService;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.example.agent.Sp.dal.entity.table.DishAttributesEntityTableDef.DISH_ATTRIBUTES_ENTITY;

/**
 * 菜品属性表 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service
@DS("postgresql-prediction")
public class DishAttributesServiceImpl extends ServiceImpl<DishAttributesMapper, DishAttributesEntity> implements DishAttributesService {

    @Override
    public List<Long> searchDishesByCandidate(Boolean enableAvgConsumption, Boolean enablePredictedRevenue,List<Long> notInId) {
        QueryWrapper query = QueryWrapper.create()
                .select(DISH_ATTRIBUTES_ENTITY.DISH_ID)
                .from(DishAttributesEntity.class);

        if (Boolean.TRUE.equals(enableAvgConsumption) && Boolean.TRUE.equals(enablePredictedRevenue)) {

            // 1. 构建安全的 SQL 排序片段
            // 逻辑：
            // A = 消耗 + 货损 (总重量)
            // B = A * edible_ratio (理论可食重量)
            // Score = 消耗 / B
            // 必须处理 B 为 0 或 NULL 的情况

            String rankingSql = """
            CASE
                WHEN (predicted_consumption + predicted_loss) * COALESCE(edible_ratio, 1.0) > 0.00001
                THEN predicted_consumption / ((predicted_consumption + predicted_loss) * COALESCE(edible_ratio, 1.0))
                ELSE 0
            END
        """;

            // 2. 执行排序
            // 结果是百分比（0.0 ~ 1.0+），越高越好，所以用 DESC (false)
            query.orderBy(rankingSql, false);

        } else {
            if     (Boolean.TRUE.equals(enableAvgConsumption)) {
                query.orderBy(DISH_ATTRIBUTES_ENTITY.PREDICTED_CONSUMPTION, false);
            }
            if (Boolean.TRUE.equals(enablePredictedRevenue)) {
                query.orderBy(DISH_ATTRIBUTES_ENTITY.PREDICTED_LOSS, true);
            }
        }
        query.where(DISH_ATTRIBUTES_ENTITY.DISH_ID.notIn(notInId).when(!notInId.isEmpty()));
        // 最多返回 48 条记录
        query.limit(48);

        return mapper.selectListByQueryAs(query,Long.class);
    }
}