-- ============================================================
-- 邮件消息表 — 在 PostgreSQL session schema 下执行
-- 数据源: postgresql-session (schema=session)
-- ============================================================

CREATE TABLE IF NOT EXISTS session.mail_message (
    id           BIGSERIAL    PRIMARY KEY,
    mail_id      VARCHAR(64)  NOT NULL,            -- 业务 ID，如 alice_1
    thread_id    VARCHAR(128) NOT NULL,             -- 会话 ID（隔离维度之一）
    from_agent   VARCHAR(64)  NOT NULL,             -- 发件人 Agent 名称
    to_agent     VARCHAR(64)  NOT NULL,             -- 收件人 Agent 名称
    subject      VARCHAR(256),                      -- 邮件主题
    body         TEXT,                               -- 邮件正文
    status       VARCHAR(16)  DEFAULT 'UNREAD',     -- UNREAD / READ / REPLIED / ARCHIVED
    request_id   VARCHAR(128),                      -- 关联请求 ID（协议匹配用，可选）
    in_reply_to  VARCHAR(64),                        -- 回复的原始邮件 ID
    created_at   TIMESTAMP    DEFAULT NOW(),
    updated_at   TIMESTAMP    DEFAULT NOW()
);

-- 按会话+收件人查询（上线恢复、收件箱查询）
CREATE INDEX IF NOT EXISTS idx_mail_thread_to
    ON session.mail_message (thread_id, to_agent);

-- 按会话+发件人查询（发件箱查询）
CREATE INDEX IF NOT EXISTS idx_mail_thread_from
    ON session.mail_message (thread_id, from_agent);

-- 按 mail_id 精确查找（更新状态、标记已读）
CREATE INDEX IF NOT EXISTS idx_mail_mail_id
    ON session.mail_message (mail_id);
