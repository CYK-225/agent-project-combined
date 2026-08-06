package org.example.repository.dal.entity;

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
 * 邮件消息表 实体类
 * <p>
 * 独立邮件存储，替代之前复用 agentscope_sessions 的 KV 模式。
 * 每封邮件一行，支持 SQL 灵活查询。
 * <p>
 * 表位于 postgresql-session 数据源，schema = session。
 *
 * @see org.example.repository.dal.mapper.MailMessageMapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(value = "mail_message")
public class MailMessageEntity {

    /** 自增主键 */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 邮件业务 ID（格式：from_序号，如 alice_1） */
    @Column(value = "mail_id")
    private String mailId;

    /** 会话 ID（隔离维度之一） */
    @Column(value = "thread_id")
    private String threadId;

    /** 发件人 Agent 名称 */
    @Column(value = "from_agent")
    private String fromAgent;

    /** 收件人 Agent 名称 */
    @Column(value = "to_agent")
    private String toAgent;

    /** 邮件主题 */
    @Column(value = "subject")
    private String subject;

    /** 邮件正文 */
    @Column(value = "body")
    private String body;

    /** 邮件状态：UNREAD / READ / REPLIED / ARCHIVED */
    @Column(value = "status")
    private String status;

    /** 关联请求 ID（协议匹配用，可选） */
    @Column(value = "request_id")
    private String requestId;

    /** 回复的原始邮件 ID */
    @Column(value = "in_reply_to")
    private String inReplyTo;

    @Column(value = "created_at", onInsertValue = "now()")
    private Date createdAt;

    @Column(value = "updated_at", onInsertValue = "now()", onUpdateValue = "now()")
    private Date updatedAt;
}
