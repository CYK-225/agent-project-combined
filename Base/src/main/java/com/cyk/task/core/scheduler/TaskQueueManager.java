package com.cyk.task.core.scheduler;

import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Service.ITaskInfoService;
import com.cyk.task.core.constants.TaskConstants;
import com.cyk.task.core.enums.TaskType;
import com.cyk.task.core.logger.TaskLogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 任务队列管理器
 * 
 * <p>【核心职责】</p>
 * 本组件是任务调度系统的核心组件之一，负责管理不同类型任务的内存队列，
 * 实现任务的缓冲、分发和流量控制。
 * 
 * <p>【设计原理】</p>
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │              任务队列管理器                  │
 * ├─────────────────────────────────────────────┤
 * │  ┌─────────┐  ┌─────────┐                  │
 * │  │ ENS队列 │  │ AI队列  │ ← SPECIAL复用    │
 * │  │(1000)   │  │(1000)   │                  │
 * │  └────┬────┘  └────┬────┘                  │
 * │       │            │                        │
 * └───────┼────────────┼────────────────────────┘
 *         │            │
 *         ▼            ▼
 *     ENS执行器    AI执行器(处理AI+SPECIAL)
 * </pre>
 * 
 * <p>【队列特性】</p>
 * <ul>
 *   <li><b>隔离性</b>：不同类型任务使用独立队列，互不影响</li>
 *   <li><b>有界性</b>：队列容量有限，防止内存溢出</li>
 *   <li><b>阻塞式</b>：队列满时阻塞生产者，队列空时阻塞消费者</li>
 *   <li><b>FIFO</b>：先进先出，确保任务按提交顺序执行</li>
 * </ul>
 * 
 * <p>【工作流程】</p>
 * <ol>
 *   <li><b>入队</b>：任务创建时，调用enqueue()方法加入对应队列</li>
 *   <li><b>出队</b>：执行器调用dequeue()方法获取任务</li>
 *   <li><b>监控</b>：定时检查队列状态，异常时告警</li>
 * </ol>
 * 
 * <p>【使用示例】</p>
 * <pre>
 * // 入队
 * taskQueueManager.enqueue(task);
 * 
 * // 出队（阻塞式）
 * TaskInfoEntity task = taskQueueManager.dequeue(TaskType.AI, 1, TimeUnit.SECONDS);
 * 
 * // 查看队列大小
 * int size = taskQueueManager.getQueueSize(TaskType.ENS);
 * </pre>
 * 
 * @author system
 * @since 1.0
 */
@Slf4j
@Component
public class TaskQueueManager {

    /**
     * 任务信息服务
     */
    private final ITaskInfoService taskInfoService;

    /**
     * ENS任务队列
     */
    private final BlockingQueue<TaskInfoEntity> ensQueue;

    /**
     * AI任务队列（SPECIAL类型也使用此队列）
     */
    private final BlockingQueue<TaskInfoEntity> aiQueue;

    /**
     * V3任务队列（独立队列，不与AI共享）
     */
    private final BlockingQueue<TaskInfoEntity> v3Queue;

    /**
     * 构造方法
     * 
     * <p>初始化三个任务队列，容量由TaskConstants.QUEUE_CAPACITY指定。</p>
     * 
     * @param taskInfoService 任务信息服务
     */

    @Autowired
    public TaskQueueManager(ITaskInfoService taskInfoService) {
        this.taskInfoService = taskInfoService;
        this.ensQueue = new LinkedBlockingQueue<>(TaskConstants.QUEUE_CAPACITY);
        this.aiQueue = new LinkedBlockingQueue<>(TaskConstants.QUEUE_CAPACITY);
        this.v3Queue = new LinkedBlockingQueue<>(TaskConstants.QUEUE_CAPACITY);
        
        log.info("任务队列管理器初始化完成，队列容量: {}, SPECIAL类型复用AI队列", TaskConstants.QUEUE_CAPACITY);
        log.info("V3任务使用独立队列");
    }

    /**
     * 将任务加入对应的队列
     * 
     * <p>根据任务类型，将任务分发到对应的队列。如果队列已满，
     * 会阻塞等待直到队列有空间。</p>
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>根据taskType获取对应的队列</li>
     *   <li>调用put()方法入队（阻塞式）</li>
     *   <li>记录日志</li>
     * </ol>
     * 
     * @param task 任务实体
     * @throws InterruptedException 如果线程在等待时被中断
     * @throws IllegalArgumentException 如果任务类型无效
     */
    public  void  enqueue(TaskInfoEntity task) throws InterruptedException {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为null");
        }

        BlockingQueue<TaskInfoEntity> queue = getQueueByType(task.getTaskType());


        log.info("任务入队: ID={}, 类型={}, 队列当前大小={}", 
                task.getId(), task.getTaskType(), queue.size());
        
        queue.put(task);

    }

    /**
     * 从指定类型的队列中取出任务
     * 
     * <p>阻塞式获取任务，如果队列为空，会等待指定的时间。</p>
     * 
     * @param taskType 任务类型
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 任务实体，如果超时则返回null
     * @throws InterruptedException 如果线程在等待时被中断
     */
    public TaskInfoEntity dequeue(String taskType, long timeout, TimeUnit unit) 
            throws InterruptedException {
        BlockingQueue<TaskInfoEntity> queue = getQueueByType(taskType);
        TaskInfoEntity task = queue.poll(timeout, unit);
        
        if (task != null) {
            log.info(TaskLogFormatter.formatSimpleLog(TaskLogFormatter.Step.DEQUEUE, task,
                    String.format("剩余队列大小=%d", queue.size())));
        }
        
        return task;
    }

    /**
     * 非阻塞式获取任务
     * 
     * <p>立即返回队列头部的任务，如果队列为空则返回null。</p>
     * 
     * @param taskType 任务类型
     * @return 任务实体，如果队列为空则返回null
     */
    public TaskInfoEntity dequeueNow(String taskType) {
        BlockingQueue<TaskInfoEntity> queue = getQueueByType(taskType);
        TaskInfoEntity task = queue.poll();
        
        if (task != null) {
            log.info("任务立即出队: ID={}, 类型={}", task.getId(), taskType);
        }
        
        return task;
    }

    /**
     * 非阻塞式获取指定类型的任务（用于共用队列的场景）
     * 
     * <p>从队列中查找并移除指定类型的任务。主要用于AI和SPECIAL共用队列但独立调度的场景。</p>
     * 
     * @param taskType 任务类型
     * @return 任务实体，如果未找到则返回null
     */
    public TaskInfoEntity dequeueByExactType(String taskType) {
        BlockingQueue<TaskInfoEntity> queue = getQueueByType(taskType);
        
        // 遍历队列查找指定类型的任务
        for (TaskInfoEntity task : queue) {
            if (taskType.equals(task.getTaskType())) {
                if (queue.remove(task)) {
                    log.info("任务按类型出队: ID={}, 类型={}", task.getId(), taskType);
                    return task;
                }
            }
        }
        
        return null;
    }

    /**
     * 获取指定类型队列的当前大小
     * 
     * @param taskType 任务类型
     * @return 队列大小
     */
    public int getQueueSize(String taskType) {
        return getQueueByType(taskType).size();
    }

    /**
     * 检查指定类型的队列是否为空
     * 
     * @param taskType 任务类型
     * @return true表示为空，false表示不为空
     */
    public boolean isQueueEmpty(String taskType) {
        return getQueueByType(taskType).isEmpty();
    }

    /**
     * 检查指定类型的队列是否已满
     * 
     * @param taskType 任务类型
     * @return true表示已满，false表示未满
     */
    public boolean isQueueFull(String taskType) {
        return getQueueByType(taskType).remainingCapacity() == 0;
    }

    /**
     * 从数据库重新加载队列
     * 
     * <p>系统启动时或队列异常时，可以从数据库重新加载PENDING状态的任务到队列。
     * 注意：此方法会清空当前队列。</p>
     */
    public void reloadFromDatabase() {
        log.info("开始从数据库重新加载任务队列...");

        ensQueue.clear();
        aiQueue.clear();
        v3Queue.clear();

        loadTasksToQueue(TaskType.ENS.getCode(), ensQueue);
        loadTasksToQueue(TaskType.AI.getCode(), aiQueue);
        loadTasksToQueue(TaskType.SPECIAL.getCode(), aiQueue); // SPECIAL也加载到AI队列
        loadTasksToQueue(TaskType.V3.getCode(), v3Queue);

        log.info("任务队列重新加载完成: ENS={}, AI(含SPECIAL)={}, V3={}", ensQueue.size(), aiQueue.size(), v3Queue.size());
    }

    /**
     * 从数据库加载指定类型的任务到队列
     * 
     * @param taskType 任务类型
     * @param queue 目标队列
     */
    private void loadTasksToQueue(String taskType, BlockingQueue<TaskInfoEntity> queue) {
        try {
            java.util.List<TaskInfoEntity> tasks = taskInfoService.getPendingTasks(
                    taskType, TaskConstants.QUEUE_CAPACITY);
            
            for (TaskInfoEntity task : tasks) {
                if (!queue.offer(task)) {
                    log.warn("队列已满，部分任务未能加载: 类型={}", taskType);
                    break;
                }
            }
            
            log.info("加载{}任务到队列: {}个", taskType, Math.min(tasks.size(), queue.size()));
        } catch (Exception e) {
            log.error("加载任务到队列失败: 类型={}", taskType, e);
        }
    }

    /**
     * 根据任务类型获取对应的队列
     *
     * <p>SPECIAL类型复用AI队列，V3使用独立队列</p>
     */
    private BlockingQueue<TaskInfoEntity> getQueueByType(String taskType) {
        TaskType type = TaskType.fromCode(taskType);

        if (type == null) {
            throw new IllegalArgumentException("无效的任务类型: " + taskType);
        }

        // SPECIAL类型复用AI队列
        if (type.usesAIInfrastructure()) {
            return aiQueue;
        }

        return switch (type) {
            case ENS -> ensQueue;
            case V3 -> v3Queue;
            default -> ensQueue; // fallback
        };
    }

    public String getQueueStatusSummary() {
        return String.format(
                "队列状态 - ENS: %d/%d, AI(含SPECIAL): %d/%d, V3: %d/%d",
                ensQueue.size(), TaskConstants.QUEUE_CAPACITY,
                aiQueue.size(), TaskConstants.QUEUE_CAPACITY,
                v3Queue.size(), TaskConstants.QUEUE_CAPACITY
        );
    }
}
