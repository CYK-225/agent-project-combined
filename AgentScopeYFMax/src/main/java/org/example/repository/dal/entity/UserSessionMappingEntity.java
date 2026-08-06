package org.example.repository.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 用户会话关联表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "user_session_mapping")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSessionMappingEntity {

    /**
     * 等同于agui里的threadID和agentscope里的会话id
     */
    @Id(keyType = KeyType.None)
    @Column(value = "sessionid")
    private String sessionid;

    /**
     * 用户id
     */
    @Column(value = "userid")
    private String userid;

    /**
     * 会话标题
     */
    @Column(value = "title")
    private String title;

    /**
     * 更新时间
     * onInsertValue = "now()"：插入时自动填充数据库当前时间
     * onUpdateValue = "now()"：更新时自动填充数据库当前时间
     */
    @Column(value = "update_time", onInsertValue = "now()", onUpdateValue = "now()")
    private OffsetDateTime updateTime;

    // 注意：因为你使用了 Lombok 的 @Data，下面的 Getter/Setter 其实都可以删掉，
    // Lombok 会在编译时自动生成它们，保持代码简洁。
    // 如果你一定要手动写，记得把 updateTime 的也加上。
}