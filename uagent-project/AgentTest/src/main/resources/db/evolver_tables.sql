-- ============================================================
-- SkillEvolver — Skill 进化循环 表结构
-- Schema: agent_test
-- 依赖: 请先执行 sliders_tables.sql 创建 schema
--   CREATE SCHEMA IF NOT EXISTS agent_test;
-- ============================================================

-- 1. 进化任务表
CREATE TABLE IF NOT EXISTS agent_test.evolver_task (
    id                  BIGSERIAL       PRIMARY KEY,
    task_id             VARCHAR(64)     NOT NULL UNIQUE,         -- 业务 ID (snowflake)
    task_name           VARCHAR(256),                            -- 任务名称（人类可读）
    instruction         TEXT            NOT NULL,                -- 任务指令（Agent 要完成的目标描述）
    task_data           JSONB,                                    -- 任务输入数据（文件路径或内联数据）
    verifier            JSONB,                                    -- 验证规则（文件存在性、关键词匹配、exit code 等）
    reward_mode         VARCHAR(32)     NOT NULL DEFAULT 'discrete',  -- discrete(pass/fail) / continuous(标量奖励)
    max_iterations      INT             NOT NULL DEFAULT 2,      -- 总迭代轮数 R
    n_exploration       INT             NOT NULL DEFAULT 4,      -- 每轮探索 trial 数 K
    n_validation        INT             NOT NULL DEFAULT 5,      -- 最终验证 trial 数
    current_iteration   INT             NOT NULL DEFAULT 0,      -- 当前迭代轮次（从 0 开始）
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING',  -- PENDING / RUNNING / COMPLETED / FAILED
    best_skill_version  VARCHAR(32),                              -- 最佳 skill 版本号（如 v2）
    best_skill_content  TEXT,                                     -- 最佳 SKILL.md markdown
    best_reward         DOUBLE PRECISION,                        -- 最佳 skill 的平均奖励分数
    validation_pass_rate DOUBLE PRECISION,                       -- 最终验证通过率 (0.0~1.0)
    token_estimate      BIGINT          NOT NULL DEFAULT 0,      -- 累积 token 消耗估算
    created_at          TIMESTAMP       DEFAULT NOW(),
    updated_at          TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE agent_test.evolver_task IS 'Skill 进化任务：Explore→Analyze→Update 循环';

CREATE INDEX IF NOT EXISTS idx_evolver_task_status ON agent_test.evolver_task (status);
CREATE INDEX IF NOT EXISTS idx_evolver_task_created ON agent_test.evolver_task (created_at DESC);

-- 2. Skill 版本快照表
CREATE TABLE IF NOT EXISTS agent_test.evolver_skill_version (
    id              BIGSERIAL       PRIMARY KEY,
    version_id      VARCHAR(64)     NOT NULL UNIQUE,         -- 业务 ID (snowflake)
    task_id         VARCHAR(64)     NOT NULL,                -- 关联 evolver_task.task_id
    iteration       INT             NOT NULL,                -- 迭代号（从 0 开始）
    version_label   VARCHAR(32),                             -- 版本标签（v0, v1, v2）
    variant_index   INT             NOT NULL DEFAULT 0,      -- 策略变体序号（1,2,3...；合并版为 0）
    skill_markdown  TEXT,                                     -- SKILL.md 内容
    pass_rate       DOUBLE PRECISION,                        -- 探索通过率 (0.0~1.0)
    mean_reward     DOUBLE PRECISION,                        -- 平均奖励分数
    success_traces  JSONB,                                    -- 成功 trace 摘要数组
    failure_traces  JSONB,                                    -- 失败 trace 摘要数组
    analysis        TEXT,                                     -- LLM 差异分析结论
    is_best         BOOLEAN         NOT NULL DEFAULT FALSE,   -- 是否为最终选定的最佳版本
    created_at      TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE agent_test.evolver_skill_version IS '每轮迭代产出的 SKILL.md 版本快照';

CREATE INDEX IF NOT EXISTS idx_evolver_version_task ON agent_test.evolver_skill_version (task_id);
CREATE INDEX IF NOT EXISTS idx_evolver_version_iter ON agent_test.evolver_skill_version (task_id, iteration);
CREATE INDEX IF NOT EXISTS idx_evolver_version_best ON agent_test.evolver_skill_version (task_id) WHERE is_best = TRUE;
