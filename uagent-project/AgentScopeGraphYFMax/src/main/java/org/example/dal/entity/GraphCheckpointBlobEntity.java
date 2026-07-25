package org.example.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

@Data
@Table(value = "graph_checkpoint_blob")
public class GraphCheckpointBlobEntity {
    @Id(keyType = KeyType.None)
    @Column(value = "checkpoint_id")
    private String checkpointId;

    @Column(value = "state_data")
    private String stateData;
}
