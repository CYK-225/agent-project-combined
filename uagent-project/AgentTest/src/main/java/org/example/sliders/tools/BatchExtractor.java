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
import java.util.stream.Collectors;

/**
 * 并发提取器 — 直接调用 DashScope API，并发处理多个 chunk 的数据提取。
 * <p>
 * 每个 chunk 独立发起一次 LLM 请求，通过虚拟线程并发执行。
 * 并发度由 semaphore 控制（默认 4），避免触发 DashScope 限流。
 */
@Slf4j
public class BatchExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final int concurrency;

    public BatchExtractor(String apiKey, String model, int concurrency) {
        this.apiKey = apiKey;
        this.model = model;
        this.concurrency = concurrency;
    }

    /**
     * 并发提取所有 chunk 的数据。
     *
     * @param schemaJson   Schema 定义 JSON
     * @param question     用户问题
     * @param chunks       chunk 列表 (chunkId -> content)
     * @return 所有提取结果，key = chunkId
     */
    public Map<String, List<Map<String, Object>>> batchExtract(
            String schemaJson, String question, Map<String, String> chunks) {

        Semaphore semaphore = new Semaphore(concurrency);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Map<String, Future<List<Map<String, Object>>>> futures = new LinkedHashMap<>();

        for (var entry : chunks.entrySet()) {
            String chunkId = entry.getKey();
            String content = entry.getValue();
            futures.put(chunkId, executor.submit(() -> {
                semaphore.acquire();
                try {
                    return extractSingle(schemaJson, question, chunkId, content);
                } finally {
                    semaphore.release();
                }
            }));
        }

        Map<String, List<Map<String, Object>>> results = new LinkedHashMap<>();
        for (var entry : futures.entrySet()) {
            try {
                List<Map<String, Object>> rows = entry.getValue().get(2, TimeUnit.MINUTES);
                if (rows != null && !rows.isEmpty()) {
                    results.put(entry.getKey(), rows);
                }
            } catch (Exception e) {
                log.warn("[BatchExtractor] chunk {} 提取失败: {}", entry.getKey(), e.getMessage());
            }
        }

        executor.shutdown();
        int totalRows = results.values().stream().mapToInt(List::size).sum();
        log.info("[BatchExtractor] 完成: {}/{} chunks 成功, 共 {} 行",
                results.size(), chunks.size(), totalRows);

        return results;
    }

    private List<Map<String, Object>> extractSingle(
            String schemaJson, String question, String chunkId, String content) {

        String prompt = """
                你是一个结构化数据提取器。根据以下 Schema 定义，从文档块中提取所有匹配的数据行。

                ## Schema 定义
                ```json
                %s
                ```

                ## 用户问题
                %s

                ## 文档块 (%s)
                ```
                %s
                ```

                ## 输出要求
                返回一个 JSON 数组，每个元素是一行提取数据（对象格式）。
                如果该块中没有匹配的数据，返回空数组 []。
                只输出 JSON，不要其他文字。
                """.formatted(schemaJson, question, chunkId,
                content.length() > 8000 ? content.substring(0, 8000) + "\n...(截断)" : content);

        try {
            String responseBody = callDashScope(prompt);
            // 提取 JSON 数组
            String json = extractJsonArray(responseBody);
            if (json == null || json.equals("[]")) {
                return Collections.emptyList();
            }
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[BatchExtractor] chunk {} LLM 调用失败: {}", chunkId, e.getMessage());
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
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new RuntimeException("DashScope HTTP " + resp.statusCode() + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
        }

        // 解析 OpenAI 格式响应
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
        // 去掉 markdown 代码块
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start >= 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        // 找到第一个 [ 和最后一个 ]
        int first = text.indexOf('[');
        int last = text.lastIndexOf(']');
        if (first >= 0 && last > first) {
            return text.substring(first, last + 1);
        }
        return null;
    }
}
