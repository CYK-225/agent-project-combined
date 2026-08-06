package org.example.agentScope.util.tool.DockerTool.V1;

import com.github.dockerjava.api.model.Container;
import org.example.masfanplus.AgentScope.util.DockerTool.V1.cmd.GuiAgentRunConfig;
import org.example.masfanplus.AgentScope.util.DockerTool.V1.config.V1AgentProperties;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docker")
public class DockerController {

    private final DockerService dockerService;
    private final V1AgentProperties properties;

    public DockerController(DockerService dockerService, V1AgentProperties properties) {
        this.dockerService = dockerService;
        this.properties = properties;
    }

    /**
     * 测试接口 1：获取所有容器列表
     * 访问地址: GET http://localhost:8080/api/docker/containers
     */
    @GetMapping("/containers")
    public List<Container> listContainers() {
        return dockerService.listAllContainers();
    }

    /**
     * 测试接口 2：启动指定的容器
     * 访问地址: POST http://localhost:8080/api/docker/containers/{容器ID}/start
     */
    @PostMapping("/containers/{id}/start")
    public String startContainer(@PathVariable("id") String containerId) {
        try {
            dockerService.startContainer(containerId);
            return "容器 [" + containerId + "] 启动成功！";
        } catch (Exception e) {
            return "容器启动失败：" + e.getMessage();
        }
    }
    @PostMapping("/containers/login")
    public String performAction(@RequestParam("instruction") String instruction) {
        GuiAgentRunConfig config = new GuiAgentRunConfig();
        config.setInstruction(instruction);
        config.setIsUpdateProfile(true);
        config.setProfileName("zzh2");
        config.setCallbackUrl(properties.getDefaultCallbackUrl());
        try {
            System.out.println(STR."获取代码\{dockerService.runGuiAgent(config)}"); ;
            return "操作执行成功！";
        } catch (Exception e) {
            return STR."操作执行失败：\{e.getMessage()}";
        }
    }
    @PostMapping("/containers/action")
    public String action(@RequestParam("instruction") String instruction) {
        GuiAgentRunConfig config = new GuiAgentRunConfig();
        config.setInstruction(instruction);
        config.setIsUpdateProfile(false);
        config.setProfileName("zzh");
        config.setCallbackUrl(properties.getDefaultCallbackUrl());

        try {
            System.out.println(STR."获取代码\{dockerService.runGuiAgent(config)}"); ;
            return "操作执行成功！";
        } catch (Exception e) {
            return STR."操作执行失败：\{e.getMessage()}";
        }
    }
    // --- 新增：接收 Python 脚本回调的接口 ---
    @PostMapping("/callback")
    public String handleAgentCallback(@RequestBody Map<String, Object> payload) {
        String taskId = (String) payload.get("task_id");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        String status = (String) data.get("status");
        System.out.println(STR."当前时间时分秒: \{LocalTime.now().withNano(0)}");
        if ("processing".equals(status)) {
            String imageUrl = (String) data.get("image_url");
            String action = (String) data.get("action");
            System.out.println(String.format("[执行中] 任务: %s | 动作: %s | 最新截图地址: %s", taskId, action, imageUrl));

            // 下一步：你可以使用 Java 的 WebClient 或者 RestTemplate 获取这个 imageUrl 的图片实体流

        } else if ("completed".equals(status) || "terminated".equals(status)) {
            System.out.println(String.format("[任务结束] 任务: %s | 结果: %s", taskId, data.get("result")));
        } else if ("timeout".equals(status) || "failure".equals(status)) {
            System.out.println(String.format("[任务异常] 任务: %s | 结果: %s", taskId, data.get("result")));
        }

        return "RECEIVED";
    }
}
