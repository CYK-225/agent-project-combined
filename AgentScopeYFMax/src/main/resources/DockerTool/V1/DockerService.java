package org.example.agentScope.util.tool.DockerTool.V1;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import org.example.masfanplus.AgentScope.util.DockerTool.V1.cmd.GuiAgentRunConfig;
import org.example.masfanplus.AgentScope.util.DockerTool.V1.config.V1AgentProperties;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class DockerService {

    private final DockerClient dockerClient;
    private final V1AgentProperties properties;

    public DockerService(DockerClient dockerClient, V1AgentProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    public List<com.github.dockerjava.api.model.Container> listAllContainers() {
        return dockerClient.listContainersCmd().withShowAll(true).exec();
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public String runGuiAgent(GuiAgentRunConfig config) {
        // --- 1. 从配置获取动态的宿主机配置目录 ---
        String profileName = config.getProfileName() != null ? config.getProfileName() : "default_profile";
        String hostProfileBasePath = properties.getProfilePath(profileName);
        
        // 确保 taskId 不为空，兜底使用时间戳
        String taskId = (config.getTaskId() != null && !config.getTaskId().isEmpty())
                ? config.getTaskId()
                : "task_" + System.currentTimeMillis();
        
        // 专属图片输出目录（与配置文件同级，任务ID作为子目录）
        // 结构：{profileBasePath}/{profileName}/OutPut/{taskId}/
        String hostOutputPath = properties.getOutputPath(profileName, taskId);

        // 动态检查并创建宿主机配置目录
        File profileDir = new File(hostProfileBasePath);
        if (!profileDir.exists()) {
            profileDir.mkdirs();
        }

        // 动态检查并创建专属输出目录（包含任务ID子目录）
        File outputDir = new File(hostOutputPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // --- 2. 构建数据卷挂载策略 (Bind) ---
        List<Bind> binds = new ArrayList<>();
        // 将输出目录挂载到当前任务的专属文件夹 (rw 权限)
        // 容器内路径：/app/anno/{taskId}/
        binds.add(new Bind(hostOutputPath, new Volume(properties.getContainerOutputPath(taskId)), AccessMode.rw));

        binds.add(new Bind(properties.getUtilsScriptPath(), new Volume(properties.getContainerUtilsPath())));
        binds.add(new Bind(properties.getRunnerScriptPath(), new Volume(properties.getContainerRunnerPath())));

        String profileSetupCmd;
        String containerChromeProfilePath = properties.getContainerChromeProfilePath();
        String containerBaseProfilePath = properties.getContainerBaseProfilePath();
        
        if (config.getIsUpdateProfile()) {
            // 【可写模式 / 养号制种】：直接挂载为 RW，并清理残留锁
            binds.add(new Bind(hostProfileBasePath, new Volume(containerChromeProfilePath), AccessMode.rw));
            profileSetupCmd = "mkdir -p " + containerChromeProfilePath + " && " +
                    "chmod -R 777 " + containerChromeProfilePath + " && " +
                    "rm -rf " + containerChromeProfilePath + "/Singleton* && " +
                    "rm -rf '" + containerChromeProfilePath + "/Crashpad' && ";
        } else {
            // 【只读模式 / 并发搜索】：复制基础配置，暴力碎锁，并剔除极度消耗 I/O 的图片/代码缓存
            binds.add(new Bind(hostProfileBasePath, new Volume(containerBaseProfilePath), AccessMode.ro));
            profileSetupCmd = "mkdir -p " + containerChromeProfilePath + " && " +
                    "cp -a " + containerBaseProfilePath + "/. " + containerChromeProfilePath + "/ && " +
                    "rm -rf " + containerChromeProfilePath + "/Singleton* && " +
                    "rm -rf '" + containerChromeProfilePath + "/Crashpad' && " +
                    "rm -rf " + containerChromeProfilePath + "/Default/Cache && " +
                    "rm -rf " + containerChromeProfilePath + "/Default/Code\\ Cache && ";
        }

        // --- 3. 构建 HostConfig (从配置读取内存限制) ---
        long memoryLimit = properties.getMemoryLimit();
        long memorySwap = properties.getMemorySwap();
        long shmSize = properties.getShmSize();

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withAutoRemove(false)
                .withMemory(memoryLimit)
                .withMemorySwap(memorySwap)
                .withShmSize(shmSize)
                .withBinds(binds);

// --- 4. 构建 Bash 启动命令 ---

        String safeInstruction = config.getInstruction().replace("'", "'\\''");
        // 确保 taskId 不为空，兜底使用时间戳

        String pythonCommand = String.format(
                "%s" +
                        // 屏蔽桌面弹窗干扰，防止 AI 卡死
                        "rm -f /usr/bin/xmessage /usr/bin/fbsetbg || true && " +
                        "mkdir -p /root/.fluxbox && " +
                        "touch /root/.Xauthority && " +
                        "Xvfb :99 -ac -screen 0 1000x1000x24 > /dev/null 2>&1 & " +
                        "sleep 5 && " +
                        "fluxbox > /dev/null 2>&1 & " +
                        "sleep 2 && " +
                        "python3 -u /app/computer_use/run_gui_owl_1_5_for_pc.py " +
                        "--api_key '%s' --base_url '%s' --model '%s' --max_steps %d --instruction '%s' " +
                        "--task_id '%s' --callback_url '%s' ; " + // <--- 注意这里用分号隔开
                        "sync && sleep 5", // <--- 强制刷盘并延缓 5 秒销毁容器，给数据落盘的时间
                profileSetupCmd,
                config.getApiKey(),
                config.getBaseUrl(),
                config.getModel(),
                config.getMaxSteps(),
                safeInstruction,
                taskId,
                config.getCallbackUrl() // 确保传入回调地址
        );


        // --- 5. 创建并启动容器 ---
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(properties.getImageName())
                .withTty(true)
                .withStdinOpen(true)
                .withEnv(
                        "DISPLAY=:99",
                        "DBUS_SESSION_BUS_ADDRESS=/dev/null",
                        "LIBGL_ALWAYS_SOFTWARE=1",
                        "VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.x86_64.json",
                        "ANGLE_DEFAULT_PLATFORM=swiftshader"
                )
                .withEntrypoint("/bin/bash")
                .withCmd("-c", pythonCommand)
                .withHostConfig(hostConfig)
                // 伪装硬件 MAC 地址，维持风控指纹一致性
                .withMacAddress(config.getMacAddress() != null ? config.getMacAddress() : "02:42:ac:11:00:02")
                .withHostName("agent-pc");

        CreateContainerResponse container = containerCmd.exec();
        String containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();

        return containerId;
    }
}