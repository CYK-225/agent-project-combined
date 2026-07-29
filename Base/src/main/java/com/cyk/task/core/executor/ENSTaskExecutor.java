package com.cyk.task.core.executor;


import com.cyk.DockerTool.ENS.ENSController;
import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Service.IAuthInfoService;
import com.cyk.task.core.enums.TaskType;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ENS任务执行器（异步回调模式）
 * 
 * <p>【核心职责】</p>
 * 本执行器负责**启动**ENS（企业信息查询）类型的任务。注意：只负责启动，
 * 不等待执行结果。结果由 Python 端通过 HTTP 回调通知。
 * 
 * <p>【异步执行流程】</p>
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │         ENS任务执行器（异步模式）            │
 * ├─────────────────────────────────────────────┤
 * │  1. 从任务中获取企业名称和查询参数          │
 * │  2. 从账号池获取鉴权信息（Cookie）          │
 * │  3. 将鉴权信息ID绑定到任务                 │
 * │  4. 调用 ENSController 启动 Docker 容器     │
 * │  5. 立即返回（不等待结果）                  │
 * └─────────────────────────────────────────────┘
 *                    │
 *                    │ Docker 容器异步执行
 *                    ▼
 *              ┌──────────┐
 *              │  Python  │
 *              │  Script  │
 *              └────┬─────┘
 *                   │
 *                   │ HTTP 回调
 *                   ▼
 *         /api/task/callback/ens
 *                   │
 *                   ▼
 *           TaskScheduler.onTaskCompleted
 *                   │
 *                   ├──── 更新任务状态
 *                   ├──── 保存结果
 *                   └──── 释放鉴权信息
 * </pre>
 * 
 * <p>【与同步模式的区别】</p>
 * <pre>
 * 同步模式（旧）：
 *   result = executor.execute(task);  // 阻塞等待 Python 返回
 *   handleResult(result);
 * 
 * 异步模式（新）：
 *   executor.start(task);             // 只启动容器，立即返回
 *   // ... Python 异步执行 ...
 *   onCallback(taskId, result);       // Python 完成后回调
 * </pre>
 * 
 * <p>【鉴权信息管理】</p>
 * <ul>
 *   <li>启动任务时获取鉴权信息并绑定到任务</li>
 *   <li>鉴权信息ID存入任务的 auth_id 字段</li>
 *   <li>Python 回调时携带 authId，由调度器释放</li>
 * </ul>
 * 
 * @author system
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ENSTaskExecutor implements TaskExecutor {

    /**
     * 鉴权信息服务
     */
    private final IAuthInfoService authInfoService;

    /**
     * 外部执行器服务（包含ENSController）
     */


    /**
     * 任务调度器（用于注册自己）
     */
    private final CustomTaskScheduler customTaskScheduler;

    /**
     * ENSController ai任务现已封装好的启动流程
     */
    @Resource
    private final ENSController ensController;

    /**
     * 初始化时注册到调度器
     */
    @PostConstruct
    public void init() {
        customTaskScheduler.registerExecutor(TaskType.ENS.getCode(), this);
        log.info("ENS任务执行器已注册（异步模式）");
    }

    /**
     * 启动ENS任务（异步）
     * 
     * <p>此方法只负责启动任务，不等待执行结果。
     * Python 端执行完成后会通过 HTTP 回调通知结果。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>从任务中提取企业名称和查询参数</li>
     *   <li>根据查询类型选择对应的鉴权信息</li>
     *   <li>将鉴权信息绑定到任务（存入 auth_id）</li>
     *   <li>调用 ENSController 启动 Docker 容器</li>
     *   <li>立即返回（不等待结果）</li>
     * </ol>
     * 
     * @param task 任务实体
     * @return true 表示启动成功，false 表示启动失败
     * @throws Exception 启动异常
     */
    @Override
    public boolean start(TaskInfoEntity task) throws Exception
    {
        log.info("[ENS] 启动任务: ID={}, 公司={}, 用户指定配置={}",
                task.getId(), task.getCompanyName(), task.getConfigName());

        // 1. 获取目标网站
        String targetWebsite = task.getWebAddress();

        // 2. 获取用户指定的配置名称（即 cloudStorageName）
        String preferredConfigName = task.getConfigName();

        // 3. 获取鉴权信息（优先使用用户指定的配置）
        AuthInfoEntity auth = authInfoService.acquireAccountWithPriority(
                targetWebsite,
                preferredConfigName,
                task.getIsExclusive()
        );

        if (auth == null) {
            throw new Exception(STR."无法获取可用账号: 网站=\{targetWebsite}, 用户指定配置=\{preferredConfigName}");
        }

        try {
            // 4. 将最终使用的配置名称更新到任务
            task.setConfigName(auth.getCloudStorageName());
            log.info("[ENS] 已绑定鉴权信息: taskId={}, authId={}, 最终使用配置={}",
                    task.getId(), auth.getId(), auth.getCloudStorageName());

            // 5. 执行任务
            Map<String, String> result = ensController.queryCompany(
                    task.getCompanyName(),
                    task.getMission(),
                    task.getConfigName(),
                    task.getId()
            );
            log.info("[ENS执行器] 执行器返回值 : result={}",result);

            if (result.get("status").equals("success")) {
                log.info("[ENS] 自动化流程执行成功: taskId={}, result={}",
                        task.getId(), result.get("data"));
                return true;
            } else {
                log.error("[ENS] 自动化流程启动失败: taskId={}", task.getId());
                // 启动失败，立即释放鉴权信息
                authInfoService.releaseAccount(auth.getId());
                return false;
            }

        } catch (Exception e) {
            log.error("[ENS] 任务启动异常: taskId={}", task.getId(), e);
            // 异常时释放鉴权信息
            authInfoService.releaseAccount(auth.getId());
            throw e;
        }
        // 注意：正常情况下不在这里释放鉴权信息
        // 鉴权信息会在 Python 回调时由调度器释放
    }

    /**
     * 获取任务类型
     * 
     * @return ENS
     */
    @Override
    public String getTaskType() {
        return TaskType.ENS.getCode();
    }

    /**
     * 获取执行器名称
     * 
     * @return ENS任务执行器
     */
    @Override
    public String getName() {
        return "ENS任务执行器（异步）";
    }


    /**
     * 根据查询类型获取网站名称
     * 
     * @param queryType 查询类型
     * @return 网站名称
     */
    private String getWebsiteName(String queryType) {
        return switch (queryType.toLowerCase()) {

            case "rb" -> "风鸟";
            case "aqc" -> "爱企查";
            default -> queryType;
        };
    }


}
