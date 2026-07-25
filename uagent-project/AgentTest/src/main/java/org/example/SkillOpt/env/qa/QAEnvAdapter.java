package org.example.skillOpt.env.qa;

import lombok.extern.slf4j.Slf4j;
import org.example.skillOpt.env.EnvAdapter;
import org.example.skillOpt.env.RolloutContext;
import org.example.skillOpt.env.RolloutResult;
import org.example.skillOpt.env.ToolAction;
import org.example.skillOpt.env.ToolFeedback;

import java.util.*;

/**
 * LLM-based QA 示例适配器。
 * <p>
 * 适用于问答任务的 skill 优化：
 * - 训练/验证数据为 JSON 格式的 QA 对（question + answer 字段）
 * - Hard score: 精确匹配（EM）
 * - Soft score: 基于 F1 的部分信用
 *
 * @author zhilin
 */
@Slf4j
public class QAEnvAdapter implements EnvAdapter {

    @Override
    public String getAdapterType() {
        return "llm-qa";
    }

    @Override
    public boolean supportsToolCalling() {
        return true;
    }

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

        var matcher = java.util.regex.Pattern.compile(
                "(?:search|lookup|query)\\s*\\(\\s*['\"]?(.+?)['\"]?\\s*\\)",
                java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(agentOutput);
        if (matcher.find()) {
            String query = matcher.group(1).trim();
            return ToolAction.builder()
                    .toolName("query")
                    .toolInput(query)
                    .rawOutput(agentOutput)
                    .build();
        }
        return null;
    }

    @Override
    public ToolFeedback executeToolAction(ToolAction action, java.util.Map<String, Object> context) {
        log.info("[QAEnvAdapter] 执行工具: tool={}, input={}", action.getToolName(), action.getToolInput());

        String toolName = action.getToolName().toLowerCase();
        String input = action.getToolInput();

        // 对于 query 类工具，返回一个通用提示，引导 Agent 使用自身知识回答
        if ("query".equals(toolName) || "query_environment".equals(toolName)) {
            return ToolFeedback.builder()
                    .success(true)
                    .content("Environment query received for: " + input + "\n\n"
                            + "The environment has no external search capability for this adapter. "
                            + "Please use your internal knowledge to answer the question accurately. "
                            + "Provide your best answer based on what you know.")
                    .build();
        }

        // 对于 execute_action 类工具
        if ("execute_action".equals(toolName) || "action".equals(toolName)) {
            return ToolFeedback.builder()
                    .success(true)
                    .content("Action recorded: " + input + "\n\n"
                            + "Please proceed with your reasoning based on your knowledge.")
                    .build();
        }

        return ToolFeedback.builder()
                .success(false)
                .content("Unknown tool: " + toolName)
                .build();
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

        sb.append("# Task: Answer the following question\n\n");
        sb.append("## Question\n").append(question).append("\n\n");

        if (skill != null && !skill.isBlank()) {
            sb.append("## Skill Guidance\n");
            sb.append("Follow these guidelines when answering:\n\n");
            sb.append(skill).append("\n\n");
        }

        sb.append("## Instructions\n");
        sb.append("Provide a clear, concise answer. ");
        sb.append("If the skill guidance provides specific strategies, apply them.\n");
        sb.append("End your response with: **Answer: <your answer>**\n");

        return sb.toString();
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

        // 匹配 **Answer: ...** 或 Answer: ... 格式
        var matcher = java.util.regex.Pattern.compile(
                "\\*{0,2}Answer\\*{0,2}\\s*[:：]\\s*(.+?)(?:\\*{0,2})\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE)
                .matcher(text);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            // 去除末尾可能残留的 ** 或其他 markdown 标记
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
                log.warn("[QAEnvAdapter] JSON parse failed: {}", e.getMessage());
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
        // Simple whitespace tokenization, keep alphanumeric and common chars
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String word : words) {
            if (!word.isBlank()) tokens.add(word);
        }
        return tokens;
    }
}
