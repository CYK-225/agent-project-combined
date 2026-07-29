package com.cyk.DockerTool.V3.model;

import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * V3代理池中的容器Pod。
 * Pod包装了一个Docker容器，并包含其状态和分配任务的元数据。
 */
@Data
public class ContainerPodV3 {

    public enum Status {
        CREATING,
        READY,
        BUSY,
        TERMINATING,
        TERMINATED,
        ERROR
    }

    private final String podId;
    private final String containerId;
    private final int assignedPort;
    private final int vncPort;
    private final String profileName;
    private final String username;
    private final AtomicReference<Status> status;
    private final Instant createdAt;
    private volatile Instant lastUsedAt;
    private volatile String currentTaskId;
    private final Map<String, Object> metadata;

    private final String macAddress;

    private final String shortId;

    public ContainerPodV3(String containerId, int assignedPort, int vncPort, String profileName, String username, String macAddress) {
        this.podId = containerId;
        this.containerId = containerId;
        this.assignedPort = assignedPort;
        this.vncPort = vncPort;
        this.profileName = profileName;
        this.username = username;
        this.macAddress = macAddress;
        this.status = new AtomicReference<>(Status.CREATING);
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
        this.metadata = new ConcurrentHashMap<>();
        this.shortId = containerId.length() > 12 ? containerId.substring(0, 12) : containerId;
    }

    public void setStatus(Status newStatus) {
        status.set(newStatus);
        this.lastUsedAt = Instant.now();
    }

    public boolean isAvailable() {
        return status.get() == Status.READY;
    }

    public boolean isBusy() {
        return status.get() == Status.BUSY;
    }

    public boolean assignTask(String taskId) {
        if (status.compareAndSet(Status.READY, Status.BUSY)) {
            this.currentTaskId = taskId;
            this.lastUsedAt = Instant.now();
            return true;
        }
        return false;
    }

    public void release() {
        Status currentStatus = status.get();
        if (currentStatus != Status.CREATING && currentStatus != Status.READY) {
            status.set(Status.READY);
            this.currentTaskId = null;
            this.lastUsedAt = Instant.now();
        }
    }

    public void forceRelease() {
        Status currentStatus = status.get();
        if (currentStatus != Status.CREATING) {
            status.set(Status.READY);
            this.currentTaskId = null;
            this.lastUsedAt = Instant.now();
        }
    }

    public void completeTask() {
        if (status.get() == Status.BUSY) {
            status.set(Status.READY);
            this.currentTaskId = null;
            this.lastUsedAt = Instant.now();
        }
    }

    public void failTask() {
        status.set(Status.ERROR);
        this.currentTaskId = null;
        this.lastUsedAt = Instant.now();
    }

    public void terminate() {
        status.set(Status.TERMINATING);
    }

    public void terminated() {
        status.set(Status.TERMINATED);
    }

    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public Object removeMetadata(String key) {
        return metadata.remove(key);
    }

    public long getIdleSeconds() {
        return java.time.Duration.between(lastUsedAt, Instant.now()).getSeconds();
    }

    public long getUptimeSeconds() {
        return java.time.Duration.between(createdAt, Instant.now()).getSeconds();
    }

    public void touch() {
        this.lastUsedAt = Instant.now();
    }

    public String getStatusDescription() {
        Status currentStatus = status.get();
        return switch (currentStatus) {
            case CREATING -> "V3容器正在创建中";
            case READY -> "V3容器已就绪，可接受任务";
            case BUSY -> "V3容器正在执行任务: " + currentTaskId;
            case TERMINATING -> "V3容器正在终止";
            case TERMINATED -> "V3容器已终止";
            case ERROR -> "V3容器遇到错误";
        };
    }

    @Override
    public String toString() {
        return String.format("V3ContainerPod{id=%s, apiPort=%d, vncPort=%d, profile=%s, username=%s, status=%s, taskId=%s}",
                shortId, assignedPort, vncPort, profileName, username, status.get(), currentTaskId);
    }
}
