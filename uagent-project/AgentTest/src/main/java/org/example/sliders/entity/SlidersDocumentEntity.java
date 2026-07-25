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
 * SLIDERS 文档表
 *
 * @see org.example.sliders.mapper.SlidersDocumentMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "sliders_document", schema = "agent_test")
public class SlidersDocumentEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "task_id")
    private String taskId;

    @Column(value = "document_name")
    private String documentName;

    @Column(value = "description")
    private String description;

    /** 原始文档内容（Markdown） */
    @Column(value = "content")
    private String content;

    @Column(value = "file_path")
    private String filePath;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;
}
