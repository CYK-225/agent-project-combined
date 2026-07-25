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
 * 提示词管理表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "prompts")
@Data
public class PromptsEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 创建者用户ID (关联users表)
     */
    @Column(value = "user_id")
    private Long userId;

    /**
     * 所属分类ID (关联categories表)
     */
    @Column(value = "category_id")
    private Long categoryId;

    /**
     * 提示词标题
     */
    @Column(value = "title")
    private String title;

    /**
     * 提示词具体内容
     */
    @Column(value = "content")
    private String content;

    /**
     * 提示词状态: 0-草稿(创造模式测试中), 1-已保存(正式落库)
     */
    @Column(value = "status")
    private Integer status;

    /**
     * 可见性: 0-私有(仅自己可见), 1-公开(所有用户可见可用)
     */
    @Column(value = "is_public")
    private Integer isPublic = 0;

    /**
     * 删除标识: 0-正常, 1-删除
     */
    @Column(value = "is_deleted")
    private Integer isDeleted = 0;

    /**
     * 被使用次数 (用于公开提示词的热门排序)
     */
    @Column(value = "use_count")
    private Integer useCount = 0;

    @Column(onInsertValue = "CURRENT_TIMESTAMP")
    private Date createTime;

    @Column(onInsertValue = "CURRENT_TIMESTAMP", onUpdateValue = "CURRENT_TIMESTAMP")
    private Date updateTime;

    /**
     * 大提示词id
     */
    @Column(value = "prompt_id")
    private Long promptId;

    /**
     * 步骤序号（系统提示词为-1，执行步骤为1,2,3...）
     */
    @Column(value = "step")
    private Integer step;

    /**
     * 提示词类型（1-角色类型 2-物理规则类型 3-步骤类型 4-背景知识类型 5-步骤输出类型）
     */
    @Column(value = "type")
    private Integer type;

    @Column(value = "prompt_title")
    private String promptTitle;

}