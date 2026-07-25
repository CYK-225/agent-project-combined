package org.example.graph.workflow.checkpoint;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dal.entity.GraphCheckpointBlobEntity;
import org.example.dal.entity.GraphCheckpointEntity;
import org.example.dal.mapper.GraphCheckpointBlobMapper;
import org.example.dal.mapper.GraphCheckpointMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis-Flex + PostgreSQL 的 BaseCheckpointSaver 实现。
 * 通过 IoC 注入到 CheckpointFactory，用于 "postgres" 策略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MyBatisFlexCheckpointSaver implements BaseCheckpointSaver {

    private final GraphCheckpointMapper checkpointMapper;
    private final GraphCheckpointBlobMapper blobMapper;

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        String threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        List<GraphCheckpointEntity> entities = checkpointMapper.selectListByQuery(
                QueryWrapper.create()
                        .where("thread_id", threadId)
                        .orderBy("created_at ASC")
        );

        return entities.stream()
                .map(this::entityToCheckpoint)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        String threadId = config.threadId().orElse(THREAD_ID_DEFAULT);
        Optional<String> checkpointId = config.checkPointId();

        if (checkpointId.isPresent()) {
            return getCheckpointById(threadId, checkpointId.get());
        }
        return getLatestCheckpoint(threadId);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) {
        String threadId = config.threadId().orElse(THREAD_ID_DEFAULT);
        String checkpointId = UUID.randomUUID().toString();

        // 保存元数据
        GraphCheckpointEntity entity = new GraphCheckpointEntity();
        entity.setThreadId(threadId);
        entity.setCheckpointId(checkpointId);
        entity.setParentCheckpointId(config.checkPointId().orElse(null));
        entity.setNodeId(checkpoint.getNodeId());
        entity.setNextNodeId(checkpoint.getNextNodeId());
        checkpointMapper.insert(entity);

        // 保存状态数据
        GraphCheckpointBlobEntity blob = new GraphCheckpointBlobEntity();
        blob.setCheckpointId(checkpointId);
        blob.setStateData(JSON.toJSONString(checkpoint.getState()));
        blobMapper.insert(blob);

        // 返回包含新检查点ID的更新配置
        return RunnableConfig.builder(config)
                .checkPointId(checkpointId)
                .build();
    }

    @Override
    public Tag release(RunnableConfig config) {
        String threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        List<GraphCheckpointEntity> entities = checkpointMapper.selectListByQuery(
                QueryWrapper.create().where("thread_id", threadId)
        );

        for (GraphCheckpointEntity entity : entities) {
            blobMapper.deleteByQuery(
                    QueryWrapper.create().where("checkpoint_id", entity.getCheckpointId())
            );
        }
        checkpointMapper.deleteByQuery(
                QueryWrapper.create().where("thread_id", threadId)
        );

        return new Tag(threadId, Collections.emptyList());
    }

    // ==================== 内部方法 ====================

    private Optional<Checkpoint> getCheckpointById(String threadId, String checkpointId) {
        GraphCheckpointEntity entity = checkpointMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("thread_id", threadId)
                        .and("checkpoint_id", checkpointId)
        );
        if (entity == null) return Optional.empty();
        return Optional.ofNullable(entityToCheckpoint(entity));
    }

    private Optional<Checkpoint> getLatestCheckpoint(String threadId) {
        GraphCheckpointEntity entity = checkpointMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("thread_id", threadId)
                        .orderBy("created_at DESC")
                        .limit(1)
        );
        if (entity == null) return Optional.empty();
        return Optional.ofNullable(entityToCheckpoint(entity));
    }

    private Checkpoint entityToCheckpoint(GraphCheckpointEntity entity) {
        GraphCheckpointBlobEntity blob = blobMapper.selectOneByQuery(
                QueryWrapper.create().where("checkpoint_id", entity.getCheckpointId())
        );
        if (blob == null) return null;

        Map<String, Object> state = JSON.parseObject(blob.getStateData(),
                new TypeReference<Map<String, Object>>() {});

        return Checkpoint.builder()
                .id(entity.getCheckpointId())
                .state(state)
                .nodeId(entity.getNodeId())
                .nextNodeId(entity.getNextNodeId())
                .build();
    }
}
