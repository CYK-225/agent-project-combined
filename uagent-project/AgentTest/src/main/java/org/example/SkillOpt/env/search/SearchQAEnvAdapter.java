package org.example.skillOpt.env.search;

import lombok.extern.slf4j.Slf4j;
import org.example.skillOpt.env.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索问答环境适配器 — 支持工具调用的示例。
 * <p>
 * 模拟一个需要搜索才能回答问题的场景：
 * <ul>
 *   <li>Agent 可以调用 search(query) 工具获取相关信息</li>
 *   <li>Agent 可以调用 lookup(topic) 工具获取详细信息</li>
 *   <li>评分基于最终答案的准确性</li>
 * </ul>
 * <p>
 * 用于演示 SkillOpt 如何优化带工具调用的 Agent skill。
 *
 * @author zhilin
 */
@Slf4j
public class SearchQAEnvAdapter implements EnvAdapter {

    /** 模拟知识库 */
    private final Map<String, String> knowledgeBase;

    /** 工具调用模式匹配 */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "(?:search|lookup|query)\\s*\\(\\s*['\"]?(.+?)['\"]?\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    /** 答案提取模式 */
    private static final Pattern ANSWER_PATTERN = Pattern.compile(
            "(?:Answer|答案|answer)[:\\s]+(.+)",
            Pattern.CASE_INSENSITIVE
    );

    public SearchQAEnvAdapter() {
        this.knowledgeBase = initKnowledgeBase();
    }

    @Override
    public String getAdapterType() {
        return "search-qa";
    }

    @Override
    public boolean supportsToolCalling() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> prepareTrainBatch(Object rawData, int batchSize) {
        List<Map<String, Object>> allItems = parseRawData(rawData);
        if (allItems.size() <= batchSize) return allItems;
        return allItems.subList(0, batchSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> prepareValBatch(Object rawData, int batchSize) {
        List<Map<String, Object>> allItems = parseRawData(rawData);
        int valSize = Math.max(1, batchSize / 2);
        if (allItems.size() <= valSize) return allItems;
        return allItems.subList(allItems.size() - valSize, allItems.size());
    }

    @Override
    public RolloutResult executeRollout(RolloutContext context) {
        // 单轮模式的简单实现
        return RolloutResult.builder()
                .variantIndex(context.getVariantIndex())
                .status("PENDING")
                .hardScore(0.0)
                .softScore(0.0)
                .trajectory("")
                .agentResponse("")
                .taskInstance(context.getTaskInstance())
                .build();
    }

    @Override
    public RolloutResult executeToolRollout(RolloutContext context, int maxSteps) {
        log.info("[SearchQA] 开始工具调用 rollout: variant={}, maxSteps={}",
                context.getVariantIndex(), maxSteps);

        // 这里会在 RolloutWorkerNode 中调用 Agent
        // Agent 会通过 Hook 记录轨迹
        return RolloutResult.builder()
                .variantIndex(context.getVariantIndex())
                .status("PENDING")
                .toolCallingUsed(true)
                .maxStepsReached(false)
                .taskInstance(context.getTaskInstance())
                .build();
    }

    @Override
    public double hardScore(RolloutResult result) {
        if (result == null) return 0.0;
        String answer = extractExpectedAnswer(result.getTaskInstance());
        String response = extractAnswerFromResponse(result);
        if (answer == null || answer.isBlank() || response == null || response.isBlank()) return 0.0;
        return response.equalsIgnoreCase(answer.trim()) ? 1.0 : 0.0;
    }

    @Override
    public double softScore(RolloutResult result) {
        if (result == null) return 0.0;
        String answer = extractExpectedAnswer(result.getTaskInstance());
        String response = extractAnswerFromResponse(result);
        if (answer == null || answer.isBlank() || response == null || response.isBlank()) return 0.0;

        Set<String> answerTokens = tokenize(answer);
        Set<String> responseTokens = tokenize(response);

        if (answerTokens.isEmpty() || responseTokens.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(answerTokens);
        intersection.retainAll(responseTokens);

        double precision = (double) intersection.size() / responseTokens.size();
        double recall = (double) intersection.size() / answerTokens.size();

        if (precision + recall == 0) return 0.0;
        return 2.0 * precision * recall / (precision + recall);
    }

    @Override
    public String buildRolloutPrompt(Map<String, Object> taskInstance, String skill) {
        String question = String.valueOf(taskInstance.getOrDefault("question", ""));
        StringBuilder sb = new StringBuilder();

        sb.append("# Task: Answer the following question using available tools\n\n");
        sb.append("## Question\n").append(question).append("\n\n");

        if (skill != null && !skill.isBlank()) {
            sb.append("## Skill Guidance\n");
            sb.append("Follow these guidelines when answering:\n\n");
            sb.append(skill).append("\n\n");
        }

        sb.append("## Instructions\n");
        sb.append("1. Analyze the question to determine what information you need\n");
        sb.append("2. Use the `query_environment` or `execute_action` tool to search for relevant information\n");
        sb.append("3. Analyze the search results\n");
        sb.append("4. When you have enough information, provide your final answer\n");
        sb.append("5. Format your final answer as: **Answer: <your answer>**\n\n");

        sb.append("## Example Tool Usage\n");
        sb.append("- To search: `execute_action(action='search for [topic]', reasoning='I need to find...')`\n");
        sb.append("- To query: `query_environment(query='What is [topic]?')`\n");

        return sb.toString();
    }

    // ==================== 工具相关方法 ====================

    @Override
    public List<String> getAvailableTools() {
        return List.of(
                "execute_action(action, reasoning) — Execute an action in the environment",
                "query_environment(query) — Query the environment for information"
        );
    }

    @Override
    public ToolAction parseToolAction(String agentOutput) {
        if (agentOutput == null) return null;

        Matcher matcher = TOOL_CALL_PATTERN.matcher(agentOutput);
        if (matcher.find()) {
            String query = matcher.group(1).trim();
            return ToolAction.builder()
                    .toolName("search")
                    .toolInput(query)
                    .rawOutput(agentOutput)
                    .build();
        }

        // 尝试解析 JSON 格式的工具调用
        if (agentOutput.contains("\"tool\"") || agentOutput.contains("\"action\"")) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = mapper.readTree(agentOutput);
                if (node.has("tool")) {
                    return ToolAction.builder()
                            .toolName(node.get("tool").asText())
                            .toolInput(node.has("input") ? node.get("input").asText() : "")
                            .rawOutput(agentOutput)
                            .build();
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    @Override
    public ToolFeedback executeToolAction(ToolAction action, Map<String, Object> context) {
        log.info("[SearchQA] 执行工具: tool={}, input={}", action.getToolName(), action.getToolInput());

        String toolName = action.getToolName().toLowerCase();
        String query = action.getToolInput();

        switch (toolName) {
            case "search":
            case "query":
                return executeSearch(query);
            case "lookup":
                return executeLookup(query);
            default:
                return ToolFeedback.builder()
                        .success(false)
                        .content("Unknown tool: " + toolName)
                        .build();
        }
    }

    // ==================== 工具实现 ====================

    private ToolFeedback executeSearch(String query) {
        log.info("[SearchQA] 搜索: {}", query);

        // 在知识库中搜索
        List<String> results = new ArrayList<>();
        String queryLower = query.toLowerCase();

        for (Map.Entry<String, String> entry : knowledgeBase.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue().toLowerCase();

            if (key.contains(queryLower) || value.contains(queryLower) ||
                    queryLower.contains(key) || containsAnyWord(queryLower, value)) {
                results.add(entry.getKey() + ": " + entry.getValue());
            }
        }

        if (results.isEmpty()) {
            return ToolFeedback.builder()
                    .success(true)
                    .content("No results found for: " + query)
                    .build();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Search Results\n\n");
        for (int i = 0; i < Math.min(results.size(), 5); i++) {
            sb.append(i + 1).append(". ").append(results.get(i)).append("\n");
        }

        return ToolFeedback.builder()
                .success(true)
                .content(sb.toString())
                .build();
    }

    private ToolFeedback executeLookup(String topic) {
        log.info("[SearchQA] 查询: {}", topic);

        String info = knowledgeBase.get(topic.toLowerCase());
        if (info == null) {
            // 模糊匹配
            for (Map.Entry<String, String> entry : knowledgeBase.entrySet()) {
                if (entry.getKey().toLowerCase().contains(topic.toLowerCase()) ||
                        topic.toLowerCase().contains(entry.getKey().toLowerCase())) {
                    info = entry.getValue();
                    break;
                }
            }
        }

        if (info == null) {
            return ToolFeedback.builder()
                    .success(true)
                    .content("No information found for: " + topic)
                    .build();
        }

        return ToolFeedback.builder()
                .success(true)
                .content("## " + topic + "\n\n" + info)
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 Agent 响应中提取最终答案文本。
     * 支持 **Answer: ...** 格式，回退到 getFinalAnswer()。
     */
    private String extractAnswerFromResponse(RolloutResult result) {
        String text = result.getFinalAnswer();
        if (text == null || text.isBlank()) {
            text = result.getAgentResponse();
        }
        if (text == null || text.isBlank()) return null;

        var matcher = java.util.regex.Pattern.compile(
                "\\*{0,2}Answer\\*{0,2}\\s*[:：]\\s*(.+?)(?:\\*{0,2})\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE)
                .matcher(text);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            extracted = extracted.replaceAll("\\*+$", "").trim();
            return extracted;
        }

        return text.trim();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRawData(Object rawData) {
        if (rawData instanceof List) {
            return (List<Map<String, Object>>) rawData;
        }
        if (rawData instanceof String json) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                log.warn("[SearchQA] JSON parse failed: {}", e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private String extractExpectedAnswer(Map<String, Object> taskInstance) {
        if (taskInstance == null) return null;
        Object answer = taskInstance.getOrDefault("answer",
                taskInstance.getOrDefault("expected_answer",
                        taskInstance.getOrDefault("gold", null)));
        return answer != null ? String.valueOf(answer) : null;
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String word : words) {
            if (!word.isBlank()) tokens.add(word);
        }
        return tokens;
    }

    private boolean containsAnyWord(String text, String words) {
        String[] wordArray = words.split("\\s+");
        for (String word : wordArray) {
            if (word.length() > 3 && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 初始化模拟知识库
     */
    private Map<String, String> initKnowledgeBase() {
        Map<String, String> kb = new HashMap<>();

        // 地理知识
        kb.put("france", "France is a country in Western Europe. Its capital is Paris.");
        kb.put("paris", "Paris is the capital and most populous city of France.");
        kb.put("london", "London is the capital and largest city of England and the United Kingdom.");
        kb.put("england", "England is a country that is part of the United Kingdom.");
        kb.put("united kingdom", "The United Kingdom is a sovereign country in northwestern Europe.");
        kb.put("germany", "Germany is a country in Central Europe. Its capital is Berlin.");
        kb.put("berlin", "Berlin is the capital and largest city of Germany.");
        kb.put("japan", "Japan is an island country in East Asia. Its capital is Tokyo.");
        kb.put("tokyo", "Tokyo is the capital and most populous prefecture of Japan.");
        kb.put("china", "China is a country in East Asia. Its capital is Beijing.");
        kb.put("beijing", "Beijing is the capital of the People's Republic of China.");

        // 海洋知识
        kb.put("pacific", "The Pacific Ocean is the largest and deepest ocean on Earth.");
        kb.put("atlantic", "The Atlantic Ocean is the second-largest ocean.");
        kb.put("indian", "The Indian Ocean is the third-largest ocean.");
        kb.put("arctic", "The Arctic Ocean is the smallest and shallowest ocean.");
        kb.put("largest ocean", "The Pacific Ocean is the largest ocean, covering more than 60 million square miles.");
        kb.put("smallest ocean", "The Arctic Ocean is the smallest ocean.");

        // 科学知识
        kb.put("water", "Water is a chemical compound with the formula H2O.");
        kb.put("h2o", "H2O is the chemical formula for water, consisting of two hydrogen atoms and one oxygen atom.");
        kb.put("sun", "The Sun is the star at the center of the Solar System.");
        kb.put("earth", "Earth is the third planet from the Sun.");
        kb.put("mars", "Mars is the fourth planet from the Sun.");
        kb.put("mercury", "Mercury is the smallest planet and closest to the Sun.");
        kb.put("venus", "Venus is the second planet from the Sun.");
        kb.put("jupiter", "Jupiter is the largest planet in the Solar System.");
        kb.put("saturn", "Saturn is the sixth planet from the Sun, known for its ring system.");

        // 数学知识
        kb.put("pi", "Pi (π) is approximately 3.14159.");
        kb.put("euler", "Euler's number (e) is approximately 2.71828.");
        kb.put("prime number", "A prime number is a natural number greater than 1 that has no positive divisors other than 1 and itself.");
        kb.put("fibonacci", "The Fibonacci sequence starts with 0 and 1, and each subsequent number is the sum of the previous two.");

        // 历史知识
        kb.put("shakespeare", "William Shakespeare was an English playwright and poet, born in 1564.");
        kb.put("romeo and juliet", "Romeo and Juliet is a tragedy written by William Shakespeare.");
        kb.put("hamlet", "Hamlet is a tragedy written by William Shakespeare.");
        kb.put("world war", "World War II lasted from 1939 to 1945.");
        kb.put("1939", "World War II began in 1939.");
        kb.put("1945", "World War II ended in 1945.");

        return kb;
    }
}
