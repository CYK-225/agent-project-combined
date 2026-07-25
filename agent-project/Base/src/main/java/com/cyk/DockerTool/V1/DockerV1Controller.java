package com.cyk.DockerTool.V1;


import com.cyk.DockerTool.V1.cmd.GuiAgentRunConfig;
import com.cyk.DockerTool.V1.config.V1AgentProperties;
import com.cyk.Enity.V1ActionTo;
import com.github.dockerjava.api.model.Container;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docker_controller")
@Slf4j
public class DockerV1Controller {


    @Resource
    private   DockerService dockerService;
    @Resource
    private V1AgentProperties properties;

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

    /**
     * 登录/更新配置接口
     */
    @PostMapping("/containers/login")
    public Map<String, String> performAction(
            @RequestParam("instruction") String instruction,
            @RequestParam(value = "configName", required = false) String configName,
            @RequestParam(value = "updateIs", required = false, defaultValue = "false") Boolean updateIs,
            @RequestParam("taskId") String taskId
    ) {

        Map<String, String> result = new HashMap<>();

        try {
            GuiAgentRunConfig config = buildRunConfig(instruction, configName, updateIs, true, taskId);
            String containerId = dockerService.runGuiAgent(config);

            result.put("result", "获取容器ID: " + containerId);
            result.put("status", "success");
            System.out.println("获取容器ID: " + containerId);

            return result;
        } catch (Exception e) {
            // 【关键新增】在控制台打印完整的错误堆栈，这能看到具体的网络报错或 Docker 响应码
            log.error("批量创建容器失败，Company: {}, 错误信息: ", configName, e);

            return Map.of(
                    "status", "failure",
                    // 尝试获取更深层的错误原因
                    "error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage()
            );
        }
    }

    /**
     * 执行操作接口
     */
    @PostMapping("/containers/action")
    public Map<String, String> action(@RequestBody V1ActionTo v1ActionTo) {
        try {
            Map<String, String> result = new HashMap<>();

            String Instruction = v1ActionTo.getInstruction();

            Instruction = this.getInstruction();

            GuiAgentRunConfig config = buildRunConfig(Instruction, v1ActionTo.getConfigName(), false, false, String.valueOf(v1ActionTo.getTaskId()));
            config.setOutputFormat(v1ActionTo.getOutputFormat());
            String containerId = dockerService.runGuiAgent(config);
            result.put("result", "获取容器ID: " + containerId);
            result.put("status", "success");
            return result;
        } catch (Exception e) {
            log.error("批量创建容器失败，Company: {}, 错误信息: ", v1ActionTo.getConfigName(), e);

            return Map.of(
                    "status", "failure",
                    // 尝试获取更深层的错误原因
                    "error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage()
            );
        }
    }

    /**
     * 构建运行时配置（从 V1AgentProperties 获取默认值）
     *
     * @param instruction    指令（必填）
     * @param profileName    配置文件名称（可选，使用默认值）
     * @param isUpdateProfile 是否更新配置
     * @param isLoginAction  是否为登录操作（登录操作使用空 API Key）
     * @return GuiAgentRunConfig 运行时配置对象
     */
    private GuiAgentRunConfig buildRunConfig(String instruction, String profileName,
                                             Boolean isUpdateProfile, boolean isLoginAction,String taskId) {
        GuiAgentRunConfig config = new GuiAgentRunConfig();

        // 从 V1AgentProperties 获取所有默认配置
        config.setImageName(properties.getImageName());
        config.setMemoryBytes(properties.getMemoryLimit());
        config.setMemorySwapBytes(properties.getMemorySwap());
        config.setShmSizeBytes(properties.getShmSize());
        config.setMaxSteps(properties.getMaxSteps());
        config.setCallbackUrl(properties.getDefaultCallbackUrl());

        // LLM API 配置
        config.setBaseUrl(properties.getBaseUrl());
        config.setModel(properties.getModel());
        // 登录操作使用空 API Key，其他操作使用配置的 API Key
        config.setApiKey(isLoginAction ? "" : properties.getApiKey());

        // 配置文件名称（使用默认值或传入值）
        config.setProfileName(profileName != null ? profileName : properties.getDefaultProfileName());
        config.setIsUpdateProfile(isUpdateProfile != null ? isUpdateProfile : false);

        // 动态生成的值
        config.setInstruction(instruction);
        config.setTaskId(taskId);

        return config;
    }




    private String getInstruction() {
        return "# Role\n" +
                "你是一个高效率的自动化计算机助手。你将通过观察屏幕截图操作电脑，专门完成【<companyName>】的【企业员工画像侧写】调研任务。\n" +
                "\n" +
                "# Task Workflow\n" +
                "请严格按照以下步骤执行，遇到付费墙、APP引流或登录弹窗优先寻找关闭按钮跳过：\n" +
                "\n" +
                "1. 启动浏览器访问百度 (baidu.com) 或直接访问 BOSS直聘 (zhipin.com)。\n" +
                "2. 检索“<companyName> 招聘”。\n" +
                "3. 浏览搜索结果，重点观察该公司所属的【行业属性】、前几个【主要在招岗位】以及卡片上显著标示的【薪资区间】（如 BOSS 直聘列表页直接显示的 8-12K 等）。\n" +
                "4. [画像侧写推断规则] 绝对禁止在网页中强行寻找精确的比例或均值数值，以免陷入死循环！请严格基于你观察到的信息进行常识推断：\n" +
                "   - 年龄比例：若岗位多为新兴技术、新媒体或初级销售等，推断为“90后与00后为主(约70-80%)”；若多为传统制造或重经验管理岗位，推断为“80后与90后为主”。\n" +
                "   - 性别比例：若核心岗位为硬核技术开发，推断为“男性主导”；若为客服、运营、人事等，推断为“女性主导”；若为综合类业务，推断为“男女均衡”。\n" +
                "   - 薪资水平：结合前几个在招岗位的薪资区间进行综合估算。若多数基础岗位薪资在 4-8K，推断为“基层薪资水平(约4-8K)”；若多数岗位在 8-15K，推断为“中等薪资水平(约8-15K)”；若频现 15K 以上的技术、研发或中高层管理岗，推断为“中高薪资水平(15K+)”。\n" +
                "   - 输出结果时，所有推断结果后必须备注“(基于行业属性与在招岗位预估)”。\n" +
                "5. 任务完成后，必须使用 answer 动作输出包含这三个字段（年龄比例、性别比例、薪资水平）的 Markdown 表格，并在表格下方说明你的推断依据（即你看到了哪些具体岗位和大概薪资区间）。\n" +
                "\n" +
                "# Action Set (必须严格遵守此 JSON 格式)\n" +
                "你的输出必须是单一的纯 JSON 字符串，严禁使用 <tool_call> 或 XML 标签。坐标系为 0-1000 相对坐标。\n" +
                "- 左键点击: {\"action\": \"left_click\", \"coordinate\": [x, y]}\n" +
                "- 键入文本: {\"action\": \"type\", \"text\": \"内容\"}\n" +
                "- 快捷键: {\"action\": \"key\", \"keys\": [\"enter\"]} (支持 ctrl, a, backspace)\n" +
                "- 滚动页面: {\"action\": \"scroll\", \"pixels\": 500} (正数向下，负数向上)\n" +
                "- 等待加载: {\"action\": \"wait\", \"time\": 3}\n" +
                "- 提交结果: {\"action\": \"answer\", \"text\": \"Markdown表格内容\"} (任务结束必用)\n" +
                "\n" +
                "# ⚠\uFE0F 动作限制与输入框操作规范 (CRITICAL)\n" +
                "1. 动作白名单限制：绝对禁止输出 `mouse_move`、`left_click_drag` 等未受支持的动作，否则会导致系统崩溃！\n" +
                "2. 清空/替换文本规范：如果需要清空或替换搜索栏中的已有文本，严禁尝试鼠标拖拽选中！必须严格按照以下步骤执行：\n" +
                "   - 第一步：使用 left_click 点击输入框内部激活光标。\n" +
                "   - 第二步：使用快捷键全选 {\"action\": \"key\", \"keys\": [\"ctrl\", \"a\"]}。\n" +
                "   - 第三步：使用快捷键删除 {\"action\": \"key\", \"keys\": [\"backspace\"]}。\n" +
                "   - 第四步：使用 type 动作输入新的文本内容。\n" +
                "\n" +
                "# Output Requirement\n" +
                "请立即开始第一步操作。记住：只输出 JSON 动作，不要解释。";
    }
}
