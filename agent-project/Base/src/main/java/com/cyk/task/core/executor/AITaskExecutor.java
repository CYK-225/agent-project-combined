package com.cyk.task.core.executor;


import com.cyk.DockerTool.V1.DockerV1Controller;
import com.cyk.Enity.V1ActionTo;
import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Service.IAuthInfoService;
import com.cyk.task.core.enums.TaskType;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI任务执行器（异步回调模式）
 * 
 * <p>【核心职责】</p>
 * 本执行器负责启动AI驱动的自动化任务。
 * 
 * <p>【支持的任务类型】</p>
 * <ul>
 *   <li><b>AI</b>：标准AI自动化任务</li>
 *   <li><b>SPECIAL</b>：特殊任务，路由到此执行器，通过task.getTaskType()区分</li>
 * </ul>
 * 
 * <p>【路由逻辑】</p>
 * SPECIAL类型任务会被TaskScheduler路由到此执行器，
 * 区别仅在于提示词，由TaskService根据taskType处理。
 *
 * @author system
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AITaskExecutor implements TaskExecutor {

    private final IAuthInfoService authInfoService;
    private final CustomTaskScheduler customTaskScheduler;

    private final DockerV1Controller dockerV1Controller;

    /**
     * 初始化时注册到调度器
     *
     * <p>只注册AI类型，SPECIAL类型由TaskScheduler路由到此执行器</p>
     */
    @PostConstruct
    public void init() {
        // 只注册AI类型，SPECIAL会路由到这里
        customTaskScheduler.registerExecutor(TaskType.AI.getCode(), this);

        log.info("AI任务执行器已注册（异步模式，同时处理AI和SPECIAL类型）");
    }

    @Override
    public boolean start(TaskInfoEntity task) throws Exception {
        log.info("[AI执行器] 准备启动任务: ID={}, 公司={}, 类型={}, 用户指定配置={}",
                task.getId(), task.getCompanyName(),
                task.getTaskType(), task.getConfigName());
        // 获取原始任务类型，区分AI和SPECIAL
        String originalTaskType = task.getTaskType();
        boolean isSpecial = TaskType.SPECIAL.getCode().equals(originalTaskType);

        log.info("[AI执行器] 启动任务: ID={}, 公司={}, 类型={}, 用户指定配置={}",
                task.getId(), task.getCompanyName(),
                isSpecial ? "SPECIAL" : "AI",
                task.getConfigName());

        String targetWebsite = task.getWebAddress();
        String preferredConfigName = task.getConfigName();

        AuthInfoEntity auth = authInfoService.acquireAccountWithPriority(
                targetWebsite, 
                preferredConfigName, 
                task.getIsExclusive()
        );
        
        if (auth == null) {
            throw new Exception(STR."无法获取可用账号: 网站=\{targetWebsite}, 用户指定配置=\{preferredConfigName}");
        }

        try {
            task.setConfigName(auth.getCloudStorageName());
            log.info("[AI执行器] 已绑定鉴权信息: taskId={}, authId={}, 最终使用配置={}, 任务类型={}",
                    task.getId(), auth.getId(), auth.getCloudStorageName(),
                    isSpecial ? "SPECIAL" : "AI");

            V1ActionTo actionTo = new V1ActionTo();
            actionTo.setInstruction(task.getMission());
            actionTo.setConfigName(task.getConfigName());
            actionTo.setUpdateIs(task.getIsExclusive());
            actionTo.setTaskId(task.getId());
            actionTo.setOutputFormat(task.getCollectedFields());
            Map<String, String> result = dockerV1Controller.action(actionTo);
            log.info("[AI执行器] 执行器返回值 : result={}",result);


            if (result.get("status").equals("success")) {
                log.info("[AI执行器] 自动化流程执行成功: taskId={}, 任务类型={}",
                        task.getId(), isSpecial ? "SPECIAL" : "AI");

                return true;
            } else {
                log.error("[AI执行器] 自动化流程启动失败: taskId={}, 任务类型={}",
                        task.getId(), isSpecial ? "SPECIAL" : "AI");
                authInfoService.releaseAccount(auth.getId());
                return false;
            }
            
        } catch (Exception e) {
            log.error("[AI执行器] 任务启动异常: taskId={}, 任务类型={}",
                    task.getId(), isSpecial ? "SPECIAL" : "AI", e);
            authInfoService.releaseAccount(auth.getId());
            throw e;
        }
    }

    @Override
    public String getTaskType() {
        return TaskType.AI.getCode();
    }

    @Override
    public String getName() {
        return "AI任务执行器（处理AI和SPECIAL）";
    }
}
