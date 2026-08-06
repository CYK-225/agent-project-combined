package org.example.skillEvolver.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.agent.EvolverReasonerAgent;
import org.example.skillEvolver.config.ApplicationContextProvider;

import java.util.*;

/**
 * Trace 差异分析节点（LLM 驱动，移植自 SkillClaw session_judge.py）。
 * <p>
 * 双重职责：
 * 1. 多维评分：对每个 trial 打 4 维度分（移植自 session_judge.py）
 *    - task_completion (权重 0.55)
 *    - response_quality (权重 0.30)
 *    - efficiency (权重 0.05)
 *    - tool_usage (权重 0.10)
 * 2. 差异分析：对比成功/失败 trace，提炼改进建议
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-analyze", description = "LLM Trace 差异分析 + 多维评分：深度对比成功/失败 trial，提炼 skill 改进方向")
public class AnalyzeNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 评分+分析的 system prompt — 合并 SkillClaw session_judge.py 的评分维度
     * 和原 AnalyzeNode 的差异分析能力。
     */
    private static final String ANALYZE_SYSTEM_PROMPT = """
            You are a trial evaluator and analysis expert for SkillEvolver.

            You have two tasks:

            ## Task 1: Multi-Dimension Scoring

            Score EACH trial on a 0.0-1.0 scale for these dimensions:
            - **task_completion**: whether the trial's goal was completed (weight: 0.55)
            - **response_quality**: correctness, completeness, and clarity of the final outcome (weight: 0.30)
            - **efficiency**: whether the path avoided unnecessary retries / detours (weight: 0.05)
            - **tool_usage**: whether tool usage was appropriate and effective (weight: 0.10)

            Scoring guidelines:
            - 1.0 means clearly excellent on that dimension.
            - 0.5 means mixed / uncertain / partially successful.
            - 0.0 means clearly failed on that dimension.
            - Prefer the trajectory as ground truth.
            - Distinguish "missing evidence" from "clear failure". If evidence is weak, be conservative.
            - Do not heavily penalize framework/runtime startup noise unless it materially interferes.
            - If a trial passed all tests, task_completion should be >= 0.8.

            ## Task 2: Trace Difference Analysis

            Analyze the differences between successful and failed trials:
            1. **Success patterns** — what did successful trials do consistently?
            2. **Failure patterns** — what did failed trials miss or do wrong?
            3. **Key differences** — specific steps/decisions present in success but absent in failure
            4. **Improvement suggestions** — what should be added, modified, or removed in the skill

            ## Output format

            Return EXACTLY one JSON object (no markdown fences):

            ```
            {
              "trial_scores": [
                {
                  "variantIndex": 1,
                  "task_completion": 0.9,
                  "response_quality": 0.8,
                  "efficiency": 0.7,
                  "tool_usage": 0.85,
                  "overall_score": 0.86
                }
              ],
              "analysis_report": "## Analysis Report\\n\\n### Success Patterns\\n...\\n\\n### Failure Patterns\\n...\\n\\n### Key Differences\\n...\\n\\n### Improvement Suggestions\\n..."
            }
            ```

            The analysis_report field should be a detailed Markdown report with actionable suggestions.
            """;

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        List<Map<String, Object>> trialResults =
                (List<Map<String, Object>>) state.value("trialResults").orElse(List.of());
        String currentSkill = (String) state.value("currentSkill").orElse("");
        int iteration = (int) state.value("currentIteration").orElse(0);

        log.info("[AnalyzeNode] 通过 LLM 分析 {} 个 trial 结果 (迭代 {})", trialResults.size(), iteration);

        // 分离成功和失败
        List<Map<String, Object>> successes = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Map<String, Object> r : trialResults) {
            String status = (String) r.getOrDefault("status", "UNKNOWN");
            if ("PASSED".equals(status) || "COMPLETED".equals(status)) {
                successes.add(r);
            } else {
                failures.add(r);
            }
        }

        String taskPrompt = buildAnalysisPrompt(trialResults, successes, failures, currentSkill, iteration);

        // 调用 LLM
        AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);

        EvolverReasonerAgent.setContext(
                new EvolverReasonerAgent.ReasonerContext(ANALYZE_SYSTEM_PROMPT, taskPrompt));

        try {
            String threadId = "evolver-analyzer-" + iteration + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "EvolverReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = (response != null) ? response.getTextContent() : "";

            // --- 解析 JSON 响应 ---
            String report;
            List<Map<String, Object>> trialScores;
            try {
                JsonNode root = MAPPER.readTree(extractJsonObject(llmResponse));
                report = root.path("analysis_report").asText(llmResponse);
                trialScores = parseTrialScores(root.path("trial_scores"));
            } catch (Exception e) {
                log.warn("[AnalyzeNode] JSON 解析失败，使用原始响应作为分析报告: {}", e.getMessage());
                report = llmResponse;
                trialScores = Collections.emptyList();
            }

            log.info("[AnalyzeNode] LLM 分析报告长度={}, 评分维度={}", report.length(), trialScores.size());

            // 计算通过率
            double passRate = trialResults.isEmpty() ? 0.0 :
                    (double) successes.size() / trialResults.size();

            // 计算最佳奖励
            double bestReward = 0.0;
            for (Map<String, Object> s : successes) {
                Object reward = s.get("reward");
                if (reward instanceof Number) {
                    bestReward = Math.max(bestReward, ((Number) reward).doubleValue());
                }
            }

            // 构建成功/失败 trace 摘要 JSON
            String successTracesJson = buildTracesSummary(successes);
            String failureTracesJson = buildTracesSummary(failures);

            // 计算加权平均分数
            double avgOverallScore = trialScores.stream()
                    .mapToDouble(s -> (Double) s.getOrDefault("overall_score", 0.0))
                    .average().orElse(0.0);

            // 双信号取最大值：验证器分数 vs LLM 评分
            bestReward = Math.max(bestReward, avgOverallScore);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("analysisReport", report);
            output.put("trialPassRate", passRate);
            output.put("bestReward", bestReward);
            output.put("successCount", successes.size());
            output.put("failureCount", failures.size());
            output.put("successTracesJson", successTracesJson);
            output.put("failureTracesJson", failureTracesJson);
            output.put("trialScoresJson", MAPPER.writeValueAsString(trialScores));
            output.put("avgOverallScore", avgOverallScore);
            return output;

        } finally {
            EvolverReasonerAgent.clearContext();
        }
    }

    // ==================== Prompt 构建 ====================

    private String buildAnalysisPrompt(List<Map<String, Object>> allTrials,
                                        List<Map<String, Object>> successes,
                                        List<Map<String, Object>> failures,
                                        String currentSkill, int iteration) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Trial Analysis Task\n\n");
        sb.append("- **Iteration**: ").append(iteration).append("\n");
        sb.append("- **Total trials**: ").append(allTrials.size()).append("\n");
        sb.append("- **Passed**: ").append(successes.size()).append("\n");
        sb.append("- **Failed**: ").append(failures.size()).append("\n\n");

        // 成功 trial
        sb.append("## Successful Trials\n\n");
        for (Map<String, Object> s : successes) {
            appendTrialDetail(sb, s);
        }

        // 失败 trial
        sb.append("## Failed Trials\n\n");
        for (Map<String, Object> f : failures) {
            appendTrialDetail(sb, f);
        }

        // 当前 Skill
        if (currentSkill != null && !currentSkill.isBlank()) {
            sb.append("## Current SKILL.md\n\n");
            sb.append(truncate(currentSkill, 3000));
            sb.append("\n\n");
        }

        sb.append("---\n\nPlease score each trial AND provide the analysis report.\n");
        return sb.toString();
    }

    private void appendTrialDetail(StringBuilder sb, Map<String, Object> trial) {
        sb.append("### Variant ").append(trial.get("variantIndex")).append("\n");
        sb.append("- **Strategy**: ").append(trial.getOrDefault("strategyHint", "N/A")).append("\n");
        sb.append("- **Status**: ").append(trial.get("status")).append("\n");
        String trace = (String) trial.getOrDefault("traceMarkdown", "");
        if (!trace.isEmpty()) {
            sb.append("- **Trace**:\n```\n").append(truncate(trace, 2000)).append("\n```\n");
        }
        String reply = (String) trial.getOrDefault("agentResponse", "");
        if (!reply.isEmpty()) {
            sb.append("- **Agent Response**:\n").append(truncate(reply, 500)).append("\n");
        }
        sb.append("\n");
    }

    // ==================== JSON 解析 ====================

    private List<Map<String, Object>> parseTrialScores(JsonNode scoresNode) {
        List<Map<String, Object>> scores = new ArrayList<>();
        if (!scoresNode.isArray()) return scores;
        for (JsonNode node : scoresNode) {
            Map<String, Object> score = new LinkedHashMap<>();
            score.put("variantIndex", node.path("variantIndex").asInt(0));
            score.put("task_completion", node.path("task_completion").asDouble(0.0));
            score.put("response_quality", node.path("response_quality").asDouble(0.0));
            score.put("efficiency", node.path("efficiency").asDouble(0.0));
            score.put("tool_usage", node.path("tool_usage").asDouble(0.0));
            score.put("overall_score", node.path("overall_score").asDouble(0.0));
            scores.add(score);
        }
        return scores;
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) return "{}";
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    // ==================== 工具方法 ====================

    private String buildTracesSummary(List<Map<String, Object>> traces) {
        if (traces.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < traces.size(); i++) {
            Map<String, Object> t = traces.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"variantIndex\":").append(t.get("variantIndex"));
            sb.append(",\"strategyHint\":\"").append(escapeJson(t.getOrDefault("strategyHint", ""))).append("\"");
            sb.append(",\"status\":\"").append(t.getOrDefault("status", "")).append("\"");
            String reply = (String) t.getOrDefault("agentResponse", "");
            sb.append(",\"agentResponse\":\"").append(escapeJson(truncate(reply, 300))).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(Object val) {
        if (val == null) return "";
        return val.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...(截断)";
    }
}
