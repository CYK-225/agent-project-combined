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
 * SLIDERS SQL 查询日志表
 *
 * @see org.example.sliders.mapper.SlidersSqlLogMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_sql_log", schema = "agent_test")
public class SlidersSqlLogEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "task_id")
    private String taskId;

    /** 执行的 SQL */
    @Column(value = "sql_query")
    private String sqlQuery;

    /** 查询目的 */
    @Column(value = "query_purpose")
    private String queryPurpose;

    /** 结果摘要 */
    @Column(value = "result_summary")
    private String resultSummary;

    @Column(value = "is_error")
    private Boolean isError;

    @Column(value = "error_detail")
    private String errorDetail;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
