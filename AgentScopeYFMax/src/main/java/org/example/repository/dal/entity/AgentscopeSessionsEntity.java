package org.example.repository.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.util.Date;
import java.lang.String;
import java.lang.Integer;

/**
 * AgentScope 会话持久化存储表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Data
@Table(value = "agentscope_sessions")
public class AgentscopeSessionsEntity {

    /**
     * 会话ID
     */
    @Id(keyType = KeyType.None)
    @Column(value = "session_id")
    private String sessionId;

    /**
     * 状态键名(如 history, memory)
     */
    @Id(keyType = KeyType.None)
    @Column(value = "state_key")
    private String stateKey;

    /**
     * 列表索引(0表示单对象，>0表示列表项)
     */
    @Id(keyType = KeyType.None)
    @Column(value = "item_index")
    private Integer itemIndex;

    /**
     * JSON序列化后的状态数据
     */
    @Column(value = "state_data")
    private String stateData;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;

    @Column(value = "updated_at", onInsertValue = "now()", onUpdateValue = "now()")
    private Date updatedAt;


}
