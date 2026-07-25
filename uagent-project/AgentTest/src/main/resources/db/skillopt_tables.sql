-- ============================================================
-- SkillOpt — Skill 文本空间优化器 表结构
-- Schema: agent_test
-- 依赖: 请先创建 schema
--   CREATE SCHEMA IF NOT EXISTS agent_test;
-- ============================================================

-- 1. 训练任务表
CREATE TABLE IF NOT EXISTS agent_test.skillopt_training_job (
    id                      BIGSERIAL       PRIMARY KEY,
    job_id                  VARCHAR(64)     NOT NULL UNIQUE,
    task_description        TEXT            NOT NULL,
    initial_skill           TEXT,
    train_data              TEXT,
    val_data                TEXT,
    env_adapter_type        VARCHAR(32)     NOT NULL DEFAULT 'llm-qa',
    lr_scheduler_type       VARCHAR(32)     NOT NULL DEFAULT 'cosine',
    gate_type               VARCHAR(32)     NOT NULL DEFAULT 'mixed',
    max_epochs              INT             NOT NULL DEFAULT 5,
    batch_size              INT             NOT NULL DEFAULT 4,
    edit_budget_base        INT             NOT NULL DEFAULT 5,
    current_epoch           INT             NOT NULL DEFAULT 0,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    best_skill_content      TEXT,
    best_skill_epoch        INT,
    best_validation_score   DOUBLE PRECISION,
    final_meta_skill        TEXT,
    token_estimate          BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMP       DEFAULT NOW(),
    updated_at              TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE agent_test.skillopt_training_job IS 'SkillOpt 训练任务：6阶段 Rollout→Reflect→Aggregate→Select→Update→Evaluate';

CREATE INDEX IF NOT EXISTS idx_skillopt_job_status ON agent_test.skillopt_training_job (status);
CREATE INDEX IF NOT EXISTS idx_skillopt_job_created ON agent_test.skillopt_training_job (created_at DESC);

-- 2. Epoch 记录表
CREATE TABLE IF NOT EXISTS agent_test.skillopt_epoch (
    id                      BIGSERIAL       PRIMARY KEY,
    job_id                  VARCHAR(64)     NOT NULL,
    epoch                   INT             NOT NULL,
    skill_before            TEXT,
    skill_after             TEXT,
    candidate_skill         TEXT,
    train_hard_score        DOUBLE PRECISION,
    train_soft_score        DOUBLE PRECISION,
    validation_score        DOUBLE PRECISION,
    previous_validation_score DOUBLE PRECISION,
    gate_accepted           BOOLEAN,
    gate_type               VARCHAR(32),
    edit_budget             INT,
    actual_edit_count       INT,
    protected_regions       TEXT,
    meta_skill              TEXT,
    epoch_summary           TEXT,
    created_at              TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE agent_test.skillopt_epoch IS '每个 epoch 的训练记录，含 skill 变化和门控判定';

CREATE INDEX IF NOT EXISTS idx_skillopt_epoch_job ON agent_test.skillopt_epoch (job_id);
CREATE INDEX IF NOT EXISTS idx_skillopt_epoch_job_epoch ON agent_test.skillopt_epoch (job_id, epoch);

-- 3. 候选 Skill 快照表
CREATE TABLE IF NOT EXISTS agent_test.skillopt_candidate (
    id                      BIGSERIAL       PRIMARY KEY,
    job_id                  VARCHAR(64)     NOT NULL,
    epoch                   INT             NOT NULL,
    candidate_skill         TEXT,
    edits_applied           TEXT,
    validation_score        DOUBLE PRECISION,
    gate_accepted           BOOLEAN,
    rejection_reason        TEXT,
    is_best                 BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE agent_test.skillopt_candidate IS '每个 epoch 的候选 Skill 快照（含门控通过/拒绝记录）';

CREATE INDEX IF NOT EXISTS idx_skillopt_candidate_job ON agent_test.skillopt_candidate (job_id);
CREATE INDEX IF NOT EXISTS idx_skillopt_candidate_best ON agent_test.skillopt_candidate (job_id) WHERE is_best = TRUE;

-- 4. Rollout 结果表
CREATE TABLE IF NOT EXISTS agent_test.skillopt_rollout_result (
    id                      BIGSERIAL       PRIMARY KEY,
    job_id                  VARCHAR(64)     NOT NULL,
    epoch                   INT             NOT NULL,
    variant_index           INT             NOT NULL,
    rollout_type            VARCHAR(16)     NOT NULL DEFAULT 'train',
    task_instance           TEXT,
    status                  VARCHAR(32),
    hard_score              DOUBLE PRECISION,
    soft_score              DOUBLE PRECISION,
    trajectory              TEXT,
    agent_response          TEXT,
    created_at              TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE agent_test.skillopt_rollout_result IS '每次 rollout 的执行结果和轨迹';

CREATE INDEX IF NOT EXISTS idx_skillopt_rollout_job_epoch ON agent_test.skillopt_rollout_result (job_id, epoch);

-- 5. 编辑操作日志表
CREATE TABLE IF NOT EXISTS agent_test.skillopt_edit_log (
    id                      BIGSERIAL       PRIMARY KEY,
    job_id                  VARCHAR(64)     NOT NULL,
    epoch                   INT             NOT NULL,
    edit_index              INT             NOT NULL,
    edit_type               VARCHAR(32)     NOT NULL,
    target_section          VARCHAR(256),
    content                 TEXT,
    rationale               TEXT,
    priority                DOUBLE PRECISION,
    applied                 BOOLEAN         NOT NULL DEFAULT TRUE,
    skipped_reason          TEXT,
    created_at              TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE agent_test.skillopt_edit_log IS '每个 epoch 的编辑操作日志（Append/Insert/Replace/Delete）';

CREATE INDEX IF NOT EXISTS idx_skillopt_edit_job_epoch ON agent_test.skillopt_edit_log (job_id, epoch);
