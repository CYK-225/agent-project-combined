package com.cyk.task.core.executor;



import com.cyk.task.DAL.DO.TaskInfoEntity;

/**
 * 任务执行器接口（异步回调模式）
 * 
 * <p>【核心职责】</p>
 * 本接口定义了任务执行器的标准行为，所有类型的任务执行器（ENS、AI、特殊）
 * 都需要实现此接口。执行器只负责**启动**任务，不等待执行结果。
 * 
 * <p>【异步回调设计】</p>
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │       TaskExecutor（异步执行器）             │
 * ├─────────────────────────────────────────────┤
 * │  1. 调度器调用 start(task)                  │
 * │  2. 执行器启动外部任务（Docker/脚本）        │
 * │  3. 执行器立即返回（不等待结果）             │
 * │  4. 外部任务完成后回调 TaskCallbackController│
 * │  5. 回调触发调度器的 onTaskCompleted        │
 * │  6. 调度器触发下一个任务                    │
 * └─────────────────────────────────────────────┘
 * 
 * 调用时序：
 * ┌────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
 * │Scheduler│────>│Executor  │────>│Docker/   │────>│  Python  │
 * │        │     │.start()  │     │Script    │     │  Script  │
 * └────────┘     └──────────┘     └──────────┘     └────┬─────┘
 *                                                        │
 *                    ┌──────────────────────────────────┘
 *                    │ HTTP回调
 *                    ▼
 *              ┌──────────┐     ┌──────────┐
 *              │Callback  │────>│Scheduler │
 *              │Controller│     │.onComplete│
 *              └──────────┘     └──────────┘
 * </pre>
 * 
 * <p>【实现要求】</p>
 * <ul>
 *   <li><b>start方法</b>：启动任务（启动容器/调用脚本），成功返回true</li>
 *   <li><b>getTaskType方法</b>：返回执行器支持的任务类型</li>
 *   <li><b>shutdown方法</b>：优雅关闭执行器，释放资源</li>
 *   <li><b>异步执行</b>：start方法必须快速返回，不能阻塞等待结果</li>
 * </ul>
 * 
 * <p>【与同步模式的区别】</p>
 * <pre>
 * 同步模式（旧）：
 *   result = executor.execute(task);  // 阻塞等待
 *   handleResult(result);
 * 
 * 异步模式（新）：
 *   executor.start(task);             // 立即返回
 *   // ... Python 回调 ...
 *   onCallback(taskId, result);       // 回调时处理结果
 * </pre>
 * 
 * @author system
 * @since 1.0
 */
public interface TaskExecutor {

    /**
     * 启动任务（异步）
     * 
     * <p>这是执行器的核心方法，负责启动外部任务（如 Docker 容器、Python 脚本）。
     * 此方法必须**快速返回**，不能阻塞等待任务完成。</p>
     * 
     * <p>执行器应该：</p>
     * <ol>
     *   <li>从任务实体中获取必要的参数</li>
     *   <li>获取鉴权信息（如需要）并绑定到任务</li>
     *   <li>启动外部任务（Docker 容器、脚本等）</li>
     *   <li>立即返回，不等待执行结果</li>
     * </ol>
     * 
     * <p>结果处理：</p>
     * <ul>
     *   <li>外部任务完成后，通过 HTTP 回调通知结果</li>
     *   <li>回调地址：/api/task/callback/{taskType}</li>
     *   <li>回调参数：taskId, success, result</li>
     * </ul>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>启动失败应抛出异常，调度器会触发重试</li>
     *   <li>鉴权信息应在任务中记录，回调时释放</li>
     *   <li>需要记录任务与外部资源的映射关系</li>
     * </ul>
     * 
     * @param task 任务实体，包含任务的所有信息
     * @return true 表示启动成功，false 表示启动失败
     * @throws Exception 启动过程中发生的异常
     */
    boolean start(TaskInfoEntity task) throws Exception;

    /**
     * 获取执行器支持的任务类型
     * 
     * <p>返回执行器支持的任务类型编码，应与TaskType枚举中的code字段一致。
     * 调度器根据此方法返回值将任务分发给对应的执行器。</p>
     * 
     * @return 任务类型编码（如："ENS"、"AI"、"特殊"）
     */
    String getTaskType();

    /**
     * 关闭执行器
     * 
     * <p>当应用关闭时，调度器会调用此方法通知执行器释放资源。
     * 执行器应该：</p>
     * <ul>
     *   <li>停止接受新任务</li>
     *   <li>等待正在执行的任务完成</li>
     *   <li>释放所有占用的资源</li>
     * </ul>
     * 
     * <p>默认实现为空，子类可以根据需要覆盖。</p>
     */
    default void shutdown() {
        // 默认空实现，子类可以覆盖
    }

    /**
     * 获取执行器名称
     * 
     * <p>用于日志记录和监控。</p>
     * 
     * @return 执行器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 检查执行器是否健康
     * 
     * <p>用于健康检查，如果执行器处于不健康状态（如资源耗尽），
     * 可以返回false，调度器将暂停向该执行器分发任务。</p>
     * 
     * @return true表示健康，false表示不健康
     */
    default boolean isHealthy() {
        return true;
    }
}
