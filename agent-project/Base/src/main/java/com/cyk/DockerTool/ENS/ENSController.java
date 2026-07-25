package com.cyk.DockerTool.ENS;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;

import com.cyk.DockerTool.ENS.cmd.ENSAgentRunConfig;
import com.cyk.DockerTool.ENS.config.ENSAgentProperties;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Container;

import jakarta.annotation.Resource;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Collections.singleton;

@RestController
@RequestMapping("/api/docker/ens_controller")
@Slf4j
public class ENSController {

    @Resource
    private SseService sseService;

    @Resource
    private CustomTaskScheduler customTaskScheduler;

    @Resource
    private  ENSService ensService;
    @Resource
    private ENSAgentProperties properties;
    @Resource
    private  ObjectMapper objectMapper; // 引入 ObjectMapper 用于解析 JSON 字符串

    // 任务状态缓存 (实际生产中应使用 Redis 等持久化存储)
    private static final Map<String, Map<String, String>> taskStatusMap = new ConcurrentHashMap<>();

    // ⚠️ 核心：创建一个固定大小为 3 的线程池（防止批量发送时服务器内存被Docker撑爆）
    private final ExecutorService dockerExecutor = Executors.newFixedThreadPool(3);

    /**
     * 测试接口 1：获取所有容器列表
     * 访问地址: GET http://localhost:8080/api/docker/ens/containers
     */
    @GetMapping("/containers")
    public List<Container> listContainers() {
        return ensService.listAllContainers();
    }

    /**
     * 测试接口 2：启动指定的容器
     * 访问地址: POST http://localhost:8080/api/docker/ens/containers/{容器ID}/start
     */
    @PostMapping("/containers/{id}/start")
    public String startContainer(@PathVariable("id") String containerId) {
        try {
            ensService.startContainer(containerId);
            return "容器 [" + containerId + "] 启动成功！";
        } catch (Exception e) {
            return "容器启动失败：" + e.getMessage();
        }
    }

    /**
     * 执行企业信息查询任务
     * 访问地址: POST http://localhost:8080/api/docker/ens/query
     *
     * @param companyName 企业名称
     * @param type        查询类型 (aqc/qcc/tianyan)，默认 aqc
     * @return 任务 ID 和容器 ID
     */
    @PostMapping("/query")
    public Map<String, String> queryCompany(
            @RequestParam("company") String companyName,
            @RequestParam(value = "type", defaultValue = "aqc") String type,
            @RequestParam("configName") String configName,
            @RequestParam("taskId") Long taskId
    ) {
        ENSAgentRunConfig config = new ENSAgentRunConfig();
        config.setCompanyName(companyName);
        config.setType(type);
        config.setCallbackUrl(properties.getDefaultCallbackUrl());
        config.setConfigName(configName);
        config.setTaskId(String.valueOf(taskId));

        try {
            String containerId = ensService.runENSAgent(config);
            log.info("任务创建成功，任务ID: " + taskId);

            // 初始化任务状态
            Map<String, String> initialStatus = new ConcurrentHashMap<>();
            initialStatus.put("status", "pending");
            initialStatus.put("company", companyName);
            initialStatus.put("type", type);
            initialStatus.put("containerId", containerId);
            initialStatus.put("createdAt", LocalTime.now().withNano(0).toString());
            taskStatusMap.put(String.valueOf(taskId), initialStatus);

            return Map.of(
                    "status", "success",
                    "taskId", String.valueOf(taskId),
                    "containerId", containerId,
                    "message", "任务已创建，请通过回调或状态查询接口获取结果"
            );
        } catch (Exception e) {
            // 【关键新增】在控制台打印完整的错误堆栈，这能看到具体的网络报错或 Docker 响应码
            log.error("批量创建容器失败，Company: {}, 错误信息: ", companyName, e);

            return Map.of(
                    "status", "failure",
                    // 尝试获取更深层的错误原因
                    "error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage()
            );
        }
    }

    /**
     * 查询任务状态
     * 访问地址: GET http://localhost:8080/api/docker/ens/status/{taskId}
     */
    @GetMapping("/status/{taskId}")
    public Map<String, Object> getTaskStatus(@PathVariable("taskId") String taskId) {
        Map<String, String> status = taskStatusMap.get(taskId);
        if (status == null) {
            return Map.of(
                    "status", "success",
                    "error", "任务不存在: " + taskId
            );
        }
        return Map.of(
                "status", "failure",
                "taskId", taskId,
                "data", status
        );
    }

//    /**
//     * 0. 前端订阅 SSE 接口(单ens)
//     */
//    @GetMapping(value = "/sse/connect/{clientId}", produces = "text/event-stream")
//    public SseEmitter connectSse(@PathVariable String clientId) {
//        return sseService.createConnect(clientId);
//    }

//    /**
//     * 1. 批量获取接口 (修改版)
//     */
//    @PostMapping("/scan/batch")
//    public ResultData<String> scanBatch(@RequestBody BatchScanRequest request) {
//        if (request.getCompanies() == null || request.getCompanies().isEmpty()) return ResultData.error("必须提供公司名称");
//        if (request.getConfigName() == null) return ResultData.error("必须提供配置名称");
//        // 确保前端传了 clientId
//        String clientId = request.getClientId();
//        for (String company : request.getCompanies()) {
//            // 🌟 提前生成 taskId 并绑定
//            String taskId = RandomUtil.randomNumbers(10);
//            if (clientId != null) {
//                sseService.bindTaskToClient(taskId, clientId);
//                // 🌟 推送初始状态：未开始
//                sseService.sendEventByTaskId(taskId, company, "未开始", null);
//            }
//
//            dockerExecutor.submit(() -> {
//                try {
//                    ENSAgentRunConfig config = new ENSAgentRunConfig();
//                    config.setTaskId(taskId); // 注入我们生成的 taskId
//                    config.setCompanyName(company);
//                    config.setConfigName(request.getConfigName());
//                    config.setType("aqc");
//                    config.setCallbackUrl("http://8.129.128.167:8081/api/docker/ens/callback1");
//                    ensService.runENSAgent(config);
//                } catch (Exception e) {
//                    log.error("启动批量任务异常: {}", e.getMessage());
//                    sseService.sendEventByTaskId(taskId, company, "失败", e.getMessage());
//                }
//            });
//        }
//        return ResultData.success("任务已开始");
//    }
//
//    /**
//     * 2. 单个获取接口 (修改版)
//     */
//    @PostMapping("/scan/single/{clientId}/{configName}/{companyName}")
//    public ResultData<String> scanSingle(@PathVariable String clientId, @PathVariable String configName, @PathVariable String companyName) {
//        String taskId = RandomUtil.randomNumbers(10);
//
//        sseService.bindTaskToClient(taskId, clientId);
//        sseService.sendEventByTaskId(taskId, companyName, "未开始", null);
//
//        ENSAgentRunConfig config = new ENSAgentRunConfig();
//        config.setTaskId(taskId);
//        config.setCompanyName(companyName);
//        config.setConfigName(configName);
//        config.setType("aqc");
//
//        try {
//            String containerId = ensService.runENSAgent(config);
//            log.info("[任务开始] 容器: {} | 任务: {} | 公司: {}", containerId, taskId, companyName);
//            return ResultData.success("任务已开始");
//        } catch (Exception e) {
//            sseService.sendEventByTaskId(taskId, companyName, "失败", e.getMessage());
//            return ResultData.error("任务执行失败: " + e.getMessage());
//        }
//    }

    /**
     * 获取所有任务状态（调试用）
     * 访问地址: GET http://localhost:8080/api/docker/ens/tasks
     */
    @GetMapping("/tasks")
    public Map<String, Object> getAllTasks() {
        return Map.of(
                "status", "success",
                "count", singleton(taskStatusMap.size()),
                "tasks", taskStatusMap
        );
    }

    /**
     * 清理指定任务的状态
     * 访问地址: DELETE http://localhost:8080/api/docker/ens/tasks/{taskId}
     */
    @DeleteMapping("/tasks/{taskId}")
    public Map<String, String> clearTask(@PathVariable("taskId") String taskId) {
        Map<String, String> removed = taskStatusMap.remove(taskId);
        if (removed == null) {
            return Map.of(
                    "status", "fail",
                    "error", "任务不存在: " + taskId
            );
        }
        return Map.of(
                "status", "success",
                "message", "任务已清理: " + taskId
        );
    }


    // 内部 DTO 类，用于接收 JSON
    @Data
    public static class BatchScanRequest {
        private String configName;      // 账号，例如 13145739225
        private List<String> companies; // 公司名称列表
        private String clientId;
    }
}