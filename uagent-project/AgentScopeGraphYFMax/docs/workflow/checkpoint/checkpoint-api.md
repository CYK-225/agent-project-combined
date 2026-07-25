# 检查点 API

## 概述

检查点持久化机制，支持内存和 PostgreSQL 两种策略。

## 类

```
graph.workflow.checkpoint
├── CheckpointFactory           ← 检查点工厂
└── MyBatisFlexCheckpointSaver  ← PostgreSQL 实现
```

## CheckpointFactory

```java
@Component
public class CheckpointFactory {
    // postgresSaver 通过 @Autowired(required = false) 注入
    public CheckpointFactory(@Autowired(required = false) MyBatisFlexCheckpointSaver postgresSaver);

    public BaseCheckpointSaver create(String strategy);
}
```

| 策略 | 结果 | 说明 |
|------|------|------|
| `"memory"` | `MemorySaver`（单例缓存） | 进程内，开发测试 |
| `"postgres"` | `MyBatisFlexCheckpointSaver` | PostgreSQL 持久化 |
| `null` / `""` | `null` | 无检查点 |

## MyBatisFlexCheckpointSaver

基于 MyBatis-Flex + PostgreSQL 的双表设计。

```java
@Component
public class MyBatisFlexCheckpointSaver implements BaseCheckpointSaver {
    Collection<Checkpoint> list(RunnableConfig config);
    Optional<Checkpoint> get(RunnableConfig config);
    RunnableConfig put(RunnableConfig config, Checkpoint checkpoint);
    Tag release(RunnableConfig config);
}
```

### 双表设计

- `graph_checkpoint` — 元数据（thread_id, checkpoint_id, node_id, next_node_id, created_at）
- `graph_checkpoint_blob` — 状态数据（checkpoint_id, state_data TEXT）

## 配置

```yaml
graph:
  workflow:
    engine:
      default-checkpoint-strategy: postgres
```

## DDL

```sql
-- src/main/resources/db/checkpoint-postgresql.sql
CREATE TABLE graph_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    thread_id VARCHAR(255) NOT NULL,
    checkpoint_id VARCHAR(255) NOT NULL,
    parent_checkpoint_id VARCHAR(255),
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE graph_checkpoint_blob (
    checkpoint_id VARCHAR(255) PRIMARY KEY,
    state_data TEXT
);
```
