package org.example.skillOpt.graph;

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
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.agent.SkillOptReasonerAgent;
import org.example.skillOpt.edit.EditType;
import org.example.skillOpt.edit.SkillEdit;

import java.util.*;

/**
 * 阶段 3: Aggregate — 分层合并补丁（失败优先）。
 * <p>
 * 类比深度学习中的梯度累积。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-aggregate", description = "阶段3: Aggregate — 分层合并补丁")
public class AggregateNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);
        String currentSkill = (String) state.value("currentSkill").orElse("");

        List<SkillEdit> failurePatches = (List<SkillEdit>) state.value("failurePatches").orElse(List.of());
        List<SkillEdit> successPatches = (List<SkillEdit>) state.value("successPatches").orElse(List.of());

        log.info("[AggregateNode] jobId={}, epoch={}, failurePatches={}, successPatches={}",
                jobId, epoch, failurePatches.size(), successPatches.size());

        List<SkillEdit> aggregatedEdits;

        if (failurePatches.isEmpty() && successPatches.isEmpty()) {
            log.info("[AggregateNode] 无补丁需要合并");
            aggregatedEdits = List.of();
        } else if (failurePatches.size() + successPatches.size() <= 5) {
            // 少量补丁，直接合并：failures 优先
            aggregatedEdits = new ArrayList<>(failurePatches);
            aggregatedEdits.addAll(successPatches);
        } else {
            // 多量补丁，通过 LLM 分层合并
            aggregatedEdits = mergePatchesWithLLM(currentSkill, failurePatches, successPatches, epoch);
        }

        log.info("[AggregateNode] 合并后编辑数: {}", aggregatedEdits.size());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("aggregatedEdits", aggregatedEdits);
        return output;
    }

    private List<SkillEdit> mergePatchesWithLLM(String skill, List<SkillEdit> failures,
                                                  List<SkillEdit> successes, int epoch) {
        String prompt = buildMergePrompt(skill, failures, successes);
        String systemPrompt = "You are a skill patch aggregation engine. Merge overlapping patches, remove duplicates, and prioritize failure fixes. Return only valid JSON.";

        SkillOptReasonerAgent.setContext(
                new SkillOptReasonerAgent.ReasonerContext(systemPrompt, prompt));

        try {
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
            String threadId = "skillopt-aggregate-" + epoch + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "SkillOptReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = response != null ? response.getTextContent() : "[]";
            return parseEdits(llmResponse);
        } catch (Exception e) {
            log.error("[AggregateNode] LLM 合并失败，使用简单合并: {}", e.getMessage());
            List<SkillEdit> combined = new ArrayList<>(failures);
            combined.addAll(successes);
            return combined;
        } finally {
            SkillOptReasonerAgent.clearContext();
        }
    }

    private String buildMergePrompt(String skill, List<SkillEdit> failures, List<SkillEdit> successes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Patch Aggregation Task\n\n");
        sb.append("## Current Skill\n```markdown\n").append(skill).append("\n```\n\n");

        sb.append("## Failure Patches (HIGH PRIORITY)\n");
        sb.append("```json\n").append(editsToJson(failures)).append("\n```\n\n");

        sb.append("## Success Patches (SUPPLEMENTARY)\n");
        sb.append("```json\n").append(editsToJson(successes)).append("\n```\n\n");

        sb.append("""
                ## Instructions
                1. Merge overlapping/duplicate patches
                2. Failure patches have HIGHER priority than success patches
                3. If a failure patch and success patch conflict, keep the failure patch
                4. Return a deduplicated, prioritized JSON array of patches
                
                ## Output Format
                Return a JSON array:
                ```json
                [{"type":"APPEND","targetSection":"...","content":"...","rationale":"...","priority":0.9}]
                ```
                """);
        return sb.toString();
    }

    private String editsToJson(List<SkillEdit> edits) {
        try {
            return MAPPER.writeValueAsString(edits);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<SkillEdit> parseEdits(String llmResponse) {
        List<SkillEdit> edits = new ArrayList<>();
        try {
            String cleaned = llmResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            JsonNode array = MAPPER.readTree(cleaned);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    edits.add(SkillEdit.builder()
                            .type(EditType.valueOf(node.path("type").asText("APPEND").toUpperCase()))
                            .targetSection(node.path("targetSection").asText(""))
                            .content(node.path("content").asText(null))
                            .rationale(node.path("rationale").asText(""))
                            .priority(node.path("priority").asDouble(0.5))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[AggregateNode] 编辑解析失败: {}", e.getMessage());
        }
        return edits;
    }
}
