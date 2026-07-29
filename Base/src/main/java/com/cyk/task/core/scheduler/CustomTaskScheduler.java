package com.cyk.task.core.scheduler;



import cn.hutool.json.JSONUtil;


import com.cyk.DockerTool.CookieGet.CookieDockerService;
import com.cyk.DockerTool.CookieGet.cmd.CookieTaskConfig;
import com.cyk.DockerTool.CookieGet.config.CookieProperties;
import com.cyk.events.BusinessDataRequestEvent;
import com.cyk.events.CompanyProcessEvent;
import com.cyk.events.SsePushEvent;
import com.cyk.task.DAL.Controller.DTO.CreateTaskTO;
import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Mapper.AuthInfoMapper;
import com.cyk.task.DAL.Mapper.TaskInfoMapper;
import com.cyk.task.DAL.Service.AllTaskService;
import com.cyk.task.DAL.Service.IAuthInfoService;
import com.cyk.task.DAL.Service.ITaskInfoService;
import com.cyk.task.DAL.Service.OtherModelService;
import com.cyk.task.DAL.Service.impl.TaskInfoServiceImpl;
import com.cyk.task.core.constants.TaskConstants;
import com.cyk.task.core.enums.TaskType;
import com.cyk.task.core.event.TaskEnqueuedEvent;
import com.cyk.task.core.executor.TaskExecutor;
import com.cyk.task.core.logger.TaskLogFormatter;
import com.mybatisflex.core.query.QueryWrapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cyk.Utils.MapObjectUtil.filterMapByNullValue;


/**
 * 任务调度器（异步回调模式）
 *
 * <p>【简化说明】</p>
 * SPECIAL类型复用AI的基础设施（执行器、线程池、队列），
 * 区别仅在于提示词，由TaskService处理。
 * 任务服务
 *
 * <p>【核心职责】</p>
 * 提供任务创建入口，负责：
 * <ul>
 *   <li>解析任务参数</li>
 *   <li>创建任务记录</li>
 *   <li>任务入队</li>
 *   <li>发布事件触发调度</li>
 * </ul>
 *
 * <p>【调用流程】</p>
 * <pre>
 * 前端请求                         后端处理
 *     │                              │
 *     │ POST /api/task/create        │
 *     │─────────────────────────────>│
 *     │                              │
 *     │                              ├── 1. 创建任务记录
 *     │                              ├── 2. 任务入队
 *     │                              ├── 3. 发布事件
 *     │                              │
 *     │<─────────────────────────────│ 4. 立即返回 taskId
 *     │                              │
 *     │                    (异步) ───┼── 5. 事件监听器触发调度
 *     │                              ├── 6. 执行器启动任务
 *     │                              └── 7. Python 完成后回调
 * </pre>
 *

 * @author system
 * @since 1.0
 */
@Slf4j
@Component("customTaskScheduler")
//TODO 现在暂时写死在这里了，后面需要修改成以
public class
CustomTaskScheduler {

    private final ITaskInfoService taskInfoService;
    private final IAuthInfoService authInfoService;
    private final TaskQueueManager queueManager;
    private final Map<String, TaskExecutor> executorMap = new ConcurrentHashMap<>();
    /**
     * ENS任务执行线程池
     */
    private final ExecutorService ensExecutorPool;

    @Resource
    AllTaskService allTaskService;

    @Resource
    ApplicationEventPublisher eventPublisher;
    @Resource
    TaskQueueManager taskQueueManager;
    @Resource
    TaskInfoMapper taskInfoMapper;

    @Resource
    CookieProperties cookieProperties;

    @Resource
    AuthInfoMapper authInfoMapper;

    @Resource
    CookieDockerService cookieDockerService;


    /**
     * AI任务执行线程池（SPECIAL任务也使用此线程池）
     */
    private final ExecutorService aiExecutorPool;

    /**
     * V3任务执行线程池（独立线程池）
     */
    private final ExecutorService v3ExecutorPool;

    private final AtomicInteger ensRunningCount = new AtomicInteger(0);
    private final AtomicInteger aiRunningCount = new AtomicInteger(0); // AI + SPECIAL 共用
    private final AtomicInteger v3RunningCount = new AtomicInteger(0); // V3 独立

    /** 任务启动时间戳 + 超时信息，用于超时检测 */
    private final ConcurrentHashMap<Long, TaskTimeoutInfo> taskStartTimestamps = new ConcurrentHashMap<>();

    /**
     * 任务超时信息内部类（支持每任务独立超时时间）
     */
    private static class TaskTimeoutInfo {
        final long startMs;
        final long timeoutMs;
        TaskTimeoutInfo(long startMs, long timeoutMs) {
            this.startMs = startMs;
            this.timeoutMs = timeoutMs;
        }
    }

    /**
     * 设置指定任务的超时时间（供V3TaskExecutor等执行器调用）
     */
    public void setTaskTimeout(Long taskId, long timeoutMs) {
        TaskTimeoutInfo info = taskStartTimestamps.get(taskId);
        if (info != null) {
            taskStartTimestamps.put(taskId, new TaskTimeoutInfo(info.startMs, timeoutMs));
            log.info("║  [动态超时] taskId={}, 更新超时时间={}ms", taskId, timeoutMs);
        }
    }
    @Autowired
    public CustomTaskScheduler(ITaskInfoService taskInfoService,
                               IAuthInfoService authInfoService,
                               TaskQueueManager queueManager) {
        this.taskInfoService = taskInfoService;
        this.authInfoService = authInfoService;
        this.queueManager = queueManager;

        this.ensExecutorPool = Executors.newFixedThreadPool(TaskConstants.ENS_POOL_MAX_SIZE);
        this.aiExecutorPool = Executors.newFixedThreadPool(TaskConstants.AI_POOL_MAX_SIZE);
        this.v3ExecutorPool = Executors.newFixedThreadPool(TaskConstants.V3_POOL_MAX_SIZE);

        log.info("任务调度器初始化完成（异步回调模式，SPECIAL复用AI基础设施，V3独立）");
    }

    public void registerExecutor(String taskType, TaskExecutor executor) {
        executorMap.put(taskType, executor);
        log.info("注册任务执行器: 类型={}, 执行器={}", taskType, executor.getClass().getSimpleName());
    }

    @PostConstruct
    public void initialize() {
        log.info("开始初始化任务调度器...");

        int released = authInfoService.releaseAllExclusiveLocks();
        if (released > 0) log.info("║  [启动清理] 已释放 {} 个遗留的账号独占锁", released);

        queueManager.reloadFromDatabase();

        for (TaskType taskType : TaskType.values()) {
            triggerNextTask(taskType.getCode());
        }

        log.info("任务调度器初始化完成");
    }

    /**
     *任务完成回调
     * @param taskId
     * @param taskType
     * @param success
     * @param result
     */
    public void onTaskCompleted(Long taskId, String taskType, boolean success, Map<String, Object> result) {
        log.info("任务完成回调: taskId={}, taskType={}, success={}", taskId, taskType, success);

        // 清除超时追踪
        taskStartTimestamps.remove(taskId);

        TaskInfoEntity task = taskInfoService.getById(taskId);

        if (task == null) {
            log.warn("回调的 taskId={} 不存在", taskId);
            getRunningCountByType(taskType).decrementAndGet();
            triggerNextTask(taskType);
            return;
        }

        if ("SUCCESS".equals(task.getStatus())) {
            log.info("║  [拦截延迟回调] 任务 taskId={} 已经是 SUCCESS 状态，忽略本次回调", taskId);
            return;
        }

        AtomicInteger runningCount = getRunningCountByType(taskType);
        int newCount = runningCount.decrementAndGet();
        log.info("║  [释放并发槽位] taskType={}, 剩余运行数={}/{}", taskType, newCount, getMaxConcurrentTasks(taskType));

        Long authId = null;
        try {
            authId = authInfoService.selectAuthIdByConfigNameAndWebsiteName(task.getConfigName(), task.getWebAddress());
            String errorMessage = result != null && result.containsKey("error") ? (String) result.get("error") : (success ? "" : "任务执行失败");

            String outcomeType;
            if (success) {
                outcomeType = "SUCCESS";
            } else if (errorMessage.startsWith("COMPANY_NOT_FOUND")) {
                outcomeType = "BUSINESS_ERROR";
            } else if (errorMessage.startsWith("ACCOUNT_LIMIT_REACHED") || errorMessage.startsWith("COOKIE_EXPIRED")) {

                /* 若是cookie问题则触发cookie自动话获取 */
                if (errorMessage.startsWith("COOKIE_EXPIRED")){
                    log.info("cookie失效，尝试获取cookie");
                    CookieTaskConfig cookieTaskConfig = new CookieTaskConfig();
                    cookieTaskConfig.setMode("fetch");
                    cookieTaskConfig.setTaskId(UUID.randomUUID().toString());
                    cookieTaskConfig.setCallbackUrl(cookieProperties.getDefaultCallbackUrl());
                    cookieTaskConfig.setAccount(task.getConfigName());
                    AuthInfoEntity authInfoEntity = authInfoMapper.selectOneByQuery(
                            QueryWrapper.create().where(AuthInfoEntity::getCloudStorageName).eq(task.getConfigName()));

                    cookieTaskConfig.setPassword(authInfoEntity.getPassword());
                    cookieTaskConfig.setSite("fengniao");

                    cookieDockerService.runCookieAgent(cookieTaskConfig);

                }

                outcomeType = "ACCOUNT_ERROR";
            } else {
                outcomeType = "SYSTEM_ERROR";
            }

            switch (outcomeType) {
                case "SUCCESS":
                    if (authId != null) authInfoService.releaseAccount(authId);
                    taskInfoService.completeTask(taskId, result);

                    // 🌟 1. 发送保存数据事件
                    try {
                        saveRightEntity(task, result);
                    } catch (Exception e) {
                        log.error("║  [异常] saveRightEntity 处理失败: {}", e.getMessage(), e);
                    }
                    String companyId = task.getCompanyId();

                    // 🌟 2. 发送更新公司状态事件
                    updateCompanyStatus(companyId, taskId, task.getCompanyName());
                    break;

                case "BUSINESS_ERROR":
                    if (authId != null) authInfoService.releaseAccount(authId);
                    taskInfoService.failTask(taskId, errorMessage, "FAILED");

                    // 🌟 3. 发送 SSE 推送事件
                    eventPublisher.publishEvent(new SsePushEvent(this, String.valueOf(taskId),
                            Map.of("company", task.getCompanyName(), "status", "任务异常")));
                    log.error("║  [业务失败] 目标公司未找到，终止重试");
                    break;

                case "ACCOUNT_ERROR":
                    if (authId != null) {
                        authInfoService.markAsUnavailable(authId, "触发账号风控: " + errorMessage);
                        authInfoService.releaseAccount(authId);
                    }
                    task.setConfigName(null);
                    executeRetryLogic(task, taskId, taskType, errorMessage, result);
                    break;

                case "SYSTEM_ERROR":
                default:
                    if (authId != null) authInfoService.releaseAccount(authId);
                    executeRetryLogic(task, taskId, taskType, errorMessage, result);
                    break;
            }
        } catch (Exception e) {
            log.error("║  [异常] 任务完成回调处理失败: {}", e.getMessage(), e);
            if (authId != null) { try { authInfoService.releaseAccount(authId); } catch (Exception ignored) {} }
            // 兜底：确保前端一定收到通知
            try {
                eventPublisher.publishEvent(new SsePushEvent(this, String.valueOf(taskId),
                        Map.of("company", task != null ? task.getCompanyName() : "未知", "status", "任务异常")));
            } catch (Exception sseEx) {
                log.error("║  [异常] SSE兜底推送也失败: {}", sseEx.getMessage());
            }
        } finally {
            triggerNextTask(taskType);
        }
    }

    /**
     * 抽取原有的重试判断逻辑，保持主代码整洁
     */
    private void executeRetryLogic(TaskInfoEntity task, Long taskId, String taskType, String errorMessage, Map<String, Object> result) {
        String status = task.getRetryCount() < 3 ? "RESTART" : "FAILED";
        taskInfoService.failTask(taskId, errorMessage, status);

        if ("RESTART".equals(status)) {
            log.warn("║  [状态更新] 任务失败，准备重试，当前重试次数={}", task.getRetryCount());

            // 修复爆红：捕获受检异常
            try {
                onTaskFailed(taskId, taskType, errorMessage, task, result);
            } catch (InterruptedException e) {
                log.error("║  [线程中断] 任务重试过程中被中断: {}", e.getMessage());
                // 恢复中断状态，让上层调度器知道当前线程被中断了
                Thread.currentThread().interrupt();
            }

        } else {
            log.error("║  [最终失败] 已达最大重试上限，任务终止");
        }
    }

    /**
     * 任务失败回调，在这里判断是否要重试，并且根据不同的代码，传入不同的重试逻辑
     * @param taskId
     * @param taskType
     * @param errorMessage
     */
    public void onTaskFailed(Long taskId, String taskType, String errorMessage, TaskInfoEntity taskInfoEntity, Map<String,Object> result) throws InterruptedException {
        if(taskInfoEntity.getRetryCount() >= 3) {
            taskInfoService.failTask(taskId, errorMessage, "FAILED");
            taskInfoEntity.setRetryCount(taskInfoEntity.getRetryCount() + 1);

            // 🌟 发送 SSE 推送事件
            eventPublisher.publishEvent(new SsePushEvent(this, String.valueOf(taskId),
                    Map.of("company", taskInfoEntity.getCompanyName(), "status", "任务异常")));
            return;
        }

        if(result == null) {
            addQueue(taskInfoEntity);
            taskInfoService.failTask(taskId, errorMessage, "RESTART");
            taskInfoEntity.setRetryCount(taskInfoEntity.getRetryCount() + 1);
            Thread.sleep(2000);
            return;
        }

        saveRightEntity(taskInfoEntity, result);
        Map<String,Object> fields = filterMapByNullValue(result, true); // 假设你的类里有这个方法
        taskInfoEntity.setCollectedFields(fields);

        // 🌟 解耦核心：发布重试数据请求事件，让业务模块填充新的 mission
        BusinessDataRequestEvent retryRequest = new BusinessDataRequestEvent(
                this, taskInfoEntity.getCompanyName(), taskInfoEntity.getTaskType(), fields.keySet().stream().toList(), true);
        eventPublisher.publishEvent(retryRequest);

        if (retryRequest.getMission() != null) {
            taskInfoEntity.setMission(retryRequest.getMission());
        }

        taskInfoEntity.setConfigName(null);
        addQueue(taskInfoEntity);
        taskInfoService.failTask(taskId, errorMessage, "RESTART");
    }

    public void triggerNextTask(String taskType) {
        try {
            AtomicInteger runningCount = getRunningCountByType(taskType);
            int currentRunning = runningCount.get();

            if (currentRunning >= getMaxConcurrentTasks(taskType)) {
                log.info("║  [并发已满] taskType={}, 当前运行={}/{}, 任务继续排队等待",
                        taskType, currentRunning, getMaxConcurrentTasks(taskType));
                return;
            }

            // 从队列中获取指定类型的任务（AI和SPECIAL共用队列，但需要独立调度）
            TaskInfoEntity task = queueManager.dequeueByExactType(taskType);

            if (task == null) {
                log.debug("║  [队列为空] taskType={}", taskType);
                loadTasksFromDatabase(taskType);
                return;
            }

            int newCount = runningCount.incrementAndGet();

            log.info(TaskLogFormatter.formatStepLog(TaskLogFormatter.Step.DEQUEUE, task,
                    String.format("当前运行=%d/%d", newCount, getMaxConcurrentTasks(taskType))));

            ExecutorService pool = getExecutorPool(taskType);
            pool.submit(() -> {
                try {
                    startTask(task);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (Exception e) {
            log.error("║  [异常] 触发任务失败: taskType={}", taskType, e.getMessage(), e);
        }
    }

    /**
     * 启动任务（异步）
     *
     * <p>调用执行器启动任务，不等待执行结果。</p>
     *
     * @param task 任务实体
     */
    private void startTask(TaskInfoEntity task) throws InterruptedException {
        String originalTaskType = task.getTaskType();
        AtomicInteger runningCount = getRunningCountByType(originalTaskType);

        try {
            log.info(TaskLogFormatter.formatStepLog(TaskLogFormatter.Step.EXECUTE_START, task, "开始启动任务执行器"));

            // SPECIAL类型路由到AI执行器
            TaskType type = TaskType.fromCode(originalTaskType);

            if (type == null) {
                throw new IllegalArgumentException("无效的任务类型: " + originalTaskType);
            }

            String executorType = type.usesAIInfrastructure() ? TaskType.AI.getCode() : type.getCode();
            log.info("║  [路由决策] executorType={} ",  executorType);
            TaskExecutor executor = executorMap.get(executorType);

            if (executor == null) {
                throw new IllegalStateException(STR."未找到任务执行器: \{executorType}");
            }

            log.info("║  [路由决策] 原始类型={} → 执行器类型={}", originalTaskType, executorType);

            boolean started = executor.start(task);

            if (started) {
                log.info("║  [等待回调] 任务已提交给外部系统，等待回调...");
                taskStartTimestamps.put(task.getId(), new TaskTimeoutInfo(System.currentTimeMillis(), TaskConstants.TASK_TIMEOUT_MS));
                taskInfoService.startTask(task.getId(),task.getConfigName());

            } else {
                log.error("║  [启动失败] 执行器返回false");

                // 任务启动失败，需要释放并发槽位
                int newCount = runningCount.decrementAndGet();
                log.info("║  [释放槽位] taskType={}, 剩余运行数={}/{}",
                        originalTaskType, newCount, getMaxConcurrentTasks(originalTaskType));

                onTaskFailed(task.getId(), originalTaskType, "任务启动失败", task, null);
            }

        } catch (Exception e) {
        // 无论什么异常，任务没启起来，先释放并发槽位
        int newCount = runningCount.decrementAndGet();

        // 【核心修改点】：识别“账号不足”异常，重新排队而不是报错失败
        if (e.getMessage() != null && e.getMessage().contains("无法获取可用账号")) {
            log.warn("║  [资源等待] 暂无可用账号，任务重新入队排队等待: taskId={}, taskType={}",
                    task.getId(), originalTaskType);
            try {
                // 1. 将任务重新放回队列尾部（底层会发布事件触发下一轮调度拉取）
                addQueue(task);

                // 2. 线程休眠 5 秒。这是非常关键的退避策略！
                // 避免队列中全是没有账号的任务时，发生疯狂的死循环出队/入队把 CPU 打满
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } else {
            // 其他真实的程序崩溃或异常，走原本的失败与重试逻辑
            log.error("║  [启动异常] taskId={}, taskType={}", task.getId(), originalTaskType, e);
            log.info("║  [释放槽位] taskType={}, 剩余运行数={}/{}",
                    originalTaskType, newCount, getMaxConcurrentTasks(originalTaskType));

            try {
                onTaskFailed(task.getId(), originalTaskType, e.getMessage(), task, null);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
    }

    /**
     * 从数据库加载任务到队列
     * @param taskType 任务类型
     */
    private void loadTasksFromDatabase(String taskType) {
        try {
            int remainingCapacity = TaskConstants.QUEUE_CAPACITY -
                    queueManager.getQueueSize(taskType);

            if (remainingCapacity <= 0) {
                log.debug("║  [队列已满] taskType={}, 无需从数据库加载", taskType);
                return;
            }

            int fetchSize = Math.min(remainingCapacity, TaskConstants.BATCH_FETCH_SIZE);
            List<TaskInfoEntity> tasks = taskInfoService.getPendingTasks(taskType, fetchSize);

            for (TaskInfoEntity task : tasks) {
                try {
                    queueManager.enqueue(task);
                } catch (InterruptedException e) {
                    log.warn("║  [中断] 任务入队被中断: taskId={}", task.getId());
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!tasks.isEmpty()) {
                log.info("║  [数据库加载] taskType={}, 加载{}个任务到队列", taskType, tasks.size());

                if (queueManager.getQueueSize(taskType) > 0) {
                    triggerNextTask(taskType);
                }
            }

        } catch (Exception e) {
            log.error("║  [异常] 从数据库加载任务失败: taskType={}", taskType, e);
        }
    }

    /**
     * 根据任务类型获取对应的线程池
     *
     * <p>SPECIAL类型复用AI线程池</p>
     */
    private ExecutorService getExecutorPool(String taskType) {
        TaskType type = TaskType.fromCode(taskType);

        if (type == null) {
            throw new IllegalArgumentException("无效的任务类型: " + taskType);
        }

        TaskType actualType = type.getActualExecutorType();

        return switch (actualType) {
            case ENS -> ensExecutorPool;
            case AI -> aiExecutorPool;
            case V3 -> v3ExecutorPool;
            default -> aiExecutorPool;
        };
    }

    /**
     * 根据任务类型获取对应的运行计数器
     *
     * <p>每种任务类型使用独立的计数器</p>
     */
    private AtomicInteger getRunningCountByType(String taskType) {
        TaskType type = TaskType.fromCode(taskType);

        if (type == null) {
            throw new IllegalArgumentException("无效的任务类型: " + taskType);
        }

        return switch (type) {
            case ENS -> ensRunningCount;
            case AI, SPECIAL -> aiRunningCount; // SPECIAL 共用 AI 计数器
            case V3 -> v3RunningCount;           // V3 独立计数器
        };
    }

    /** 根据任务类型获取最大并发数：AI+SPECIAL 共享上限 5，ENS 上限 2，V3 上限 3 */
    private int getMaxConcurrentTasks(String taskType) {
        TaskType type = TaskType.fromCode(taskType);
        if (type == null) return 2;
        return switch (type) {
            case ENS -> TaskConstants.ENS_POOL_MAX_SIZE;
            case AI, SPECIAL -> TaskConstants.AI_POOL_MAX_SIZE;
            case V3 -> TaskConstants.V3_POOL_MAX_SIZE;
        };
    }

    /**
     * 超时看门狗：每 60 秒扫描一次，检测超过 TASK_TIMEOUT_MS 未回调的 RUNNING 任务
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000)
    public void checkTaskTimeout() {
        if (taskStartTimestamps.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (Map.Entry<Long, TaskTimeoutInfo> entry : taskStartTimestamps.entrySet()) {
            long taskId = entry.getKey();
            TaskTimeoutInfo timeoutInfo = entry.getValue();
            long startMs = timeoutInfo.startMs;
            long timeoutMs = timeoutInfo.timeoutMs;

            if (now - startMs < timeoutMs) continue;

            log.warn("║  [超时看门狗] 任务超时: taskId={}, 已运行{}秒", taskId, (now - startMs) / 1000);

            TaskInfoEntity task = taskInfoService.getById(taskId);
            if (task == null || !"RUNNING".equals(task.getStatus())) {
                taskStartTimestamps.remove(taskId);
                continue;
            }

            String taskType = task.getTaskType();
            taskStartTimestamps.remove(taskId);
            getRunningCountByType(taskType).decrementAndGet();

            try {
                Long authId = authInfoService.selectAuthIdByConfigNameAndWebsiteName(task.getConfigName(), task.getWebAddress());
                if (authId != null) authInfoService.releaseAccount(authId);
            } catch (Exception e) {
                log.error("║  [超时处理] 释放鉴权失败，可能遗留孤儿锁: configName={}, error={}",
                        task.getConfigName(), e.getMessage());
            }

            int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
            executeRetryLogic(task, taskId, taskType, "任务超时：容器无回调", null);

            if (retryCount >= 3) {
                try {
                    eventPublisher.publishEvent(new SsePushEvent(this, String.valueOf(taskId),
                            Map.of("company", task.getCompanyName(), "status", "任务异常")));
                } catch (Exception e) {
                    log.error("║  [超时处理] SSE推送失败: {}", e.getMessage());
                }
            }

            triggerNextTask(taskType);
        }
    }

    public void manualTrigger(String taskType) {
        log.info("[手动触发] taskType={}", taskType);
        triggerNextTask(taskType);
    }

    public String getStatusInfo() {
        return String.format(
                "调度器状态（异步模式） - ENS: 队列=%d, 运行=%d/%d | AI+SPECIAL: 队列=%d, 运行=%d/%d | V3: 队列=%d, 运行=%d/%d | 已注册执行器: %d",
                queueManager.getQueueSize(TaskType.ENS.getCode()),
                ensRunningCount.get(),
                getMaxConcurrentTasks(TaskType.ENS.getCode()),
                queueManager.getQueueSize(TaskType.AI.getCode()),
                aiRunningCount.get(),
                getMaxConcurrentTasks(TaskType.AI.getCode()),
                queueManager.getQueueSize(TaskType.V3.getCode()),
                v3RunningCount.get(),
                getMaxConcurrentTasks(TaskType.V3.getCode()),
                executorMap.size()
        );
    }

    public int getRunningCount(String taskType) {
        return getRunningCountByType(taskType).get();
    }

    public int getMaxConcurrentTasks() {
        return getMaxConcurrentTasks(TaskType.AI.getCode());
    }
    public void saveRightEntity(TaskInfoEntity taskInfoEntity, Map<String,Object> result){
        Map<String, Object> targetData = result;
        if (result.containsKey("result") && result.get("result") instanceof Map) {
            Map<String, Object> innerResult = (Map<String, Object>) result.get("result");
            if (innerResult.containsKey("message")) {
                String msg = (String) innerResult.get("message");
                if (msg != null && msg.trim().startsWith("{")) {
                    targetData = JSONUtil.parseObj(msg);
                } else {
                    targetData = new HashMap<>();
                    targetData.put("message", msg);
                }
            }
        }
        Map<String, Object> finalData = filterMapByNullValue(targetData, false);
        // 🌟 发布事件交由 BaiduService 写库
        eventPublisher.publishEvent(new CompanyProcessEvent(this, taskInfoEntity.getCompanyId(), taskInfoEntity.getCompanyName(), finalData, "UPDATE_DATA"));
    }

    // 🌟 发送状态更新与推送事件
    public void updateCompanyStatus(String companyId, Long taskId, String companyName) {
        QueryWrapper queryWrapper = QueryWrapper.create().where(TaskInfoEntity::getCompanyId).eq(companyId);
        List<TaskInfoEntity> taskList = taskInfoMapper.selectListByQuery(queryWrapper);

        if (taskList == null || taskList.isEmpty()) return;

        boolean isAllSuccess = taskList.stream().allMatch(task -> "SUCCESS".equals(task.getStatus()));

        // 检查是否同时存在 ENS 和 AI 两种类型的成功任务
        boolean hasEnsSuccess = taskList.stream().anyMatch(task -> "SUCCESS".equals(task.getStatus()) && "ENS".equals(task.getTaskType()));
        boolean hasAiSuccess = taskList.stream().anyMatch(task -> "SUCCESS".equals(task.getStatus()) && "AI".equals(task.getTaskType()));

        if (isAllSuccess && hasEnsSuccess && hasAiSuccess) {
            // ENS + AI 全部成功 -> 已深度初始化
            eventPublisher.publishEvent(new CompanyProcessEvent(this, companyId, companyName, null, "UPDATE_STATUS_2"));
            eventPublisher.publishEvent(new SsePushEvent(this, String.valueOf(taskId), Map.of("company", companyName, "status", "已完成")));
        } else if (isAllSuccess && hasEnsSuccess) {
            // 仅 ENS 任务且全部成功（普通初始化）-> 已初始化
            eventPublisher.publishEvent(new CompanyProcessEvent(this, companyId, companyName, null, "UPDATE_STATUS_1"));
            eventPublisher.publishEvent(new SsePushEvent(this, String.valueOf(taskId), Map.of("company", companyName, "status", "已完成")));
        }
        // 其他情况（如深度初始化中 ENS 先完成但 AI 还在跑）：不做任何操作，等 AI 也完成
    }


    /**
     * 首次创建任务，后续的故障回调不经过这个方法
     *
     * <p>创建任务并加入调度队列，立即返回任务ID。
     * 调度逻辑通过事件异步触发，不阻塞当前请求。</p>
     *
     * @param createTaskTO 任务创建参数
     * @return 任务ID
     */

    /**
     * 首次创建任务，后续的故障回调不经过这个方法
     *
     * <p>创建任务并加入调度队列，立即返回任务ID。
     * 调度逻辑通过事件异步触发，不阻塞当前请求。</p>
     *
     * @param createTaskTO 任务创建参数
     * @return 任务ID
     */
    public Long startTask(CreateTaskTO createTaskTO) throws InterruptedException {

        String companyName = createTaskTO.getCompanyName();
        String taskType = createTaskTO.getTaskType();
        String configName = createTaskTO.getConfigName();
        List<String> fieldList = createTaskTO.getFieldList();
        Boolean isUpdate = createTaskTO.getIsUpdate();
        String webAddress = createTaskTO.getWebAddress();

        log.info("\n╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  [创建任务] 公司={} | 类型={} | 配置={}", companyName, taskType, configName);
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        // 检查是否存在重复的进行中任务（相同公司名+任务类型，状态为PENDING或RUNNING）
        // V3任务不需要公司维度重复检测，taskId为主键天然不重复
        if (!TaskType.V3.getCode().equals(taskType) && taskInfoService.existsActiveTask(companyName, taskType)) {
            log.warn("║  [重复检测] 公司={} 已存在进行中的{}任务", companyName, taskType);
            throw new IllegalStateException("该公司已存在相同类型的任务正在执行或排队中，请等待完成后再创建新任务");
        }

        // OTHER 任务必须指定字段列表 (基础参数校验保留在调度器层面实现快速失败)
        if (taskType.equals("OTHER") && !(fieldList != null && !fieldList.isEmpty())) {
            throw new IllegalArgumentException("OTHER任务必须指定字段列表");
        }

        // 🌟 解耦核心：通过发布同步请求事件，向 BaiduService 索要 needFind(待抓取字段)、mission(提示词) 和 companyId
        // V3类型跳过此事件，直接用promptId创建任务
        String companyId;
        Map<String, Object> needFind;
        String mission;
        String finalWebAddress;

        if (TaskType.V3.getCode().equals(taskType)) {
            // V3任务不需要BusinessDataRequestEvent，直接使用前端传入的参数
            // V3没有公司概念，companyName用configName（用户标识）兜底，同时作为容器username
            if (companyName == null || companyName.isBlank()) {
                companyName = configName;
            }
            companyId = "v3_" + System.currentTimeMillis();
            needFind = Map.of();
            mission = "";
            finalWebAddress = webAddress;
            log.info("║  [V3跳过] V3任务跳过BusinessDataRequestEvent，直接使用promptId={}", createTaskTO.getPromptId());
        } else {
            BusinessDataRequestEvent requestEvent = new BusinessDataRequestEvent(
                    this, companyName, taskType, fieldList, false);
            eventPublisher.publishEvent(requestEvent);

            // 获取业务层处理并填充完毕的数据
            companyId = requestEvent.getCompanyId();
            needFind = requestEvent.getNeedFind();
            mission = requestEvent.getMission();
            // 优先使用业务层返回的webAddress（例如ENS和AI被业务层写死了网址），如果业务层没返回，则用前端传的
            finalWebAddress = requestEvent.getWebAddress() != null ? requestEvent.getWebAddress() : webAddress;

            // 如果业务层计算后发现没有需要抓取的字段
            if (needFind == null || needFind.isEmpty()){
                // 保留原有的 log 打印（由于 entities 数据现在在 BaiduService 处理，这里用占位说明）
                log.info("companiesEntityNow: (已交由业务层匹配, 当前该企业无需要抓取的空缺字段) - " + companyName);
                return 0L;
            }
        }

        // 1. 创建任务记录
        TaskInfoEntity taskInfoEntity = allTaskService.taskInfoService.createTask(
                companyId,              // 现在使用的是由业务模块查库后返回的真实 UID
                companyName,
                taskType,
                configName,
                needFind,
                isUpdate,
                mission,
                finalWebAddress,        // 使用最终确定的 webAddress
                createTaskTO.getUserId(),
                createTaskTO.getPromptId()
        );

        // 完美保留原来的创建成功日志和格式
        log.info(TaskLogFormatter.formatStepLog(TaskLogFormatter.Step.CREATE, taskInfoEntity,
                String.format("待收集字段=%s | 任务指令=%s", needFind.keySet(), mission)));

        // 2. 加入队列
        addQueue(taskInfoEntity);

        // 4. 立即返回任务ID
        return taskInfoEntity.getId();
    }

    public void addQueue(TaskInfoEntity taskInfoEntity) throws InterruptedException {
        // 2. 任务入队
        taskQueueManager.enqueue(taskInfoEntity);
        log.info(TaskLogFormatter.formatStepLog(TaskLogFormatter.Step.ENQUEUE, taskInfoEntity,
                String.format("队列大小=%d", taskQueueManager.getQueueSize(taskInfoEntity.getTaskType()))));

        // 3. 发布任务入队事件（异步触发调度）
        eventPublisher.publishEvent(new TaskEnqueuedEvent(this, taskInfoEntity));
        log.info("║  [事件发布] 已发布 TaskEnqueuedEvent，等待异步调度触发");
    }
}
