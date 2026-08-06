package org.example.skillOpt.env;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具执行反馈 — 工具调用后的返回结果。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolFeedback {

    /** 工具是否执行成功 */
    private boolean success;

    /** 工具返回的文本内容 */
    private String content;

    /** 工具返回的结构化数据（可选） */
    private Map<String, Object> metadata;
}
