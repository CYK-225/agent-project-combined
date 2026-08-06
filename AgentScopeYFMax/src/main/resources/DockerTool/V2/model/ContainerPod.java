package org.example.agentScope.util.tool.DockerTool.V2.model;

import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 表示代理池中的容器Pod。
 * Pod包装了一个Docker容器，并包含其状态和分配任务的元数据。
 */
@Data
public class ContainerPod {

    /**
     * Pod状态枚举，表示生命周期中的各个状态
     */
    public enum Status {
        /** 容器正在创建和初始化中 */
        CREATING,
        /** 容器已准备好接受任务 */
        READY,
        /** 容器正在执行任务 */
        BUSY,
        /** 容器已标记为待清理 */
        TERMINATING,
        /** 容器已停止并移除 */
        TERMINATED,
        /** 容器遇到错误 */
        ERROR
    }

    /** 此Pod的唯一标识符（与容器ID相同) */
    private final String podId;
    /** Docker容器ID */
    private final String containerId;
    /** 分配给容器代理服务器的端口（FastAPI，奇数） */
    private final int assignedPort;
    /** 分配给VNC服务器的端口（偶数，= assignedPort + 1） */
    private final int vncPort;
    /** 与此Pod关联的配置文件名称 */
    private final String profileName;
    /** 绑定的用户名（唯一标识用户） */
    private final String username;
    /** Pod的当前状态 */
    private final AtomicReference<Status> status;
    /** Pod创建时的时间戳 */
    private final Instant createdAt;
    /** Pod最后一次使用时的时间戳 */
    private volatile Instant lastUsedAt;
    /** 当前正在处理的任务ID（空闲时为null) */
    private volatile String currentTaskId;
    /** 关于Pod的额外元数据 */
    private final Map<String, Object> metadata;

    /** 分配给容器的MAC地址 */
    private final String macAddress;

    /** 等容器ID（前12个字符) */
    private final String shortId;

    public ContainerPod(String containerId, int assignedPort, int vncPort, String profileName, String username, String macAddress) {
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

    // ==================== 状态管理方法 ====================
    /**
     * setStatus
     */
    public void setStatus(Status newStatus) {
        status.set(newStatus);
        this.lastUsedAt = Instant.now();
    }


    /**
     * 检查Pod是否可用于新任务。
     * 当状态为READY时，Pod可用。
     *
     * @return 如果Pod可用则返回true
     */
    public boolean isAvailable() {
        return status.get() == Status.READY;
    }

    /**
     * 检查Pod是否正在执行任务。
     *
     * @return 如果Pod正忙则返回true
     */
    public boolean isBusy() {
        return status.get() == Status.BUSY;
    }

    /**
     * 向Pod分派任务。
     * 只有当Pod处于READY状态时才能分派任务。
     *
     * @param taskId 要分派的任务ID
     * @return 如果任务成功分派则返回true
     */
    public boolean assignTask(String taskId) {
        if (status.compareAndSet(Status.READY, Status.BUSY)) {
            this.currentTaskId = taskId;
            this.lastUsedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 释放Pod，使其可用于新任务。
     * 将状态从 BUSY、ERROR、TERMINATED 等非 CREATING 状态重置为 READY。
     */
    public void release() {
        Status currentStatus = status.get();
        // 除了 CREATING 状态外，其他状态都可以释放为 READY
        if (currentStatus != Status.CREATING && currentStatus != Status.READY) {
            status.set(Status.READY);
            this.currentTaskId = null;
            this.lastUsedAt = Instant.now();
        }
    }

    /**
     * 强制释放Pod，无论当前状态如何（除 CREATING 外）。
     * 用于异常恢复场景。
     */
    public void forceRelease() {
        Status currentStatus = status.get();
        if (currentStatus != Status.CREATING) {
            status.set(Status.READY);
            this.currentTaskId = null;
            this.lastUsedAt = Instant.now();
        }
    }

    /**
     * 标记任务已完成并释放Pod。
     */
    public void completeTask() {
        if (status.get() == Status.BUSY) {
            status.set(Status.READY);
            this.currentTaskId = null;
            this.lastUsedAt = Instant.now();
        }
    }

    /**
     * 标记任务失败。
     * Pod进入ERROR状态。
     */
    public void failTask() {
        status.set(Status.ERROR);
        this.currentTaskId = null;
        this.lastUsedAt = Instant.now();
    }

    /**
     * 标记Pod正在终止。
     */
    public void terminate() {
        status.set(Status.TERMINATING);
    }

    /**
     * 标记Pod已终止。
     */
    public void terminated() {
        status.set(Status.TERMINATED);
    }

    // ==================== 元数据管理方法 ====================

    /**
     * 存储元数据键值对。
     *
     * @param key   键
     * @param value 值
     */
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取元数据值。
     *
     * @param key 键
     * @return 值，如果不存在则返回null
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 获取元数据值并转换为指定类型。
     *
     * @param key  键
     * @param type 目标类型
     * @param <T>  类型参数
     * @return 转换后的值，如果不存在或类型不匹配则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 移除元数据。
     *
     * @param key 键
     * @return 被移除的值
     */
    public Object removeMetadata(String key) {
        return metadata.remove(key);
    }

    // ==================== 状态查询方法 ====================

    /**
     * 获取Pod空闲时间（秒）。
     * 从最后一次使用时间开始计算。
     *
     * @return 空闲秒数
     */
    public long getIdleSeconds() {
        return java.time.Duration.between(lastUsedAt, Instant.now()).getSeconds();
    }

    /**
     * 获取Pod运行时间（秒）。
     * 从创建时间开始计算。
     *
     * @return 运行秒数
     */
    public long getUptimeSeconds() {
        return java.time.Duration.between(createdAt, Instant.now()).getSeconds();
    }

    /**
     * 更新最后使用时间为当前时间。
     * 用于保持Pod活跃。
     */
    public void touch() {
        this.lastUsedAt = Instant.now();
    }

    /**
     * 获取状态的可读描述。
     *
     * @return 状态描述
     */
    public String getStatusDescription() {
        Status currentStatus = status.get();
        return switch (currentStatus) {
            case CREATING -> "容器正在创建中";
            case READY -> "容器已就绪，可接受任务";
            case BUSY -> "容器正在执行任务: " + currentTaskId;
            case TERMINATING -> "容器正在终止";
            case TERMINATED -> "容器已终止";
            case ERROR -> "容器遇到错误";
        };
    }

    @Override
    public String toString() {
        return String.format("ContainerPod{id=%s, apiPort=%d, vncPort=%d, profile=%s, username=%s, status=%s, taskId=%s}",
                shortId, assignedPort, vncPort, profileName, username, status.get(), currentTaskId);
    }
}
