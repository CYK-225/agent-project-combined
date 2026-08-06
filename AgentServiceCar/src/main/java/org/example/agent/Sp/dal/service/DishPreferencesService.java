package org.example.agent.Sp.dal.service;


import com.mybatisflex.core.service.IService;
import org.example.agent.Sp.dal.entity.DishPreferencesEntity;

import java.util.List;

/**
 * 菜品偏好记录表(画像表) 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface DishPreferencesService extends IService<DishPreferencesEntity> {

    List<Long> searchDishesByHistoricalRating( List<Long> notInId);
}