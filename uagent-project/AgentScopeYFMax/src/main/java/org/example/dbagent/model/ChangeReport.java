package org.example.dbagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 变更报告
 * 记录表结构变更的详细信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangeReport {
    /** 表名 */
    private String tableName;
    /** 表注释 */
    private String tableComment;
    /** 变更列表 */
    private List<FieldChange> changes;
    /** 人类区域的字段列表 */
    private List<String> humanFields;
    /** AI区域的字段列表 */
    private List<String> aiFields;

    /**
     * 字段变更详情
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FieldChange {
        /** 变更类型 */
        private ChangeType type;
        /** 列名 */
        private String columnName;
        /** 旧Java类型 */
        private String oldJavaType;
        /** 新Java类型 */
        private String newJavaType;
        /** 旧注释 */
        private String oldComment;
        /** 新注释 */
        private String newComment;
        /** 变更原因 */
        private String reason;
    }

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        /** 新增字段 */
        ADD_FIELD,
        /** 删除字段 */
        REMOVE_FIELD,
        /** 类型变更 */
        UPDATE_TYPE,
        /** 注释变更 */
        UPDATE_COMMENT
    }
}
