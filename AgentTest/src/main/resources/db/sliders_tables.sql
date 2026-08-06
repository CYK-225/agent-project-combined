-- ============================================================
-- SLIDERS 多智能体流水线 — 表结构
-- Schema: agent_test
-- 执行前请先创建 schema:
--   CREATE SCHEMA IF NOT EXISTS agent_test;
-- ============================================================

-- 1. 流水线任务表
CREATE TABLE IF NOT EXISTS agent_test.sliders_task (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL UNIQUE,         -- 业务 ID (UUID)
    thread_id       VARCHAR(128)    NOT NULL,                -- 会话 ID（邮件隔离维度）
    question        TEXT            NOT NULL,                -- 用户问题
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',  -- PENDING / CHUNKING / SCHEMA_INDUCING / EXTRACTING / RECONCILING / ANSWERING / COMPLETED / FAILED
    answer          TEXT,                                     -- 最终答案
    error_message   TEXT,                                     -- 错误信息（如果失败）
    created_at      TIMESTAMP       DEFAULT NOW(),
    updated_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_task_thread ON agent_test.sliders_task (thread_id);
CREATE INDEX IF NOT EXISTS idx_sliders_task_status ON agent_test.sliders_task (status);

-- 2. 文档表
CREATE TABLE IF NOT EXISTS agent_test.sliders_document (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL,                -- 关联 sliders_task.task_id
    document_name   VARCHAR(256)    NOT NULL,                -- 文档名称
    description     TEXT,                                     -- 文档描述（LLM 生成）
    content         TEXT            NOT NULL,                -- 原始文档内容（Markdown）
    file_path       VARCHAR(512),                            -- 文件路径（可选）
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_doc_task ON agent_test.sliders_document (task_id);

-- 3. 文档分块表
CREATE TABLE IF NOT EXISTS agent_test.sliders_chunk (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL,
    document_name   VARCHAR(256)    NOT NULL,                -- 来源文档
    chunk_index     INT             NOT NULL,                -- 块序号（从 0 开始）
    chunk_id        VARCHAR(64)     NOT NULL,                -- 块业务 ID（如 docName_chunkIdx）
    content         TEXT            NOT NULL,                -- 块内容
    chunk_header    VARCHAR(256),                            -- 块标题/章节头
    metadata        JSONB,                                    -- 块元数据（结构标签等）
    is_relevant     BOOLEAN,                                  -- 相关性门控结果
    relevance_reason TEXT,                                    -- 相关性判断理由
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_chunk_task ON agent_test.sliders_chunk (task_id);
CREATE INDEX IF NOT EXISTS idx_sliders_chunk_task_doc ON agent_test.sliders_chunk (task_id, document_name);

-- 4. Schema 定义表
CREATE TABLE IF NOT EXISTS agent_test.sliders_schema (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL,
    schema_version  INT             NOT NULL DEFAULT 1,      -- Schema 版本（可能多次生成）
    schema_json     JSONB           NOT NULL,                -- 完整的 Tables 定义 { tables: [{ name, description, fields: [...] }] }
    reasoning       TEXT,                                     -- Schema 生成推理过程
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_schema_task ON agent_test.sliders_schema (task_id);

-- 5. 提取行数据表（核心数据表）
CREATE TABLE IF NOT EXISTS agent_test.sliders_extracted_row (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL,
    table_name      VARCHAR(128)    NOT NULL,                -- 归属的 Schema 表名
    chunk_id        VARCHAR(64)     NOT NULL,                -- 来源 chunk
    document_name   VARCHAR(256),                            -- 来源文档
    row_index       INT             NOT NULL DEFAULT 0,      -- 块内行序号
    field_data      JSONB           NOT NULL,                -- { "field_name": { "value": ..., "quote": ..., "rationale": ..., "confidence": ... } }
    metadata        JSONB,                                    -- { "chunk_header": ..., "is_placeholder": ... }
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_ext_task ON agent_test.sliders_extracted_row (task_id);
CREATE INDEX IF NOT EXISTS idx_sliders_ext_task_table ON agent_test.sliders_extracted_row (task_id, table_name);

-- 6. 协调结果表（合并后的干净数据）
CREATE TABLE IF NOT EXISTS agent_test.sliders_reconciled_table (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL,
    table_name      VARCHAR(128)    NOT NULL,
    row_index       INT             NOT NULL DEFAULT 0,
    row_data        JSONB           NOT NULL,                -- 合并后的行数据 { "field": "value", ... }
    provenance      JSONB,                                    -- 数据溯源 { "source_chunks": [...], "merge_operations": [...] }
    reconciliation_context TEXT,                              -- 协调上下文（记录了哪些消歧/合并操作）
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_recon_task ON agent_test.sliders_reconciled_table (task_id);
CREATE INDEX IF NOT EXISTS idx_sliders_recon_task_table ON agent_test.sliders_reconciled_table (task_id, table_name);

-- 7. SQL 查询日志表（Answer Agent 的查询记录）
CREATE TABLE IF NOT EXISTS agent_test.sliders_sql_log (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL,
    sql_query       TEXT            NOT NULL,                -- 执行的 SQL
    query_purpose   TEXT,                                     -- 查询目的
    result_summary  TEXT,                                     -- 结果摘要
    is_error        BOOLEAN         DEFAULT FALSE,           -- 是否执行出错
    error_detail    TEXT,                                     -- 错误详情
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_sql_task ON agent_test.sliders_sql_log (task_id);

-- 8. 流水线执行日志表（哪个 Agent 在何时做了什么）
CREATE TABLE IF NOT EXISTS agent_test.sliders_pipeline_log (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         VARCHAR(64)     NOT NULL,
    agent_name      VARCHAR(64)     NOT NULL,                -- Agent 名称
    stage           VARCHAR(32)     NOT NULL,                -- CHUNKING / SCHEMA / EXTRACTION / RECONCILIATION / ANSWER
    action          VARCHAR(64)     NOT NULL,                -- STARTED / COMPLETED / FAILED / MAIL_SENT
    detail          TEXT,                                     -- 详细信息（JSON 或自由文本）
    duration_ms     BIGINT,                                   -- 耗时（毫秒）
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sliders_log_task ON agent_test.sliders_pipeline_log (task_id);
CREATE INDEX IF NOT EXISTS idx_sliders_log_task_stage ON agent_test.sliders_pipeline_log (task_id, stage);
