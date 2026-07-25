package org.example.agentScope.util.tool.DockerTool.Weixin;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.masfanplus.AgentScope.util.DockerTool.Weixin.config.WeChatDockerConfiguration;
import org.example.masfanplus.AgentScope.util.DockerTool.V2.model.ContainerPod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 微信 Docker 容器池管理器。
 * 
 * 与现有的 Chrome 实现完全隔离，专门用于运行原生 Linux 微信客户端。
 * 
 * 关键特性：
 * 1. 确定性 MAC 地址生成：基于用户名 MD5 哈希，防止微信风控触发
 * 2. 微信数据持久化：挂载 /root/.xwechat 和 /root/xwechat_files
 * 3. 用户-容器绑定：每个用户绑定唯一容器，保持登录状态
 */
@Component("weChatDockerPoolManager")
public class WeChatDockerPoolManager {

    private static final Logger log = LoggerFactory.getLogger(WeChatDockerPoolManager.class);

    private final DockerClient dockerClient;
    private final WeChatDockerConfiguration config;
    private final RestTemplate restTemplate;

    /** 按 Pod ID 索引的活动容器 Pod */
    private final ConcurrentHashMap<String, ContainerPod> activePods = new ConcurrentHashMap<>();

    /** 用户名到容器 ID 的映射（用于实现用户-容器绑定） */
    private final ConcurrentHashMap<String, String> userToPodMapping = new ConcurrentHashMap<>();

    /** 端口分配跟踪器 */
    private final Set<Integer> allocatedPorts = ConcurrentHashMap.newKeySet();

    /** 端口对记录（FastAPI端口 -> VNC端口） */
    private final ConcurrentHashMap<Integer, Integer> portPairs = new ConcurrentHashMap<>();

    /** 异步操作线程池 */
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /** MAC 地址缓存（用户名 -> MAC地址） */
    private final ConcurrentHashMap<String, String> macAddressCache = new ConcurrentHashMap<>();

    public WeChatDockerPoolManager(DockerClient dockerClient, WeChatDockerConfiguration config) {
        this.dockerClient = dockerClient;
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("初始化微信 Docker 池管理器");
        log.info("========================================");
        log.info("最大池大小：{}", config.getMaxPoolSize());
        log.info("端口范围：{} - {}", config.getPortRangeStart(), config.getPortRangeEnd());
        log.info("镜像名称：{}", config.getImageName());
        log.info("基础路径：{}", config.getProfileBasePath());
        log.info("========================================");

        // 清理上次运行遗留的孤立容器
        cleanupOrphanedContainers();
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭微信 Docker 池管理器，清理 {} 个 Pod", activePods.size());

        for (ContainerPod pod : activePods.values()) {
            try {
                stopAndRemoveContainer(pod.getContainerId());
            } catch (Exception e) {
                log.warn("关闭时停止容器 {} 失败：{}", pod.getShortId(), e.getMessage());
            }
        }

        userToPodMapping.clear();
        macAddressCache.clear();

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

    // ==================== 核心方法 ====================

    /**
     * 获取或创建指定用户名的容器 Pod。
     * 
     * 实现用户-容器绑定机制：
     * 1. 如果用户已有运行中的容器，直接复用
     * 2. 如果用户容器已停止，清理后创建新容器
     * 3. 如果用户没有容器，创建新容器
     * 
     * 注意：微信模块不需要区分读写模式，所有数据目录始终可读写
     * 因为微信登录状态需要持久化保存。
     *
     * @param username 用户名（用于绑定容器和生成确定性 MAC 地址）
     * @param profileName 容器的配置文件名称
     * @return 可用的 ContainerPod
     */
    public ContainerPod getOrCreatePod(String username, String profileName) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        // 1. 检查用户是否已有绑定容器
        String existingPodId = userToPodMapping.get(username);
        if (existingPodId != null) {
            ContainerPod existingPod = activePods.get(existingPodId);
            if (existingPod != null) {
                if (isContainerRunning(existingPod.getContainerId())) {
                    ContainerPod.Status podStatus = existingPod.getStatus().get();
                    log.info("用户 {} 复用已存在的运行中容器：{}，当前状态：{}",
                            username, existingPod.getShortId(), podStatus);

                    if (podStatus != ContainerPod.Status.READY) {
                        log.info("容器 {} 状态为 {}，尝试强制释放", existingPod.getShortId(), podStatus);
                        existingPod.forceRelease();
                    }

                    existingPod.touch();
                    return existingPod;
                } else {
                    log.info("用户 {} 的容器 {} 已停止，正在清理...", username, existingPod.getShortId());
                    removeUserPodBinding(username);
                    stopAndRemoveContainer(existingPodId);
                }
            } else {
                userToPodMapping.remove(username);
            }
        }

        // 2. 检查是否可以创建新 Pod
        if (activePods.size() >= config.getMaxPoolSize()) {
            throw new IllegalStateException("微信代理池已满且没有可用容器。" +
                    "当前大小：" + activePods.size() + "，最大值：" + config.getMaxPoolSize());
        }

        // 3. 创建新 Pod
        log.info("为用户 {} 创建新微信容器 Pod，配置文件：{}", username, profileName);
        return createNewPod(username, profileName);
    }

    /**
     * 创建新的容器 Pod。
     */
    private ContainerPod createNewPod(String username, String profileName) {
        // 分配端口对
        int[] portPair = allocatePortPair();
        int apiPort = portPair[0];
        int vncPort = portPair[1];

        // 生成确定性 MAC 地址（关键：基于用户名）
        String macAddress = generateDeterministicMacAddress(username);

        String containerId = createContainer(username, profileName, apiPort, vncPort, macAddress);

        ContainerPod pod = new ContainerPod(containerId, apiPort, vncPort, profileName, username, macAddress);
        activePods.put(containerId, pod);
        userToPodMapping.put(username, containerId);

        try {
            dockerClient.startContainerCmd(containerId).exec();
            log.info("微信容器 {} 已在端口 {}(API) 和 {}(VNC) 上启动，绑定用户：{}，MAC：{}",
                    pod.getShortId(), apiPort, vncPort, username, macAddress);

            waitForContainerReady(pod);

            pod.setStatus(ContainerPod.Status.READY);
            log.info("微信容器 {} 已就绪，绑定用户：{}", pod.getShortId(), username);

        } catch (Exception e) {
            log.error("启动微信容器 {} 失败：{}", pod.getShortId(), e.getMessage());
            pod.setStatus(ContainerPod.Status.ERROR);
            releasePortPair(apiPort);
            activePods.remove(containerId);
            userToPodMapping.remove(username);
            throw new RuntimeException("创建微信容器失败：" + e.getMessage(), e);
        }

        return pod;
    }

    /**
     * 创建 Docker 容器。
     * 
     * 关键挂载：
     * - /root/.xwechat：微信登录状态数据
     * - /root/xwechat_files：微信聊天文件
     * 
     * 注意：微信模块所有目录都以读写模式挂载，因为微信登录状态需要持久化
     */
    private String createContainer(String username, String profileName, int apiPort, int vncPort,
                                    String macAddress) {
        // 确保用户目录存在
        config.validateAndCreateUserDirectories(username);

        // 获取路径
        String hostUserBasePath = config.getUserBasePath(username);
        String hostOutputPath = config.getUserOutputPath(username);
        String hostWeChatDataPath = config.getUserWeChatDataPath(username);
        String hostWeChatFilesPath = config.getUserWeChatFilesPath(username);

        // 设置目录权限
        setDirectoryPermissions(hostWeChatDataPath);
        setDirectoryPermissions(hostWeChatFilesPath);
        setDirectoryPermissions(hostOutputPath);

        // 构建卷挂载
        List<Bind> binds = new ArrayList<>();

        // == 核心修改点：清洗路径 ==
        String safeOutputPath = sanitizeHostPathForDocker(hostOutputPath);
        String safeWeChatDataPath = sanitizeHostPathForDocker(hostWeChatDataPath);
        String safeWeChatFilesPath = sanitizeHostPathForDocker(hostWeChatFilesPath);

        // 1. 输出目录挂载（可写）
        binds.add(new Bind(safeOutputPath, new Volume("/app/anno"), AccessMode.rw));
        log.debug("挂载输出目录：{} (原路径: {}) -> /app/anno (rw)", safeOutputPath, hostOutputPath);

        // 2. 微信登录状态目录挂载（关键：保持登录状态）
        binds.add(new Bind(safeWeChatDataPath, new Volume("/root/.xwechat"), AccessMode.rw));
        log.debug("挂载微信数据目录：{} (原路径: {}) -> /root/.xwechat (rw)", safeWeChatDataPath, hostWeChatDataPath);

        // 3. 微信文件目录挂载（聊天文件）
        binds.add(new Bind(safeWeChatFilesPath, new Volume("/root/xwechat_files"), AccessMode.rw));
        log.debug("挂载微信文件目录：{} (原路径: {}) -> /root/xwechat_files (rw)", safeWeChatFilesPath, hostWeChatFilesPath);

        // 配置端口绑定
        Ports portBindings = new Ports();
        portBindings.bind(ExposedPort.tcp(apiPort), Ports.Binding.bindPort(apiPort));
        portBindings.bind(ExposedPort.tcp(vncPort), Ports.Binding.bindPort(vncPort));

        // 主机配置
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withAutoRemove(false)
                .withMemory(config.getMemoryLimit())
                .withMemorySwap(config.getMemorySwap())
                .withShmSize(config.getShmSize())
                .withBinds(binds)
                .withPortBindings(portBindings);

        // 环境变量
        List<String> envVars = Arrays.asList(
                "DISPLAY=:99",
                "DBUS_SESSION_BUS_ADDRESS=/dev/null",
                "LIBGL_ALWAYS_SOFTWARE=1",
                "AGENT_Port=" + apiPort,
                "VNC_Port=" + vncPort,
                "CONTAINER_ID=wechat-" + System.currentTimeMillis(),
                "PROFILE_NAME=" + profileName,
                "USERNAME=" + username,
                "LANG=zh_CN.UTF-8",
                "LC_ALL=zh_CN.UTF-8"
        );

        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(config.getImageName())
                .withTty(true)
                .withStdinOpen(true)
                .withEnv(envVars)
                .withHostConfig(hostConfig)
                .withMacAddress(macAddress)  // 使用确定性 MAC 地址
                .withHostName("wechat-" + sanitizeHostName(username))
                .withExposedPorts(ExposedPort.tcp(apiPort), ExposedPort.tcp(vncPort));

        CreateContainerResponse response = containerCmd.exec();
        String containerId = response.getId();

        log.info("创建微信容器 {}，用户 {}，API端口 {}，VNC端口 {}，MAC地址 {}",
                containerId.substring(0, 12), username, apiPort, vncPort, macAddress);

        return containerId;
    }

    // ==================== 确定性 MAC 地址生成 ====================

    /**
     * 基于用户名生成确定性 MAC 地址。
     * 
     * 使用 MD5 哈希确保：
     * 1. 同一用户名总是生成相同的 MAC 地址
     * 2. 不同用户名生成不同的 MAC 地址
     * 3. MAC 地址符合标准格式（02:XX:XX:XX:XX:XX）
     * 
     * 这对于微信登录状态至关重要：
     * - 微信风控会检测 MAC 地址漂移
     * - 确定性 MAC 地址防止触发风控
     * 
     * @param username 用户名
     * @return 确定性的 MAC 地址字符串
     */
    public String generateDeterministicMacAddress(String username) {
        // 检查缓存
        return macAddressCache.computeIfAbsent(username, this::computeMacFromUsername);
    }

    /**
     * 从用户名计算 MAC 地址。
     */
    private String computeMacFromUsername(String username) {
        try {
            // 使用 MD5 哈希用户名
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(username.getBytes(StandardCharsets.UTF_8));

            // 使用哈希的前5个字节生成 MAC 地址
            // 格式：02:XX:XX:XX:XX:XX（02 表示本地管理的地址）
            return String.format("02:%02x:%02x:%02x:%02x:%02x",
                    hash[0] & 0xff,
                    hash[1] & 0xff,
                    hash[2] & 0xff,
                    hash[3] & 0xff,
                    hash[4] & 0xff);

        } catch (NoSuchAlgorithmException e) {
            // MD5 应该总是可用，但作为后备
            log.warn("MD5 不可用，使用备用 MAC 地址生成方法");
            int hashCode = username.hashCode();
            return String.format("02:%02x:%02x:%02x:%02x:%02x",
                    (hashCode >> 24) & 0xff,
                    (hashCode >> 16) & 0xff,
                    (hashCode >> 8) & 0xff,
                    hashCode & 0xff,
                    (hashCode >> 8) & 0xff);
        }
    }

    // ==================== 任务分派 ====================

    /**
     * 向容器分派任务。
     */
    public boolean dispatchTask(ContainerPod pod, String taskId, String instruction,
                                String apiKey, String baseUrl, String model, int maxSteps) {
        ContainerPod.Status currentStatus = pod.getStatus().get();
        log.info("尝试向微信 Pod {} 分派任务，当前状态：{}", pod.getShortId(), currentStatus);

        if (!pod.assignTask(taskId)) {
            log.warn("无法向 Pod {} 分派任务：Pod 状态为 {}", pod.getShortId(), currentStatus);

            if (currentStatus != ContainerPod.Status.CREATING) {
                pod.forceRelease();
                if (!pod.assignTask(taskId)) {
                    return false;
                }
            } else {
                return false;
            }
        }

        String callbackUrl = config.getCallbackBaseUrl() +
                "/api/wechat/docker/callback/" + pod.getContainerId();

        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("task_id", taskId);
        taskRequest.put("instruction", instruction);
        taskRequest.put("api_key", apiKey);
        taskRequest.put("base_url", baseUrl);
        taskRequest.put("model", model);
        taskRequest.put("max_steps", maxSteps);
        taskRequest.put("callback_url", callbackUrl);
        taskRequest.put("profile_name", pod.getProfileName());
        taskRequest.put("app_type", "wechat");  // 标识为微信任务

        pod.putMetadata("currentTaskId", taskId);
        pod.putMetadata("taskStartTime", System.currentTimeMillis());

        try {
            String url = String.format("%s:%d/task", config.getJavaBaseUrl(), pod.getAssignedPort());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(taskRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("微信任务 {} 已分派到容器 {}", taskId, pod.getShortId());
                return true;
            } else {
                log.error("向微信容器 {} 分派任务失败：状态码 {}", pod.getShortId(), response.getStatusCode());
                pod.release();
                return false;
            }

        } catch (Exception e) {
            log.error("向微信容器 {} 分派任务失败：{}", pod.getShortId(), e.getMessage());
            pod.release();
            return false;
        }
    }

    /**
     * 处理来自容器的任务完成回调。
     */
    public void handleTaskCallback(String containerId, String status) {
        ContainerPod pod = activePods.get(containerId);
        if (pod == null) {
            log.warn("收到未知微信容器的回调：{}", containerId);
            return;
        }

        log.info("收到微信容器 {} 的任务回调：状态={}", pod.getShortId(), status);

        switch (status) {
            case "completed":
            case "terminated":
                pod.completeTask();
                log.info("微信容器 {} 已释放，可用于新任务", pod.getShortId());
                break;

            case "timeout":
            case "failure":
                pod.failTask();
                log.warn("微信容器 {} 因任务失败标记为错误状态", pod.getShortId());
                pod.release();
                break;

            case "processing":
                break;

            default:
                log.warn("收到未知的微信任务状态：{}", status);
        }
    }

    // ==================== 容器管理 ====================

    /**
     * 停止并移除容器。
     */
    public void stopAndRemoveContainer(String containerId) {
        ContainerPod pod = activePods.remove(containerId);
        if (pod != null) {
            releasePortPair(pod.getAssignedPort());
            if (pod.getUsername() != null) {
                userToPodMapping.remove(pod.getUsername());
            }
        }

        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(5).exec();
        } catch (Exception e) {
            log.debug("微信容器 {} 可能已停止：{}", containerId, e.getMessage());
        }

        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
            log.info("微信容器 {} 已移除", containerId.substring(0, 12));
        } catch (Exception e) {
            log.warn("移除微信容器 {} 失败：{}", containerId, e.getMessage());
        }
    }

    /**
     * 定时清理空闲容器。
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupIdleContainers() {
        long idleTimeout = config.getIdleTimeoutSeconds();
        List<String> toRemove = new ArrayList<>();

        for (ContainerPod pod : activePods.values()) {
            if (pod.isAvailable() && pod.getIdleSeconds() > idleTimeout) {
                log.info("微信容器 {} 超过空闲超时（{}秒），标记为清理",
                        pod.getShortId(), pod.getIdleSeconds());
                toRemove.add(pod.getContainerId());
            }
        }

        for (String containerId : toRemove) {
            try {
                stopAndRemoveContainer(containerId);
            } catch (Exception e) {
                log.error("清理空闲微信容器 {} 失败：{}", containerId, e.getMessage());
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 等待容器准备好接受连接。
     */
    private void waitForContainerReady(ContainerPod pod) {
        int maxAttempts = 45;  // 微信容器可能需要更长时间启动
        int attempt = 0;
        String healthUrl = String.format("%s:%d/health", config.getJavaBaseUrl(), pod.getAssignedPort());
        
        log.info("等待微信容器 {} 就绪，健康检查 URL: {}", pod.getShortId(), healthUrl);

        while (attempt < maxAttempts) {
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String status = (String) response.getBody().get("status");
                    if ("healthy".equals(status)) {
                        log.info("微信容器 {} 健康检查通过，状态: {}", pod.getShortId(), status);
                        return;
                    } else {
                        log.debug("微信容器 {} 健康检查返回非 healthy 状态: {}", pod.getShortId(), status);
                    }
                }
            } catch (Exception e) {
                // 首次尝试、每 10 次尝试或最后一次尝试时输出详细日志
                if (attempt == 0 || (attempt + 1) % 10 == 0 || attempt == maxAttempts - 1) {
                    log.debug("微信容器 {} 尚未就绪（尝试 {}/{}）：{} - {}",
                            pod.getShortId(), attempt + 1, maxAttempts, healthUrl, e.getMessage());
                }
            }

            attempt++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待微信容器就绪时被中断", e);
            }
        }

        throw new RuntimeException("微信容器 " + pod.getShortId() + " 在超时时间内未能就绪");
    }

    /**
     * 检查容器是否仍在运行中。
     */
    private boolean isContainerRunning(String containerId) {
        try {
            com.github.dockerjava.api.command.InspectContainerResponse inspectResponse =
                    dockerClient.inspectContainerCmd(containerId).exec();
            return inspectResponse.getState().getRunning();
        } catch (Exception e) {
            log.warn("检查微信容器 {} 状态失败：{}", containerId, e.getMessage());
            return false;
        }
    }

    /**
     * 移除用户-容器绑定关系。
     */
    private void removeUserPodBinding(String username) {
        String containerId = userToPodMapping.remove(username);
        if (containerId != null) {
            log.debug("已移除用户 {} 到微信容器 {} 的绑定", username, containerId.substring(0, 12));
        }
    }

    /**
     * 分配端口对。
     */
    private int[] allocatePortPair() {
        int start = config.getPortRangeStart();
        int end = config.getPortRangeEnd();

        if (start % 2 == 0) {
            start++;
        }

        for (int apiPort = start; apiPort < end; apiPort += 2) {
            int vncPort = apiPort + 1;
            if (allocatedPorts.add(apiPort)) {
                if (allocatedPorts.add(vncPort)) {
                    portPairs.put(apiPort, vncPort);
                    log.debug("分配微信端口对：API={}, VNC={}", apiPort, vncPort);
                    return new int[]{apiPort, vncPort};
                } else {
                    allocatedPorts.remove(apiPort);
                }
            }
        }

        throw new IllegalStateException("微信端口范围 " + config.getPortRangeStart() + "-" +
                config.getPortRangeEnd() + " 中没有可用的连续端口对");
    }

    /**
     * 释放端口对。
     */
    private void releasePortPair(int apiPort) {
        Integer vncPort = portPairs.remove(apiPort);
        if (vncPort != null) {
            allocatedPorts.remove(vncPort);
        }
        allocatedPorts.remove(apiPort);
        log.debug("释放微信端口对：API={}, VNC={}", apiPort, vncPort);
    }

    /**
     * 设置目录权限。
     */
    private void setDirectoryPermissions(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            dir.setReadable(true, false);
            dir.setWritable(true, false);
            dir.setExecutable(true, false);
        }
    }

    /**
     * 清理主机名中的非法字符。
     */
    private String sanitizeHostName(String username) {
        return username.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    /**
     * 清理孤立容器。
     */
    private void cleanupOrphanedContainers() {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withStatusFilter(Arrays.asList("running", "exited"))
                    .exec();

            for (Container container : containers) {
                String imageName = container.getImage();
                if (imageName != null && imageName.contains("wechat-agent")) {
                    String[] names = container.getNames();
                    if (names != null) {
                        for (String name : names) {
                            if (name.contains("wechat-")) {
                                log.info("发现孤立的微信容器 {}，正在移除...", container.getId().substring(0, 12));
                                try {
                                    dockerClient.removeContainerCmd(container.getId())
                                            .withForce(true)
                                            .withRemoveVolumes(true)
                                            .exec();
                                } catch (Exception e) {
                                    log.warn("移除孤立微信容器失败：{}", e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("清理孤立微信容器失败：{}", e.getMessage());
        }
    }

    // ==================== 查询方法 ====================

    public Collection<ContainerPod> getActivePods() {
        return Collections.unmodifiableCollection(activePods.values());
    }

    public Optional<ContainerPod> getPod(String containerId) {
        return Optional.ofNullable(activePods.get(containerId));
    }

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

    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPods", activePods.size());
        stats.put("maxPoolSize", config.getMaxPoolSize());
        stats.put("availablePods", activePods.values().stream().filter(ContainerPod::isAvailable).count());
        stats.put("busyPods", activePods.values().stream().filter(ContainerPod::isBusy).count());
        stats.put("allocatedPorts", allocatedPorts.size());
        stats.put("userBindings", userToPodMapping.size());
        return stats;
    }

    public void releasePod(String containerId) {
        ContainerPod pod = activePods.get(containerId);
        if (pod != null) {
            pod.release();
            log.info("微信容器 {} 已显式释放", pod.getShortId());
        }
    }
    // ==================== 辅助方法 ====================
    /**
     * 将宿主机路径转换为 Docker (Linux) 兼容的绝对路径格式。
     * 解决 Windows 运行 Java 时向 Linux Docker 引擎发送带反斜杠路径导致的 BadRequestException 报错。
     */
    private String sanitizeHostPathForDocker(String originalPath) {
        if (originalPath == null || originalPath.isBlank()) {
            return originalPath;
        }

        // 1. 将所有 Windows 反斜杠替换为正斜杠
        String sanitized = originalPath.replace("\\", "/");

        // 2. 确保它是一个合法的 Linux 绝对路径（必须以 / 开头）
        // 如果转换后变成了类似 usr/local/... 还需要补齐前导斜杠
        // 排除掉 Windows 盘符开头的路径 (如 C:/)，如果有盘符，这取决于你的 WSL 挂载配置
        if (!sanitized.startsWith("/") && !sanitized.matches("^[a-zA-Z]:/.*")) {
            sanitized = "/" + sanitized;
        }

        return sanitized;
    }
}
