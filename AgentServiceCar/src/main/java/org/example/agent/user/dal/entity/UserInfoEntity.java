package org.example.agent.user.dal.entity;

import com.mybatisflex.annotation.*;
import lombok.Data;

import java.lang.Long;
import java.util.Date;
import java.lang.String;

/**
 * 用户信息表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "user_info")
@Data
public class UserInfoEntity {

    /**
     * 用户ID
     */
    @Id(keyType = KeyType.Auto)
    private Long userId;

    /**
     * 用户真实名称
     */
    @Column(value = "real_name")
    private String realName;

    /**
     * 用户类型
     */
    @Column(value = "user_type")
    private String userType;

    /**
     * 用户所属部门
     */
    @Column(value = "department")
    private String department;

    /**
     * 用户出生省份
     */
    @Column(value = "birth_province")
    private String birthProvince;

    /**
     * 用户喜好ID
     */
    @Column(value = "preference_id")
    private Long preferenceId;

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

    @RelationOneToOne(selfField = "preferenceId", targetField = "preferenceId")
    private UserPreferenceBiasEntity userPreferenceBias;


}
