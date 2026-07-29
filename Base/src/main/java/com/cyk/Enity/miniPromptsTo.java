package com.cyk.Enity;

import lombok.Data;

@Data
public class miniPromptsTo {

    /**
     * 小提示词id
     */
    private String id;

    /**
     * 小提示词内容
     */
    private String content;

    /**
     * 小提示词步骤
     */
    private Integer step;

    /**
     * 小提示词标题
     */
    private String title;
}
