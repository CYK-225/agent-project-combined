package org.example.agent.financeForecastAgent.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * @author zhilin
 * 2026.04.14
 * 开放平台接口测试类
 * 测试 queryPositionList 和 queryFoodTruckDataAnalysis 两个接口是否正常可用
 */
public class OpenPlatformApiTest {

    // ========== 测试环境配置 ==========
    private static final String BASE_URL = "https://api.youfantech.cn";
    // 使用 appId=1111 可绕过签名校验，方便开发自测
    private static final String APP_ID = "cli_79b4340a2e9142e";
    private static final String APP_SECRET = "7846f1ec3a2411f1ae0400163e0c5614";

    // ========== 接口路径 ==========
    private static final String PATH_POSITION_LIST = "/api/ai/agent/queryPositionList";
    private static final String PATH_DATA_ANALYSIS = "/api/ai/agent/queryFoodTruckDataAnalysis";

    public static void main(String[] args) throws Exception {
        System.out.println("===== 开放平台接口测试开始 =====\n");

        // 1. 测试获取点位列表
        System.out.println("测试 queryPositionList");
        System.out.println("-------------------------------------------");
        String positionResult = testQueryPositionList();
        System.out.println("返回结果：");
        System.out.println(formatJson(positionResult));
        System.out.println();

        // 2. 测试获取经营分析数据（使用指定参数）
        String testDate = "2026-02-04";
        int testPositionId = 3845;
        int testIntervalNo = 1; // 早餐
        System.out.println("测试 queryFoodTruckDataAnalysis");
        System.out.println("参数：useDate=" + testDate + ", positionId=" + testPositionId + ", intervalNo=" + testIntervalNo);
        System.out.println("-------------------------------------------");
        String analysisResult = testQueryFoodTruckDataAnalysis(testDate, testPositionId, testIntervalNo);
        System.out.println("返回结果：");
        System.out.println(formatJson(analysisResult));
        System.out.println();

        System.out.println("===== 测试完成 =====");
    }

    /**
     * 测试获取点位列表
     */
    private static String testQueryPositionList() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("appId", APP_ID);
        params.put("nonce_str", generateNonceStr());
        long timestamp = System.currentTimeMillis();
        String sign = sign(APP_SECRET, timestamp, params);
        params.put("time", String.valueOf(timestamp));
        params.put("sign", sign);

        return doPost(BASE_URL + PATH_POSITION_LIST, toJson(params));
    }

    /**
     * 测试获取经营分析数据
     */
    private static String testQueryFoodTruckDataAnalysis(String useDate, int positionId, int intervalNo) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("appId", APP_ID);
        params.put("nonce_str", generateNonceStr());
        long timestamp = System.currentTimeMillis();
        String sign = sign(APP_SECRET, timestamp, params);
        params.put("time", String.valueOf(timestamp));
        params.put("sign", sign);
        params.put("useDate", useDate);
        params.put("positionId", positionId);
        params.put("intervalNo", intervalNo);

        return doPost(BASE_URL + PATH_DATA_ANALYSIS, toJson(params));
    }

    // ========== 签名算法（与 ApiSignUtils 一致） ==========

    /**
     * 生成签名
     * 规则：secretKey + 参数字典序(key+value) + "_timestamp" + timestamp + secretKey → MD5 → 大写
     */
    private static String sign(String secretKey, long timestamp, Map<String, Object> params) throws Exception {
        StringBuilder message = new StringBuilder();
        message.append(secretKey);

        // 按 key 字典序排列，跳过下划线开头的参数
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (key.startsWith("_")) {
                continue;
            }
            message.append(key);
            message.append(params.get(key));
        }

        message.append("_timestamp").append(timestamp);
        message.append(secretKey);

        return md5(message.toString());
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

    // ========== HTTP 工具 ==========

    private static String doPost(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        // 连接超时：10秒，读取超时：30秒（开放平台接口可能需要较长时间处理）
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        System.out.println("HTTP状态码：" + responseCode);
        return response.toString();
    }

    // ========== 辅助方法 ==========

    private static String generateNonceStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 简易 JSON 拼接（不依赖外部库）
     */
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

    /**
     * 从点位列表返回结果中提取第一个点位的ID
     */
    private static Integer extractFirstPositionId(String json) {
        // 简易解析：找 "id": 数字
        int idx = json.indexOf("\"id\":");
        if (idx == -1) {
            return null;
        }
        int start = idx + 5;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 简易 JSON 格式化（缩进输出）
     */
    private static String formatJson(String json) {
        if (json == null || json.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                switch (c) {
                    case '{', '[' -> {
                        sb.append(c).append('\n');
                        indent++;
                        sb.append("  ".repeat(indent));
                    }
                    case '}', ']' -> {
                        sb.append('\n');
                        indent--;
                        sb.append("  ".repeat(indent)).append(c);
                    }
                    case ',' -> {
                        sb.append(c).append('\n');
                        sb.append("  ".repeat(indent));
                    }
                    case ':' -> sb.append(c).append(' ');
                    default -> {
                        if (!Character.isWhitespace(c)) {
                            sb.append(c);
                        }
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
