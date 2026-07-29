package com.cyk.Config;


import com.cyk.DockerTool.V1.config.V1AgentProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class V1DockerManager {

    @Resource
    private DockerClient dockerClient;

    @Resource
    private V1AgentProperties v1AgentProperties;

    @PostConstruct
    public void startV1Manager() {
        try {
            log.info("==> 正在随系统初始化 V1 Agent 模块环境...");

            // 1. 检查 Docker 服务连通性
            dockerClient.pingCmd().exec();
            log.info("<== V1 Docker 宿主服务连接正常！");

            // 2. 清理历史残留的无用容器 (释放服务器磁盘和内存)
            String targetImage = v1AgentProperties.getImageName();
            List<Container> allContainers = dockerClient.listContainersCmd().withShowAll(true).exec();
            
            int removeCount = 0;
            for (Container container : allContainers) {
                String image = container.getImage();
                String state = container.getState();
                
                // 找到属于 V1 模块且已经处于退出/错误状态的容器进行销毁
                if (image != null && image.contains(targetImage) && !"running".equalsIgnoreCase(state)) {
                    dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                    removeCount++;
                }
            }
            
            if (removeCount > 0) {
                log.info("<== V1 环境初始化：成功清理了 {} 个历史残留的休眠容器。", removeCount);
            }

        } catch (Exception e) {
            log.error("💥 致命错误：V1 模块 Docker 初始化异常，请检查 Docker Daemon 是否启动: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stopV1Manager() {
        log.info("==> 正在关闭 V1 模块，准备安全清理运行中的任务容器...");
        try {
            String targetImage = v1AgentProperties.getImageName();
            
            // 仅获取当前仍在运行的容器
            List<Container> runningContainers = dockerClient.listContainersCmd().exec(); 

            int stopCount = 0;
            for (Container container : runningContainers) {
                String image = container.getImage();
                if (image != null && image.contains(targetImage)) {
                    log.info("🚨 拦截到正在运行的 V1 任务容器: {}，正在强制停止...", container.getId());
                    
                    // 设定 5 秒超时强制停止，随后删除容器
                    dockerClient.stopContainerCmd(container.getId()).withTimeout(5).exec();
                    dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                    stopCount++;
                }
            }
            log.info("<== V1 模块关闭完成，共拦截并销毁 {} 个运行中的容器。", stopCount);
            
        } catch (Exception e) {
            log.error("关闭 V1 容器环境时发生异常: {}", e.getMessage());
        }
    }
}