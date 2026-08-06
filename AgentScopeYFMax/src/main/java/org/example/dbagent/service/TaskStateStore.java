package org.example.dbagent.service;

import lombok.extern.slf4j.Slf4j;
import org.example.dbagent.model.DBAgentConfig;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 任务状态存储（内存态）
 * 保证：同一数据库 + 同一表，同时只允许一个生成任务运行
 */
@Slf4j
@Component
public class TaskStateStore {

    public enum TaskState {
        IDLE,
        GENERATING,
        GENERATED,
        FAILED
    }

    public record TaskStatus(TaskState state, String message) {}

    private final ConcurrentHashMap<String, TaskStatus> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public String buildKey(DBAgentConfig config, String tableName) {
        String host = Objects.toString(config.getMysqlHost(), "localhost").trim().toLowerCase(Locale.ROOT);
        int port = config.getMysqlPort() != null ? config.getMysqlPort() : 3306;
        String database = Objects.toString(config.getMysqlDatabase(), "").trim().toLowerCase(Locale.ROOT);
        String table = Objects.toString(tableName, "").trim().toLowerCase(Locale.ROOT);

        return host + ":" + port + "/" + database + "/" + table;
    }

    public TaskStatus getStatus(String key) {
        return states.getOrDefault(key, new TaskStatus(TaskState.IDLE, null));
    }

    public TaskStatus getStatus(DBAgentConfig config, String tableName) {
        return getStatus(buildKey(config, tableName));
    }

    public boolean startIfAbsent(String key) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            TaskStatus current = states.get(key);
            if (current != null && current.state() == TaskState.GENERATING) {
                log.warn("任务已在执行中，拒绝重复启动: {}", key);
                return false;
            }

            states.put(key, new TaskStatus(TaskState.GENERATING, null));
            log.info("任务开始: {}", key);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void complete(String key) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            states.put(key, new TaskStatus(TaskState.GENERATED, "代码生成完成"));
            log.info("任务完成: {}", key);
        } finally {
            lock.unlock();
        }
    }

    public void fail(String key, String errorMessage) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            states.put(key, new TaskStatus(TaskState.FAILED, errorMessage));
            log.warn("任务失败: {}, error={}", key, errorMessage);
        } finally {
            lock.unlock();
        }
    }
}
