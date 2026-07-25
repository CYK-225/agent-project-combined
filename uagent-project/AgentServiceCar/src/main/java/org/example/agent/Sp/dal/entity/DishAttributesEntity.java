package org.example.agent.Sp.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 菜品属性表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "dish_attributes")
public class DishAttributesEntity {

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 菜品唯一标识
     */
    @Column(value = "dish_id")
    private Long dishId;

    /**
     * 菜品名称
     */
    @Column(value = "name")
    private String name;

    /**
     * 辣度等级
     */
    @Column(value = "spiciness")
    private Integer spiciness;

    /**
     * 主料列表(JSON数组)
     */
    @Column(value = "main_ingredients")
    private Object mainIngredients;

    /**
     * 辅料列表(JSON数组)
     */
    @Column(value = "side_ingredients")
    private Object sideIngredients;

    /**
     * 调味品列表(JSON数组)
     */
    @Column(value = "seasonings")
    private Object seasonings;

    /**
     * 主辅料比例
     */
    @Column(value = "ingredient_ratio")
    private String ingredientRatio;

    /**
     * 菜品价格
     */
    @Column(value = "price")
    private BigDecimal price;

    /**
     * 菜品分类(全荤/半荤/纯素)
     */
    @Column(value = "category")
    private String category;

    /**
     * 烹饪工艺
     */
    @Column(value = "cooking_method")
    private String cookingMethod;

    /**
     * 是否应季菜品
     */
    @Column(value = "is_seasonal")
    private Boolean isSeasonal;

    /**
     * 预测人均消耗量(kg)
     */
    @Column(value = "predicted_consumption")
    private BigDecimal predictedConsumption;

    /**
     * 预测人均货损量(kg)
     */
    @Column(value = "predicted_loss")
    private BigDecimal predictedLoss;
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


}
