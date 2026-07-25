package org.example.agent.financeForecastAgent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 开放平台接口工具类
 * 提供两个 @Tool 方法供 LLM 调用：
 * 1. QueryPositionList — 查询所有点位列表
 * 2. QueryFoodTruckDataAnalysis — 查询经营分析数据
 * author: zhilin
 * 2026.04.14
 */
@Component
@Scope("prototype")
@Slf4j
public class FinanceApiTools {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=UTF-8");

    @Value("${open-platform.base-url:https://api.youfantech.cn}")
    private String baseUrl;

    @Value("${open-platform.app-id:cli_79b4340a2e9142e}")
    private String appId;

    @Value("${open-platform.app-secret:7846f1ec3a2411f1ae0400163e0c5614}")
    private String appSecret;

    @Value("${open-platform.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${open-platform.read-timeout:30000}")
    private int readTimeout;

    private volatile OkHttpClient httpClient;

    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = new OkHttpClient.Builder()
                            .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                            .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                            .build();
                }
            }
        }
        return httpClient;
    }

    /**
     * 查询所有点位（客户）列表
     * 返回每个点位的ID、名称及支持的餐段列表
     */
    @Tool(
            description = "查询所有点位（客户）列表。返回每个点位的ID、名称及支持的餐段列表。用于根据客户名称查找对应的positionId。",
            name = "QueryPositionList"
    )
    public String queryPositionList() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("appId", appId);
            params.put("nonce_str", generateNonceStr());

            long timestamp = System.currentTimeMillis();
            String sign = sign(appSecret, timestamp, params);

            params.put("time", String.valueOf(timestamp));
            params.put("sign", sign);

            String jsonBody = toJson(params);
            return doPost("/api/ai/agent/queryPositionList", jsonBody);
        } catch (Exception e) {
            log.error("调用 queryPositionList 接口失败", e);
            return "接口调用失败: " + e.getMessage();
        }
    }

    /**
     * 查询指定日期、点位、餐段的经营分析数据
     */
    @Tool(
            description = "查询指定日期、点位、餐段的经营分析数据，包括利润、客流、食材消耗、菜品分析、商家结算等全量数据。调用前需先通过 QueryPositionList 获取 positionId。",
            name = "QueryFoodTruckDataAnalysis"
    )
    public String queryFoodTruckDataAnalysis(
            @ToolParam(description = "查询日期，格式yyyy-MM-dd，如2025-02-20", required = true, name = "useDate") String useDate,
            @ToolParam(description = "点位ID，通过 QueryPositionList 获取", required = true, name = "positionId") int positionId,
            @ToolParam(description = "餐段编号：1=早餐，2=午餐，3=晚餐", required = true, name = "intervalNo") int intervalNo
    ) {
        try {
            long timestamp = System.currentTimeMillis();
            
            // 构建参与签名的参数（仅公共参数，与 OpenPlatformApiTest 保持一致）
            Map<String, Object> signParams = new LinkedHashMap<>();
            signParams.put("appId", appId);
            signParams.put("nonce_str", generateNonceStr());

            // 计算签名
            String sign = sign(appSecret, timestamp, signParams);

            // 构建完整请求参数
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("appId", appId);
            params.put("nonce_str", signParams.get("nonce_str"));
            params.put("time", String.valueOf(timestamp));
            params.put("sign", sign);
            params.put("useDate", useDate);
            params.put("positionId", positionId);
            params.put("intervalNo", intervalNo);

            String jsonBody = toJson(params);
            return doPost("/api/ai/agent/queryFoodTruckDataAnalysis", jsonBody);
        } catch (Exception e) {
            log.error("调用 queryFoodTruckDataAnalysis 接口失败", e);
            return "接口调用失败: " + e.getMessage();
        }
    }

    // ========== HTTP 请求 ==========

    private String doPost(String path, String jsonBody) throws IOException {
        String url = baseUrl + path;
        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("接口返回非200状态码: {} , url: {}, body: {}", response.code(), url, respBody);
                return "接口返回错误，HTTP状态码: " + response.code() + "，响应: " + respBody;
            }
            return respBody;
        }
    }

    // ========== 签名算法（与 ApiSignUtils 一致） ==========

    /**
     * 生成签名
     * 规则：secretKey + 参数字典序(key+value) + "_timestamp" + timestamp + secretKey → MD5 → 大写
     */
    private static String sign(String secretKey, long timestamp, Map<String, Object> params) throws Exception {
        StringBuilder message = new StringBuilder();
        message.append(secretKey);

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        
        // 调试日志：打印参与签名的参数
        if (log.isDebugEnabled()) {
            log.debug("签名参数（字典序）: {}", keys);
        }
        
        for (String key : keys) {
            if (key.startsWith("_")) {
                continue;
            }
            Object value = params.get(key);
            // 确保值是字符串形式
            String valueStr = value != null ? String.valueOf(value) : "";
            message.append(key).append(valueStr);
            
            if (log.isDebugEnabled()) {
                log.debug("  参数: {} = {}", key, valueStr);
            }
        }

        message.append("_timestamp").append(timestamp);
        message.append(secretKey);

        String signInput = message.toString();
        if (log.isDebugEnabled()) {
            log.debug("签名字符串: {}", signInput);
        }

        return md5(signInput);
    }

    private static String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                sb.append("0");
            }
            sb.append(hex);
        }
        return sb.toString().toUpperCase();
    }

    // ========== 辅助方法 ==========

    private static String generateNonceStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof Number) {
                sb.append(val);
            } else {
                sb.append("\"").append(val).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
