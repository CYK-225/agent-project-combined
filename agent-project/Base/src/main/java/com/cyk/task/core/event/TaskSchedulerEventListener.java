package com.cyk.task.core.event;


import com.cyk.task.core.scheduler.CustomTaskScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 任务调度事件监听器
 * 
 * <p>【核心职责】</p>
 * 监听任务入队事件，异步触发任务调度。
 * 
 * <p>【异步处理】</p>
 * <pre>
 * 前端请求线程                      异步线程
 *      │                              │
 *      ├── 创建任务                   │
 *      ├── 入队                       │
 *      ├── 发布事件                   │
 *      └── 立即返回 taskId            │
 *                                     │
 *                           ┌────────┴────────┐
 *                           │ @Async @EventListener
 *                           │ 监听到事件
 *                           └────────┬────────┘
 *                                    │
 *                                    ├── 触发调度
 *                                    └── 启动任务
 * </pre>
 * 
 * <p>【优势】</p>
 * <ul>
 *   <li>前端接口响应时间：~10ms（只有入队和发布事件）</li>
 *   <li>调度逻辑在异步线程执行，不阻塞请求</li>
 *   <li>即使调度失败，也不影响前端收到 taskId</li>
 * </ul>
 * 
 * @author system
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSchedulerEventListener {

    private final CustomTaskScheduler customTaskScheduler;

    /**
     * 监听任务入队事件（异步处理）
     * <p>当新任务入队时，触发对应类型的任务调度。</p>
     * @param event 任务入队事件
     */
    @Async
    @EventListener
    public void onTaskEnqueued(TaskEnqueuedEvent event) {
        String taskType = event.getTaskType();
        Long taskId = event.getTask().getId();
        
        log.info("[事件监听] 收到任务入队事件: taskId={}, taskType={}", taskId, taskType);
        
        try {
            // 触发调度（如果有任务正在执行，这次调用会从空队列返回null，不会重复执行）
            customTaskScheduler.triggerNextTask(taskType);
            
            log.info("[事件监听] 调度触发完成: taskId={}, taskType={}", taskId, taskType);
            
        } catch (Exception e) {

            log.error("[事件监听] 调度触发失败: taskId={}, taskType={}", taskId, taskType, e);

        }
    }
}
