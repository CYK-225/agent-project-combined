package org.example.dbagent.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码解析器
 * 识别AI标记区域和人类区域
 */
@Slf4j
@Component
public class CodeParser {

    // AI标记正则
    private static final Pattern AI_START = Pattern.compile(
            "// ========== AI-GENERATED-START: (\\w+) ==========.*");
    private static final Pattern AI_END = Pattern.compile(
            "// ========== AI-GENERATED-END: (\\w+) ==========.*");

    // 人类标记正则
    private static final Pattern HUMAN_START = Pattern.compile(
            "// ========== HUMAN-AREA: (\\w+) ==========.*");
    private static final Pattern HUMAN_END = Pattern.compile(
            "// ========== HUMAN-AREA-END: (\\w+) ==========.*");

    // 包名正则
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([\\w.]+)\\s*;.*");

    // 类名正则
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*public\\s+class\\s+(\\w+).*");

    /**
     * 解析代码文件
     *
     * @param code 原始代码
     * @return 解析结果
     */
    public ParsedCode parse(String code) {
        ParsedCode result = new ParsedCode();
        List<CodeBlock> aiBlocks = new ArrayList<>();
        List<CodeBlock> humanBlocks = new ArrayList<>();

        String[] lines = code.split("\n");
        StringBuilder headerBuilder = new StringBuilder();
        StringBuilder currentBlock = null;
        String currentBlockType = null;
        String currentBlockId = null;
        int blockStartLine = -1;
        boolean inBlock = false;
        boolean headerEnded = false;

        // 用于收集AI区域标记行的列表
        List<String> aiBlockLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 提取包名
            if (result.getPackageName() == null) {
                Matcher pkgMatcher = PACKAGE_PATTERN.matcher(line);
                if (pkgMatcher.matches()) {
                    result.setPackageName(pkgMatcher.group(1));
                }
            }

            // 提取类名
            if (result.getClassName() == null) {
                Matcher classMatcher = CLASS_PATTERN.matcher(line);
                if (classMatcher.matches()) {
                    result.setClassName(classMatcher.group(1));
                }
            }

            // 检查标记
            Matcher aiStart = AI_START.matcher(line);
            Matcher aiEnd = AI_END.matcher(line);
            Matcher humanStart = HUMAN_START.matcher(line);
            Matcher humanEnd = HUMAN_END.matcher(line);

            if (aiStart.matches() && !inBlock) {
                // 开始AI块
                inBlock = true;
                currentBlockType = "AI";
                currentBlockId = aiStart.group(1);
                currentBlock = new StringBuilder();
                blockStartLine = i;
                headerEnded = true;
            } else if (aiEnd.matches() && "AI".equals(currentBlockType)) {
                // 结束AI块
                aiBlocks.add(new CodeBlock(currentBlockId, currentBlock.toString()));
                currentBlock = null;
                currentBlockType = null;
                inBlock = false;
            } else if (humanStart.matches() && !inBlock) {
                // 开始人类块
                inBlock = true;
                currentBlockType = "HUMAN";
                currentBlockId = humanStart.group(1);
                currentBlock = new StringBuilder();
                blockStartLine = i;
                headerEnded = true;
            } else if (humanEnd.matches() && "HUMAN".equals(currentBlockType)) {
                // 结束人类块
                humanBlocks.add(new CodeBlock(currentBlockId, currentBlock.toString()));
                currentBlock = null;
                currentBlockType = null;
                inBlock = false;
            } else if (inBlock && currentBlock != null) {
                // 在块内部
                currentBlock.append(line).append("\n");
            } else if (!headerEnded) {
                // 还在头部区域
                headerBuilder.append(line).append("\n");
            }
        }

        result.setAiBlocks(aiBlocks);
        result.setHumanBlocks(humanBlocks);
        result.setHeader(headerBuilder.toString());
        result.setOriginalCode(code);

        log.debug("代码解析完成: 包名={}, 类名={}, AI块数={}, 人类块数={}",
                result.getPackageName(), result.getClassName(),
                aiBlocks.size(), humanBlocks.size());

        return result;
    }

    /**
     * 从AI代码块中提取字段信息
     *
     * @param aiBlocks AI代码块列表
     * @return 字段名列表
     */
    public List<String> extractFieldNames(List<CodeBlock> aiBlocks) {
        List<String> fieldNames = new ArrayList<>();
        Pattern fieldPattern = Pattern.compile("private\\s+[\\w.<>]+\\s+(\\w+)\\s*;");

        for (CodeBlock block : aiBlocks) {
            String[] lines = block.getContent().split("\n");
            for (String line : lines) {
                Matcher matcher = fieldPattern.matcher(line.trim());
                if (matcher.matches()) {
                    fieldNames.add(matcher.group(1));
                }
            }
        }

        return fieldNames;
    }
}
