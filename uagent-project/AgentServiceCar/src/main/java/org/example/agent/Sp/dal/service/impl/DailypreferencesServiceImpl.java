package org.example.agent.Sp.dal.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.example.agent.Sp.dal.entity.DishPreferencesEntity;
import org.example.agent.Sp.dal.mapper.DishPreferencesMapper;
import org.example.agent.Sp.dal.service.DishPreferencesService;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.example.agent.Sp.dal.entity.table.DishAttributesEntityTableDef.DISH_ATTRIBUTES_ENTITY;
import static org.example.agent.Sp.dal.entity.table.DishPreferencesEntityTableDef.DISH_PREFERENCES_ENTITY;


@Service
public class DailypreferencesServiceImpl extends ServiceImpl<DishPreferencesMapper, DishPreferencesEntity> implements DishPreferencesService {
        @Override
        public List<Long> searchDishesByHistoricalRating( List<Long> notInId){
            return mapper.selectListByQueryAs(
                    QueryWrapper.create()
                            .select(DISH_PREFERENCES_ENTITY.DISH_ID)
                            .where(DISH_PREFERENCES_ENTITY.DISH_ID.notIn(notInId).when(notInId != null && !notInId.isEmpty()))
                            .orderBy(DISH_PREFERENCES_ENTITY.PREFERENCE_SCORE, false)

                            .limit(48)
                    , Long.class
            );
        }
}
