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
 * SLIDERS Schema 定义表
 *
 * @see org.example.sliders.mapper.SlidersSchemaMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_schema", schema = "agent_test")
public class SlidersSchemaEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "task_id")
    private String taskId;

    /** Schema 版本号 */
    @Column(value = "schema_version")
    private Integer schemaVersion;

    /** 完整 Tables 定义 JSON */
    @Column(value = "schema_json")
    private String schemaJson;

    /** Schema 生成推理过程 */
    @Column(value = "reasoning")
    private String reasoning;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
