package org.example.dbagent.parser;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 代码块模型
 * 表示一段被标记的代码区域
 */
@Data
@AllArgsConstructor
public class CodeBlock {
    /** 块ID（如 fields, constants, methods 等） */
    private String blockId;
    /** 代码内容 */
    private String content;
}
