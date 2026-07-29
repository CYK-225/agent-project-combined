package com.cyk.task.DAL.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 任务数据访问服务聚合类
 * 
 * <p>【核心职责】</p>
 * 本类是一个聚合服务类，统一管理任务调度系统所需的所有数据访问服务。
 * 通过构造器注入的方式，一次性提供所有服务的访问入口，简化依赖注入。
 * 
 * <p>【设计原理】</p>
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │         AllTaskService                  │
 * ├─────────────────────────────────────────┤
 * │  - authInfoService (鉴权服务)           │
 * │  - taskInfoService (任务服务)           │
 * │  - taskLogService (日志服务)            │
 * └─────────────────────────────────────────┘
 *                    │
 *                    ▼
 *         提供给Controller/Executor使用
 * </pre>
 * 
 * <p>【使用优势】</p>
 * <ul>
 *   <li><b>简化注入</b>：只需注入一个类，即可访问所有服务</li>
 *   <li><b>统一管理</b>：所有数据访问服务集中管理</li>
 *   <li><b>易于测试</b>：可以方便地Mock所有服务</li>
 *   <li><b>代码简洁</b>：避免构造器参数过多</li>
 * </ul>
 * 
 * <p>【使用示例】</p>
 * <pre>
 * // 方式1：注入AllTaskService
 * {@literal @}Autowired
 * private AllTaskService allTaskService;
 * 
 * // 使用服务
 * allTaskService.taskInfoService.createTask(...);
 * allTaskService.authInfoService.acquireAccount(...);
 * 
 * // 方式2：构造器注入（推荐）
 * public class MyExecutor {
 *     private final AllTaskService allTaskService;
 *     
 *     public MyExecutor(AllTaskService allTaskService) {
 *         this.allTaskService = allTaskService;
 *     }
 * }
 * </pre>
 * 
 * <p>【注意事项】</p>
 * <ul>
 *   <li>所有字段都是public final，不可修改</li>
 *   <li>使用@RequiredArgsConstructor自动生成构造器</li>
 *   <li>建议在业务复杂时拆分为更细粒度的服务</li>
 * </ul>
 * 
 * @author system
 * @since 1.0
 * @see IAuthInfoService 鉴权信息服务
 * @see ITaskInfoService 任务信息服务
 * @see ITaskLogService 任务日志服务
 */
@Component
@RequiredArgsConstructor
public class AllTaskService {
    
    /**
     * 鉴权信息服务
     * 
     * <p>提供账号池管理、独占锁控制、健康检查等功能。</p>
     * 
     * @see IAuthInfoService
     */
    public final IAuthInfoService authInfoService;
    
    /**
     * 任务信息服务
     * 
     * <p>提供任务的创建、查询、状态管理、重试等功能。</p>
     * 
     * @see ITaskInfoService
     */
    public final ITaskInfoService taskInfoService;
    
    /**
     * 任务日志服务
     * 
     * <p>提供任务执行日志的记录和查询功能。</p>
     * 
     * @see ITaskLogService
     */
    public final ITaskLogService taskLogService;
}
