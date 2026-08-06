package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.agent.SkillOptReasonerAgent;

import java.util.*;

/**
 * Epoch 级: MetaSkill — 更新优化器侧记忆。
 * <p>
 * Meta-skill 不修改 skill 文档，但指导未来优化器决策。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-meta-skill", description = "Epoch级: 更新优化器侧记忆")
public class MetaSkillNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);
        String metaSkill = (String) state.value("metaSkill").orElse("");
        Boolean gateAccepted = (Boolean) state.value("gateAccepted").orElse(true);
        Integer editBudget = (Integer) state.value("editBudget").orElse(5);
        Integer actualEditCount = (Integer) state.value("actualEditCount").orElse(0);
        Double validationScore = (Double) state.value("validationScore").orElse(0.0);
        Double previousValidationScore = (Double) state.value("previousValidationScore").orElse(0.0);

        log.info("[MetaSkillNode] jobId={}, epoch={}, gateAccepted={}, scoreDelta={:.4f}",
                jobId, epoch, gateAccepted, validationScore - previousValidationScore);

        String prompt = buildMetaSkillPrompt(metaSkill, epoch, gateAccepted, editBudget,
                actualEditCount, validationScore, previousValidationScore);
        String systemPrompt = "You are a meta-skill optimizer. Update the optimizer's memory based on this epoch's results. This memory guides future optimization decisions but does NOT modify the skill document itself. Return only the updated meta-skill text.";

        SkillOptReasonerAgent.setContext(
                new SkillOptReasonerAgent.ReasonerContext(systemPrompt, prompt));

        try {
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
            String threadId = "skillopt-meta-" + epoch + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "SkillOptReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String updatedMetaSkill = response != null ? response.getTextContent() : metaSkill;

            log.info("[MetaSkillNode] meta-skill 已更新 ({} chars)", updatedMetaSkill.length());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("metaSkill", updatedMetaSkill);
            return output;
        } catch (Exception e) {
            log.warn("[MetaSkillNode] LLM 调用失败: {}", e.getMessage());
            return Map.of();
        } finally {
            SkillOptReasonerAgent.clearContext();
        }
    }

    private String buildMetaSkillPrompt(String currentMetaSkill, int epoch,
                                         Boolean gateAccepted, int editBudget, int actualEditCount,
                                         double validationScore, double previousValidationScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Meta-Skill Update Task\n\n");

        if (!currentMetaSkill.isBlank()) {
            sb.append("## Current Meta-Skill\n").append(currentMetaSkill).append("\n\n");
        } else {
            sb.append("## Current Meta-Skill\n(Empty — this is the first update)\n\n");
        }

        sb.append("## Epoch ").append(epoch).append(" Summary\n");
        sb.append("- Gate Accepted: ").append(gateAccepted).append("\n");
        sb.append("- Edit Budget: ").append(editBudget).append("\n");
        sb.append("- Actual Edits Applied: ").append(actualEditCount).append("\n");
        sb.append("- Validation Score: ").append(String.format("%.4f", validationScore)).append("\n");
        sb.append("- Previous Score: ").append(String.format("%.4f", previousValidationScore)).append("\n");
        sb.append("- Score Delta: ").append(String.format("%.4f", validationScore - previousValidationScore)).append("\n\n");

        sb.append("""
                Based on this epoch's results, update the meta-skill memory.
                This memory should capture patterns like:
                - What edit strategies worked or didn't work
                - How many edits tend to be effective
                - Which types of changes led to score improvements
                - Any recurring failure patterns
                
                Return ONLY the updated meta-skill text (plain text, not JSON).
                """);
        return sb.toString();
    }
}
