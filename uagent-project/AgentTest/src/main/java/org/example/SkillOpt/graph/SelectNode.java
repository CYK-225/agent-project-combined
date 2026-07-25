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
import org.example.skillOpt.edit.SkillEdit;
import org.example.skillOpt.schedule.LRScheduler;
import org.example.skillOpt.schedule.LRSchedulerFactory;

import java.util.*;

/**
 * 阶段 4: Select — LR 调度器计算编辑预算，超出预算时 LLM 排名选择。
 * <p>
 * 类比深度学习中的梯度裁剪。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-select", description = "阶段4: Select — LR 调度 + 编辑预算裁剪")
public class SelectNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);
        int maxEpochs = (int) state.value("maxEpochs").orElse(5);
        int editBudgetBase = (int) state.value("editBudgetBase").orElse(5);
        String lrSchedulerType = (String) state.value("lrSchedulerType").orElse("cosine");

        List<SkillEdit> aggregatedEdits =
                (List<SkillEdit>) state.value("aggregatedEdits").orElse(List.of());

        // 创建 LR 调度器并计算预算
        LRScheduler scheduler = LRSchedulerFactory.create(lrSchedulerType);
        int editBudget = scheduler.computeEditBudget(epoch, maxEpochs, editBudgetBase);

        log.info("[SelectNode] jobId={}, epoch={}, scheduler={}, budget={}/{}, aggregated={}",
                jobId, epoch, scheduler.getName(), editBudget, editBudgetBase, aggregatedEdits.size());

        List<SkillEdit> selectedEdits;

        if (aggregatedEdits.isEmpty()) {
            selectedEdits = List.of();
        } else if (aggregatedEdits.size() <= editBudget) {
            // 在预算内，全部保留
            selectedEdits = aggregatedEdits;
            log.info("[SelectNode] 全部 {} 个编辑在预算内", selectedEdits.size());
        } else {
            // 超出预算，LLM 排名选择
            selectedEdits = rankAndSelect(aggregatedEdits, editBudget, epoch);
            log.info("[SelectNode] 裁剪 {} → {} 个编辑", aggregatedEdits.size(), selectedEdits.size());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("editBudget", editBudget);
        output.put("selectedEdits", selectedEdits);
        output.put("actualEditCount", selectedEdits.size());
        return output;
    }

    private List<SkillEdit> rankAndSelect(List<SkillEdit> edits, int budget, int epoch) {
        String prompt = buildRankPrompt(edits, budget);
        String systemPrompt = "You are a skill editor selecting the most impactful edits. Rank by importance. Return only valid JSON.";

        SkillOptReasonerAgent.setContext(
                new SkillOptReasonerAgent.ReasonerContext(systemPrompt, prompt));

        try {
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
            String threadId = "skillopt-select-" + epoch + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "SkillOptReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = response != null ? response.getTextContent() : "";

            // 解析排名
            return parseRankedEdits(llmResponse, edits, budget);
        } catch (Exception e) {
            log.warn("[SelectNode] LLM 排名失败，使用 priority 排序: {}", e.getMessage());
            // fallback: 按 priority 降序排列，取 top-budget
            return edits.stream()
                    .sorted((a, b) -> Double.compare(b.getPriority(), a.getPriority()))
                    .limit(budget)
                    .toList();
        } finally {
            SkillOptReasonerAgent.clearContext();
        }
    }

    private String buildRankPrompt(List<SkillEdit> edits, int budget) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Edit Selection Task\n\n");
        sb.append("You have ").append(edits.size()).append(" proposed edits but can only apply ").append(budget).append(".\n");
        sb.append("Rank them by importance (most impactful first).\n\n");

        sb.append("## Proposed Edits\n");
        for (int i = 0; i < edits.size(); i++) {
            SkillEdit e = edits.get(i);
            sb.append(String.format("%d. [%s] target='%s' priority=%.2f rationale=%s\n",
                    i, e.getType(), e.getTargetSection(), e.getPriority(),
                    truncate(e.getRationale(), 80)));
        }

        sb.append("\nReturn JSON: {\"selected_indices\": [").append("0,1,2...").append("]}\n");
        return sb.toString();
    }

    private List<SkillEdit> parseRankedEdits(String llmResponse, List<SkillEdit> allEdits, int budget) {
        try {
            String cleaned = llmResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            JsonNode node = MAPPER.readTree(cleaned);
            JsonNode indices = node.path("selected_indices");
            if (indices.isArray()) {
                List<SkillEdit> selected = new ArrayList<>();
                for (JsonNode idx : indices) {
                    int i = idx.asInt(-1);
                    if (i >= 0 && i < allEdits.size() && selected.size() < budget) {
                        selected.add(allEdits.get(i));
                    }
                }
                return selected;
            }
        } catch (Exception e) {
            log.warn("[SelectNode] 排名解析失败: {}", e.getMessage());
        }
        // fallback
        return allEdits.stream()
                .sorted((a, b) -> Double.compare(b.getPriority(), a.getPriority()))
                .limit(budget)
                .toList();
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
