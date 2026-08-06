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
 * 阶段 2: Reflect — 将轨迹分 mini-batch，LLM 分析失败/成功轨迹，生成补丁。
 * <p>
 * 类比深度学习中的梯度计算。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-reflect", description = "阶段2: Reflect — mini-batch 分析，生成补丁")
public class ReflectNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);
        String currentSkill = (String) state.value("currentSkill").orElse("");
        String metaSkill = (String) state.value("metaSkill").orElse("");

        List<Map<String, Object>> passedResults =
                (List<Map<String, Object>>) state.value("passedResults").orElse(List.of());
        List<Map<String, Object>> failedResults =
                (List<Map<String, Object>>) state.value("failedResults").orElse(List.of());

        log.info("[ReflectNode] jobId={}, epoch={}, passed={}, failed={}",
                jobId, epoch, passedResults.size(), failedResults.size());

        List<SkillEdit> failurePatches = new ArrayList<>();
        List<SkillEdit> successPatches = new ArrayList<>();

        // Error analysis — 分析失败轨迹
        if (!failedResults.isEmpty()) {
            String failurePrompt = buildFailureAnalysisPrompt(currentSkill, failedResults, metaSkill);
            String failureResponse = callReasoner(failurePrompt, epoch);
            failurePatches.addAll(parseEdits(failureResponse));
            log.info("[ReflectNode] Failure analysis: {} patches generated", failurePatches.size());
        }

        // Success analysis — 分析成功轨迹
        if (!passedResults.isEmpty()) {
            String successPrompt = buildSuccessAnalysisPrompt(currentSkill, passedResults, metaSkill);
            String successResponse = callReasoner(successPrompt, epoch);
            successPatches.addAll(parseEdits(successResponse));
            log.info("[ReflectNode] Success analysis: {} patches generated", successPatches.size());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("failurePatches", failurePatches);
        output.put("successPatches", successPatches);
        return output;
    }

    private String buildFailureAnalysisPrompt(String skill, List<Map<String, Object>> failures, String metaSkill) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Failure Analysis Task\n\n");
        sb.append("You are a skill failure analyst. Analyze the FAILED trajectories below.\n");
        sb.append("Identify what guidance was missing or wrong in the skill, and produce specific patches.\n\n");

        if (!metaSkill.isBlank()) {
            sb.append("## Optimizer Memory (Meta-Skill)\n").append(metaSkill).append("\n\n");
        }

        sb.append("## Current Skill\n```markdown\n").append(skill).append("\n```\n\n");

        sb.append("## Failed Trajectories\n");
        for (int i = 0; i < failures.size(); i++) {
            Map<String, Object> r = failures.get(i);
            sb.append("### Failure ").append(i + 1).append("\n");
            sb.append("- Agent Response: ").append(truncate(String.valueOf(r.getOrDefault("agentResponse", "")), 500)).append("\n");
            sb.append("- Soft Score: ").append(r.getOrDefault("softScore", 0.0)).append("\n\n");
        }

        sb.append("""
                ## Output Format
                Return a JSON array of patches:
                ```json
                [
                  {
                    "type": "APPEND|INSERT_AFTER|REPLACE|DELETE",
                    "targetSection": "section name or line pattern",
                    "content": "new content (null for DELETE)",
                    "rationale": "why this edit is needed",
                    "priority": 0.8
                  }
                ]
                ```
                """);
        return sb.toString();
    }

    private String buildSuccessAnalysisPrompt(String skill, List<Map<String, Object>> successes, String metaSkill) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Success Analysis Task\n\n");
        sb.append("You are a skill success analyst. Analyze the SUCCEEDED trajectories below.\n");
        sb.append("Identify what guidance was effective and should be preserved or reinforced.\n\n");

        sb.append("## Current Skill\n```markdown\n").append(skill).append("\n```\n\n");

        sb.append("## Successful Trajectories\n");
        for (int i = 0; i < successes.size(); i++) {
            Map<String, Object> r = successes.get(i);
            sb.append("### Success ").append(i + 1).append("\n");
            sb.append("- Agent Response: ").append(truncate(String.valueOf(r.getOrDefault("agentResponse", "")), 500)).append("\n\n");
        }

        sb.append("""
                ## Output Format
                Return a JSON array of patches to REINFORCE effective patterns:
                ```json
                [
                  {
                    "type": "APPEND|REPLACE",
                    "targetSection": "section name",
                    "content": "reinforced content",
                    "rationale": "why this pattern should be preserved",
                    "priority": 0.5
                  }
                ]
                ```
                """);
        return sb.toString();
    }

    private String callReasoner(String taskPrompt, int epoch) {
        String systemPrompt = "You are a skill optimization analyst. Analyze trajectories and produce specific, actionable patches. Return only valid JSON.";

        SkillOptReasonerAgent.setContext(
                new SkillOptReasonerAgent.ReasonerContext(systemPrompt, taskPrompt));

        try {
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
            String threadId = "skillopt-reflect-" + epoch + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "SkillOptReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            return response != null ? response.getTextContent() : "[]";
        } catch (Exception e) {
            log.error("[ReflectNode] LLM 调用失败: {}", e.getMessage());
            return "[]";
        } finally {
            SkillOptReasonerAgent.clearContext();
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
                    SkillEdit edit = SkillEdit.builder()
                            .type(EditType.valueOf(node.path("type").asText("APPEND").toUpperCase()))
                            .targetSection(node.path("targetSection").asText(""))
                            .content(node.path("content").asText(null))
                            .rationale(node.path("rationale").asText(""))
                            .priority(node.path("priority").asDouble(0.5))
                            .build();
                    edits.add(edit);
                }
            }
        } catch (Exception e) {
            log.warn("[ReflectNode] 编辑解析失败: {}", e.getMessage());
        }
        return edits;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
