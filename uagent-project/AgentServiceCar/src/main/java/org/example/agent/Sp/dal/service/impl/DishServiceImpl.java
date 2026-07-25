package org.example.agent.Sp.dal.service.impl;


import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dal.entity.DishAttributesEntity;
import org.example.agent.Sp.dal.entity.DishEntity;
import org.example.agent.Sp.dal.entity.DishPreferencesEntity;
import org.example.agent.Sp.dal.mapper.DishMapper;
import org.example.agent.Sp.dal.service.DishAttributesService;
import org.example.agent.Sp.dal.service.DishService;

import org.example.agent.Sp.dataModel.BaseDataModel.DishInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.agent.Sp.dal.entity.table.DishAttributesEntityTableDef.DISH_ATTRIBUTES_ENTITY;
import static org.example.agent.Sp.dal.entity.table.DishEntityTableDef.DISH_ENTITY;
import static org.example.agent.Sp.dal.entity.table.DishPreferencesEntityTableDef.DISH_PREFERENCES_ENTITY;


/**
 * 基础菜品表 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Slf4j
@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, DishEntity> implements DishService {
    @Resource
    private DishAttributesService dishAttributesService;


    /**
     * 提取的私有方法：处理价格参数
     * 如果价格为 null 或小于 0，则返回提供的默认值
     */
    private Double sanitizePrice(Double price, Double defaultValue) {
        return (price == null || price < 0) ? defaultValue : price;
    }
    // 辅助方法：格式化 List 为 SQL 字符串 (简单实现)
    private String formatSqlArray(List<String> list) {
        if (list == null || list.isEmpty()) return "''";
        return list.stream()
                .map(s -> STR."'\{s.replace("'", "''")}'")
                .collect(Collectors.joining(","));
    }

    @Override
    public List<Long> searchDishesByAttributes(List<String> name, List<String> dishType,
                                               List<String> spicinessLevel, List<String> flavor,
                                               List<String> mainIngredient, Double minCostPrice,
                                               Double maxCostPrice, Double minPrice, Double maxPrice,
                                               List<Long> notInId) {
        //对四个价格区间进行处理，若为null或负数则设定为极值0和99999
// 使用提取后的私有方法进行参数初始化
        minCostPrice = sanitizePrice(minCostPrice, 0.0);
        maxCostPrice = sanitizePrice(maxCostPrice, 99999.0);
        minPrice = sanitizePrice(minPrice, 0.0);
        maxPrice = sanitizePrice(maxPrice, 99999.0);

// 2. 判空处理 (因为 required = false，可能为 null)
        List<Integer> spicinessLevelInt;
        if (spicinessLevel != null && !spicinessLevel.isEmpty()) {

            spicinessLevelInt = spicinessLevel.stream()
                    .map(level -> {
                        // 对 List 中的每一个 String 进行 switch 转换
                        return switch (level) {
                            case "不辣" -> 0;
                            case "微辣" -> 1;
                            case "中辣" -> 2;
                            case "无辣不欢" -> 3;
                            default -> 0; // 默认值，或者可以选择 return null 后续过滤掉
                        };
                    })
                    .toList();
        } else {
            spicinessLevel = new ArrayList<>();
            spicinessLevelInt = new ArrayList<>();
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select(DISH_ENTITY.ID)
                .from(DISH_ENTITY)
                // 注意：第一个条件用 where，后续的用 and
                // MyBatis-Flex 的字段方法自带第三个参数 condition，用于动态 SQL 判断
                .where(DISH_ENTITY.DISH_TYPE.in(dishType, !dishType.isEmpty()))
                .and(DISH_ENTITY.SPICY_LEVEL.in(spicinessLevelInt, !spicinessLevelInt.isEmpty()))
                .and(DISH_ENTITY.COOKING_METHOD.in(flavor, !flavor.isEmpty()))

                // between 同样挂在字段后面
                .and(DISH_ENTITY.ALL_COST.between(minCostPrice, maxCostPrice))
                .and(DISH_ENTITY.UNIT_PRICE_KG.between(minPrice, maxPrice))

                // notIn 也是挂在字段后面，并带上动态判断
                .and(DISH_ENTITY.ID.notIn(notInId, !notInId.isEmpty()));
        if (name != null && !name.isEmpty()) {
            queryWrapper.or(DishEntity::getName).in(name);
        }

        List<Long> dishInfos = mapper.selectListByQueryAs(queryWrapper, Long.class);
        log.info("dishInfos: {}", dishInfos);
        if (dishInfos.isEmpty()) {
            return new ArrayList<>();
        }
        if (mainIngredient != null && !mainIngredient.isEmpty()) {
            String pgArraySql = STR."ARRAY[\{formatSqlArray(mainIngredient)}]::varchar[]";
            List<Long> ids = mapper.selectListByQueryAs(
                    QueryWrapper.create()
                            // --- Select 部分 (计算重合度) ---
                            // 【修复】使用表常量
                            .select(DISH_ENTITY.ID)
                            .select(STR."cardinality(array(select unnest(all_ingredients) intersect select unnest(\{pgArraySql}))) AS score")
                            // 【修复】使用表常量
                            .from(DISH_ENTITY)
                            // --- Where 部分 (核心逻辑) ---
                            // 1. 倒排索引过滤 (必须包含至少一个食材) -> 原生 SQL 条件直接 where 没问题
                            .where(STR."all_ingredients && \{pgArraySql}")
                            // 2. 【核心修复】将 wrapper.in(...) 改为 .and( 字段.in(...) )
                            .and(DISH_ENTITY.ID.in(dishInfos, !dishInfos.isEmpty()))
                            // --- Order By 部分 ---
                            // 按重合度倒序
                            .orderBy("score", false)
                            .limit(48),
                    Long.class
            );
            return ids;
        }
        log.info(STR."属性结果\{dishInfos}");
        return dishInfos;
    }
    @Override
    public List<DishInfo>  getDishInfoByIds(List<Long> dishIds){

// ==========================================
// 1. 联表查询 DishInfo
// ==========================================
        List<DishInfo> dishInfos = mapper.selectListByQueryAs(
                QueryWrapper.create()
                        .select(
                                // 主表字段
                                DISH_ENTITY.ID, DISH_ENTITY.NAME, DISH_ENTITY.SOURCE, DISH_ENTITY.DISH_TYPE,
                                DISH_ENTITY.SPICY_LEVEL, DISH_ENTITY.COOKING_METHOD, DISH_ENTITY.EDIBLE_RATIO,
                                DISH_ENTITY.IS_SEASONAL, DISH_ENTITY.ALL_COST, DISH_ENTITY.UNIT_PRICE_KG,
                                // 偏好表字段 (帮你删除了重复的 RECENT_REVIEWS)
                                DISH_PREFERENCES_ENTITY.PREFERENCE_SCORE, DISH_PREFERENCES_ENTITY.APPEARANCE_COUNT,
                                DISH_PREFERENCES_ENTITY.LAST_APPEARANCE_DATE, DISH_PREFERENCES_ENTITY.RECENT_REVIEWS,
                                DISH_PREFERENCES_ENTITY.HISTORY_REVIEWS
                        )
                        .from(DISH_ENTITY)
                        // 【核心变化 1】联表 ON 条件的写法：表1字段.eq(表2字段)
                        .leftJoin(DISH_PREFERENCES_ENTITY).on(DISH_ENTITY.ID.eq(DISH_PREFERENCES_ENTITY.DISH_ID))
                        // 【核心变化 2】where In 写法，并加入判空保护
                        .where(DISH_ENTITY.ID.in(dishIds, !dishIds.isEmpty())),
                DishInfo.class
        );

// ==========================================
// 2. 查询属性并转为 Map
// ==========================================
        Map<Long, DishAttributesEntity> dishInfosAttrition = dishAttributesService.getMapper().selectListByQuery(
                QueryWrapper.create()
                        .from(DISH_ATTRIBUTES_ENTITY)
                        // 同样改为 where 挂载 IN 条件
                        .where(DISH_ATTRIBUTES_ENTITY.DISH_ID.in(dishIds, !dishIds.isEmpty()))
        ).stream().collect(Collectors.toMap(
                DishAttributesEntity::getDishId,
                attr -> attr,
                (attr1, attr2) -> attr1
        ));
        // 将属性信息补全到 dishInfos 中
        for (DishInfo dishInfo : dishInfos) {
            DishAttributesEntity attr = dishInfosAttrition.get(dishInfo.getId());
            if (attr != null) {
                dishInfo.setPredictedConsumption(attr.getPredictedConsumption());
                dishInfo.setPredictedLoss(attr.getPredictedLoss());
            }
        }
        return  dishInfos;

    }
    @Override
    public List<DishInfo>  getDishInfoByIdsAndNames(List<Long> dishIds, List<String> dishNames){

// ==========================================
// 1. 根据名称查询 ID 并加入集合
// ==========================================
        dishIds.addAll(mapper.selectListByQueryAs(
                QueryWrapper.create()
                        .select(DISH_ENTITY.ID)
                        .from(DISH_ENTITY)
                        // 【修复】将 wrapper.in 改为 where(字段.in(值, 动态条件))
                        .where(DISH_ENTITY.NAME.in(dishNames, dishNames != null && !dishNames.isEmpty())),
                Long.class
        ));

// ==========================================
// 2. 联表查询详细信息 (DishInfo)
// ==========================================
        List<DishInfo> dishInfos = mapper.selectListByQueryAs(
                QueryWrapper.create()
                        .select(
                                // 主表字段
                                DISH_ENTITY.ID, DISH_ENTITY.NAME, DISH_ENTITY.SOURCE, DISH_ENTITY.DISH_TYPE,
                                DISH_ENTITY.SPICY_LEVEL, DISH_ENTITY.COOKING_METHOD, DISH_ENTITY.EDIBLE_RATIO,
                                DISH_ENTITY.IS_SEASONAL, DISH_ENTITY.ALL_COST, DISH_ENTITY.UNIT_PRICE_KG,
                                // 偏好表字段 (帮你去掉了原代码中重复写了两次的 RECENT_REVIEWS)
                                DISH_PREFERENCES_ENTITY.PREFERENCE_SCORE, DISH_PREFERENCES_ENTITY.APPEARANCE_COUNT,
                                DISH_PREFERENCES_ENTITY.LAST_APPEARANCE_DATE, DISH_PREFERENCES_ENTITY.RECENT_REVIEWS,
                                DISH_PREFERENCES_ENTITY.HISTORY_REVIEWS
                        )
                        .from(DISH_ENTITY)
                        // 【修复】联表写法：leftJoin(表常量).on(主表字段.eq(从表字段))
                        .leftJoin(DISH_PREFERENCES_ENTITY).on(DISH_ENTITY.ID.eq(DISH_PREFERENCES_ENTITY.DISH_ID))
                        // 【修复】where IN 写法，并为你补上了 dishIds 的判空保护
                        .where(DISH_ENTITY.ID.in(dishIds, !dishIds.isEmpty())),
                DishInfo.class
        );

        Map<Long, DishAttributesEntity> dishInfosAttrition = dishAttributesService.getMapper().selectListByQuery(
                QueryWrapper.create()
                        .from(DishAttributesEntity.class)
                        .in(DishAttributesEntity::getDishId,dishIds)
        ).stream().collect(Collectors.toMap(DishAttributesEntity::getDishId,
                attr -> attr, (attr1, attr2) -> attr1));
        // 将属性信息补全到 dishInfos 中
        for (DishInfo dishInfo : dishInfos) {
            DishAttributesEntity attr = dishInfosAttrition.get(dishInfo.getId());
            if (attr != null) {
                dishInfo.setPredictedConsumption(attr.getPredictedConsumption());
                dishInfo.setPredictedLoss(attr.getPredictedLoss());
    }
        }
            return  dishInfos;
    }

}