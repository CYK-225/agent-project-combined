package org.example.acl.apiClient;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Agent 中台 API 客户端
 * <p>
 * 封装与 Agent 中台的所有 HTTP 通信，供工具平台调用。
 * 工具平台通过此客户端调用 Agent。
 * </p>
 * 
 * <h3>使用示例：</h3>
 * <pre>
 * // 初始化（指向 Agent 中台地址）
 * AgentTaskApiClient client = new AgentTaskApiClient("http://localhost:8080");
 * 
 * // 1. 调用 Agent（首次创建，非首次拿到记忆）
 * boolean invoked = client.invokeAgent("hr-agent", "session_001", "task_001", 9301, "打开浏览器", "http://tool-platform/callback");
 * 
 * // 2. 查询状态
 * TaskStatus status = client.getTaskStatus("task_001");
 * 
 * // 3. 容器执行完成后，恢复任务
 * boolean resumed = client.resumeTask("task_001", "执行结果", true, "点击成功", null, null, 1);
 * </pre>
 * 
 * @author AgentScope-Team
 * @version 2.0
 */
@Slf4j
public class AgentTaskApiClient {

    private final String baseUrl;
    private final OkHttpClient httpClient;

    /**
     * 初始化客户端
     * 
     * @param baseUrl Agent 中台地址，例如 "http://localhost:8089"
     */
    public AgentTaskApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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
     * 统一响应
     */
    public static class ApiResponse {
        private final boolean success;
        private final String message;
        private final JSONObject data;

        public ApiResponse(boolean success, String message, JSONObject data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public JSONObject getData() { return data; }

        @Override
        public String toString() {
            return "ApiResponse{success=" + success +
                    ", message='" + message + '\'' +
                    ", data=" + data + '}';
        }
    }



    // ================================================================
    //  核心请求方法
    // ================================================================

    /**
     * 同步 POST 请求
     */
    private ApiResponse postSync(String path, JSONObject body) {
        String url = baseUrl + path;
        RequestBody requestBody = RequestBody.create(
                body.toJSONString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(requestBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() != null) {
                JSONObject json = JSON.parseObject(response.body().string());
                return new ApiResponse(
                        json.getBooleanValue("success"),
                        json.getString("message"),
                        json
                );
            }
        } catch (IOException e) {
            log.error("[AgentTaskApiClient] POST 请求失败 {}: {}", path, e.getMessage());
        }
        return new ApiResponse(false, "请求失败", null);
    }

    /**
     * 同步 GET 请求
     */
    private ApiResponse getSync(String path) {
        String url = baseUrl + path;
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() != null) {
                JSONObject json = JSON.parseObject(response.body().string());
                return new ApiResponse(
                        json.getBooleanValue("success"),
                        json.getString("message"),
                        json
                );
            }
        } catch (IOException e) {
            log.error("[AgentTaskApiClient] GET 请求失败 {}: {}", path, e.getMessage());
        }
        return new ApiResponse(false, "请求失败", null);
    }

    // ================================================================
    //  业务接口
    // ================================================================

    /**
     * 调用 Agent（创建或恢复会话）
     * <p>
     * 首次调用：创建新会话
     * 非首次调用：拿到历史记忆
     * </p>
     * 
     * @param agentName Agent 名称，例如 "hr-agent"
     * @param sessionId 会话 ID（用于记忆隔离，可选）
     * @param taskId 任务 ID（可选，不传则自动生成）
     * @param containerPort 容器端口号
     * @param instruction 指令内容
     * @param callbackUrl 工具平台回调地址（Agent 中台会向此地址发送日志）
     * @return 是否调用成功
     */
    public boolean invokeAgent(String agentName, String sessionId, String taskId, 
                               Integer containerPort, String instruction, String callbackUrl) {
        JSONObject body = new JSONObject();
        body.put("agentName", agentName);
        body.put("sessionId", sessionId);
        body.put("taskId", taskId);
        body.put("containerPort", containerPort);
        body.put("instruction", instruction);
        body.put("callbackUrl", callbackUrl);

        ApiResponse resp = postSync("/api/agent/invoke", body);
        log.info("[AgentTaskApiClient] invokeAgent: {}", resp);
        return resp.isSuccess();
    }

    /**
     * 恢复 Agent 任务
     * <p>
     * 容器执行完成后调用此接口，恢复 Agent 继续推理。
     * </p>
     * 
     * @param taskId 任务 ID
     * @param executionResult 容器执行结果（描述信息）
     * @param success 工具执行是否成功
     * @param result 成功时的结果描述
     * @param screenshot base64 截图（可选）
     * @param screenshotPath 宿主机截图路径（可选）
     * @param step 步骤编号
     * @return 是否恢复成功
     */
    public boolean resumeTask(String taskId, String executionResult, Boolean success,
                              String result, String screenshot, String screenshotPath, Integer step) {
        JSONObject body = new JSONObject();
        body.put("taskId", taskId);
        body.put("executionResult", executionResult);
        body.put("success", success);
        body.put("result", result);
        body.put("screenshot", screenshot);
        body.put("screenshotPath", screenshotPath);
        body.put("step", step);

        ApiResponse resp = postSync("/api/agent/resume", body);
        log.info("[AgentTaskApiClient] resumeTask: {}", resp);
        return resp.isSuccess();
    }


}
