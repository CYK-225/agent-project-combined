package org.example.sliders.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 并发协调器 — 直接调用 DashScope API，并发处理多个表的数据协调。
 * <p>
 * 每个表独立发起一次 LLM 请求，通过虚拟线程并发执行。
 */
@Slf4j
public class BatchReconciler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final int concurrency;

    public BatchReconciler(String apiKey, String model, int concurrency) {
        this.apiKey = apiKey;
        this.model = model;
        this.concurrency = concurrency;
    }

    /**
     * 并发协调所有表的数据。
     *
     * @param question   用户问题
     * @param tableData  表名 -> 该表的行数据 JSON
     * @return 协调结果，key = tableName
     */
    public Map<String, List<Map<String, Object>>> batchReconcile(
            String question, Map<String, String> tableData) {

        Semaphore semaphore = new Semaphore(concurrency);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Map<String, Future<List<Map<String, Object>>>> futures = new LinkedHashMap<>();

        for (var entry : tableData.entrySet()) {
            String tableName = entry.getKey();
            String rowsJson = entry.getValue();
            futures.put(tableName, executor.submit(() -> {
                semaphore.acquire();
                try {
                    return reconcileSingle(question, tableName, rowsJson);
                } finally {
                    semaphore.release();
                }
            }));
        }

        Map<String, List<Map<String, Object>>> results = new LinkedHashMap<>();
        for (var entry : futures.entrySet()) {
            try {
                List<Map<String, Object>> rows = entry.getValue().get(3, TimeUnit.MINUTES);
                if (rows != null && !rows.isEmpty()) {
                    results.put(entry.getKey(), rows);
                }
            } catch (Exception e) {
                log.warn("[BatchReconciler] 表 {} 协调失败: {}", entry.getKey(), e.getMessage());
            }
        }

        executor.shutdown();
        int totalRows = results.values().stream().mapToInt(List::size).sum();
        log.info("[BatchReconciler] 完成: {}/{} 表成功, 共 {} 行",
                results.size(), tableData.size(), totalRows);

        return results;
    }

    private List<Map<String, Object>> reconcileSingle(
            String question, String tableName, String rowsJson) {

        String prompt = """
                你是数据协调器。对以下表 "%s" 的多源提取数据进行去重、冲突解决、合并。

                ## 用户问题
                %s

                ## 原始提取数据
                %s

                ## 协调规则
                1. 同一实体在多个 chunk 中出现 → 保留置信度最高、信息最完整的版本
                2. 字段值冲突 → 选 confidence 更高的；相同则保留更详细的
                3. 信息互补 → 合并为一条完整记录
                4. 每个字段保留 source_attribution（来自哪些 chunk）

                ## 输出要求
                返回协调后的行数据 JSON 数组。每个元素是对象，包含表的所有字段。
                只输出 JSON 数组，不要其他文字。
                """.formatted(tableName, question,
                rowsJson.length() > 12000 ? rowsJson.substring(0, 12000) + "\n...(截断)" : rowsJson);

        try {
            String responseBody = callDashScope(prompt);
            String json = extractJsonArray(responseBody);
            if (json == null || json.equals("[]")) {
                return Collections.emptyList();
            }
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[BatchReconciler] 表 {} LLM 调用失败: {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String callDashScope(String userPrompt) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.1,
                "max_tokens", 4096
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DASHSCOPE_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(180))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new RuntimeException("DashScope HTTP " + resp.statusCode() + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
        }

        Map<String, Object> respMap = MAPPER.readValue(resp.body(), new TypeReference<>() {});
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("DashScope 返回空 choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private String extractJsonArray(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start >= 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        int first = text.indexOf('[');
        int last = text.lastIndexOf(']');
        if (first >= 0 && last > first) {
            return text.substring(first, last + 1);
        }
        return null;
    }
}
