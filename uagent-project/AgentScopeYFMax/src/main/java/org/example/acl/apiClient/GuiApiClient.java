package org.example.acl.apiClient;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.example.repository.dal.entity.AgentTaskEntity;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * V3 GUI 容器 API 客户端
 * 封装与 V3 GUI 容器的所有 HTTP 通信
 * 每个方法对应容器暴露的一个 GUI 操作接口
 */
@Slf4j
public class GuiApiClient {

    private final String containerUrl;
    private final OkHttpClient httpClient;

    public GuiApiClient(String containerUrl) {
        this.containerUrl = containerUrl.endsWith("/") ? containerUrl.substring(0, containerUrl.length() - 1) : containerUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    // ================================================================
    //  响应结果
    // ================================================================

    /**
     * GUI 操作的统一响应
     */
    public static class GuiResponse {
        private final boolean success;
        private final String result;
        private final String screenshot;      // base64 编码的截图
        private final String screenshotPath;  // 宿主机上的截图路径
        private final String agentId;         // AgentScope 的 agent ID

        public GuiResponse(boolean success, String result, String screenshot, String screenshotPath, String agentId) {
            this.success = success;
            this.result = result;
            this.screenshot = screenshot;
            this.screenshotPath = screenshotPath;
            this.agentId = agentId;
        }

        public boolean isSuccess() { return success; }
        public String getResult() { return result; }
        public String getScreenshot() { return screenshot; }
        public String getScreenshotPath() { return screenshotPath; }
        public String getAgentId() { return agentId; }

        @Override
        public String toString() {
            return "GuiResponse{success=" + success +
                    ", result='" + result + '\'' +
                    ", screenshotPath='" + screenshotPath + '\'' +
                    ", agentId='" + agentId + '\'' +
                    '}';
        }
    }

    // ================================================================
    //  核心请求方法 - 异步发送，不等待执行结果
    // ================================================================

    /**
     * 异步发送 GUI 请求，发完即返回，不等待执行结果。
     * 执行结果通过 callback_url 异步回调。
     */
    private void postAsync(String path, JSONObject body) {
        String url = containerUrl + path;
        RequestBody requestBody = RequestBody.create(body.toJSONString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(requestBody).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("[GuiApiClient] 异步请求失败 {}: {}", path, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
                log.info("[GuiApiClient] 异步请求已提交 {}，HTTP {}", path, response.code());
            }
        });
    }

    // ================================================================
    //  注册接口
    // ================================================================

    /**
     * 注册 agent 信息到容器（在 AgentScope 创建 agent 前调用）
     * <p>
     * 容器会将注册时的完整 DTO 原样回传（追加 step/success/result/screenshot 等字段），
     * 因此注册时必须同时发送两种格式的字段：
     * <ul>
     *   <li>snake_case（如 callback_url）：供容器脚本自身读取回调目标地址</li>
     *   <li>camelCase（如 taskId, sessionId, agentName）：供 Java 端回调时 Jackson 正确反序列化为 AgentTaskNotifyDTO</li>
     * </ul>
     */
    public boolean registerAgent(AgentTaskEntity taskEntity) {
        JSONObject body = new JSONObject();
        // ---- snake_case：容器脚本自身依赖的字段 ----
        body.put("agent_id", taskEntity.getSessionId());
        body.put("callback_url", taskEntity.getAiCallbackUrl());
        body.put("agent_name", taskEntity.getAgentName());
        // ---- camelCase：回调时 Jackson 反序列化 AgentTaskNotifyDTO 需要的字段 ----
        body.put("taskId", taskEntity.getTaskId());
        body.put("sessionId", taskEntity.getSessionId());
        body.put("agentName", taskEntity.getAgentName());
        body.put("callbackUrl", taskEntity.getCallbackUrl());
        body.put("aiCallbackUrl", taskEntity.getAiCallbackUrl());
        body.put("containerUrl", containerUrl);
        String url = containerUrl + "/gui/register";
        RequestBody requestBody = RequestBody.create(body.toJSONString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(requestBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = JSON.parseObject(response.body().string());
                return json.getBooleanValue("success");
            }
        } catch (IOException e) {
            log.error("[GuiApiClient] 注册失败: {}", e.getMessage());
        }
        return false;
    }

    // ================================================================
    //  GUI 操作方法
    // ================================================================

    /**
     * 鼠标移动
     */
    public void mouseMove(int step, int x, int y) {
        log.info("[GuiApiClient] mouseMove step={}, x={}, y={}", step, x, y);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("x", x);
        body.put("y", y);
        postAsync("/gui/mouse_move", body);
    }

    /**
     * 左键单击
     */
    public void leftClick(int step, int x, int y) {
        log.info("[GuiApiClient] leftClick step={}, x={}, y={}", step, x, y);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("x", x);
        body.put("y", y);
        postAsync("/gui/left_click", body);
    }

    /**
     * 右键单击
     */
    public void rightClick(int step, int x, int y) {
        log.info("[GuiApiClient] rightClick step={}, x={}, y={}", step, x, y);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("x", x);
        body.put("y", y);
        postAsync("/gui/right_click", body);
    }

    /**
     * 双击
     */
    public void doubleClick(int step, int x, int y) {
        log.info("[GuiApiClient] doubleClick step={}, x={}, y={}", step, x, y);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("x", x);
        body.put("y", y);
        postAsync("/gui/double_click", body);
    }

    /**
     * 三击
     */
    public void tripleClick(int step, int x, int y) {
        log.info("[GuiApiClient] tripleClick step={}, x={}, y={}", step, x, y);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("x", x);
        body.put("y", y);
        postAsync("/gui/triple_click", body);
    }

    /**
     * 中键单击
     */
    public void middleClick(int step, int x, int y) {
        log.info("[GuiApiClient] middleClick step={}, x={}, y={}", step, x, y);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("x", x);
        body.put("y", y);
        postAsync("/gui/middle_click", body);
    }

    /**
     * 拖动（从当前位置拖到目标坐标）
     */
    public void drag(int step, int x, int y) {
        log.info("[GuiApiClient] drag step={}, x={}, y={}", step, x, y);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("x", x);
        body.put("y", y);
        postAsync("/gui/drag", body);
    }

    /**
     * 输入文字
     */
    public void type(int step, String text) {
        log.info("[GuiApiClient] type step={}, text={}", step, text);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("text", text);
        postAsync("/gui/type", body);
    }

    /**
     * 按键
     */
    public void key(int step, List<String> keys) {
        log.info("[GuiApiClient] key step={}, keys={}", step, keys);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("keys", keys);
        postAsync("/gui/key", body);
    }

    /**
     * 按单个键
     */
    public void key(int step, String key) {
        key(step, List.of(key));
    }

    /**
     * 滚动
     */
    public void scroll(int step, int pixels) {
        log.info("[GuiApiClient] scroll step={}, pixels={}", step, pixels);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("pixels", pixels);
        postAsync("/gui/scroll", body);
    }

    /**
     * 打开应用程序
     */
    public void openApp(int step, String appName) {
        log.info("[GuiApiClient] openApp step={}, appName={}", step, appName);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("app_name", appName);
        postAsync("/gui/open_app", body);
    }

    /**
     * 等待
     */
    public void wait(int step, int seconds) {
        log.info("[GuiApiClient] wait step={}, seconds={}", step, seconds);
        JSONObject body = new JSONObject();
        body.put("step", step);
        body.put("seconds", seconds);
        postAsync("/gui/wait", body);
    }

    /**
     * 重置桌面
     */
    public void reset(int step) {
        log.info("[GuiApiClient] reset step={}", step);
        JSONObject body = new JSONObject();
        body.put("step", step);
        postAsync("/gui/reset", body);
    }

    // ================================================================
    //  系统接口
    // ================================================================

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        String url = containerUrl + "/health";
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = JSON.parseObject(response.body().string());
                return "healthy".equals(json.getString("status"));
            }
        } catch (IOException e) {
            log.error("[GuiApiClient] 健康检查失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 关闭容器
     */
    public void shutdown() {
        String url = containerUrl + "/shutdown";
        RequestBody requestBody = RequestBody.create("", MediaType.parse("application/json"));
        Request request = new Request.Builder().url(url).post(requestBody).build();
        try (Response response = httpClient.newCall(request).execute()) {
            log.info("[GuiApiClient] 已发送关闭指令");
        } catch (IOException e) {
            log.error("[GuiApiClient] 关闭失败: {}", e.getMessage());
        }
    }

    // ================================================================
    //  Getter
    // ================================================================

    public String getContainerUrl() {
        return containerUrl;
    }
}
