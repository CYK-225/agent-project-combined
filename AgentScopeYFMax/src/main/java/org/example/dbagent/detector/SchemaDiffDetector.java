package org.example.dbagent.detector;

import lombok.extern.slf4j.Slf4j;
import org.example.dbagent.model.ChangeReport;
import org.example.dbagent.model.ColumnSchema;
import org.example.dbagent.model.TableSchema;
import org.example.dbagent.parser.CodeBlock;
import org.example.dbagent.parser.ParsedCode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表结构差异检测器
 * 对比数据库表结构和现有Entity代码，生成变更报告
 */
@Slf4j
@Component
public class SchemaDiffDetector {

    // 字段定义正则
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "(?:/\\*.*?\\*/\\s*)?(?:@Column.*\\s+)*private\\s+([\\w.<>]+)\\s+(\\w+)\\s*;");

    /**
     * 检测表结构变更
     *
     * @param dbSchema   数据库表结构
     * @param parsedCode 解析后的代码
     * @return 变更报告
     */
    public ChangeReport detect(TableSchema dbSchema, ParsedCode parsedCode) {
        List<ChangeReport.FieldChange> changes = new ArrayList<>();
        List<String> aiFields = new ArrayList<>();
        List<String> humanFields = new ArrayList<>();

        // 提取AI区域中的字段
        Map<String, String> existingAiFields = extractFieldsFromBlocks(parsedCode.getAiBlocks());
        aiFields.addAll(existingAiFields.keySet());

        // 提取人类区域中的字段
        Map<String, String> existingHumanFields = extractFieldsFromBlocks(parsedCode.getHumanBlocks());
        humanFields.addAll(existingHumanFields.keySet());

        // 获取数据库中的所有列
        Map<String, ColumnSchema> dbColumns = new LinkedHashMap<>();
        for (ColumnSchema col : dbSchema.getColumns()) {
            dbColumns.put(col.getColumnName(), col);
        }

        // 检测新增字段（数据库有，AI区域无）
        for (ColumnSchema dbCol : dbSchema.getColumns()) {
            String camelName = toCamelCase(dbCol.getColumnName());
            if (!existingAiFields.containsKey(camelName) && !existingHumanFields.containsKey(camelName)) {
                changes.add(ChangeReport.FieldChange.builder()
                        .type(ChangeReport.ChangeType.ADD_FIELD)
                        .columnName(dbCol.getColumnName())
                        .newJavaType(dbCol.getJavaType())
                        .newComment(dbCol.getComment())
                        .reason("数据库中存在，代码中未定义")
                        .build());
            }
        }

        // 检测删除字段（AI区域有，数据库无）
        for (String aiFieldName : existingAiFields.keySet()) {
            String snakeName = toSnakeCase(aiFieldName);
            if (!dbColumns.containsKey(snakeName)) {
                changes.add(ChangeReport.FieldChange.builder()
                        .type(ChangeReport.ChangeType.REMOVE_FIELD)
                        .columnName(snakeName)
                        .oldJavaType(existingAiFields.get(aiFieldName))
                        .reason("代码中存在，数据库中已删除")
                        .build());
            }
        }

        // 检测类型变更
        for (ColumnSchema dbCol : dbSchema.getColumns()) {
            String camelName = toCamelCase(dbCol.getColumnName());
            String existingType = existingAiFields.get(camelName);
            if (existingType != null && !existingType.equals(dbCol.getJavaType())) {
                changes.add(ChangeReport.FieldChange.builder()
                        .type(ChangeReport.ChangeType.UPDATE_TYPE)
                        .columnName(dbCol.getColumnName())
                        .oldJavaType(existingType)
                        .newJavaType(dbCol.getJavaType())
                        .reason("数据库类型变更: " + existingType + " -> " + dbCol.getJavaType())
                        .build());
            }
        }

        ChangeReport report = ChangeReport.builder()
                .tableName(dbSchema.getTableName())
                .tableComment(dbSchema.getTableComment())
                .changes(changes)
                .humanFields(humanFields)
                .aiFields(aiFields)
                .build();

        log.info("变更检测完成: 表={}, 变更数={}, AI字段数={}, 人类字段数={}",
                dbSchema.getTableName(), changes.size(), aiFields.size(), humanFields.size());

        return report;
    }

    /**
     * 从代码块中提取字段定义
     *
     * @param blocks 代码块列表
     * @return 字段名 -> Java类型的映射
     */
    private Map<String, String> extractFieldsFromBlocks(List<CodeBlock> blocks) {
        Map<String, String> fields = new LinkedHashMap<>();

        for (CodeBlock block : blocks) {
            String[] lines = block.getContent().split("\n");
            for (String line : lines) {
                Matcher matcher = FIELD_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    String javaType = matcher.group(1);
                    String fieldName = matcher.group(2);
                    fields.put(fieldName, javaType);
                }
            }
        }

        return fields;
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

    /**
     * 驼峰转下划线
     */
    private String toSnakeCase(String camel) {
        if (camel == null || camel.isEmpty()) {
            return camel;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
