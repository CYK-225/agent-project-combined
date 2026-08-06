package org.example.agent.Sp.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 菜品偏好记录表(画像表) 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "dish_preferences")
public class DishPreferencesEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 关联菜品ID
     */
    @Column(value = "dish_id")
    private Long dishId;

    /**
     * 综合喜好程度评分
     */
    @Column(value = "preference_score")
    private BigDecimal preferenceScore;

    /**
     * 历史供应次数
     */
    @Column(value = "appearance_count")
    private Integer appearanceCount;

    /**
     * 上次供应日期
     */
    @Column(value = "last_appearance_date")
    private LocalDate lastAppearanceDate;

    /**
     * 最近一次评价内容
     */
    @Column(value = "recent_reviews")
    private String recentReviews;

    /**
     * 历史评价摘要
     */
    @Column(value = "history_reviews")
    private String historyReviews;

    @Column(value = "create_by")
    private String createBy;

    @Column(value = "create_time")
    private Date createTime;

    @Column(value = "update_by")
    private String updateBy;

    @Column(value = "update_time")
    private Date updateTime;


}
