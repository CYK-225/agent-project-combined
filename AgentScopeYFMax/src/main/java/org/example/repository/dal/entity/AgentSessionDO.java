package org.example.repository.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agentscope_sessions")
public class AgentSessionDO {

    @Id(keyType = KeyType.None) // 联合主键，不自增
    private String sessionId;

    @Id(keyType = KeyType.None)
    private String stateKey;

    @Id(keyType = KeyType.None)
    private Integer itemIndex;

    private String stateData;



    @Column(onInsertValue = "now()")
    private Timestamp createdAt;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private Timestamp updatedAt;
}