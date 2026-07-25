-- ============================================
-- Graph Workflow Checkpoint Tables - PostgreSQL
-- Used by MyBatisFlexCheckpointSaver
-- ============================================

-- Checkpoint metadata
CREATE TABLE IF NOT EXISTS graph_checkpoint (
    id                    BIGSERIAL     PRIMARY KEY,
    thread_id             VARCHAR(128)  NOT NULL,
    checkpoint_id         VARCHAR(64)   NOT NULL,
    parent_checkpoint_id  VARCHAR(64),
    node_id               VARCHAR(128),
    next_node_id          VARCHAR(128),
    created_at            TIMESTAMP     DEFAULT NOW()
);

-- Checkpoint state blob (separated for large state data)
CREATE TABLE IF NOT EXISTS graph_checkpoint_blob (
    checkpoint_id   VARCHAR(64) PRIMARY KEY,
    state_data      TEXT        NOT NULL
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_checkpoint_thread_id ON graph_checkpoint(thread_id);
CREATE INDEX IF NOT EXISTS idx_checkpoint_checkpoint_id ON graph_checkpoint(checkpoint_id);
CREATE INDEX IF NOT EXISTS idx_checkpoint_thread_checkpoint ON graph_checkpoint(thread_id, checkpoint_id);

COMMENT ON TABLE graph_checkpoint IS '工作流 Checkpoint 元数据表';
COMMENT ON TABLE graph_checkpoint_blob IS '工作流 Checkpoint 状态数据表';
