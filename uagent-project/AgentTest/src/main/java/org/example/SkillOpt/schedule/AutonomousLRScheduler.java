package org.example.skillOpt.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.skillOpt.agent.SkillOptReasonerAgent;

import java.util.List;

/**
 * LLM 自主决定编辑预算的调度器。
 * <p>
 * 构建 prompt 包含 epoch 历史和分数趋势，LLM 返回 JSON 指定 editBudget。
 * 如果 LLM 解析失败，fallback 到 cosine schedule。
 *
 * @author zhilin
 */
@Slf4j
public class AutonomousLRScheduler implements LRScheduler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CosineLRScheduler fallback = new CosineLRScheduler();

    @Override
    public int computeEditBudget(int currentEpoch, int maxEpochs, int baseBudget) {
        try {
            String prompt = buildAutonomousPrompt(currentEpoch, maxEpochs, baseBudget);
            String response = callLLM(prompt);
            return parseBudget(response, currentEpoch, maxEpochs, baseBudget);
        } catch (Exception e) {
            log.warn("[AutonomousLR] LLM 调用/解析失败，fallback 到 cosine: {}", e.getMessage());
            return fallback.computeEditBudget(currentEpoch, maxEpochs, baseBudget);
        }
    }

    @Override
    public String getName() {
        return "autonomous";
    }

    private String buildAutonomousPrompt(int currentEpoch, int maxEpochs, int baseBudget) {
        return """
                You are an autonomous learning rate scheduler for a skill optimization system.

                The system optimizes a skill document (markdown) across multiple epochs.
                Each epoch, a set of edits is proposed and applied to the skill.
                Your job: decide how many edits to allow this epoch.

                Current state:
                - Epoch: %d / %d
                - Base edit budget (max): %d
                - Progress: %.1f%%

                Guidelines:
                - Early epochs: allow more edits (exploration)
                - Later epochs: fewer, more targeted edits (exploitation)
                - If progress > 80%%, be very conservative (1-2 edits)
                - Return a JSON: {"edit_budget": <number>, "reasoning": "<brief explanation>"}

                Return ONLY the JSON object, no markdown fences.
                """.formatted(currentEpoch, maxEpochs, baseBudget,
                100.0 * currentEpoch / Math.max(1, maxEpochs));
    }

    private String callLLM(String prompt) throws Exception {
        // 使用 SkillOptReasonerAgent 的 ThreadLocal 上下文模式
        String systemPrompt = "You are an autonomous learning rate scheduler. Return only valid JSON.";
        SkillOptReasonerAgent.setContext(
                new SkillOptReasonerAgent.ReasonerContext(systemPrompt, prompt));

        try {
            AgentPoolManager poolManager = org.example.skillEvolver.config.ApplicationContextProvider
                    .getBean(AgentPoolManager.class);
            String threadId = "skillopt-lr-autonomous-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "SkillOptReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            return response != null ? response.getTextContent() : "";
        } finally {
            SkillOptReasonerAgent.clearContext();
        }
    }

    private int parseBudget(String response, int currentEpoch, int maxEpochs, int baseBudget) {
        try {
            String cleaned = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            JsonNode node = MAPPER.readTree(cleaned);
            int budget = node.path("edit_budget").asInt(-1);
            if (budget > 0 && budget <= baseBudget * 2) {
                log.info("[AutonomousLR] Epoch {}/{}: LLM 决定 editBudget={}, reasoning={}",
                        currentEpoch, maxEpochs, budget, node.path("reasoning").asText(""));
                return budget;
            }
        } catch (Exception e) {
            log.warn("[AutonomousLR] JSON 解析失败: {}", e.getMessage());
        }
        return fallback.computeEditBudget(currentEpoch, maxEpochs, baseBudget);
    }
}
