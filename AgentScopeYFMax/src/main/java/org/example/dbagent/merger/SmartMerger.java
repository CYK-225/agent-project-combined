package org.example.dbagent.merger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dbagent.model.ChangeReport;
import org.example.dbagent.model.ColumnSchema;
import org.example.dbagent.model.TableSchema;
import org.example.dbagent.parser.CodeBlock;
import org.example.dbagent.parser.CodeParser;
import org.example.dbagent.parser.ParsedCode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能合并器
 * 根据变更报告更新AI标记区域，保留人类区域
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartMerger {

    private final CodeParser codeParser;

    // 字段定义正则
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "private\\s+([\\w.<>]+)\\s+(\\w+)\\s*;");

    /**
     * 合并代码
     *
     * @param existingCode 现有代码
     * @param dbSchema     数据库表结构
     * @param changeReport 变更报告
     * @return 合并结果
     */
    public MergeResult merge(String existingCode, TableSchema dbSchema, ChangeReport changeReport) {
        try {
            // 解析现有代码
            ParsedCode parsed = codeParser.parse(existingCode);

            // 更新AI块
            List<String> updatedBlockIds = new ArrayList<>();
            List<String> preservedBlockIds = new ArrayList<>();
            List<String> changeDescriptions = new ArrayList<>();
            Map<String, String> updatedAiBlocks = new LinkedHashMap<>();

            for (CodeBlock aiBlock : parsed.getAiBlocks()) {
                if ("fields".equals(aiBlock.getBlockId())) {
                    // 更新fields块
                    String updatedContent = updateFieldsBlock(aiBlock.getContent(), dbSchema, changeReport);
                    updatedAiBlocks.put(aiBlock.getBlockId(), updatedContent);
                    updatedBlockIds.add(aiBlock.getBlockId());

                    // 记录变更
                    for (ChangeReport.FieldChange change : changeReport.getChanges()) {
                        changeDescriptions.add(change.getType() + ": " + change.getColumnName());
                    }
                } else {
                    // 其他AI块保持不变
                    updatedAiBlocks.put(aiBlock.getBlockId(), aiBlock.getContent());
                    preservedBlockIds.add(aiBlock.getBlockId());
                }
            }

            // 重新组装代码
            String mergedCode = assembleCode(parsed, updatedAiBlocks);

            log.info("代码合并完成: 更新块={}, 保留块={}", updatedBlockIds, preservedBlockIds);

            return MergeResult.builder()
                    .success(true)
                    .mergedCode(mergedCode)
                    .updatedBlocks(updatedBlockIds)
                    .preservedBlocks(preservedBlockIds)
                    .changes(changeDescriptions)
                    .build();

        } catch (Exception e) {
            log.error("代码合并失败", e);
            return MergeResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 更新fields块
     */
    private String updateFieldsBlock(String existingContent, TableSchema dbSchema, ChangeReport changeReport) {
        // 提取现有字段
        Map<String, String> existingFields = new LinkedHashMap<>();
        String[] lines = existingContent.split("\n");
        for (String line : lines) {
            Matcher matcher = FIELD_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                existingFields.put(matcher.group(2), matcher.group(1));
            }
        }

        // 处理删除字段
        for (ChangeReport.FieldChange change : changeReport.getChanges()) {
            if (change.getType() == ChangeReport.ChangeType.REMOVE_FIELD) {
                String camelName = toCamelCase(change.getColumnName());
                existingFields.remove(camelName);
            }
        }

        // 处理新增字段
        for (ColumnSchema col : dbSchema.getColumns()) {
            String camelName = toCamelCase(col.getColumnName());
            if (!existingFields.containsKey(camelName)) {
                existingFields.put(camelName, col.getJavaType());
            }
        }

        // 处理类型变更
        for (ChangeReport.FieldChange change : changeReport.getChanges()) {
            if (change.getType() == ChangeReport.ChangeType.UPDATE_TYPE) {
                String camelName = toCamelCase(change.getColumnName());
                existingFields.put(camelName, change.getNewJavaType());
            }
        }

        // 重新生成fields块
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : existingFields.entrySet()) {
            String fieldName = entry.getKey();
            String javaType = entry.getValue();

            // 查找对应的数据库列信息
            ColumnSchema colInfo = findColumnByFieldName(dbSchema, fieldName);
            if (colInfo != null) {
                sb.append("\n");
                // 添加注释
                if (colInfo.getComment() != null && !colInfo.getComment().isEmpty()) {
                    sb.append("    /** ").append(colInfo.getComment()).append(" */\n");
                }
                // 添加@Column注解
                sb.append("    @Column(value = \"").append(colInfo.getColumnName()).append("\"");
                if ("created_at".equals(colInfo.getColumnName()) || "create_time".equals(colInfo.getColumnName())) {
                    sb.append(", onInsertValue = \"now()\"");
                } else if ("updated_at".equals(colInfo.getColumnName()) || "update_time".equals(colInfo.getColumnName())) {
                    sb.append(", onInsertValue = \"now()\", onUpdateValue = \"now()\"");
                }
                sb.append(")\n");
                // 添加@Id注解（如果是主键）
                if (colInfo.isPrimaryKey()) {
                    sb.append("    @Id(keyType = ").append(colInfo.isAutoIncrement() ? "KeyType.Auto" : "KeyType.None").append(")\n");
                }
                sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n");
            }
        }

        return sb.toString();
    }

    /**
     * 查找对应的数据库列信息
     */
    private ColumnSchema findColumnByFieldName(TableSchema dbSchema, String fieldName) {
        for (ColumnSchema col : dbSchema.getColumns()) {
            if (toCamelCase(col.getColumnName()).equals(fieldName)) {
                return col;
            }
        }
        return null;
    }

    /**
     * 重新组装代码
     */
    private String assembleCode(ParsedCode parsed, Map<String, String> updatedAiBlocks) {
        StringBuilder sb = new StringBuilder();

        // 添加头部
        sb.append(parsed.getHeader());

        // 添加AI块
        for (Map.Entry<String, String> entry : updatedAiBlocks.entrySet()) {
            sb.append("\n    // ========== AI-GENERATED-START: ").append(entry.getKey()).append(" ==========\n");
            sb.append(entry.getValue());
            sb.append("    // ========== AI-GENERATED-END: ").append(entry.getKey()).append(" ==========\n");
        }

        // 添加人类块
        for (CodeBlock humanBlock : parsed.getHumanBlocks()) {
            sb.append("\n    // ========== HUMAN-AREA: ").append(humanBlock.getBlockId()).append(" ==========\n");
            sb.append("    // 人类手写的自定义字段（AI不会修改此区域）\n");
            sb.append(humanBlock.getContent());
            sb.append("    // ========== HUMAN-AREA-END: ").append(humanBlock.getBlockId()).append(" ==========\n");
        }

        // 添加尾部
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 下划线转驼峰
     */
    private String toCamelCase(String snake) {
        if (snake == null || snake.isEmpty()) {
            return snake;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
