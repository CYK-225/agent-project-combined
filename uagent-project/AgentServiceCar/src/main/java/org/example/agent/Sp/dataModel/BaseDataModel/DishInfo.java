package org.example.agent.Sp.dataModel.BaseDataModel;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMappers;
import lombok.Data;
import org.example.agent.Sp.dal.entity.DishEntity;
import org.example.common.validator.GlobalMapAdapter;


import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @Auter lxz
 * @Date 25/12/29
 */
//  TODO:没测试
@Data
@AutoMappers({
        @AutoMapper(target = DishEntity.class, uses = {GlobalMapAdapter.class}),
        @AutoMapper(target = DishInfoEasy.class)
})
public class DishInfo {
//菜品基本信息
    // 菜品 id
    @JsonPropertyDescription("菜品唯一标识ID")
    private Long id;
    // 菜品来源
    @JsonPropertyDescription("菜品来源渠道")
    private String source;
    //菜品名称
    @JsonPropertyDescription("菜品名称")
    private String name;
    //菜品类型
    @JsonPropertyDescription("菜品分类")
    private String dishType;
    //辣度
    @JsonPropertyDescription("辣度等级")
//    @AutoMapping(qualifiedByName = "SpicyLevel")
    private int spicyLevel;
    //烹饪方式
    @JsonPropertyDescription("主要烹饪方式")
    private String cookingMethod;
    //可食用率
    @JsonPropertyDescription("食材可食用部分比例(0-1)")
    private BigDecimal edibleRatio;
    // 是否应季
    @JsonPropertyDescription("是否为应季菜品")
    private boolean isSeasonal;

//菜品价格信息
    //其他成本
    @JsonPropertyDescription("其他杂项成本")
    private BigDecimal otherCost;
    //成品单价
    @JsonPropertyDescription("每公斤成品单价")
    private BigDecimal unit_price_kg;

//菜品营收数据信息

    @JsonPropertyDescription("预测人均消耗量(kg)")
    private BigDecimal predictedConsumption;


    @JsonPropertyDescription("预测人均货损量(kg)")
    private BigDecimal predictedLoss;

    //历史用户偏好
    @JsonPropertyDescription("综合喜好程度评分")
    private BigDecimal preferenceScore;
//历史供应数据

    @JsonPropertyDescription("历史供应次数")
    private Integer appearanceCount;


    @JsonPropertyDescription("上次供应日期")
    private LocalDate lastAppearanceDate;


    @JsonPropertyDescription("最近一次评价内容")
    private String recentReviews;


    @JsonPropertyDescription("历史评价摘要")
    private String historyReviews;









}
