package org.example.agentScope.mas.mailbox.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮件消息体 — 纯内存 POJO
 * <p>
 * 持久化由 {@link MailboxCenter} 通过 {@code mail_message} 表承担，
 * 不实现 State / StateModule。
 * <p>
 * 邮件 ID 采用 {@code <发件人>_<收件箱内序号>} 格式（如 alice_1、bob_3），
 * 语义即 ID，LLM 几乎不可能拼错。
 *
 * @see MailboxState
 * @see MailboxCenter
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mail {

    /** 邮件唯一 ID，格式：from_序号（如 alice_1） */
    private String id;

    /** 发件人 Agent 名称 */
    private String from;

    /** 收件人 Agent 名称 */
    private String to;

    /** 主题 */
    private String subject;

    /** 正文 */
    private String body;

    /** 邮件状态：UNREAD / READ / REPLIED / ARCHIVED */
    private String status;

    /** 创建时间（epoch millis，便于 JSON 序列化） */
    private long createdAt;

    /** 关联请求 ID（用于协议匹配，可选） */
    private String requestId;

    /** 原始邮件 ID（回复时引用） */
    private String inReplyTo;

    // ======================== 工厂方法 ========================

    /**
     * 创建新邮件（ID 由 MailboxCenter.send() 设置）
     */
    public static Mail create(String from, String to, String subject, String body) {
        return Mail.builder()
                .from(from)
                .to(to)
                .subject(subject)
                .body(body)
                .status("UNREAD")
                .createdAt(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建回复邮件（收件人变发件人，透传 requestId）
     */
    public static Mail reply(Mail original, String body) {
        return Mail.builder()
                .from(original.getTo())
                .to(original.getFrom())
                .subject("Re: " + original.getSubject())
                .body(body)
                .status("UNREAD")
                .createdAt(System.currentTimeMillis())
                .inReplyTo(original.getId())
                .requestId(original.getRequestId())
                .build();
    }
}
