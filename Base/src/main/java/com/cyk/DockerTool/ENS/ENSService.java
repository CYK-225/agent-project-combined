package com.cyk.DockerTool.ENS;


import com.cyk.DockerTool.ENS.cmd.ENSAgentRunConfig;
import com.cyk.DockerTool.ENS.config.ENSAgentProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ENS (ENScan) 自动化调度服务
 * 职责：负责 Docker 容器的生命周期管理，包括动态挂载配置、资源限制分配以及异步任务启动。
 */
@Slf4j
@Service
@RequiredArgsConstructor // 自动生成包含非空字段的构造函数，替代手动构造注入
public class ENSService {

    private final DockerClient dockerClient;
    private final ENSAgentProperties properties;

    /**
     * 获取宿主机上所有的 Docker 容器列表（包含已停止的）
     * * @return 容器对象列表
     */
    public List<com.github.dockerjava.api.model.Container> listAllContainers() {
        return dockerClient.listContainersCmd().withShowAll(true).exec();
    }

    /**
     * 根据容器 ID 启动一个现有的容器
     * * @param containerId 目标容器 ID
     */
    public void startContainer(String containerId) {
        log.info("正在启动容器: {}", containerId);
        dockerClient.startContainerCmd(containerId).exec();
    }

    /**
     * 核心方法：根据任务配置，动态创建并启动一个新的 ENS 采集容器
     * * @param config 包含公司名称、账号(configName)、任务ID等信息的配置对象
     * @return 创建成功的容器 ID
     */
    public String runENSAgent(ENSAgentRunConfig config) {
        // 1. 任务 ID 初始化：如果外部没传，则基于时间戳生成唯一 ID
        String taskId = (config.getTaskId() != null && !config.getTaskId().isEmpty())
                ? config.getTaskId()
                : "ens_" + System.currentTimeMillis();
        config.setTaskId(taskId);

        // 2. 路径处理：获取宿主机输出路径，并统一转化为正斜杠以兼容 Docker 挂载
        String hostOutputPath = properties.getOutputPath(taskId).replace("\\", "/");

        // 3. 挂载策略配置 (Binds)
        List<Bind> binds = new ArrayList<>();

        // 挂载点 A: 结果输出目录 (读写权限 rw)
        // 容器内部路径由 properties 动态提供
        binds.add(new Bind(hostOutputPath, new Volume(properties.getContainerOutputPath(taskId)), AccessMode.rw));

        // 挂载点 B: 账号配置文件 (只读权限 ro)
        // 核心隔离逻辑：根据 configName 动态定位 Linux 上的 .yaml 文件路径
        String configYamlPath = properties.getConfigYamlPath(config.getConfigName());
        binds.add(new Bind(configYamlPath, new Volume(properties.getContainerConfigYamlPath()), AccessMode.ro));

        // 4. 资源配置 (HostConfig)
        // 设置内存限制、Swap 限制以及共享内存大小，防止容器内存溢出导致宿主机宕机
        long memoryLimit = config.getMemoryBytes() != null ? config.getMemoryBytes() : properties.getMemoryLimit();
        long memorySwap = config.getMemorySwapBytes() != null ? config.getMemorySwapBytes() : properties.getMemorySwap();
        long shmSize = config.getShmSizeBytes() != null ? config.getShmSizeBytes() : properties.getShmSize();

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withAutoRemove(true)
                .withMemory(memoryLimit)
                .withMemorySwap(memorySwap)
                .withShmSize(shmSize)
                .withBinds(binds);

        // 5. 命令构建 (Command List)
        // 使用列表形式传递参数，避免 String.split() 可能导致的空格截断 Bug
        String callbackUrl = config.getCallbackUrl() != null ? config.getCallbackUrl() : properties.getDefaultCallbackUrl();
        List<String> cmdList = new ArrayList<>();
        cmdList.add("--company");
        cmdList.add(config.getCompanyName());
        cmdList.add("--task_id");
        cmdList.add(taskId);
        cmdList.add("--callback_url");
        cmdList.add(callbackUrl);
        // 6. 容器创建与启动
        String imageName = (config.getImageName() != null && !config.getImageName().isEmpty())
                ? config.getImageName() : properties.getImageName();

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imageName)
                .withTty(true)
                .withStdinOpen(true)
                .withEnv(
                        "TZ=Asia/Shanghai", // 设置容器时区
                        "ENSCAN_API_PORT=" + properties.getApiPort()
                )
                .withCmd(cmdList)
                .withHostConfig(hostConfig)
                .withMacAddress(config.getMacAddress() != null ? config.getMacAddress() : "02:42:ac:11:00:03")
                .withHostName("ens-agent");

        log.info("正在为任务 [{}] 创建 ENS 容器, 绑定配置文件: {}", taskId, configYamlPath);

        CreateContainerResponse container = containerCmd.exec();
        String containerId = container.getId();

        // 立即启动
        dockerClient.startContainerCmd(containerId).exec();
        log.info("ENS 容器已启动, ID: {}", containerId);

        return containerId;
    }

    /**
     * 安全地停止并强制移除容器
     * * @param containerId 目标容器 ID
     */
    public void stopAndRemoveContainer(String containerId) {
        try {
            log.info("正在停止容器: {}", containerId);
            dockerClient.stopContainerCmd(containerId).exec();
        } catch (Exception e) {
            log.warn("停止容器 {} 时发生异常（可能容器已停止）: {}", containerId, e.getMessage());
        }
        try {
            log.info("正在移除容器: {}", containerId);
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.error("强制移除容器 {} 失败: {}", containerId, e.getMessage());
        }
    }
}