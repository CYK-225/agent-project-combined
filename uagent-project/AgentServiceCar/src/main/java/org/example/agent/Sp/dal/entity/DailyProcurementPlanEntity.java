package org.example.agent.Sp.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 每日采购计划与消耗实录表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "daily_procurement_plan")
public class DailyProcurementPlanEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 关联菜品ID
     */
    @Column(value = "dish_id")
    private Long dishId;

    @Column(value = "dish_name")
    private String dishName;

    /**
     * 计划采购重量(kg)
     */
    @Column(value = "procurement_weight")
    private BigDecimal procurementWeight;

    /**
     * 实际送达重量(kg)
     */
    @Column(value = "actual_delivered")
    private BigDecimal actualDelivered;

    /**
     * 实际损耗或剩余(kg)
     */
    @Column(value = "actual_loss")
    private BigDecimal actualLoss;

    /**
     * 实际被消耗重量(kg)
     */
    @Column(value = "actual_consumed")
    private BigDecimal actualConsumed;

    /**
     * 送达消耗率(消耗/送达)
     */
    @Column(value = "delivery_consumption_rate")
    private BigDecimal deliveryConsumptionRate;

    /**
     * 采购消耗率(消耗/采购)
     */
    @Column(value = "procurement_consumption_rate")
    private BigDecimal procurementConsumptionRate;

    /**
     * 就餐人数
     */
    @Column(value = "headcount")
    private Integer headcount;

    /**
     * 实际人均消耗(kg/人)
     */
    @Column(value = "per_capita_consumption")
    private BigDecimal perCapitaConsumption;

    /**
     * 当日备注(如天气、特殊事件)
     */
    @Column(value = "remarks")
    private String remarks;

    @Column(value = "plan_date")
    private Date planDate;

    @Column(value = "create_by")
    private String createBy;

    @Column(value = "create_time")
    private Date createTime;

    @Column(value = "update_by")
    private String updateBy;

    @Column(value = "update_time")
    private Date updateTime;


}
