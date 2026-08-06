package org.example.repository.dal.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.table.TableDef;

/**
 * 邮件消息表 TableDef — 手动创建（对应 MailMessageEntity）
 * <p>
 * MyBatis-Flex APT 未自动生成，手动补齐以支持类型安全的 QueryWrapper 构建。
 *
 * @see org.example.repository.dal.entity.MailMessageEntity
 */
public class MailMessageEntityTableDef extends TableDef {

    public static final MailMessageEntityTableDef MAIL_MESSAGE_ENTITY = new MailMessageEntityTableDef();

    /** 自增主键 */
    public final QueryColumn ID = new QueryColumn(this, "id");

    /** 邮件业务 ID（格式：from_序号，如 alice_1） */
    public final QueryColumn MAIL_ID = new QueryColumn(this, "mail_id");

    /** 会话 ID（隔离维度之一） */
    public final QueryColumn THREAD_ID = new QueryColumn(this, "thread_id");

    /** 发件人 Agent 名称 */
    public final QueryColumn FROM_AGENT = new QueryColumn(this, "from_agent");

    /** 收件人 Agent 名称 */
    public final QueryColumn TO_AGENT = new QueryColumn(this, "to_agent");

    /** 邮件主题 */
    public final QueryColumn SUBJECT = new QueryColumn(this, "subject");

    /** 邮件正文 */
    public final QueryColumn BODY = new QueryColumn(this, "body");

    /** 邮件状态：UNREAD / READ / REPLIED / ARCHIVED */
    public final QueryColumn STATUS = new QueryColumn(this, "status");

    /** 关联请求 ID（协议匹配用，可选） */
    public final QueryColumn REQUEST_ID = new QueryColumn(this, "request_id");

    /** 回复的原始邮件 ID */
    public final QueryColumn IN_REPLY_TO = new QueryColumn(this, "in_reply_to");

    public final QueryColumn CREATED_AT = new QueryColumn(this, "created_at");

    public final QueryColumn UPDATED_AT = new QueryColumn(this, "updated_at");

    /** 所有字段 */
    public final QueryColumn ALL_COLUMNS = new QueryColumn(this, "*");

    /** 默认字段 */
    public final QueryColumn[] DEFAULT_COLUMNS = new QueryColumn[]{
            ID, MAIL_ID, THREAD_ID, FROM_AGENT, TO_AGENT,
            SUBJECT, BODY, STATUS, REQUEST_ID, IN_REPLY_TO,
            CREATED_AT, UPDATED_AT
    };

    public MailMessageEntityTableDef() {
        super("", "mail_message");
    }

    private MailMessageEntityTableDef(String schema, String name, String alias) {
        super(schema, name, alias);
    }

    public MailMessageEntityTableDef as(String alias) {
        String key = getNameWithSchema() + "." + alias;
        return getCache(key, k -> new MailMessageEntityTableDef("", "mail_message", alias));
    }
}
