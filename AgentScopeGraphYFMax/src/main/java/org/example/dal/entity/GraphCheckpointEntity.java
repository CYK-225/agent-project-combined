package org.example.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table(value = "graph_checkpoint")
public class GraphCheckpointEntity {
    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "thread_id")
    private String threadId;

    @Column(value = "checkpoint_id")
    private String checkpointId;

    @Column(value = "parent_checkpoint_id")
    private String parentCheckpointId;

    @Column(value = "node_id")
    private String nodeId;

    @Column(value = "next_node_id")
    private String nextNodeId;

    @Column(value = "created_at", onInsertValue = "now()")
    private LocalDateTime createdAt;
}
