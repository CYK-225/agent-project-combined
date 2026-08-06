package org.example.agent.user.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.lang.Long;
import java.util.Date;
import java.lang.String;
import java.lang.Integer;

/**
 * 用户喜好偏差表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "user_preference_bias")
@Data
public class UserPreferenceBiasEntity {

    /**
     * 用户喜好ID
     */
    @Id(keyType = KeyType.Auto)
    private Long preferenceId;

    /**
     * 用户ID
     */
    @Column(value = "user_id")
    private Long userId;

    /**
     * 用户名称
     */
    @Column(value = "user_name")
    private String userName;

    /**
     * 用户喜好辣度（0-不辣，1-微辣，2-重辣）
     */
    @Column(value = "spicy_level")
    private Integer spicyLevel;

    /**
     * 用户喜欢吃的菜品(数组)
     */
    @Column(value = "favorite_dishes")
    private String favoriteDishes;

    /**
     * 用户一定不吃的菜品(文本描述)
     */
    @Column(value = "disliked_dishes_desc")
    private String dislikedDishesDesc;

    /**
     * 平时更容易接受的类型(数组)
     */
    @Column(value = "accepted_types")
    private String acceptedTypes;

    /**
     * 无法接受的食材(数组)
     */
    @Column(value = "unacceptable_ingredients")
    private String unacceptableIngredients;

    /**
     * 创建者
     */
    @Column(value = "create_by")
    private String createBy;

    /**
     * 创建时间
     */
    @Column(value = "create_time")
    private Date createTime;

    /**
     * 更新者
     */
    @Column(value = "update_by")
    private String updateBy;

    /**
     * 更新时间
     */
    @Column(value = "update_time")
    private Date updateTime;


    public Long getPreferenceId() {
        return preferenceId;
    }

    public void setPreferenceId(Long preferenceId) {
        this.preferenceId = preferenceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getSpicyLevel() {
        return spicyLevel;
    }

    public void setSpicyLevel(Integer spicyLevel) {
        this.spicyLevel = spicyLevel;
    }

    public String getFavoriteDishes() {
        return favoriteDishes;
    }

    public void setFavoriteDishes(String favoriteDishes) {
        this.favoriteDishes = favoriteDishes;
    }

    public String getDislikedDishesDesc() {
        return dislikedDishesDesc;
    }

    public void setDislikedDishesDesc(String dislikedDishesDesc) {
        this.dislikedDishesDesc = dislikedDishesDesc;
    }

    public String getAcceptedTypes() {
        return acceptedTypes;
    }

    public void setAcceptedTypes(String acceptedTypes) {
        this.acceptedTypes = acceptedTypes;
    }

    public String getUnacceptableIngredients() {
        return unacceptableIngredients;
    }

    public void setUnacceptableIngredients(String unacceptableIngredients) {
        this.unacceptableIngredients = unacceptableIngredients;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
