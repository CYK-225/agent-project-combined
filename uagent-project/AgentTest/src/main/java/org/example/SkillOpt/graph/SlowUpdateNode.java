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
import org.example.skillOpt.entity.SkillOptEpochEntity;
import org.example.skillOpt.service.SkillOptEpochService;

import java.util.*;

/**
 * Epoch 级: SlowUpdate — 纵向比较 epoch 间 skill 版本，更新 protectedRegions。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-slow-update", description = "Epoch级: 纵向比较 skill 版本，更新受保护区域")
public class SlowUpdateNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);

        log.info("[SlowUpdateNode] jobId={}, epoch={}", jobId, epoch);

        // 至少需要 2 个 epoch 才能做纵向比较
        if (epoch < 1) {
            log.info("[SlowUpdateNode] epoch < 1，跳过纵向比较");
            return Map.of();
        }

        // 加载历史 epoch 数据
        SkillOptEpochService epochService =
                ApplicationContextProvider.getBean(SkillOptEpochService.class);
        List<SkillOptEpochEntity> history = epochService.listByJobId(jobId);

        if (history.size() < 2) {
            return Map.of();
        }

        // 构建 prompt
        String prompt = buildSlowUpdatePrompt(history);
        String systemPrompt = "You are a longitudinal skill analyzer. Compare skill versions across epochs and identify consistently effective sections that should be protected from editing. Return only valid JSON.";

        SkillOptReasonerAgent.setContext(
                new SkillOptReasonerAgent.ReasonerContext(systemPrompt, prompt));

        try {
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
            String threadId = "skillopt-slow-update-" + epoch + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "SkillOptReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = response != null ? response.getTextContent() : "";

            List<String> protectedRegions = parseProtectedRegions(llmResponse);
            log.info("[SlowUpdateNode] 受保护区域: {}", protectedRegions);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("protectedRegions", protectedRegions);
            return output;
        } catch (Exception e) {
            log.warn("[SlowUpdateNode] LLM 调用失败: {}", e.getMessage());
            return Map.of();
        } finally {
            SkillOptReasonerAgent.clearContext();
        }
    }

    private String buildSlowUpdatePrompt(List<SkillOptEpochEntity> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Longitudinal Skill Comparison\n\n");
        sb.append("Compare skill versions across epochs:\n\n");

        for (SkillOptEpochEntity e : history) {
            sb.append("## Epoch ").append(e.getEpoch()).append("\n");
            sb.append("- Gate Accepted: ").append(e.getGateAccepted()).append("\n");
            sb.append("- Validation Score: ").append(e.getValidationScore()).append("\n");
            sb.append("- Train Hard/Soft: ").append(e.getTrainHardScore()).append("/")
                    .append(e.getTrainSoftScore()).append("\n\n");
        }

        sb.append("""
                Based on the longitudinal evidence:
                1. Identify sections that have been consistently effective (accepted gates, improved scores)
                2. These sections should be PROTECTED from future editing
                3. Return JSON: {"protected_sections": ["section1", "section2", ...]}
                """);
        return sb.toString();
    }

    private List<String> parseProtectedRegions(String llmResponse) {
        try {
            String cleaned = llmResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            var node = MAPPER.readTree(cleaned);
            var sections = node.path("protected_sections");
            if (sections.isArray()) {
                List<String> result = new ArrayList<>();
                for (var s : sections) {
                    result.add(s.asText());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("[SlowUpdateNode] 解析失败: {}", e.getMessage());
        }
        return List.of();
    }
}
