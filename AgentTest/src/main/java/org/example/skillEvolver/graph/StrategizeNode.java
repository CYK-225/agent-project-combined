package org.example.skillEvolver.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * 策略变体生成节点（LLM 驱动）。
 * <p>
 * 通过 EvolverReasonerAgent 调用 LLM，基于 currentSkill 和任务指令，
 * 生成 K 个差异化的策略变体。每个变体包含 strategyHint + skillMarkdown。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-strategize", description = "LLM 策略变体生成：基于 current skill 生成 K 个差异化策略")
public class StrategizeNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String currentSkill = (String) state.value("currentSkill").orElse("");
        String instruction = (String) state.value("taskInstruction").orElse("");
        int iteration = (int) state.value("currentIteration").orElse(0);
        int k = (int) state.value("nExploration").orElse(4);
        String analysisReport = (String) state.value("analysisReport").orElse("");
        Double bestReward = (Double) state.value("bestReward").orElse(0.0);
        Double trialPassRate = (Double) state.value("trialPassRate").orElse(0.0);

        log.info("[StrategizeNode] 迭代 {}: 通过 LLM 生成 {} 个策略变体, currentSkill 长度={}",
                iteration, k, currentSkill.length());

        String systemPrompt = """
                你是 SkillEvolver 的策略生成器。你的任务是为 Agent 生成多种差异化的执行策略变体，
                每个变体代表一种不同的 SKILL.md 编写方法论。
                
                策略之间必须有明确的差异，例如：
                - 不同的任务拆解方式（逐步分解 vs 整体规划 vs 错误预防优先）
                - 不同的代码组织风格（函数式 vs OOP vs 过程式）
                - 不同的验证策略（前置检查 vs 后置断言 vs 运行时校验）
                - 不同的复杂度权衡（简洁直观 vs 严格完备 vs 高性能）
                
                输出格式：严格输出 JSON 数组，不要任何其他文字。
                每个元素格式：{"variantIndex": 数字, "strategyHint": "策略名称 — 一句话描述", "skillMarkdown": "## 执行指导\\n1) ...\\n2) ..."}
                """;

        String taskPrompt = buildStrategyPrompt(instruction, currentSkill, iteration, k,
                analysisReport, bestReward, trialPassRate);

        // 调用 LLM
        AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
        EvolverReasonerAgent.setContext(
                new EvolverReasonerAgent.ReasonerContext(systemPrompt, taskPrompt));

        try {
            String threadId = "evolver-strategist-" + iteration + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "EvolverReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = (response != null) ? response.getTextContent() : "";

            log.info("[StrategizeNode] LLM 响应长度={}", llmResponse.length());

            // 解析 JSON
            List<Map<String, Object>> variants = parseVariants(llmResponse, k, instruction, currentSkill);

            log.info("[StrategizeNode] 解析出 {} 个策略变体", variants.size());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("strategyVariants", variants);
            output.put("currentIteration", iteration);
            return output;

        } finally {
            EvolverReasonerAgent.clearContext();
        }
    }

    private String buildStrategyPrompt(String instruction, String currentSkill, int iteration, int k,
                                        String analysisReport, double bestReward, double trialPassRate) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 策略生成任务\n\n");
        sb.append("- **任务指令**: ").append(instruction).append("\n");
        sb.append("- **需要的变体数量**: ").append(k).append("\n");
        sb.append("- **当前迭代**: ").append(iteration).append("\n\n");

        // 上一轮进化反馈（仅迭代 > 0 时注入）
        if (iteration > 0 && analysisReport != null && !analysisReport.isBlank()) {
            sb.append("## 上一轮进化反馈（迭代 ").append(iteration - 1).append("）\n\n");
            sb.append("- **通过率**: ").append(String.format("%.0f%%", trialPassRate * 100)).append("\n");
            sb.append("- **最佳奖励**: ").append(String.format("%.3f", bestReward)).append("\n");
            String truncated = analysisReport.length() > 800
                    ? analysisReport.substring(0, 800) + "..." : analysisReport;
            sb.append("- **分析摘要**: ").append(truncated).append("\n\n");
            sb.append("请根据以上反馈，避免重复上一轮失败的策略方向，重点改进失败点。\n\n");
        }

        if (currentSkill != null && !currentSkill.isBlank()) {
            sb.append("## 当前 SKILL.md（上一轮进化结果）\n\n");
            sb.append(currentSkill);
            sb.append("\n\n");
            sb.append("请基于这个 Skill 生成 ").append(k).append(" 个改进方向各异的策略变体。\n");
            sb.append("每个变体的 skillMarkdown 应该是对当前 Skill 的针对性修改。\n");
        } else {
            sb.append("这是第一轮，还没有基础 Skill。\n");
            sb.append("请生成 ").append(k).append(" 个从零开始的执行指导，每个代表不同的方法论。\n");
            sb.append("skillMarkdown 字段应该是具体的执行步骤指导（用 Markdown 格式），不是空字符串。\n");
        }

        sb.append("\n请严格输出 JSON 数组格式。\n");
        return sb.toString();
    }

    private List<Map<String, Object>> parseVariants(String llmResponse, int k, String instruction, String currentSkill) {
        // 提取 JSON 数组
        String json = extractJsonArray(llmResponse);

        if (json != null) {
            try {
                List<Map<String, Object>> parsed = MAPPER.readValue(json, new TypeReference<>() {});
                if (!parsed.isEmpty()) {
                    // 补全 variantIndex
                    for (int i = 0; i < parsed.size(); i++) {
                        parsed.get(i).putIfAbsent("variantIndex", i + 1);
                        // 确保 skillMarkdown 不是 null
                        parsed.get(i).putIfAbsent("skillMarkdown",
                                currentSkill != null ? currentSkill : "");
                    }
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("[StrategizeNode] JSON 解析失败, 使用 fallback: {}", e.getMessage());
            }
        }

        // Fallback: 硬编码策略
        log.warn("[StrategizeNode] LLM 输出无法解析, 使用硬编码 fallback 策略");
        return buildFallbackVariants(k, currentSkill);
    }

    private String extractJsonArray(String text) {
        if (text == null || text.isBlank()) return null;
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private List<Map<String, Object>> buildFallbackVariants(int k, String currentSkill) {
        String[] hints = {
                "策略 A — 逐步分解法：将任务拆解为明确的步骤，每步附带验证检查点",
                "策略 B — 示例驱动法：通过构建具体示例来推导通用规则",
                "策略 C — 错误预防法：先列出常见失败模式，再为每种失败写预防性指导",
                "策略 D — 模式匹配法：识别任务类型，匹配已知成功模式，适配具体约束"
        };
        List<Map<String, Object>> variants = new ArrayList<>();
        for (int i = 0; i < k && i < hints.length; i++) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("variantIndex", i + 1);
            v.put("strategyHint", hints[i]);
            v.put("skillMarkdown", currentSkill != null ? currentSkill : "");
            variants.add(v);
        }
        return variants;
    }
}
