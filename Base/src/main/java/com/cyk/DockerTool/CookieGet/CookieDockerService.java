package com.cyk.DockerTool.CookieGet;


import com.cyk.DockerTool.CookieGet.cmd.CookieTaskConfig;
import com.cyk.DockerTool.CookieGet.config.CookieProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class CookieDockerService {

    private final DockerClient dockerClient;
    private final CookieProperties properties;

    public CookieDockerService(DockerClient dockerClient, CookieProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    public String runCookieAgent(CookieTaskConfig config) {
        String hostPoolPath = properties.getCookiePoolPath();

        log.info("[CookieDockerService] runCookieAgent: " + config);

        // ==================== 新增：配置同步路径 ====================
        // 宿主机上的 YAML 配置目录
        String hostEnsConfigPath = "/usr/local/server/ai/AutoGUI/ENS/configs/" + config.getAccount() ;
        // 映射到容器内部的路径
        String containerEnsConfigPath = "/app/ens_configs";

        // 精确到站点的专属持久化目录：/Profiles/账号/数据源
        String targetHostDir = hostPoolPath + "/" + config.getAccount() + "/" + config.getSite();
        File poolDir = new File(targetHostDir);
        if (!poolDir.exists()) poolDir.mkdirs();

        List<Bind> binds = new ArrayList<>();

        // ==================== 核心修改：增加同步目录挂载 ====================
        // 无论什么模式，都挂载这个目录，以便 Python 脚本成功后直接写 YAML
        binds.add(new Bind(hostEnsConfigPath, new Volume(containerEnsConfigPath), AccessMode.rw));

        // ==========================================
        // 🚀 衍生需求：挂载 V1 和 V2 的用户配置文件根目录
        // ==========================================
        String hostV1ProfilePath = "/usr/local/server/ai/AutoGUI/V1/profiles";
        String hostV2ProfilePath = "/usr/local/server/ai/AutoGUI/V2/profiles";

        // 将宿主机的 V1/V2 配置根目录挂载进当前容器 (RW权限)
        binds.add(new Bind(hostV1ProfilePath, new Volume("/app/v1_profiles"), AccessMode.rw));
        binds.add(new Bind(hostV2ProfilePath, new Volume("/app/v2_profiles"), AccessMode.rw));

        // 【修正2】：动态获取当前任务的账号和站点，拼接出带“数据源”的双重隔离路径
        // 例如最终变成：/app/v1_profiles/13145739225/fengniao
        String targetAccount = config.getAccount();
        String targetSite = config.getSite();

        String containerV1Target = "/app/v1_profiles/" + targetAccount;
        String containerV2Target = "/app/v2_profiles/" + targetAccount;

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withAutoRemove(false)
                .withMemory(properties.getMemoryLimit());

        String pythonCommand;
        String imageName;

        // ==================== 场景 A：爱企查获取 (调用 V1 AI 镜像破防) ====================
        if ("aiqicha".equals(config.getSite()) && "fetch".equals(config.getMode())) {
            imageName = properties.getV1ImageName();
            // 将宿主机目录挂载为 V1 容器内 Chrome 的专属配置目录
            binds.add(new Bind(targetHostDir, new Volume("/app/chrome_profile"), AccessMode.rw));

            // 【新增】：挂载 AI 截图输出目录，方便你随时查看 AI 的操作画面！
            String annoHostPath = targetHostDir + "/OutPut";
            File annoDir = new File(annoHostPath);
            if (!annoDir.exists()) annoDir.mkdirs();
            binds.add(new Bind(annoHostPath, new Volume("/app/anno"), AccessMode.rw));

            hostConfig.withBinds(binds).withShmSize(properties.getShmSize());

            // 将参数编码进 taskId，以便回调时认领
            String customTaskId = "V1_AIQICHA|" + config.getAccount() + "|" + config.getSite();

            // ==========================================
            // 🧠 升级版 AI 指令（彻底消灭单引号，防止 Bash 截断）
            // ==========================================
            String instruction = "任务目标：请先使用 \"open app\" 动作(app_name为 google-chrome) 打开 Chrome 浏览器，访问 aiqicha.baidu.com 并搜索\"百度\"。请根据屏幕实际情况灵活应对：\n" +
                    "1. 【无阻拦情况】：如果看到搜索框，直接点击，输入'百度'\n" +
                    "2. 【遭遇拦截】：如果在访问途中弹出验证码：\n" +
                    "   - 「滑块验证码」：将滑块准确拖拽过去完成验证。\n" +
                    "   - 「复杂验证码」或拖拽失败：请点击浏览器\"刷新\"按钮重新加载页面，直到刷出滑块或被放行。\n" +
                    "3. 【最后收尾】：确认在搜索栏出现'百度'后，原地等待 8 秒钟，最后使用 \"answer\" 动作输出\"任务完成\"。";

            // 严厉警告：必须保持严格的 XML 格式！
            instruction += "\n[CRITICAL FORMAT RULE]: You MUST always wrap your JSON inside <tool_call></tool_call> XML tags! When you need to drag the slider, your argument inside the tag MUST use \"action\": \"drag\". DO NOT use left_click_drag.";


            pythonCommand = String.format(
                    "export DISPLAY=:99 && " +
                            "mkdir -p /app/chrome_profile && chmod -R 777 /app/chrome_profile && " +
                            "rm -f /usr/bin/xmessage /usr/bin/fbsetbg || true && " +
                            "mkdir -p /root/.fluxbox && touch /root/.Xauthority && " +
                            "Xvfb :99 -ac -screen 0 1000x1000x24 > /dev/null 2>&1 & sleep 3 && " +
                            "fluxbox > /dev/null 2>&1 & sleep 2 && " +
                            "python3 -u /app/computer_use/run_gui_owl_1_5_for_pc.py " +
                            "--api_key '%s' --base_url '%s' --model '%s' --max_steps 30 --instruction '%s' " +
                            "--task_id '%s' --callback_url '%s' ; sync && sleep 3",
                    properties.getAiApiKey(), properties.getAiBaseUrl(), properties.getAiModel(),
                    instruction, customTaskId, config.getCallbackUrl()
            );
        }
        // ==================== 场景 B：提取已存活的 Cookie (收割果实) ====================
        else if ("extract".equals(config.getMode())) {
            imageName = properties.getImageName();
            binds.add(new Bind(targetHostDir, new Volume("/app/chrome_profile"), AccessMode.rw));
            hostConfig.withBinds(binds).withShmSize(properties.getShmSize());

            // 暴力砸碎 SingletonLock，并传入 V1/V2 同步路径参数
            pythonCommand = String.format(
                    "rm -rf /app/chrome_profile/Singleton* && " +
                            "python -u /app/cookie_agent.py --site '%s' --account '%s' --password 'none' --callback '%s' --mode 'extract' --ens_config_dir '%s' " +
                            "--v1_sync_dir '%s' --v2_sync_dir '%s'",
                    config.getSite(), config.getAccount(), config.getCallbackUrl(), containerEnsConfigPath,
                    containerV1Target, containerV2Target
            );
        }
        // ==================== 场景 C：原有的风鸟 Fetch / Heartbeat ====================
        // ==================== 场景 C：原有的风鸟 Fetch / Heartbeat ====================
        else {

            log.info("场景 C：原有的风鸟 Fetch / Heartbeat");
            imageName = properties.getImageName();
            binds.add(new Bind(targetHostDir, new Volume("/app/cookie_pool"),
                    "heartbeat".equals(config.getMode()) ? AccessMode.ro : AccessMode.rw));
            hostConfig.withBinds(binds).withShmSize(properties.getShmSize());

            // 传入 V1/V2 同步路径参数
            pythonCommand = String.format(
                    "mkdir -p /app/cookie_pool && " +
                            ("heartbeat".equals(config.getMode()) ? "cp -a /app/base_cookie_pool/. /app/cookie_pool/ && " : "") +
                            "python -u /app/cookie_agent.py --site '%s' --account '%s' --password '%s' --callback '%s' --mode '%s' --ens_config_dir '%s' " +
                            "--v1_sync_dir '%s' --v2_sync_dir '%s'",
                    config.getSite(), config.getAccount(), config.getPassword(), config.getCallbackUrl(), config.getMode(), containerEnsConfigPath,
                    containerV1Target, containerV2Target
            );
        }

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imageName)
                .withTty(true).withStdinOpen(true).withEntrypoint("/bin/bash")
                .withEnv(
                        "DISPLAY=:99",
                        "DBUS_SESSION_BUS_ADDRESS=/dev/null",
                        "LIBGL_ALWAYS_SOFTWARE=1",
                        "VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.x86_64.json",
                        "ANGLE_DEFAULT_PLATFORM=swiftshader"
                )
                .withCmd("-c", pythonCommand)
                .withHostConfig(hostConfig)
                .withHostName("agent-" + config.getSite() + "-" + config.getMode());

        String containerId = containerCmd.exec().getId();
        dockerClient.startContainerCmd(containerId).exec();

        // ==================== 核心架构升级：Docker 原生事件监听 ====================
        // 如果是 V1 破防阶段，我们不再依赖 HTTP 回调，而是直接监听容器停止事件
        if ("aiqicha".equals(config.getSite()) && "fetch".equals(config.getMode())) {
            dockerClient.waitContainerCmd(containerId).exec(new WaitContainerResultCallback() {
                @Override
                public void onNext(WaitResponse waitResponse) {
                    // 当 V1 容器跑完并自动销毁时，这个方法会被瞬间触发
                    System.out.println("✨ V1 容器运行结束 (状态码: " + waitResponse.getStatusCode() + ")，底层无缝触发 Playwright 提取！");

                    CookieTaskConfig extractConfig = new CookieTaskConfig();
                    extractConfig.setSite(config.getSite());
                    extractConfig.setAccount(config.getAccount());
                    extractConfig.setMode("extract");
                    extractConfig.setCallbackUrl(config.getCallbackUrl()); // 提取完后再给 Java 发真正的入库回调

                    // 递归调用，启动提取容器
                    runCookieAgent(extractConfig);
                }
            });
        }
        // =========================================================================
        log.info("启动 Cookie 获取容器成功，ID: " + containerId);
        return containerId;
    }
    /**
     * 列出所有容器
     */
    public List<Container> listAllContainers() {
        return dockerClient.listContainersCmd().withShowAll(true).exec();
    }

    /**
     * 强制停止并移除容器 (防止僵尸容器占用资源)
     */
    public void stopAndRemoveContainer(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).exec();
        } catch (Exception e) {
            // 容器可能已经停止，忽略异常
        }
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            System.err.println("移除容器失败: " + e.getMessage());
        }
    }
}