package com.cyk.DockerTool.V1;


import com.cyk.DockerTool.V1.config.V1AgentProperties;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import com.github.dockerjava.api.model.Container;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docker")
@Slf4j
public class DockerV1CallBack {

    @Resource
    private CustomTaskScheduler customTaskScheduler;


    private final DockerService dockerService;
    private final V1AgentProperties properties;

    public DockerV1CallBack(DockerService dockerService, V1AgentProperties properties) {
        this.dockerService = dockerService;
        this.properties = properties;
    }
    /**
     * 接收 Python 脚本回调的接口
     */
    @PostMapping("/callback")
    public String handleAgentCallback(@RequestBody Map<String, Object> payload) {
        String taskId = (String) payload.get("task_id");
        log.info("接收到回调数据: {}", payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        String status = (String) data.get("status");
        System.out.println("当前时间时分秒: " + LocalTime.now().withNano(0));

        if ("processing".equals(status)) {
            String imageUrl = (String) data.get("image_url");
            String action = (String) data.get("action");
            System.out.println(String.format("[执行中] 任务: %s | 动作: %s | 最新截图地址: %s", taskId, action, imageUrl));
        } else if ("completed".equals(status) || "terminated".equals(status)) {
            customTaskScheduler.onTaskCompleted(Long.valueOf(taskId),"AI",true, data);
            System.out.println(String.format("[任务结束] 任务: %s | 结果: %s", taskId, data.get("result")));
        } else if ("timeout".equals(status) || "failure".equals(status)) {
            customTaskScheduler.onTaskCompleted(Long.valueOf(taskId),"AI",false, data);
            System.out.println(String.format("[任务异常] 任务: %s | 结果: %s", taskId, data.get("result")));
        }


        return "RECEIVED";
    }
}
