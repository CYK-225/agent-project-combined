package org.example.acl;

import org.example.acl.apiClient.GuiApiClient;
import org.example.repository.dal.entity.AgentTaskEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * V3 GUI API 测试控制器
 * 所有 GUI 方法已改为异步（void），测试接口只验证请求能否正确发送
 * 实际执行结果通过 /api/phone/callback 异步回调到 PhoneCallbackController
 */
@RestController
@RequestMapping("/api/v3/test")
public class testController {

    private static final String FRP_CALLBACK_URL = "http://8.163.67.126:8989/api/phone/callback";

    /**
     * 测试健康检查（同步接口，可以直接看结果）
     */
    @PostMapping("/health")
    public String testHealth(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        GuiApiClient client = new GuiApiClient(url);
        boolean healthy = client.healthCheck();
        return healthy ? "✅ 健康检查通过" : "❌ 健康检查失败，容器不可达";
    }

    /**
     * 测试注册 + 全部 GUI 操作回调
     * 先注册 callback_url（通过 frp 穿透到本地 8089），再依次发送全部 GUI 操作，观察本地回调
     */
    @PostMapping("/callback")
    public String testCallback(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String agentId = body.getOrDefault("agent_id", "test_agent_001");
        GuiApiClient client = new GuiApiClient(url);

        // 先检查健康
        boolean healthy = client.healthCheck();
        if (!healthy) {
            return "❌ 健康检查失败，容器不可达";
        }

        // 构造注册请求
        AgentTaskEntity taskEntity = AgentTaskEntity.builder()
                .sessionId(agentId)
                .callbackUrl(FRP_CALLBACK_URL)
                .build();

        boolean registered = client.registerAgent(taskEntity);
        if (!registered) {
            return "❌ 注册失败，callback_url: " + FRP_CALLBACK_URL;
        }

        StringBuilder log = new StringBuilder();
        log.append("✅ 注册成功，agent_id：").append(agentId).append("\n");
        log.append("回调地址：").append(FRP_CALLBACK_URL).append("\n\n");

        int step = 1;

        client.mouseMove(step, 500, 500);
        log.append("✅ ").append(step++).append(". mouseMove(500, 500) 已发送\n");

        client.leftClick(step, 500, 500);
        log.append("✅ ").append(step++).append(". leftClick(500, 500) 已发送\n");

        client.rightClick(step, 500, 500);
        log.append("✅ ").append(step++).append(". rightClick(500, 500) 已发送\n");

        client.doubleClick(step, 500, 500);
        log.append("✅ ").append(step++).append(". doubleClick(500, 500) 已发送\n");

        client.tripleClick(step, 500, 500);
        log.append("✅ ").append(step++).append(". tripleClick(500, 500) 已发送\n");

        client.middleClick(step, 500, 500);
        log.append("✅ ").append(step++).append(". middleClick(500, 500) 已发送\n");

        client.drag(step, 300, 300);
        log.append("✅ ").append(step++).append(". drag(300, 300) 已发送\n");

        client.type(step, "测试文字");
        log.append("✅ ").append(step++).append(". type(\"测试文字\") 已发送\n");

        client.key(step, "enter");
        log.append("✅ ").append(step++).append(". key(\"enter\") 已发送\n");

        client.scroll(step, -300);
        log.append("✅ ").append(step++).append(". scroll(-300) 已发送\n");

        client.openApp(step, "google-chrome");
        log.append("✅ ").append(step++).append(". openApp(\"google-chrome\") 已发送\n");

        client.wait(step, 2);
        log.append("✅ ").append(step++).append(". wait(2) 已发送\n");

        client.reset(step);
        log.append("✅ ").append(step++).append(". reset() 已发送\n");

        log.append("\n共 ").append(step - 1).append(" 个 GUI 操作已异步发送");
        log.append("\n请查看本地 Spring Boot 日志确认是否收到回调（应收到 ").append(step - 1).append(" 次回调）");

        return log.toString();
    }

    /**
     * 批量发送所有 GUI 操作（异步，发完即返回）
     * 需要先调用 /callback 注册，否则容器不知道回调地址
     * 实际结果请查看容器日志或 PhoneCallbackController 回调日志
     */
    @PostMapping("/all")
    public String testAll(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        GuiApiClient client = new GuiApiClient(url);

        // 先检查健康
        boolean healthy = client.healthCheck();
        if (!healthy) {
            return "❌ 健康检查失败，容器不可达";
        }

        StringBuilder log = new StringBuilder();
        log.append("✅ 健康检查通过\n");
        log.append("以下操作已异步发送（结果通过回调获取）：\n\n");

        // 异步发送所有 GUI 操作
        client.mouseMove(1, 500, 500);
        log.append("✅ mouseMove(1, 500, 500) 已发送\n");

        client.leftClick(2, 500, 500);
        log.append("✅ leftClick(2, 500, 500) 已发送\n");

        client.rightClick(3, 500, 500);
        log.append("✅ rightClick(3, 500, 500) 已发送\n");

        client.doubleClick(4, 500, 500);
        log.append("✅ doubleClick(4, 500, 500) 已发送\n");

        client.tripleClick(5, 500, 500);
        log.append("✅ tripleClick(5, 500, 500) 已发送\n");

        client.middleClick(6, 500, 500);
        log.append("✅ middleClick(6, 500, 500) 已发送\n");

        client.drag(7, 300, 300);
        log.append("✅ drag(7, 300, 300) 已发送\n");

        client.type(8, "测试文字");
        log.append("✅ type(8, \"测试文字\") 已发送\n");

        client.key(9, "enter");
        log.append("✅ key(9, \"enter\") 已发送\n");

        client.scroll(10, -300);
        log.append("✅ scroll(10, -300) 已发送\n");

        client.openApp(11, "google-chrome");
        log.append("✅ openApp(11, \"google-chrome\") 已发送\n");

        client.wait(12, 1);
        log.append("✅ wait(12, 1) 已发送\n");

        client.reset(13);
        log.append("✅ reset(13) 已发送\n");

        log.append("\n共 13 个 GUI 操作已异步发送");
        log.append("\n回调端点：POST /api/phone/callback → PhoneCallbackController");

        return log.toString();
    }
}
