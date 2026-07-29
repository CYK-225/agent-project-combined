package com.cyk.Enity;

import com.cyk.Enity.miniPromptsTo;
import lombok.Data;
import java.util.List;

@Data
public class PromptsVO {
    // 注意：请确保这里的类型和你数据库 prompt_id 的实际类型一致（如果是字符串请用 String）
    private Long promptId; 
    private Long userId;
    private String categoryId;
    private String status;
    private String isPublic;
    private String promptTitle;

    /**
     * 嵌套的小提示词数组
     */
    private List<miniPromptsTo> miniPrompts;

    /**
     * 系统提示词数组（step为-1的提示词）
     */
    private List<miniPromptsTo> systemPrompts;
}