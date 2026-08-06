package org.example.dbagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 列结构模型
 * 对应表中的一列
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ColumnSchema {
    /** 列名 */
    private String columnName;
    /** 数据库数据类型 */
    private String dataType;
    /** 对应的Java类型 */
    private String javaType;
    /** 是否主键 */
    private boolean primaryKey;
    /** 是否自增 */
    private boolean autoIncrement;
    /** 是否可空 */
    private boolean nullable;
    /** 列注释 */
    private String comment;
    /** 最大长度（VARCHAR等） */
    private Integer maxLength;
    /** 精度（DECIMAL等） */
    private Integer precision;
    /** 小数位数 */
    private Integer scale;
    /** TypeHandler全类名（用于JSONB等复杂类型） */
    private String typeHandler;
    /** JSONB字段映射的目标类名 */
    private String targetClass;
}
