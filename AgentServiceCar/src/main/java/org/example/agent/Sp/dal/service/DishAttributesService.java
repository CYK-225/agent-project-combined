package org.example.agent.Sp.dal.service;


import com.mybatisflex.core.service.IService;
import org.example.agent.Sp.dal.entity.DishAttributesEntity;

import java.util.List;

/**
 * 菜品预测属性表 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface DishAttributesService extends IService<DishAttributesEntity> {

    List<Long> searchDishesByCandidate(Boolean enableAvgConsumption, Boolean enablePredictedRevenue,List<Long> notInId);
}