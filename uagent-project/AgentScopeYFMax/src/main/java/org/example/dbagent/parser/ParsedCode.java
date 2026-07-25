package org.example.dbagent.parser;

import lombok.Data;

import java.util.List;

/**
 * 代码解析结果
 * 包含解析后的各个部分
 */
@Data
public class ParsedCode {
    /** 包名 */
    private String packageName;
    /** 类名 */
    private String className;
    /** AI标记的代码块列表 */
    private List<CodeBlock> aiBlocks;
    /** 人类标记的代码块列表 */
    private List<CodeBlock> humanBlocks;
    /** 文件头部（package + import + 类声明） */
    private String header;
    /** 文件尾部（类结束括号） */
    private String footer;
    /** 原始代码 */
    private String originalCode;
}
