package com.cyk.task.core.event;


import com.cyk.task.DAL.DO.TaskInfoEntity;
import org.springframework.context.ApplicationEvent;

/**
 * 任务入队事件
 * 
 * <p>【核心职责】</p>
 * 当新任务入队时发布此事件，解耦任务创建与调度触发。
 * 
 * <p>【事件流程】</p>
 * <pre>
 * TaskService.startTask()
 *       │
 *       ├── 创建任务
 *       ├── 任务入队
 *       └── 发布 TaskEnqueuedEvent
 *                │
 *                │ (异步)
 *                ▼
 *       TaskSchedulerEventListener
 *                │
 *                └── 触发调度
 * </pre>
 * 
 * <p>【解耦优势】</p>
 * <ul>
 *   <li>前端请求立即返回，不等待调度</li>
 *   <li>调度逻辑异步执行，不影响接口性能</li>
 *   <li>事件驱动，易于扩展（可添加日志、监控等监听器）</li>
 * </ul>
 * 
 * @author system
 * @since 1.0
 */
public class TaskEnqueuedEvent extends ApplicationEvent {

    /**
     * 任务类型
     */
    private final String taskType;
    
    /**
     * 任务实体
     */
    private final TaskInfoEntity task;

    /**
     * 构造方法
     * @param task 任务实体
     */

    public TaskEnqueuedEvent(Object source, TaskInfoEntity task) {
        super(source);
        this.task = task;
        this.taskType = task.getTaskType();
    }


    public String getTaskType() {
        return taskType;
    }


    public TaskInfoEntity getTask() {
        return task;
    }


}
