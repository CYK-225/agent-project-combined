package org.example.agent.Sp.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 食材表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "ingredient")
public class IngredientEntity {

    /**
     * 食材ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 食材名称
     */
    @Column(value = "ingredient_name")
    private String ingredientName;

    /**
     * 食材级别
     */
    @Column(value = "food_grade")
    private String foodGrade;

    /**
     * 生料单价(元)
     */
    @Column(value = "raw_price")
    private BigDecimal rawPrice;

    /**
     * 生料重量(kg)
     */
    @Column(value = "raw_weight")
    private BigDecimal rawWeight;

    /**
     * 成品率(0-1)
     */
    @Column(value = "finished_ratio")
    private BigDecimal finishedRatio;

    /**
     * 成品重量(kg)
     */
    @Column(value = "finished_weight")
    private BigDecimal finishedWeight;

    /**
     * 成品成本(元)
     */
    @Column(value = "finished_cost")
    private BigDecimal finishedCost;

    /**
     * 食材类型(主/辅/调味)
     */
    @Column(value = "ingredient_type")
    private String ingredientType;

    /**
     * 占比(0-1)
     */
    @Column(value = "ratio")
    private BigDecimal ratio;

    @Column(value = "create_by")
    private String createBy;

    @Column(value = "create_time")
    private Date createTime;

    @Column(value = "update_by")
    private String updateBy;

    @Column(value = "update_time")
    private Date updateTime;


}
