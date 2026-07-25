package com.cyk.Enity;

import lombok.Data;

import java.util.List;

@Data
public class addPromptsTo {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 分类id
     */
    private String categoryId;

    /**
     * 状态(0，草稿 ； 1，已保存)
     */
    private String status;

    /**
     * 是否公开(0，私有 ； 1，公开)
     */
    private String isPublic;

    /**
     * 大提示词标题
     */
    private String promptTitle;

    /**
     * 小提示词数组
     */
    private List<miniPromptsTo> miniPrompts;

    /**
     * 系统提示词数组（step强制为-1）
     */
    private List<miniPromptsTo> systemPrompts;
}
