package org.example.agent.Sp.dal.service;


import com.mybatisflex.core.service.IService;
import org.example.agent.Sp.dal.entity.DishEntity;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfo;


import java.util.List;

/**
 * 基础菜品表 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface DishService extends IService<DishEntity> {
    List<Long> searchDishesByAttributes(List<String> name, List<String> dishType, List<String> spicinessLevel,
                                        List<String> flavor, List<String>  mainIngredient, Double minCostPrice,
                                            Double maxCostPrice, Double minPrice, Double maxPrice,List<Long> notInId);


    List<DishInfo>  getDishInfoByIds(List<Long> dishIds);

    List<DishInfo>  getDishInfoByIdsAndNames(List<Long> dishIds, List<String> dishNames);
}