package com.cyk.DockerTool.V3;

import com.cyk.DockerTool.V3.config.AgentPoolPropertiesV3;
import com.cyk.DockerTool.V3.model.ContainerPodV3;
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
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * V3 Docker容器池管理器。
 * 完全独立于V2，使用独立的端口范围、容器前缀和回调路径。
 */
@Component
public class DockerPoolManagerV3 {
    @Value("${agent.v3.pool.java-base-url}")
    String javaBaseUrl;
    private static final Logger log = LoggerFactory.getLogger(DockerPoolManagerV3.class);

    private final DockerClient dockerClient;
    private final AgentPoolPropertiesV3 properties;
    private final RestTemplate restTemplate;

    private final ConcurrentHashMap<String, ContainerPodV3> activePods = new ConcurrentHashMap<>();

    private final Set<Integer> allocatedPorts = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<Integer, Integer> portPairs = new ConcurrentHashMap<>();

    private final AtomicInteger macCounter = new AtomicInteger(0);

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public DockerPoolManagerV3(DockerClient dockerClient, AgentPoolPropertiesV3 properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        log.info("[V3] 正在初始化DockerPoolManager，最大池大小：{}", properties.getMaxPoolSize());
        log.info("[V3] 回调基础URL：{}", properties.getCallbackBaseUrl());

        cleanupOrphanedContainers();
    }

    @PreDestroy
    public void shutdown() {
        log.info("[V3] 正在关闭DockerPoolManager，清理{}个Pod", activePods.size());

        for (ContainerPodV3 pod : activePods.values()) {
            try {
                stopAndRemoveContainer(pod.getContainerId());
            } catch (Exception e) {
                log.warn("[V3] 关闭时停止容器{}失败：{}", pod.getShortId(), e.getMessage());
            }
        }

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
     * 为任务创建全新的容器Pod（一任务一容器，不复用）。
     */
    public ContainerPodV3 createPod(String profileName, int port) {
        if (activePods.size() >= properties.getMaxPoolSize()) {
            throw new IllegalStateException("[V3] 代理池已满且没有可用容器。" +
                    "当前大小：" + activePods.size() + "，最大值：" + properties.getMaxPoolSize());
        }

        log.info("[V3] 创建新容器Pod，配置文件：{}，只读模式", profileName);
        return createNewPod(profileName, port);
    }

    private ContainerPodV3 createNewPod(String profileName, int port) {
        // 端口分配失败时自动重试，最多重试5次（仅自动分配端口时）
        int maxRetries = (port > 0) ? 1 : 5; // 用户指定端口不重试，自动分配最多重试5次
        Set<Integer> failedPorts = new HashSet<>(); // 记录本次失败的端口，重试时跳过
        
        for (int retry = 0; retry < maxRetries; retry++) {
            int[] portPair = port > 0 ? allocatePortPair(port) : allocatePortPair(failedPorts);
            int apiPort = portPair[0];
            int vncPort = portPair[1];
            String macAddress = generateMacAddress();
            String containerId = null;
            
            try {
                containerId = createContainer(profileName, apiPort, vncPort, macAddress);
                
                dockerClient.startContainerCmd(containerId).exec();
                
                // 获取容器实际映射的端口（Docker可能分配了不同的端口）
                int[] actualPorts = getActualContainerPorts(containerId, apiPort, vncPort);
                int actualApiPort = actualPorts[0];
                int actualVncPort = actualPorts[1];
                
                ContainerPodV3 pod = new ContainerPodV3(containerId, actualApiPort, actualVncPort, profileName, profileName, macAddress);
                activePods.put(containerId, pod);
                
                log.info("[V3] 容器{}已在端口{}(API)和{}(VNC)上启动（请求端口：{}/{}），配置文件：{}", 
                        pod.getShortId(), actualApiPort, actualVncPort, apiPort, vncPort, profileName);
                
                waitForContainerReady(pod);
                
                pod.setStatus(ContainerPodV3.Status.READY);
                log.info("[V3] 容器{}已就绪，配置文件：{}", pod.getShortId(), profileName);
                
                return pod;
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                boolean isPortConflict = errorMsg != null && (
                    errorMsg.contains("port is already allocated") ||
                    errorMsg.contains("Bind for") && errorMsg.contains("failed: port is already allocated")
                );
                
                log.error("[V3] 创建容器失败（尝试{}/{}）：{}", retry + 1, maxRetries, errorMsg);
                
                // 释放端口
                releasePortPair(apiPort);
                
                // 【核心修复】：物理级摧毁僵尸容器，释放端口占用
                if (containerId != null) {
                    try {
                        dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
                        log.info("[V3] 已清理启动失败的僵尸容器: {}", containerId.substring(0, 12));
                    } catch (Exception cleanEx) {
                        log.warn("[V3] 清理失败容器时出现异常: {}", cleanEx.getMessage());
                    }
                }
                // 同时清理 activePods 中可能已添加的记录
                if (containerId != null) {
                    activePods.remove(containerId);
                }
                
                // 如果是端口冲突且还有重试机会，继续重试
                if (isPortConflict && retry < maxRetries - 1) {
                    failedPorts.add(apiPort); // 记录失败端口，下次重试跳过
                    log.warn("[V3] 端口{}冲突，将重试分配新端口对", apiPort);
                    continue;
                }
                
                // 其他错误或重试次数用尽，抛出异常
                throw new RuntimeException("[V3] 创建容器失败：" + errorMsg, e);
            }
        }
        
        throw new RuntimeException("[V3] 创建容器失败：超过最大重试次数");
    }

    /**
     * 获取容器实际映射的端口（Docker可能分配了与请求不同的端口）
     */
    private int[] getActualContainerPorts(String containerId, int requestedApiPort, int requestedVncPort) {
        try {
            com.github.dockerjava.api.command.InspectContainerResponse containerInfo = 
                    dockerClient.inspectContainerCmd(containerId).exec();
            
            Map<ExposedPort, Ports.Binding[]> bindings = containerInfo.getNetworkSettings().getPorts().getBindings();
            
            int actualApiPort = requestedApiPort;
            int actualVncPort = requestedVncPort;
            
            // 获取API端口的实际映射
            Ports.Binding[] apiBindings = bindings.get(ExposedPort.tcp(requestedApiPort));
            if (apiBindings != null && apiBindings.length > 0) {
                actualApiPort = Integer.parseInt(apiBindings[0].getHostPortSpec());
            }
            
            // 获取VNC端口的实际映射
            Ports.Binding[] vncBindings = bindings.get(ExposedPort.tcp(requestedVncPort));
            if (vncBindings != null && vncBindings.length > 0) {
                actualVncPort = Integer.parseInt(vncBindings[0].getHostPortSpec());
            }
            
            if (actualApiPort != requestedApiPort || actualVncPort != requestedVncPort) {
                log.info("[V3] 容器{}端口已重新映射：请求端口{}/{} -> 实际端口{}/{}",
                        containerId.substring(0, 12), requestedApiPort, requestedVncPort, actualApiPort, actualVncPort);
            }
            
            return new int[]{actualApiPort, actualVncPort};
            
        } catch (Exception e) {
            log.warn("[V3] 获取容器{}实际端口失败，使用请求端口：{}", containerId.substring(0, 12), e.getMessage());
            return new int[]{requestedApiPort, requestedVncPort};
        }
    }

    private String createContainer(String profileName, int apiPort, int vncPort, String macAddress) {
        String basePath = properties.getProfileBasePath();
        String outputDirName = properties.getOutputDirName();

        String hostUserBasePath = basePath + "/" + profileName;
        String hostOutputPath = hostUserBasePath + "/" + outputDirName;

        try {
            File baseDir = new File(basePath);
            if (!baseDir.exists()) {
                log.info("[V3] 基础目录不存在，正在创建：{}", basePath);
                boolean created = baseDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("[V3] 无法创建基础目录：" + basePath);
                }
                log.info("[V3] 基础目录创建成功：{}", basePath);
            }

            File userDir = new File(hostUserBasePath);
            if (!userDir.exists()) {
                log.info("[V3] 用户目录不存在，正在创建：{}", hostUserBasePath);
                boolean created = userDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("[V3] 无法创建用户目录：" + hostUserBasePath);
                }
                log.info("[V3] 用户目录创建成功：{}", hostUserBasePath);
            }

            File outputDir = new File(hostOutputPath);
            if (!outputDir.exists()) {
                log.info("[V3] 输出目录不存在，正在创建：{}", hostOutputPath);
                boolean created = outputDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("[V3] 无法创建输出目录：" + hostOutputPath);
                }
                log.info("[V3] 输出目录创建成功：{}", hostOutputPath);
            }

            if (!userDir.canRead() || !userDir.canWrite()) {
                log.warn("[V3] 用户目录权限不足，尝试设置权限：{}", hostUserBasePath);
                userDir.setReadable(true, false);
                userDir.setWritable(true, false);
                userDir.setExecutable(true, false);
            }

            if (!outputDir.canRead() || !outputDir.canWrite()) {
                log.warn("[V3] 输出目录权限不足，尝试设置权限：{}", hostOutputPath);
                outputDir.setReadable(true, false);
                outputDir.setWritable(true, false);
                outputDir.setExecutable(true, false);
            }

            log.info("[V3] 目录校验完成 - 用户：{}，输出目录：{}", hostUserBasePath, hostOutputPath);

        } catch (Exception e) {
            log.error("[V3] 目录创建失败：{}", e.getMessage(), e);
            throw new RuntimeException("[V3] 无法创建用户目录：" + e.getMessage(), e);
        }

        List<Bind> binds = new ArrayList<>();

        binds.add(new Bind(hostOutputPath, new Volume("/app/anno"), AccessMode.rw));
        log.debug("[V3] 挂载输出目录：{} -> /app/anno (rw)", hostOutputPath);

        // V3全部为只读模式，浏览器配置以只读方式挂载
        binds.add(new Bind(hostUserBasePath, new Volume("/app/base_profile/" + profileName), AccessMode.ro));
        log.debug("[V3] 挂载浏览器配置（只读）：{} -> /app/base_profile (ro)", hostUserBasePath);

        Ports portBindings = new Ports();
        portBindings.bind(ExposedPort.tcp(apiPort), Ports.Binding.bindIpAndPort("0.0.0.0", apiPort));
        portBindings.bind(ExposedPort.tcp(vncPort), Ports.Binding.bindIpAndPort("0.0.0.0", vncPort));

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withAutoRemove(false)
                .withMemory(properties.getMemoryLimit())
                .withMemorySwap(properties.getMemorySwap())
                .withShmSize(properties.getShmSize())
                .withBinds(binds)
                .withPortBindings(portBindings);

        List<String> envVars = Arrays.asList(
                "DISPLAY=:99",
                "DBUS_SESSION_BUS_ADDRESS=/dev/null",
                "LIBGL_ALWAYS_SOFTWARE=1",
                "VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.x86_64.json",
                "ANGLE_DEFAULT_PLATFORM=swiftshader",
                "AGENT_PORT=" + apiPort,
                "VNC_PORT=" + vncPort,
                "CONTAINER_ID=" + "v3-agent-" + System.currentTimeMillis(),
                "PROFILE_NAME=" + profileName,
                "USERNAME=" + profileName
        );

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(properties.getImageName())
                .withTty(true)
                .withStdinOpen(true)
                .withEnv(envVars)
                .withHostConfig(hostConfig)
                .withMacAddress(macAddress)
                .withHostName("v3-agent-" + profileName.replaceAll("[^a-zA-Z0-9]", ""))
                .withExposedPorts(ExposedPort.tcp(apiPort), ExposedPort.tcp(vncPort));

        CreateContainerResponse response = containerCmd.exec();
        String containerId = response.getId();

        log.debug("[V3] 创建容器{}，配置文件：{}，API端口{}，VNC端口{}，MAC地址{}",
                containerId.substring(0, 12), profileName, apiPort, vncPort, macAddress);

        return containerId;
    }

    private void waitForContainerReady(ContainerPodV3 pod) {
        int maxAttempts = 30;
        int attempt = 0;
        String url = STR."\{javaBaseUrl}:\{pod.getAssignedPort()}/health";

        log.info("[V3] 开始等待容器{}就绪，健康检查URL：{}", pod.getShortId(), url);

        while (attempt < maxAttempts) {
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String status = (String) response.getBody().get("status");
                    log.info("[V3] 容器{}健康检查响应：status={}", pod.getShortId(), status);
                    if ("healthy".equals(status)) {
                        log.info("[V3] 容器{}已就绪", pod.getShortId());
                        return;
                    }
                } else {
                    log.warn("[V3] 容器{}健康检查响应异常：statusCode={}", pod.getShortId(), response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("[V3] 容器{}尚未就绪（尝试{}/{}）：{}",
                        pod.getShortId(), attempt + 1, maxAttempts, e.getMessage());
            }

            attempt++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("[V3] 等待容器就绪时被中断", e);
            }
        }

        log.error("[V3] 容器{}在{}次尝试后仍未能就绪，最后尝试的URL：{}", pod.getShortId(), maxAttempts, url);
        throw new RuntimeException("[V3] 容器" + pod.getShortId() + "在超时时间内未能就绪");
    }

    public boolean dispatchTask(ContainerPodV3 pod, String taskId, String instruction,
                                String apiKey, String baseUrl, String model, int maxSteps) {
        ContainerPodV3.Status currentStatus = pod.getStatus().get();
        log.info("[V3] 尝试向Pod{}分派任务，当前状态：{}", pod.getShortId(), currentStatus);

        if (!pod.assignTask(taskId)) {
            log.warn("[V3] 无法向Pod{}分派任务：Pod状态为{}，不可用", pod.getShortId(), currentStatus);

            if (currentStatus != ContainerPodV3.Status.CREATING) {
                log.info("[V3] Pod{}处于{}状态，尝试强制释放后重试", pod.getShortId(), currentStatus);
                pod.forceRelease();
                if (pod.assignTask(taskId)) {
                    log.info("[V3] Pod{}释放后成功分配任务", pod.getShortId());
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        // 新任务开始前，重置容器的中止标志
        resetAbortFlag(pod);

        String callbackUrl = properties.getCallbackBaseUrl() +
                "/api/v3/docker/callback/" + pod.getContainerId();

        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("task_id", taskId);
        taskRequest.put("instruction", instruction);
        taskRequest.put("api_key", apiKey);
        taskRequest.put("base_url", baseUrl);
        taskRequest.put("model", model);
        taskRequest.put("max_steps", maxSteps);
        taskRequest.put("callback_url", callbackUrl);
        taskRequest.put("profile_name", pod.getProfileName());
        taskRequest.put("is_update_profile", false);

        pod.putMetadata("currentTaskId", taskId);
        pod.putMetadata("taskStartTime", System.currentTimeMillis());

        try {
            String url = STR."\{javaBaseUrl}:\{pod.getAssignedPort()}/task";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(taskRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[V3] 任务{}已分派到容器{}", taskId, pod.getShortId());
                return true;
            } else {
                log.error("[V3] 向容器{}分派任务失败：状态码{}",
                        pod.getShortId(), response.getStatusCode());
                pod.release();
                return false;
            }

        } catch (Exception e) {
            log.error("[V3] 向容器{}分派任务失败：{}", pod.getShortId(), e.getMessage());
            pod.release();
            return false;
        }
    }

    public void handleTaskCallback(String containerId, String status) {
        ContainerPodV3 pod = activePods.get(containerId);
        if (pod == null) {
            log.warn("[V3] 收到未知容器的回调：{}", containerId);
            return;
        }

        log.info("[V3] 收到容器{}的任务回调：状态={}", pod.getShortId(), status);

        switch (status) {
            case "completed":
            case "terminated":
                log.info("[V3] 容器{}任务完成，自动销毁容器（一任务一容器）", pod.getShortId());
                try {
                    stopAndRemoveContainer(containerId);
                } catch (Exception e) {
                    log.error("[V3] 自动销毁容器{}失败：{}", pod.getShortId(), e.getMessage());
                }
                break;

            case "timeout":
            case "failure":
                log.warn("[V3] 容器{}任务失败，自动销毁容器", pod.getShortId());
                pod.failTask();
                try {
                    stopAndRemoveContainer(containerId);
                } catch (Exception e) {
                    log.error("[V3] 自动销毁失败容器{}失败：{}", pod.getShortId(), e.getMessage());
                }
                break;

            case "processing":
                break;

            default:
                log.warn("[V3] 收到未知的任务状态：{}", status);
        }
    }

    public void releasePod(String containerId) {
        ContainerPodV3 pod = activePods.get(containerId);
        if (pod != null) {
            pod.release();
            log.info("[V3] 容器{}已显式释放", pod.getShortId());
        }
    }

    public void stopAndRemoveContainer(String containerId) {
        log.info("[V3] 触发容器停止");
        ContainerPodV3 pod = activePods.remove(containerId);
        if (pod != null) {
            releasePortPair(pod.getAssignedPort());
        }
        backupContainerDownloads(containerId);
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(5).exec();
        } catch (Exception e) {
            log.debug("[V3] 容器{}可能已停止：{}", containerId, e.getMessage());
        }

        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
            log.info("[V3] 容器{}已移除", containerId.substring(0, 12));
        } catch (Exception e) {
            log.warn("[V3] 移除容器{}失败：{}", containerId, e.getMessage());
        }
    }

    public Collection<ContainerPodV3> getActivePods() {
        return Collections.unmodifiableCollection(activePods.values());
    }

    public Optional<ContainerPodV3> getPod(String containerId) {
        return Optional.ofNullable(activePods.get(containerId));
    }

    public Optional<ContainerPodV3> getPodByContainerId(String containerId) {
        ContainerPodV3 pod = activePods.get(containerId);
        return Optional.ofNullable(pod);
    }

    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPods", activePods.size());
        stats.put("maxPoolSize", properties.getMaxPoolSize());
        stats.put("availablePods", activePods.values().stream().filter(ContainerPodV3::isAvailable).count());
        stats.put("busyPods", activePods.values().stream().filter(ContainerPodV3::isBusy).count());
        stats.put("allocatedPorts", allocatedPorts.size());
        return stats;
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupIdleContainers() {
        long idleTimeout = properties.getIdleTimeoutSeconds();
        List<String> toRemove = new ArrayList<>();

        for (ContainerPodV3 pod : activePods.values()) {
            if (pod.isAvailable() && pod.getIdleSeconds() > idleTimeout) {
                log.info("[V3] 容器{}超过空闲超时（{}秒），标记为清理",
                        pod.getShortId(), pod.getIdleSeconds());
                toRemove.add(pod.getContainerId());
            }
        }

        for (String containerId : toRemove) {
            try {
                stopAndRemoveContainer(containerId);
            } catch (Exception e) {
                log.error("[V3] 清理空闲容器{}失败：{}", containerId, e.getMessage());
            }
        }
    }

    // ==================== 私有辅助方法 ====================

    private boolean isContainerRunning(String containerId) {
        try {
            com.github.dockerjava.api.command.InspectContainerResponse inspectResponse =
                    dockerClient.inspectContainerCmd(containerId).exec();
            return inspectResponse.getState().getRunning();
        } catch (Exception e) {
            log.warn("[V3] 检查容器{}状态失败：{}", containerId, e.getMessage());
            return false;
        }
    }

    private void logRemovePodBinding(String containerId) {
        log.debug("[V3] 已移除容器 {} 的绑定", containerId.length() > 12 ? containerId.substring(0, 12) : containerId);
    }

    private int[] allocatePortPair(int apiPort) {
        int start = properties.getPortRangeStart();
        int end = properties.getPortRangeEnd();
        int vncPort = apiPort + 1;

        if (apiPort % 2 == 0) {
            throw new IllegalArgumentException("[V3] 指定的 API 端口必须是奇数: " + apiPort);
        }
        if (apiPort < start || vncPort > end) {
            throw new IllegalArgumentException("[V3] 端口超出可用范围 [" + start + ", " + end + "]: " + apiPort);
        }

        if (allocatedPorts.add(apiPort)) {
            if (allocatedPorts.add(vncPort)) {
                portPairs.put(apiPort, vncPort);
                log.debug("[V3] 成功分配指定端口对：API={}, VNC={}", apiPort, vncPort);
                return new int[]{apiPort, vncPort};
            } else {
                allocatedPorts.remove(apiPort);
                throw new IllegalStateException("[V3] VNC 端口已被占用: " + vncPort);
            }
        } else {
            throw new IllegalStateException("[V3] API 端口已被占用: " + apiPort);
        }
    }

    private int[] allocatePortPair() {
        return allocatePortPair(Collections.emptySet());
    }
    
    private int[] allocatePortPair(Set<Integer> failedPorts) {
        int start = properties.getPortRangeStart();
        int end = properties.getPortRangeEnd();

        if (start % 2 == 0) {
            start++;
        }

        for (int apiPort = start; apiPort < end; apiPort += 2) {
            if (failedPorts.contains(apiPort)) {
                continue; // 跳过本次失败的端口
            }
            try {
                return allocatePortPair(apiPort);
            } catch (IllegalStateException e) {
                continue;
            }
        }

        throw new IllegalStateException("[V3] 端口范围 " + start + "-" + end + " 中没有可用的连续端口对");
    }

    private void releasePortPair(int apiPort) {
        Integer vncPort = portPairs.remove(apiPort);
        if (vncPort != null) {
            allocatedPorts.remove(vncPort);
        }
        allocatedPorts.remove(apiPort);
        log.debug("[V3] 释放端口对：API={}, VNC={}", apiPort, vncPort);
    }

    private void releasePort(int port) {
        allocatedPorts.remove(port);
    }

    private String generateMacAddress() {
        int counter = macCounter.incrementAndGet();
        return String.format("02:42:ad:%02x:%02x:%02x",
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
                if (imageName != null && imageName.contains("autogui-v3")) {
                    String[] names = container.getNames();
                    if (names != null) {
                        for (String name : names) {
                            if (name.contains("v3-agent-")) {
                                log.info("[V3] 发现孤立容器{}，正在移除...", container.getId().substring(0, 12));
                                try {
                                    dockerClient.removeContainerCmd(container.getId())
                                            .withForce(true)
                                            .withRemoveVolumes(true)
                                            .exec();
                                } catch (Exception e) {
                                    log.warn("[V3] 移除孤立容器失败：{}", e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[V3] 清理孤立容器失败：{}", e.getMessage());
        }
    }

    /**
     * 向容器发送 GUI 工具操作指令（V3 Bridge 模式）
     * <p>
     * 当 AI 中台 Agent 挂起并需要工具平台执行 GUI 操作时调用此方法。
     * 容器执行完成后，会回调 callbackUrl 通知工具平台。
     * </p>
     *
     * @param pod 目标容器 Pod
     * @param taskId 任务 ID
     * @param toolName 工具名称（如 gui_left_click、gui_input_text）
     * @param toolInput 工具输入参数（JSON 字符串）
     * @param callbackUrl 容器执行完成后的回调地址
     */
    public void executeToolAction(ContainerPodV3 pod, String taskId, String toolName,
                                  String toolInput, String callbackUrl) {
        log.info("[V3] 向容器 {} 发送工具操作指令，taskId: {}, toolName: {}", pod.getShortId(), taskId, toolName);

        Map<String, Object> request = new HashMap<>();
        request.put("task_id", taskId);
        request.put("tool_name", toolName);
        request.put("tool_input", toolInput);
        request.put("callback_url", callbackUrl);

        CompletableFuture.runAsync(() -> {
            try {
                String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/execute_tool";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("[V3] 工具操作指令已发送到容器 {}，taskId: {}", pod.getShortId(), taskId);
                } else {
                    log.error("[V3] 工具操作指令发送失败，容器 {}，状态码: {}", pod.getShortId(), response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("[V3] 向容器 {} 发送工具操作指令失败: {}", pod.getShortId(), e.getMessage(), e);
            }
        }, executorService);
    }

    public Optional<ContainerPodV3> getPodByTaskId(String taskId) {
        return activePods.values().stream()
                .filter(pod -> taskId.equals(pod.getCurrentTaskId()))
                .findFirst();
    }

    public Map<String, Object> executeSingleStep(ContainerPodV3 pod, String taskId, String instruction,
                                                 String apiKey, String baseUrl, String model) {
        if (!pod.assignTask(taskId)) {
            pod.forceRelease();
            pod.assignTask(taskId);
        }

        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("task_id", taskId);
        taskRequest.put("instruction", instruction);
        taskRequest.put("api_key", apiKey);
        taskRequest.put("base_url", baseUrl);
        taskRequest.put("model", model);
        taskRequest.put("profile_name", pod.getProfileName());
        taskRequest.put("is_update_profile", false);

        try {
            String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/step";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(taskRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            pod.setStatus(ContainerPodV3.Status.READY);
            return response.getBody();

        } catch (Exception e) {
            pod.setStatus(ContainerPodV3.Status.READY);
            throw new RuntimeException("[V3] 向容器发送单步指令失败：" + e.getMessage(), e);
        }
    }

    public void abortTask(String taskId) {
        TaskContext ctx = taskContexts.get(taskId);
        if (ctx == null) return;

        ctx.setAborted(true);

        ContainerPodV3 pod = ctx.getPod();
        log.info("[V3] 正在软中止任务: {}, 容器: {}（保留容器，不销毁）", taskId, pod.getShortId());

        // 1. 异步向容器发送 /abort 请求，通知容器内部停止执行
        CompletableFuture.runAsync(() -> {
            try {
                String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/abort";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                Map<String, String> body = Map.of("task_id", taskId);
                restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
                log.info("[V3] 已向容器 {} 发送中止信号", pod.getShortId());
            } catch (Exception e) {
                log.warn("[V3] 向容器发送中止信号失败 (可能容器已关闭): {}", e.getMessage());
            }
        });

        // 2. 释放容器状态（从 BUSY 回退到 READY），不销毁容器
        pod.release();
        log.info("[V3] 容器 {} 已释放回 READY 状态，可接受新任务", pod.getShortId());

        // 3. 清理任务上下文
        taskContexts.remove(taskId);
        log.info("[V3] 任务 {} 的上下文已清理", taskId);
    }

    /**
     * 重置容器的中止标志（新任务开始前调用）
     */
    public void resetAbortFlag(ContainerPodV3 pod) {
        try {
            String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/abort/reset";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(headers), Map.class);
            log.info("[V3] 已重置容器 {} 的中止标志", pod.getShortId());
        } catch (Exception e) {
            log.warn("[V3] 重置容器中止标志失败 (可能容器已关闭): {}", e.getMessage());
        }
    }

    // ==========================================
    // V3任务上下文存储区
    // ==========================================
    @Data
    public static class TaskContext {
        private Long taskId;
        private ContainerPodV3 pod;
        private String instruction;
        private Queue<String> instructionQueue = new ConcurrentLinkedQueue<>();
        private String apiKey;
        private String baseUrl;
        private String model;
        private boolean isUpdateProfile; // 保留字段兼容，实际不再使用，V3全部只读
        private int maxSteps;
        private int currentStep;
        private boolean aborted = false;
    }

    private final Map<String, TaskContext> taskContexts = new ConcurrentHashMap<>();

    public void initTaskContext(Long taskId, ContainerPodV3 pod, String instruction,
                                String apiKey, String baseUrl, String model,
                                int maxSteps) {
        TaskContext ctx = new TaskContext();
        ctx.setTaskId(taskId);
        ctx.setPod(pod);
        ctx.setInstruction(instruction);
        ctx.setApiKey(apiKey);
        ctx.setBaseUrl(baseUrl);
        ctx.setModel(model);
        ctx.setMaxSteps(maxSteps);
        ctx.setCurrentStep(0);
        taskContexts.put(String.valueOf(taskId), ctx);
    }

    public TaskContext getTaskContext(String taskId) {
        return taskContexts.get(taskId);
    }

    /**
     * V3核心动作：向Python容器发射下一个"乒乓球"
     * 【V3已废弃】容器不再有 /step 和 LLM，改由 AI 中台 AgentScope Agent 直接调度 GUI 操作
     * 保留方法签名供编译通过，方法体注释掉
     */
    public void triggerNextStepAsync(String taskId) {
        log.warn("[V3] triggerNextStepAsync 已废弃，不再向容器发 /step。taskId: {}", taskId);
        /*
        TaskContext ctx = taskContexts.get(taskId);
        if (ctx == null) return;

        ContainerPodV3 pod = ctx.getPod();
        pod.touch();
        pod.setStatus(ContainerPodV3.Status.BUSY);

        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("task_id", taskId);
        taskRequest.put("instruction", ctx.getInstruction());
        taskRequest.put("api_key", ctx.getApiKey());
        taskRequest.put("base_url", ctx.getBaseUrl());
        taskRequest.put("model", ctx.getModel());
        taskRequest.put("profile_name", pod.getProfileName());
        taskRequest.put("is_update_profile", ctx.isUpdateProfile());

        String callbackUrl = properties.getCallbackBaseUrl() + "/api/v3/docker/callback/" + pod.getContainerId();
        taskRequest.put("callback_url", callbackUrl);

        CompletableFuture.runAsync(() -> {
            try {
                String url = javaBaseUrl + ":" + pod.getAssignedPort() + "/step";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(taskRequest, headers);

                restTemplate.postForEntity(url, entity, Map.class);
                log.info("[V3] 已向容器 {} (Task: {}) 发送单步执行指令", pod.getShortId(), taskId);
            } catch (Exception e) {
                log.error("[V3] 向Python发送指令失败: {}", e.getMessage());
            }
        });
        */
    }

    /**
     * 在容器销毁前，转移容器内的PDF下载文件到宿主机
     */
    private void backupContainerDownloads(String containerId) {
        String containerPath = "/root/Downloads";
        String hostPath = "/home/zjhtest/v3_downFile";

        File hostDir = new File(hostPath);
        if (!hostDir.exists()) {
            boolean mkdirsSuccess = hostDir.mkdirs();
            if (!mkdirsSuccess && !hostDir.exists()) {
                log.error("[V3] 宿主机根存储目录创建失败，请检查用户权限: {}", hostPath);
                return;
            }
        }

        log.info("[V3] 准备直接复制容器 {} 的 PDF 下载文件到服务器...", containerId.substring(0, 12));

        try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(containerId, containerPath).exec();
             TarArchiveInputStream tar = new TarArchiveInputStream(tarStream)) {

            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {

                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                if (entryName != null && entryName.toLowerCase().endsWith(".pdf")) {

                    String pureFileName = new File(entryName).getName();

                    if (pureFileName == null || pureFileName.isBlank()) {
                        continue;
                    }

                    File destFile = new File(hostDir, pureFileName);

                    log.info("[V3] 查找到有效PDF，正在写入服务器: {}", destFile.getAbsolutePath());

                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        tar.transferTo(fos);
                    }
                    log.info("[V3] 成功提取并保存 PDF 文件: {}", destFile.getName());
                }
            }
            log.info("[V3] 容器 {} 的 PDF 文件已成功合并到服务器", containerId.substring(0, 12));

        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.debug("[V3] 容器 {} 中没有 /root/Downloads 目录或目录为空，无需备份跳过", containerId.substring(0, 12));
        } catch (Exception e) {
            log.error("[V3] 复制容器 {} 下载文件时发生异常: {}", containerId.substring(0, 12), e.getMessage(), e);
        }
    }
}
