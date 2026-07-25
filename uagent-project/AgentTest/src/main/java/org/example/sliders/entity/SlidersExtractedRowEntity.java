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
 * SLIDERS 提取行数据表（核心数据表）
 *
 * @see org.example.sliders.mapper.SlidersExtractedRowMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_extracted_row", schema = "agent_test")
public class SlidersExtractedRowEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "task_id")
    private String taskId;

    /** 归属的 Schema 表名 */
    @Column(value = "table_name")
    private String tableName;

    /** 来源 chunk */
    @Column(value = "chunk_id")
    private String chunkId;

    @Column(value = "document_name")
    private String documentName;

    /** 块内行序号 */
    @Column(value = "row_index")
    private Integer rowIndex;

    /** 字段数据 JSON: { "field_name": { "value", "quote", "rationale", "confidence" } } */
    @Column(value = "field_data")
    private String fieldData;

    /** 元数据 JSON */
    @Column(value = "metadata")
    private String metadata;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
