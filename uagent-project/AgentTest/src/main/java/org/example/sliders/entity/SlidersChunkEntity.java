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
 * SLIDERS 文档分块表
 *
 * @see org.example.sliders.mapper.SlidersChunkMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_chunk", schema = "agent_test")
public class SlidersChunkEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "task_id")
    private String taskId;

    @Column(value = "document_name")
    private String documentName;

    /** 块序号（从 0 开始） */
    @Column(value = "chunk_index")
    private Integer chunkIndex;

    /** 块业务 ID */
    @Column(value = "chunk_id")
    private String chunkId;

    /** 块内容 */
    @Column(value = "content")
    private String content;

    /** 块标题/章节头 */
    @Column(value = "chunk_header")
    private String chunkHeader;

    /** 块元数据 JSON */
    @Column(value = "metadata")
    private String metadata;

    /** 相关性门控结果 */
    @Column(value = "is_relevant")
    private Boolean isRelevant;

    /** 相关性判断理由 */
    @Column(value = "relevance_reason")
    private String relevanceReason;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
