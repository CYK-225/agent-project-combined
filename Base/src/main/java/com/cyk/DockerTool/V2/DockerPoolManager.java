package com.cyk.DockerTool.V2;

import com.cyk.DockerTool.V2.config.AgentPoolProperties;
import com.cyk.DockerTool.V2.model.ContainerPod;
import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.Data;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理GUI自动化代理的Docker容器池。
 * 实现延迟容器创建和自动清理功能。
 */
@Component
public class DockerPoolManager {
    @Value("${agent.pool.java-base-url}")
    String javaBaseUrl;
    private static final Logger log = LoggerFactory.getLogger(DockerPoolManager.class);

    private final DockerClient dockerClient;
    private final AgentPoolProperties properties;
    private final RestTemplate restTemplate;

    /** 按Pod ID索引的活动容器Pod */
    private final ConcurrentHashMap<String, ContainerPod> activePods = new ConcurrentHashMap<>();

    /** 用户名到容器ID的映射（用于实现用户-容器绑定） */
    private final ConcurrentHashMap<String, String> userToPodMapping = new ConcurrentHashMap<>();

    /** 端口分配跟踪器（存储端口对，每个容器占用两个连续端口） */
    private final Set<Integer> allocatedPorts = ConcurrentHashMap.newKeySet();

    /** 端口对记录（FastAPI端口 -> VNC端口） */
    private final ConcurrentHashMap<Integer, Integer> portPairs = new ConcurrentHashMap<>();

    /** MAC地址生成器计数器 */
    private final AtomicInteger macCounter = new AtomicInteger(0);

    /** 异步操作线程池 */
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public DockerPoolManager(DockerClient dockerClient, AgentPoolProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        log.info("正在初始化DockerPoolManager，最大池大小：{}", properties.getMaxPoolSize());
        log.info("回调基础URL：{}", properties.getCallbackBaseUrl());

        // 清理上次运行遗留的孤立容器
        cleanupOrphanedContainers();
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭DockerPoolManager，清理{}个Pod", activePods.size());

        // 停止所有活动容器
        for (ContainerPod pod : activePods.values()) {
            try {
                stopAndRemoveContainer(pod.getContainerId());
            } catch (Exception e) {
                log.warn("关闭时停止容器{}失败：{}", pod.getShortId(), e.getMessage());
            }
        }

        // 清理所有用户绑定
        userToPodMapping.clear();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取或创建指定用户名的容器Pod。
     * 实现用户-容器绑定机制：
     * 1. 如果用户已有运行中的容器，直接复用
     * 2. 如果用户容器已停止，清理后创建新容器
     * 3. 如果用户没有容器，创建新容器
     *
     * @param username 用户名（用于绑定容器）
     * @param profileName 容器的配置文件名称
     * @param isUpdateProfile 是否为更新配置文件模式（true=可写挂载，false=只读挂载）
     * @return 可用的ContainerPod
     */
    public ContainerPod getOrCreatePod(String username, String profileName, boolean isUpdateProfile ,int port) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        // 1. 检查用户是否已有绑定容器
        String existingPodId = userToPodMapping.get(username);
        if (existingPodId != null) {
            ContainerPod existingPod = activePods.get(existingPodId);
            if (existingPod != null) {
                // 验证容器是否仍在运行中
                if (isContainerRunning(existingPod.getContainerId())) {
                    ContainerPod.Status podStatus = existingPod.getStatus().get();
                    log.info("用户 {} 复用已存在的运行中容器：{}，当前状态：{}",
                            username, existingPod.getShortId(), podStatus);

                    // 如果容器状态不是 READY，尝试强制释放
                    if (podStatus != ContainerPod.Status.READY) {
                        log.info("容器 {} 状态为 {}，尝试强制释放", existingPod.getShortId(), podStatus);
                        existingPod.forceRelease();
                    }

                    existingPod.touch(); // 更新最后使用时间
                    return existingPod;
                } else {
                    // 容器已停止，清理绑定关系
                    log.info("用户 {} 的容器 {} 已停止，正在清理...", username, existingPod.getShortId());
                    removeUserPodBinding(username);
                    stopAndRemoveContainer(existingPodId);
                }
            } else {
                // Pod对象不存在，清理映射
                userToPodMapping.remove(username);
            }
        }

        // 2. 检查是否可以创建新Pod
        if (activePods.size() >= properties.getMaxPoolSize()) {
            throw new IllegalStateException("代理池已满且没有可用容器。" +
                    "当前大小：" + activePods.size() + "，最大值：" + properties.getMaxPoolSize());
        }

        // 3. 创建新Pod（延迟创建）
        log.info("为用户 {} 创建新容器Pod，配置文件：{}，更新模式：{}", username, profileName, isUpdateProfile);
        return createNewPod(username, profileName, isUpdateProfile,port);
    }

    /**
     * 创建新的容器Pod。
     *
     * @param username 用户名（用于绑定和目录挂载）
     * @param profileName 配置文件名称
     * @param isUpdateProfile 是否为更新配置文件模式（true=可写挂载，false=只读挂载）
     */
    private ContainerPod createNewPod(String username, String profileName, boolean isUpdateProfile , int port) {
        // 分配端口对（FastAPI端口 + VNC端口）
        int[] portPair = allocatePortPair(port);
        int apiPort = portPair[0];  // 奇数端口，用于FastAPI
        int vncPort = portPair[1];  // 偶数端口，用于VNC
        String macAddress = generateMacAddress();
        String containerId = createContainer(username, profileName, apiPort, vncPort, macAddress, isUpdateProfile);

        // 创建Pod对象（添加用户名）
        ContainerPod pod = new ContainerPod(containerId, apiPort, vncPort, profileName, username, macAddress);
        activePods.put(containerId, pod);

        // 建立用户-容器绑定关系
        userToPodMapping.put(username, containerId);

        // 启动容器并等待就绪
        try {
            dockerClient.startContainerCmd(containerId).exec();
            log.info("容器{}已在端口{}(API)和{}(VNC)上启动，绑定用户：{}", pod.getShortId(), apiPort, vncPort, username);

            // 等待容器就绪
            waitForContainerReady(pod);

            pod.setStatus(ContainerPod.Status.READY);
            log.info("容器{}已就绪，绑定用户：{}", pod.getShortId(), username);

        } catch (Exception e) {
            log.error("启动容器{}失败：{}", pod.getShortId(), e.getMessage());
            pod.setStatus(ContainerPod.Status.ERROR);
            releasePortPair(apiPort);
            activePods.remove(containerId);
            userToPodMapping.remove(username); // 清理用户绑定

            // 【核心修复】：如果启动或超时失败，必须物理级摧毁这个半残容器，释放它的端口占用！
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
                log.info("已成功清理启动失败的僵尸容器: {}", containerId);
            } catch (Exception cleanEx) {
                log.warn("尝试清理失败的容器时出现异常: {}", cleanEx.getMessage());
            }
            throw new RuntimeException("创建容器失败：" + e.getMessage(), e);
        }

        return pod;
    }

    /**
     * 使用指定配置创建Docker容器。
     * 实现用户目录挂载：
     * - 输出目录：{profileBasePath}/{username}/OutPut
     * - 浏览器状态：{profileBasePath}/{username}/ (可写/只读根据配置)
     *
     * 包含目录校验和自动创建机制：
     * 1. 检查基础目录是否存在，不存在则自动创建
     * 2. 检查用户目录是否存在，不存在则自动创建
     * 3. 检查输出目录是否存在，不存在则自动创建
     * 4. 验证目录权限
     *
     * @param username    用户名（用于目录挂载）
     * @param profileName 配置文件名称
     * @param apiPort     FastAPI 端口（奇数）
     * @param vncPort     VNC 端口（偶数）
     * @param macAddress  MAC 地址
     * @param isUpdateProfile 是否为更新配置文件模式（true=可写挂载，false=只读挂载）
     * @return 容器ID
     */
    private String createContainer(String username, String profileName, int apiPort, int vncPort, String macAddress, boolean isUpdateProfile) {
        // 从配置获取基础路径
        String basePath = properties.getProfileBasePath();
        String outputDirName = properties.getOutputDirName();

        // 用户专属目录：{profileBasePath}/{username}/
        String hostUserBasePath = basePath + "/" + username;
        // 输出目录：{profileBasePath}/{username}/OutPut
        String hostOutputPath = hostUserBasePath + "/" + outputDirName;

        // --- 目录校验和自动创建 ---
        try {
            // 1. 检查并创建基础目录
            File baseDir = new File(basePath);
            if (!baseDir.exists()) {
                log.info("基础目录不存在，正在创建：{}", basePath);
                boolean created = baseDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("无法创建基础目录：" + basePath);
                }
                log.info("基础目录创建成功：{}", basePath);
            }

            // 2. 检查并创建用户目录
            File userDir = new File(hostUserBasePath);
            if (!userDir.exists()) {
                log.info("用户目录不存在，正在创建：{}", hostUserBasePath);
                boolean created = userDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("无法创建用户目录：" + hostUserBasePath);
                }
                log.info("用户目录创建成功：{}", hostUserBasePath);
            }

            // 3. 检查并创建输出目录
            File outputDir = new File(hostOutputPath);
            if (!outputDir.exists()) {
                log.info("输出目录不存在，正在创建：{}", hostOutputPath);
                boolean created = outputDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("无法创建输出目录：" + hostOutputPath);
                }
                log.info("输出目录创建成功：{}", hostOutputPath);
            }

            // 4. 验证目录权限
            if (!userDir.canRead() || !userDir.canWrite()) {
                log.warn("用户目录权限不足，尝试设置权限：{}", hostUserBasePath);
                userDir.setReadable(true, false);
                userDir.setWritable(true, false);
                userDir.setExecutable(true, false);
            }

            if (!outputDir.canRead() || !outputDir.canWrite()) {
                log.warn("输出目录权限不足，尝试设置权限：{}", hostOutputPath);
                outputDir.setReadable(true, false);
                outputDir.setWritable(true, false);
                outputDir.setExecutable(true, false);
            }

            log.info("目录校验完成 - 用户：{}，输出目录：{}", hostUserBasePath, hostOutputPath);

        } catch (Exception e) {
            log.error("目录创建失败：{}", e.getMessage(), e);
            throw new RuntimeException("无法创建用户目录：" + e.getMessage(), e);
        }

        // 构建卷挂载
        List<Bind> binds = new ArrayList<>();

        // 1. 输出目录挂载（可写）- 用于保存截图和任务结果
        binds.add(new Bind(hostOutputPath, new Volume("/app/anno"), AccessMode.rw));
        log.debug("挂载输出目录：{} -> /app/anno (rw)", hostOutputPath);

        // 2. 用户浏览器状态目录挂载（根据 isUpdateProfile 参数决定是否可写）
        // 登录/养号时使用可写模式，搜索时使用只读模式
        if (isUpdateProfile) {
            // 【可写模式 / 养号制种】：直接挂载为 RW，并清理残留锁
            binds.add(new Bind(hostUserBasePath, new Volume("/app/chrome_profile"), AccessMode.rw));
            log.debug("挂载浏览器配置（可写）：{} -> /app/chrome_profile (rw)", hostUserBasePath);
        } else {
            // 【只读模式 / 并发搜索】：挂载为只读，容器内使用临时配置
            binds.add(new Bind(hostUserBasePath, new Volume("/app/base_profile/" + profileName), AccessMode.ro));
            log.debug("挂载浏览器配置（只读）：{} -> /app/base_profile (ro)", hostUserBasePath);
        }

        // 配置端口绑定（FastAPI端口 + VNC端口）
        Ports portBindings = new Ports();
        portBindings.bind(ExposedPort.tcp(apiPort), Ports.Binding.bindPort(apiPort));
        portBindings.bind(ExposedPort.tcp(vncPort), Ports.Binding.bindPort(vncPort));

        // 主机配置
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withAutoRemove(false)
                .withMemory(properties.getMemoryLimit())
                .withMemorySwap(properties.getMemorySwap())
                .withShmSize(properties.getShmSize())
                .withBinds(binds)
                .withPortBindings(portBindings);

        // 环境变量
        List<String> envVars = Arrays.asList(
                "DISPLAY=:99",
                "DBUS_SESSION_BUS_ADDRESS=/dev/null",
                "LIBGL_ALWAYS_SOFTWARE=1",
                "VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.x86_64.json",
                "ANGLE_DEFAULT_PLATFORM=swiftshader",
                "AGENT_PORT=" + apiPort,
                "VNC_PORT=" + vncPort,
                "CONTAINER_ID=" + "agent-" + System.currentTimeMillis(),
                "PROFILE_NAME=" + profileName,
                "USERNAME=" + username
        );

        // 创建容器，暴露两个端口
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(properties.getImageName())
                .withTty(true)
                .withStdinOpen(true)
                .withEnv(envVars)
                .withHostConfig(hostConfig)
                .withMacAddress(macAddress)
                .withHostName("agent-" + username.replaceAll("[^a-zA-Z0-9]", ""))
                .withExposedPorts(ExposedPort.tcp(apiPort), ExposedPort.tcp(vncPort));

        CreateContainerResponse response = containerCmd.exec();
        String containerId = response.getId();

        log.debug("创建容器{}，用户{}，API端口{}，VNC端口{}，MAC地址{}",
                containerId.substring(0, 12), username, apiPort, vncPort, macAddress);

        return containerId;
    }

    /**
     * 等待容器准备好接受连接。
     */
    private void waitForContainerReady(ContainerPod pod) {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                String url = STR."\{javaBaseUrl}:\{pod.getAssignedPort()}/health";
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String status = (String) response.getBody().get("status");
                    if ("healthy".equals(status)) {
                        return;
                    }
                }
            } catch (Exception e) {
                log.trace("容器{}尚未就绪（尝试{}/{}）：{}",
                        pod.getShortId(), attempt + 1, maxAttempts, e.getMessage());
            }

            attempt++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待容器就绪时被中断", e);
            }
        }

        throw new RuntimeException("容器" + pod.getShortId() + "在超时时间内未能就绪");
    }

    /**
     * 向容器分派任务。
     *
     * @param pod         要使用的容器Pod
     * @param taskId      唯一任务标识符
     * @param instruction 任务指令
     * @param apiKey      LLM的API密钥
     * @param baseUrl     LLM API的基础URL
     * @param model       要使用的模型名称
     * @param maxSteps    允许的最大步骤数
     * @param isUpdateProfile 是否为更新配置文件模式
     * @return 如果任务被接受则返回true
     */
    public boolean dispatchTask(ContainerPod pod, String taskId, String instruction,
                                String apiKey, String baseUrl, String model, int maxSteps, boolean isUpdateProfile) {
        // 检查 Pod 当前状态
        ContainerPod.Status currentStatus = pod.getStatus().get();
        log.info("尝试向Pod{}分派任务，当前状态：{}", pod.getShortId(), currentStatus);

        if (!pod.assignTask(taskId)) {
            log.warn("无法向Pod{}分派任务：Pod状态为{}，不可用", pod.getShortId(), currentStatus);

            // 如果 Pod 处于非 CREATING 状态，尝试强制释放后重试
            if (currentStatus != ContainerPod.Status.CREATING) {
                log.info("Pod{}处于{}状态，尝试强制释放后重试", pod.getShortId(), currentStatus);
                pod.forceRelease();
                // 重新尝试分配
                if (pod.assignTask(taskId)) {
                    log.info("Pod{}释放后成功分配任务", pod.getShortId());
                    // 继续执行任务分派逻辑
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        // 使用host.docker.internal构建回调URL，用于容器到主机通信
        String callbackUrl = properties.getCallbackBaseUrl() +
                "/api/v2/docker/callback/" + pod.getContainerId();

        // 构建任务请求
        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("task_id", taskId);
        taskRequest.put("instruction", instruction);
        taskRequest.put("api_key", apiKey);
        taskRequest.put("base_url", baseUrl);
        taskRequest.put("model", model);
        taskRequest.put("max_steps", maxSteps);
        taskRequest.put("callback_url", callbackUrl);
        taskRequest.put("profile_name", pod.getProfileName());
        taskRequest.put("is_update_profile", isUpdateProfile);

        // 在Pod元数据中存储任务ID
        pod.putMetadata("currentTaskId", taskId);
        pod.putMetadata("taskStartTime", System.currentTimeMillis());

        // 向容器发送任务
        try {
//            String url = String.format("http://localhost:%d/task", pod.getAssignedPort());
            String url = STR."\{javaBaseUrl}:\{pod.getAssignedPort()}/task";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(taskRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("任务{}已分派到容器{}", taskId, pod.getShortId());
                return true;
            } else {
                log.error("向容器{}分派任务失败：状态码{}",
                        pod.getShortId(), response.getStatusCode());
                pod.release();
                return false;
            }

        } catch (Exception e) {
            log.error("向容器{}分派任务失败：{}", pod.getShortId(), e.getMessage());
            pod.release();
            return false;
        }
    }

    /**
     * 处理来自容器的任务完成回调。
     * 当容器发送回调时，由DockerController调用此方法。
     *
     * @param containerId 容器ID
     * @param status      任务状态
     */
    public void handleTaskCallback(String containerId, String status) {
        ContainerPod pod = activePods.get(containerId);
        if (pod == null) {
            log.warn("收到未知容器的回调：{}", containerId);
            return;
        }

        log.info("收到容器{}的任务回调：状态={}", pod.getShortId(), status);

        switch (status) {
            case "completed":
            case "terminated":
                pod.completeTask();
                log.info("容器{}已释放，可用于新任务", pod.getShortId());
                break;

            case "timeout":
            case "failure":
                pod.failTask();
                log.warn("容器{}因任务失败标记为错误状态", pod.getShortId());
                // 可选：释放Pod以供重用
                pod.release();
                break;

            case "processing":
                // 任务仍在运行，无需操作
                break;

            default:
                log.warn("收到未知的任务状态：{}", status);
        }
    }

    /**
     * 显式释放容器Pod。
     *
     * @param containerId 要释放的容器ID
     */
    public void releasePod(String containerId) {
        ContainerPod pod = activePods.get(containerId);
        if (pod != null) {
            pod.release();
            log.info("容器{}已显式释放", pod.getShortId());
        }
    }

    /**
     * 从池中停止并移除容器。
     * 同时清理用户-容器绑定关系。
     *
     * @param containerId 容器ID
     */
    public void stopAndRemoveContainer(String containerId) {
        log.info("触发容器停止");
        ContainerPod pod = activePods.remove(containerId);
        if (pod != null) {
            releasePortPair(pod.getAssignedPort());
            // 清理用户-容器绑定
            if (pod.getUsername() != null) {
                userToPodMapping.remove(pod.getUsername());
            }
        }
// ====== //只转移PDF文件======
        backupContainerDownloads(containerId);
        try {
            // 停止容器
            dockerClient.stopContainerCmd(containerId).withTimeout(5).exec();
        } catch (Exception e) {
            log.debug("容器{}可能已停止：{}", containerId, e.getMessage());
        }

        try {
            // 移除容器
            dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
            log.info("容器{}已移除", containerId.substring(0, 12));
        } catch (Exception e) {
            log.warn("移除容器{}失败：{}", containerId, e.getMessage());
        }
    }

    /**
     * 获取所有活动Pod。
     */
    public Collection<ContainerPod> getActivePods() {
        return Collections.unmodifiableCollection(activePods.values());
    }

    /**
     * 通过容器ID获取特定Pod。
     */
    public Optional<ContainerPod> getPod(String containerId) {
        return Optional.ofNullable(activePods.get(containerId));
    }

    /**
     * 通过用户名获取绑定的Pod。
     *
     * @param username 用户名
     * @return 绑定到用户的Pod（如果存在）
     */
    public Optional<ContainerPod> getPodByUsername(String username) {
        String containerId = userToPodMapping.get(username);
        if (containerId != null) {
            ContainerPod pod = activePods.get(containerId);
            if (pod != null && isContainerRunning(containerId)) {
                return Optional.of(pod);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取池统计信息。
     */
    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPods", activePods.size());
        stats.put("maxPoolSize", properties.getMaxPoolSize());
        stats.put("availablePods", activePods.values().stream().filter(ContainerPod::isAvailable).count());
        stats.put("busyPods", activePods.values().stream().filter(ContainerPod::isBusy).count());
        stats.put("allocatedPorts", allocatedPorts.size());
        return stats;
    }

    /**
     * 定时清理空闲容器。
     * 每5分钟运行一次。
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupIdleContainers() {
        long idleTimeout = properties.getIdleTimeoutSeconds();
        List<String> toRemove = new ArrayList<>();

        for (ContainerPod pod : activePods.values()) {
            if (pod.isAvailable() && pod.getIdleSeconds() > idleTimeout) {
                log.info("容器{}超过空闲超时（{}秒），标记为清理",
                        pod.getShortId(), pod.getIdleSeconds());
                toRemove.add(pod.getContainerId());
            }
        }

        for (String containerId : toRemove) {
            try {
                stopAndRemoveContainer(containerId);
            } catch (Exception e) {
                log.error("清理空闲容器{}失败：{}", containerId, e.getMessage());
            }
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 检查容器是否仍在运行中。
     *
     * @param containerId 容器ID
     * @return 如果容器正在运行则返回true
     */
    private boolean isContainerRunning(String containerId) {
        try {
            com.github.dockerjava.api.command.InspectContainerResponse inspectResponse =
                    dockerClient.inspectContainerCmd(containerId).exec();
            return inspectResponse.getState().getRunning();
        } catch (Exception e) {
            log.warn("检查容器{}状态失败：{}", containerId, e.getMessage());
            return false;
        }
    }

    /**
     * 移除用户-容器绑定关系。
     *
     * @param username 用户名
     */
    private void removeUserPodBinding(String username) {
        String containerId = userToPodMapping.remove(username);
        if (containerId != null) {
            log.debug("已移除用户 {} 到容器 {} 的绑定", username, containerId.substring(0, 12));
        }
    }

    /**
     * 指定起始奇数端口分配一对连续端口。
     * * @param apiPort 指定的 FastAPI 奇数端口
     * @return 包含两个端口的数组 [apiPort, vncPort]
     * @throws IllegalArgumentException 如果端口不是奇数或超出范围
     * @throws IllegalStateException 如果指定的端口对已被占用
     */
    private int[] allocatePortPair(int apiPort) {
        int start = properties.getPortRangeStart();
        int end = properties.getPortRangeEnd();
        int vncPort = apiPort + 1;

        // 1. 校验输入合法性
        if (apiPort % 2 == 0) {
            throw new IllegalArgumentException("指定的 API 端口必须是奇数: " + apiPort);
        }
        if (apiPort < start || vncPort > end) {
            throw new IllegalArgumentException("端口超出可用范围 [" + start + ", " + end + "]: " + apiPort);
        }

        // 2. 尝试锁定端口对
        if (allocatedPorts.add(apiPort)) {
            if (allocatedPorts.add(vncPort)) {
                // 两个端口都成功分配
                portPairs.put(apiPort, vncPort);
                log.debug("成功分配指定端口对：API={}, VNC={}", apiPort, vncPort);
                return new int[]{apiPort, vncPort};
            } else {
                // VNC端口已被占用，回滚 API 端口
                allocatedPorts.remove(apiPort);
                throw new IllegalStateException("VNC 端口已被占用: " + vncPort);
            }
        } else {
            throw new IllegalStateException("API 端口已被占用: " + apiPort);
        }
    }

    /**
     * 自动分配一对连续端口（FastAPI奇数端口 + VNC偶数端口）。
     */
    private int[] allocatePortPair() {
        int start = properties.getPortRangeStart();
        int end = properties.getPortRangeEnd();

        if (start % 2 == 0) {
            start++;
        }

        for (int apiPort = start; apiPort < end; apiPort += 2) {
            try {
                // 调用重载方法进行实际分配逻辑
                return allocatePortPair(apiPort);
            } catch (IllegalStateException e) {
                // 如果当前端口对不可用，继续寻找下一个
                continue;
            }
        }

        throw new IllegalStateException("端口范围 " + start + "-" + end + " 中没有可用的连续端口对");
    }


    /**
     * 释放端口对。
     *
     * @param apiPort FastAPI 端口
     */
    private void releasePortPair(int apiPort) {
        Integer vncPort = portPairs.remove(apiPort);
        if (vncPort != null) {
            allocatedPorts.remove(vncPort);
        }
        allocatedPorts.remove(apiPort);
        log.debug("释放端口对：API={}, VNC={}", apiPort, vncPort);
    }

    private void releasePort(int port) {
        allocatedPorts.remove(port);
    }

    private String generateMacAddress() {
        int counter = macCounter.incrementAndGet();
        return String.format("02:42:ac:%02x:%02x:%02x",
                (counter >> 16) & 0xff,
                (counter >> 8) & 0xff,
                counter & 0xff);
    }

    private void cleanupOrphanedContainers() {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withStatusFilter(Arrays.asList("running", "exited"))
                    .exec();

            for (Container container : containers) {
                String imageName = container.getImage();
                if (imageName != null && imageName.contains("gui-agent")) {
                    // 通过查找标签或名称模式检查是否为V2容器
                    String[] names = container.getNames();
                    if (names != null) {
                        for (String name : names) {
                            if (name.contains("agent-")) {
                                log.info("发现孤立容器{}，正在移除...", container.getId().substring(0, 12));
                                try {
                                    dockerClient.removeContainerCmd(container.getId())
                                            .withForce(true)
                                            .withRemoveVolumes(true)
                                            .exec();
                                } catch (Exception e) {
                                    log.warn("移除孤立容器失败：{}", e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("清理孤立容器失败：{}", e.getMessage());
        }
    }

    /**
     * 新增：根据 taskId 获取正在执行该任务的容器 Pod
     */
    public Optional<ContainerPod> getPodByTaskId(String taskId) {
        return activePods.values().stream()
                .filter(pod -> taskId.equals(pod.getCurrentTaskId()))
                .findFirst();
    }

    /**
     * 新增：向 Python 容器发送单步指令（同步阻塞等待返回）
     */
    public Map<String, Object> executeSingleStep(ContainerPod pod, String taskId, String instruction,
                                                 String apiKey, String baseUrl, String model, boolean isUpdateProfile) {
        // 1. 刷新活跃时间，并标记容器为 BUSY 状态
        if (!pod.assignTask(taskId)) {
            pod.forceRelease();
            pod.assignTask(taskId);
        }

        // 2. 组装发送给 Python /step 接口的请求体
        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("task_id", taskId);
        taskRequest.put("instruction", instruction);
        taskRequest.put("api_key", apiKey);
        taskRequest.put("base_url", baseUrl);
        taskRequest.put("model", model);
        taskRequest.put("profile_name", pod.getProfileName());
        taskRequest.put("is_update_profile", isUpdateProfile);

        try {
            // 调用 Python 容器的 9001 端口上的 /step 接口
            String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/step";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(taskRequest, headers);

            // 3. 同步等待 Python 截图、调 LLM、执行动作的返回结果
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            // 4. 执行完成后，让容器回归 READY 状态，等待你下一次点击发送指令！
            pod.setStatus(ContainerPod.Status.READY);
            return response.getBody();

        } catch (Exception e) {
            // 异常时也要释放状态
            pod.setStatus(ContainerPod.Status.READY);
            throw new RuntimeException("向容器发送单步指令失败：" + e.getMessage(), e);
        }
    }

    /**
     * 强行中止任务
     */
    public void abortTask(String taskId) {
        TaskContext ctx = taskContexts.get(taskId);
        if (ctx == null) return;

        // 1. 标记为已中止（防止后续的回调再次触发发球）
        ctx.setAborted(true);

        ContainerPod pod = ctx.getPod();
        log.info("正在强行中止任务: {}, 容器: {}", taskId, pod.getShortId());

        // 2. 异步通知 Python 容器内部急刹车
        CompletableFuture.runAsync(() -> {
            try {
                String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/abort";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                Map<String, String> body = Map.of("task_id", taskId);
                restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            } catch (Exception e) {
                log.warn("向容器发送中止信号失败 (可能容器已关闭): {}", e.getMessage());
            }
        });

        // 3. 释放容器回池子里，供其他任务使用
        handleTaskCallback(pod.getContainerId(), "terminated");
    }

    // ==========================================
    // 任务上下文存储区 (Java端的总控记忆)
    // ==========================================
    @Data
    public static class TaskContext {
        private String taskId;
        private ContainerPod pod;
        private String instruction;
        private Queue<String> instructionQueue = new ConcurrentLinkedQueue<>();//批量调用时用来存储剩余指令的队列（使用线程安全的并发队列）
        private String apiKey;
        private String baseUrl;
        private String model;
        private boolean isUpdateProfile;
        private int maxSteps;
        private int currentStep;
        private boolean aborted = false;
    }

    private final Map<String, TaskContext> taskContexts = new ConcurrentHashMap<>();

    /**
     * 初始化一个任务的上下文
     */
    public void initTaskContext(String taskId, ContainerPod pod, String instruction,
                                String apiKey, String baseUrl, String model,
                                boolean isUpdateProfile, int maxSteps) {
        TaskContext ctx = new TaskContext();
        ctx.setTaskId(taskId);
        ctx.setPod(pod);
        ctx.setInstruction(instruction);
        ctx.setApiKey(apiKey);
        ctx.setBaseUrl(baseUrl);
        ctx.setModel(model);
        ctx.setUpdateProfile(isUpdateProfile);
        ctx.setMaxSteps(maxSteps);
        ctx.setCurrentStep(0);
        taskContexts.put(taskId, ctx);
    }

    public TaskContext getTaskContext(String taskId) {
        return taskContexts.get(taskId);
    }





    /**
     * 核心动作：向 Python 容器发射下一个“乒乓球”
     */
    public void triggerNextStepAsync(String taskId) {
        TaskContext ctx = taskContexts.get(taskId);
        if (ctx == null) return;

        ContainerPod pod = ctx.getPod();
        pod.touch();
        pod.setStatus(ContainerPod.Status.BUSY);

        // 组装发送给 Python 的单步请求
        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("task_id", taskId);
        taskRequest.put("instruction", ctx.getInstruction());
        taskRequest.put("api_key", ctx.getApiKey());
        taskRequest.put("base_url", ctx.getBaseUrl());
        taskRequest.put("model", ctx.getModel());
        taskRequest.put("profile_name", pod.getProfileName());
        taskRequest.put("is_update_profile", ctx.isUpdateProfile());

        // 告诉 Python 它的回调地址在哪里
        String callbackUrl = properties.getCallbackBaseUrl() + "/api/v2/docker/callback/" + pod.getContainerId();
        taskRequest.put("callback_url", callbackUrl);

        // 异步发送 HTTP 请求给 Python 的 /step 接口 (发完就不管了，等 Python 回调)
        CompletableFuture.runAsync(() -> {
            try {
                String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/step";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(taskRequest, headers);

                restTemplate.postForEntity(url, entity, Map.class);
                log.info("已向容器 {} (Task: {}) 发送单步执行指令", pod.getShortId(), taskId);
            } catch (Exception e) {
                log.error("向 Python 发送指令失败: {}", e.getMessage());
            }
        });
    }
    /**
     * 在容器销毁前，直接将容器内的下载文件流式写入到宿主机指定的文件夹下。
     * 特性：不生成压缩包文件；保留原有文件；同名文件自动覆盖；仅转移 PDF 文件。
     *
     * @param containerId 容器ID
     */
    private void backupContainerDownloads(String containerId) {
        String containerPath = "/root/Downloads";
        // ======= 已成功修改为你的目标存放路径 =======
        String hostPath = "/home/zjhtest/downFile";

        File hostDir = new File(hostPath);
        if (!hostDir.exists()) {
            boolean mkdirsSuccess = hostDir.mkdirs();
            if (!mkdirsSuccess && !hostDir.exists()) {
                log.error("宿主机根存储目录创建失败，请检查用户权限: {}", hostPath);
                return;
            }
        }

        log.info("准备直接复制容器 {} 的 PDF 下载文件到服务器...", containerId.substring(0, 12));

        try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(containerId, containerPath).exec();
             TarArchiveInputStream tar = new TarArchiveInputStream(tarStream)) {

            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {

                // 1. 严格过滤：如果是目录，直接跳过，我们只需要处理具体的文件
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                // 2. 检查是否是 PDF 文件（忽略大小写）
                if (entryName != null && entryName.toLowerCase().endsWith(".pdf")) {

                    // 3. 提取纯文件名 (丢弃 Tar 内部自带的 "Downloads/" 等前缀路径)
                    String pureFileName = new File(entryName).getName();

                    // 安全防空：如果文件名为空或纯空格，说明不是合法的独立文件，直接跳过
                    if (pureFileName == null || pureFileName.isBlank()) {
                        continue;
                    }

                    // 4. 构建宿主机最终绝对路径（此时会精准拼成 /home/zjhtest/downFile/xxxx.pdf）
                    File destFile = new File(hostDir, pureFileName);

                    log.info("查找到有效PDF，正在写入服务器: {}", destFile.getAbsolutePath());

                    // 5. 写入文件（FileOutputStream 默认行为：文件不存在则创建，存在同名则直接覆盖）
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        tar.transferTo(fos);
                    }
                    log.info("成功提取并保存 PDF 文件: {}", destFile.getName());
                }
            }
            log.info("容器 {} 的 PDF 文件已成功合并到服务器", containerId.substring(0, 12));

        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.debug("容器 {} 中没有 /root/Downloads 目录或目录为空，无需备份跳过", containerId.substring(0, 12));
        } catch (Exception e) {
            log.error("复制容器 {} 下载文件时发生异常: {}", containerId.substring(0, 12), e.getMessage(), e);
        }
    }
}
