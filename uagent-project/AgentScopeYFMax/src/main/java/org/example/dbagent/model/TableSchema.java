package org.example.dbagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表结构模型
 * 对应数据库中的一张表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TableSchema {
    /** 表名 */
    private String tableName;
    /** 表注释 */
    private String tableComment;
    /** 列信息列表 */
    private List<ColumnSchema> columns;
    /** 主键列名列表 */
    private List<String> primaryKeys;
}
