package org.example.agent.Sp.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.Fastjson2TypeHandler;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 基础菜品表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "dish")
public class DishEntity {

    /**
     * 菜品ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 菜品来源
     */
    @Column(value = "source")
    private String source;

    /**
     * 菜品名称
     */
    @Column(value = "name")
    private String name;

    /**
     * 菜品类型(大荤/小荤/纯素)
     */
    @Column(value = "dish_type")
    private String dishType;
    /**
     * 辣度等级
     */
    @Column(value = "spicy_level")
    private Integer spicyLevel;

    /**
     * 烹饪工艺
     */
    @Column(value = "cooking_method")
    private String cookingMethod;

    /**
     * 其他成本(元)
     */
    @Column(value = "other_cost")
    private BigDecimal otherCost;

    /**
     * 成品单价(元/kg)
     */
    @Column(value = "unit_price_kg")
    private BigDecimal unitPriceKg;

    /**
     * 可食用率(0-1)
     */
    @Column(value = "edible_ratio")
    private BigDecimal edibleRatio;

    @Column(value = "create_by")
    private String createBy;

    @Column(value = "create_time")
    private Date createTime;

    @Column(value = "update_by")
    private String updateBy;

    @Column(value = "update_time")
    private Date updateTime;

    /**
     * 是否应季
     */
    @Column(value = "is_seasonal")
    private Boolean isSeasonal;

    /**
     * 总的成本
     */
    @Column(value = "all_cost")
    private BigDecimal allCost;

    /**
     * 主料列表（json数组）
     */
    @Column(value = "main_ingredients",typeHandler = Fastjson2TypeHandler.class)
    private Map<String, BigDecimal> mainIngredients;

    /**
     * 辅料列表（json数组）
     */
    @Column(value = "side_ingredients",typeHandler = Fastjson2TypeHandler.class)
    private Map<String, BigDecimal> sideIngredients;

    /**
     * 调味品列表（json）
     */
    @Column(value = "seasonings",typeHandler = Fastjson2TypeHandler.class)
    private Map<String, BigDecimal> seasonings;


    /**
     * 所有的原材料名称列表
     */
//    @Column(value = "all_ingredients",typeHandler = Fastjson2TypeHandler.class)
    private List<String> allIngredients;
    






}
