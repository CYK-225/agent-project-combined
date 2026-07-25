package com.cyk.DockerTool.CookieGet;


import com.cyk.DockerTool.CookieGet.cmd.CookieTaskConfig;
import com.cyk.DockerTool.CookieGet.config.CookieProperties;
import com.cyk.Enity.table.SpiderCookiePoolEntity;
import com.cyk.Service.ISpiderCookiePoolService;
import com.github.dockerjava.api.model.Container;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cookie")
@Slf4j
public class CookieController {

    private final CookieDockerService dockerService;
    private final CookieProperties properties;

    // 注入你刚生成的 Service
    private final ISpiderCookiePoolService spiderCookiePoolService;

    // 推荐使用构造器注入
    @Autowired
    public CookieController(CookieDockerService dockerService,
                            CookieProperties properties,
                            ISpiderCookiePoolService spiderCookiePoolService) {
        this.dockerService = dockerService;
        this.properties = properties;
        this.spiderCookiePoolService = spiderCookiePoolService;
    }

    // ==================== 运维与调试接口 ====================

    @GetMapping("/containers")
    public List<Container> listContainers() {
        return dockerService.listAllContainers();
    }

    @PostMapping("/containers/{id}/stop")
    public String stopContainer(@PathVariable("id") String containerId) {
        try {
            dockerService.stopAndRemoveContainer(containerId);
            return STR."容器 [\{containerId}] 已强制停止并移除！";
        } catch (Exception e) {
            return STR."容器停止失败：\{e.getMessage()}";
        }
    }

    // ==================== 业务触发接口 ====================

    @PostMapping("/fetch")
    public String fetchCookie(@RequestBody CookieTaskConfig config) {
        config.setMode("fetch");
        config.setTaskId(UUID.randomUUID().toString());
        if(config.getCallbackUrl() == null) config.setCallbackUrl(properties.getDefaultCallbackUrl());

        try {
            String containerId = dockerService.runCookieAgent(config);
            return STR."操作执行成功！任务模式: FETCH, 容器ID: \{containerId}";
        } catch (Exception e) {
            return STR."操作执行失败：\{e.getMessage()}";
        }
    }

    @PostMapping("/heartbeat")
    public String heartbeatCookie(@RequestBody CookieTaskConfig config) {
        config.setMode("heartbeat");
        config.setTaskId(UUID.randomUUID().toString());
        if(config.getCallbackUrl() == null) config.setCallbackUrl(properties.getDefaultCallbackUrl());

        try {
            String containerId = dockerService.runCookieAgent(config);
            return STR."操作执行成功！任务模式: HEARTBEAT, 容器ID: \{containerId}";
        } catch (Exception e) {
            return STR."操作执行失败：\{e.getMessage()}";
        }
    }

    @PostMapping("/quick/fetch")
    public String quickFetch(@RequestParam("site") String site,
                             @RequestParam("account") String account,
                             @RequestParam("password") String password) {
        CookieTaskConfig config = new CookieTaskConfig();
        config.setSite(site);
        config.setAccount(account);
        config.setPassword(password);
        config.setMode("fetch");
        config.setTaskId(UUID.randomUUID().toString());
        config.setCallbackUrl(properties.getDefaultCallbackUrl());

        try {
            String containerId = dockerService.runCookieAgent(config);
            System.out.println(STR."启动 Cookie Fetch 容器成功，ID: \{containerId}");
            return "操作执行成功！";
        } catch (Exception e) {
            return STR."操作执行失败：\{e.getMessage()}";
        }
    }

    // ==================== 回调接收接口 (核心入库逻辑) ====================

    @PostMapping("/callback")
    public String handleCallback(@RequestBody Map<String, Object> payload) {
        // 0. 极限防御：防止整个 payload 都是空的
        if (payload == null) {
            System.err.println("🛑 收到完全为空的回调 payload，直接拦截！");
            return "INVALID_PAYLOAD";
        }
        if (payload.containsKey("task_id") && payload.containsKey("data")) {
            // 如果你想看进度，可以把下面这行解开，否则直接 return
            // System.out.println("收到 V1 Agent 进度上报: " + ((Map)payload.get("data")).get("step"));
            return "RECEIVED_V1_IGNORED";
        }
        String site = (String) payload.get("site");
        String account = (String) payload.get("account");
        String mode = (String) payload.get("mode");
        String status = (String) payload.get("status");

        System.out.println(STR."当前时间时分秒: \{LocalTime.now().withNano(0)}");
        System.out.println(String.format("[Cookie任务结束] 模式:%s | 站点:%s | 账号:%s | 最终状态:%s", mode, site, account, status));

        // ==========================================
        // 🛡️ 核心防御：如果 site 或 account 为空，绝对不能查库和入库！
        // ==========================================
        if (site == null || site.trim().isEmpty() || account == null || account.trim().isEmpty()) {
            System.err.println("🛑 拦截异常请求：缺失关键字段 site 或 account，放弃入库以保护数据库！");
            return "MISSING_PARAMS";
        }

        // 1. 根据 site 和 account 查询数据库是否已有该记录
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("site = ?", site)
                .and("account = ?", account);
        SpiderCookiePoolEntity entity = spiderCookiePoolService.getOne(queryWrapper);

        // 如果没有记录，则初始化一条新数据
        if (entity == null) {
            entity = new SpiderCookiePoolEntity();
            entity.setSite(site);
            entity.setAccount(account);
        }

        // 2. 根据回调状态更新实体字段
        if ("success".equals(status)) {
            String cookieStr = (String) payload.get("cookie");

            // 防御：防止 cookieStr 为 null 导致下面的 substring 报空指针异常
            if (cookieStr != null && !cookieStr.isEmpty()) {
                System.out.println(STR."成功提取并准备写入数据库的 Cookie: \{cookieStr.substring(0, Math.min(cookieStr.length(), 50))}...");
                entity.setCookieValue(cookieStr);
            } else {
                System.err.println("⚠️ 警告：状态为 success，但没有传回有效的 cookie 字符串！");
            }

            entity.setStatus(1); // 1 表示有效可用
            entity.setFailReason(null); // 清空以往的失败原因
            entity.setLastVerifyTime(new java.util.Date()); // 更新最后验证时间

        } else {
            String reason = (String) payload.get("reason");
            System.err.println(STR."[任务异常] 原因: \{reason}");

            // 写入失败信息：状态置为 0，并记录原因
            entity.setStatus(0); // 0 表示失效或获取失败
            entity.setFailReason(reason);
        }

        // 3. 统一执行保存或更新操作
        spiderCookiePoolService.saveOrUpdate(entity);

        return "RECEIVED";
    }}