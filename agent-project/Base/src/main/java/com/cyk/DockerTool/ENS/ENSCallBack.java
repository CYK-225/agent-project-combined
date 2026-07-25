package com.cyk.DockerTool.ENS;

import com.cyk.DockerTool.ENS.config.ENSAgentProperties;
import com.cyk.events.EnsDataSyncEvent;
import com.cyk.events.SsePushEvent;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Service.ITaskInfoService;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/docker/ens")
@Slf4j
public class ENSCallBack {

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private CustomTaskScheduler customTaskScheduler;

    @Resource
    private ITaskInfoService taskInfoService;


    private final ENSService ensService;
    private final ENSAgentProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, Map<String, String>> taskStatusMap = new ConcurrentHashMap<>();

    // 构造函数也记得把耦合的组件删掉（如果有的话）
    public ENSCallBack(ENSService ensService, ENSAgentProperties properties, ObjectMapper objectMapper) {
        this.ensService = ensService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/callback")
    public String handleCallback(@RequestBody Map<String, Object> payload) {
        String taskIdStr = String.valueOf(payload.get("task_id"));
        if (taskIdStr == null || "null".equals(taskIdStr)) return "INVALID";

        Long taskId = Long.valueOf(taskIdStr);
        String status = String.valueOf(payload.get("status"));

        TaskInfoEntity taskInfo = taskInfoService.getById(taskId);
        if (taskInfo == null) {
            log.warn("[ENS回调] 未找到对应的任务ID: {}", taskId);
            return "IGNORE";
        }

        String originalCompany = taskInfo.getCompanyName();

        switch (status) {
            case "started" -> {
                log.info("[任务开始] 任务: {} | 公司: {}", taskId, originalCompany);
                // 🌟 2. 发布 SSE 推送事件，由 BaiduService 去监听处理
                eventPublisher.publishEvent(new SsePushEvent(this, taskIdStr, originalCompany, "采集中", null));
            }
            case "completed" -> {
                log.info("[任务成功] 任务: {} 爬取完成！", taskId);
                Object dataObj = payload.get("data");
                if (dataObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                    if (!dataMap.isEmpty()) {
                        // 🌟 3. 发布数据同步事件，由 BaiduService 监听并调用 EnsDataSyncComponent
                        eventPublisher.publishEvent(new EnsDataSyncEvent(this, originalCompany, dataMap));
                        customTaskScheduler.onTaskCompleted(taskId, "ENS", true, dataMap);
                    }
                }
            }
            case "failure" -> {
                log.error("[任务失败] 任务: {} | 公司: {}", taskId, originalCompany);
                // 如果失败也需要推送 SSE，也可以发事件
                eventPublisher.publishEvent(new SsePushEvent(this, taskIdStr, originalCompany, "采集失败", null));
                customTaskScheduler.onTaskCompleted(taskId, "ENS", false, payload);
            }
        }
        return "SUCCESS";
    }
}