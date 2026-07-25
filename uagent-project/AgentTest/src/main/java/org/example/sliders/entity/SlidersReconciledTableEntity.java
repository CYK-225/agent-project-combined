package org.example.sliders.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * SLIDERS 协调结果表（合并后的干净数据）
 *
 * @see org.example.sliders.mapper.SlidersReconciledTableMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_reconciled_table", schema = "agent_test")
public class SlidersReconciledTableEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "task_id")
    private String taskId;

    /** 归属表名 */
    @Column(value = "table_name")
    private String tableName;

    /** 行序号 */
    @Column(value = "row_index")
    private Integer rowIndex;

    /** 合并后的行数据 JSON */
    @Column(value = "row_data")
    private String rowData;

    /** 数据溯源 JSON */
    @Column(value = "provenance")
    private String provenance;

    /** 协调上下文 */
    @Column(value = "reconciliation_context")
    private String reconciliationContext;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
