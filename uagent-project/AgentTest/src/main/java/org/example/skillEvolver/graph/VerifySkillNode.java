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
 * Skill 发布验证门控（移植自 SkillClaw skill_verifier.py）。
 * <p>
 * 在 UpdateSkillNode 合成候选 Skill 后、FinalizeNode 持久化之前运行。
 * 用 LLM 对候选 Skill 做 4 维度质量检查：
 * <ul>
 *   <li>grounded_in_evidence — 改动是否有 trial 证据支撑</li>
 *   <li>preserves_existing_value — 是否保留了有用的现有内容</li>
 *   <li>specificity_and_reusability — 是否足够具体且可复用</li>
 *   <li>safe_to_publish — 是否可以安全发布</li>
 * </ul>
 * 不通过则回滚到上一版本的 Skill。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-verify-skill", description = "Skill 发布门控：4 维度质量验证，不通过则回滚")
public class VerifySkillNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 验证 prompt — 移植自 SkillClaw skill_verifier.py _VERIFY_SKILL_SYSTEM
     */
    private static final String VERIFY_SYSTEM_PROMPT = """
            You are the final publication gate for SkillEvolver.

            You are given:
            - the candidate skill (newly generated)
            - the current skill (previous version)
            - the analysis report that motivated the change
            - trial evidence (successful and failed traces)

            Your job is NOT to improve the skill. Your job is only to decide whether this \
            candidate is safe and worthwhile to publish.

            Approve the candidate only if ALL of the following are true:
            - it is grounded in the provided evidence
            - it does not throw away useful existing environment-specific facts without evidence
            - it is specific and reusable rather than generic agent advice
            - it is coherent enough to be used immediately

            Reject the candidate if ANY of the following are true:
            - it is speculative or weakly supported by the evidence
            - it removes useful existing instructions, endpoints, ports, filenames, or payload details without justification
            - it mostly adds generic best practices instead of environment-specific knowledge
            - it is significantly longer than the current skill without proportional evidence gain
            - it rewrites the entire skill rather than making targeted edits

            ## Output format

            Return EXACTLY one JSON object (no markdown fences, no extra text):

            ```
            {
              "decision": "accept" or "reject",
              "score": <number in [0, 1]>,
              "reason": "<short explanation>",
              "checks": {
                "grounded_in_evidence": <number in [0, 1]>,
                "preserves_existing_value": <number in [0, 1]>,
                "specificity_and_reusability": <number in [0, 1]>,
                "safe_to_publish": <number in [0, 1]>
              }
            }
            ```
            """;

    /** 验证通过的最低分数阈值 */
    private static final double ACCEPT_THRESHOLD = 0.5;

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String candidateSkill = (String) state.value("currentSkill").orElse("");
        String previousSkill = (String) state.value("previousSkill").orElse("");
        String analysisReport = (String) state.value("analysisReport").orElse("");
        String successTracesJson = (String) state.value("successTracesJson").orElse("[]");
        String failureTracesJson = (String) state.value("failureTracesJson").orElse("[]");
        String updateAction = (String) state.value("updateAction").orElse("improve_skill");
        String editSummary = (String) state.value("editSummary").orElse("");
        int iteration = (int) state.value("currentIteration").orElse(0);

        // 如果 UpdateSkillNode 决定 skip，直接通过
        if ("skip".equals(updateAction)) {
            log.info("[VerifySkillNode] UpdateSkillNode 决定 SKIP，跳过验证");
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("verificationPassed", true);
            output.put("verificationScore", 1.0);
            output.put("verificationReason", "Skipped by UpdateSkillNode");
            return output;
        }

        // 如果没有上一版本（首轮），降低验证标准
        if (previousSkill == null || previousSkill.isBlank()) {
            log.info("[VerifySkillNode] 首轮迭代，无旧版可比，直接通过");
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("verificationPassed", true);
            output.put("verificationScore", 0.8);
            output.put("verificationReason", "First iteration, no previous version to compare");
            return output;
        }

        log.info("[VerifySkillNode] 验证候选 Skill v{} (候选={}, 旧版={})",
                iteration, candidateSkill.length(), previousSkill.length());

        // 构建验证 prompt
        String taskPrompt = buildVerifyPrompt(candidateSkill, previousSkill, analysisReport,
                editSummary, successTracesJson, failureTracesJson);

        // 调用 LLM
        AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
        EvolverReasonerAgent.setContext(
                new EvolverReasonerAgent.ReasonerContext(VERIFY_SYSTEM_PROMPT, taskPrompt));

        try {
            String threadId = "evolver-verifier-" + iteration + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "EvolverReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = (response != null) ? response.getTextContent() : "";

            // 解析结果
            JsonNode root;
            try {
                root = MAPPER.readTree(extractJsonObject(llmResponse));
            } catch (Exception e) {
                log.warn("[VerifySkillNode] JSON 解析失败，默认通过: {}", e.getMessage());
                root = MAPPER.readTree("{\"decision\":\"accept\",\"score\":0.6,\"reason\":\"JSON parse failed, defaulting to accept\"}");
            }

            String decision = root.path("decision").asText("accept");
            double score = root.path("score").asDouble(0.0);
            String reason = root.path("reason").asText("");
            JsonNode checks = root.path("checks");

            boolean passed = "accept".equals(decision) && score >= ACCEPT_THRESHOLD;

            log.info("[VerifySkillNode] 验证结果: decision={}, score={}, passed={}", decision, score, passed);
            if (checks.isObject()) {
                log.info("[VerifySkillNode] 检查维度: evidence={}, preserve={}, specificity={}, safe={}",
                        checks.path("grounded_in_evidence").asDouble(),
                        checks.path("preserves_existing_value").asDouble(),
                        checks.path("specificity_and_reusability").asDouble(),
                        checks.path("safe_to_publish").asDouble());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("verificationPassed", passed);
            output.put("verificationScore", score);
            output.put("verificationReason", reason);
            output.put("verificationChecks", checks.toString());

            if (!passed) {
                log.info("[VerifySkillNode] 验证未通过，回滚到旧版 Skill");
                output.put("currentSkill", previousSkill);  // 回滚
                output.put("updateAction", "rollback");
            }

            return output;

        } finally {
            EvolverReasonerAgent.clearContext();
        }
    }

    // ==================== Prompt 构建 ====================

    private String buildVerifyPrompt(String candidate, String current, String analysis,
                                      String editSummary, String successTraces, String failureTraces) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Skill Verification Task\n\n");

        sb.append("## Edit Summary\n").append(editSummary).append("\n\n");

        sb.append("## Current Skill (previous version)\n");
        sb.append("```markdown\n").append(truncate(current, 4000)).append("\n```\n\n");

        sb.append("## Candidate Skill (proposed update)\n");
        sb.append("```markdown\n").append(truncate(candidate, 8000)).append("\n```\n\n");

        sb.append("## Analysis Report\n").append(truncate(analysis, 3000)).append("\n\n");

        sb.append("## Trial Evidence\n");
        sb.append("### Successful Traces\n").append(truncate(successTraces, 2000)).append("\n\n");
        sb.append("### Failed Traces\n").append(truncate(failureTraces, 2000)).append("\n\n");

        sb.append("---\n\nDecide: accept or reject this candidate?\n");
        return sb.toString();
    }

    // ==================== 工具方法 ====================

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

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...(截断)";
    }
}
