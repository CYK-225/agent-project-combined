package com.cyk.Enity.table;

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
 * 用户表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "users")
@Data
public class UsersEntity {

    /**
     * 用户主键ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户名 (唯一)
     */
    @Column(value = "username")
    private String username;

    /**
     * 登录密码
     */
    @Column(value = "password")
    private String password;

    @Column(value = "email")
    private String email;

    /**
     * 用户收藏的楼宇uid数组
     */
    @Column(typeHandler = org.apache.ibatis.type.ArrayTypeHandler.class)
    private String[] favoriteBuildings;

    @Column(value = "is_deleted")
    private Integer isDeleted;

    @Column(value = "create_time")
    private Date createTime;

    @Column(value = "update_time")
    private Date updateTime;
}
