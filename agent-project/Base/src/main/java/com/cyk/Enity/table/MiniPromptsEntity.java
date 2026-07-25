package com.cyk.Enity.table;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;
import java.lang.Object;
import java.lang.String;
import java.lang.Integer;

/**
 * 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Data
@Table(value = "mini_prompts")
public class MiniPromptsEntity {

    /**
     * 主键id
     */
    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * 小提示词标题
     */
    @Column(value = "title")
    private String title;

    /**
     * 提示词内容
     */
    @Column(value = "content")
    private String content;

    /**
     * 提示词类型
     */
    @Column(value = "type")
    private String type;

    /**
     * 所属用户id
     */
    @Column(value = "user_id")
    private String userId;

    /**
     * 创建时间
     */
    @Column(onInsertValue = "CURRENT_TIMESTAMP")
    private Timestamp createTime;

    /**
     * 修改时间
     */
    @Column(onInsertValue = "CURRENT_TIMESTAMP", onUpdateValue = "CURRENT_TIMESTAMP")
    private Timestamp updateTime;

    /**
     * 是否被被删除
     */
    @Column(value = "is_deleted")
    private Integer isDeleted = 0;

    /**
     * 可见性: 0-私有(仅自己可见), 1-公开(所有用户可见可用)
     */
    @Column(value = "is_public")
    private Integer isPublic = 0;

}
