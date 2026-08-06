package org.example.skillEvolver.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Trace 分析工具 — 对比成功 vs 失败 trial，提炼 skill 需要补充的指导。
 * <p>
 * 核心方法论来自 SkillEvolver 论文的 Analyze Traces 阶段：
 * 找出成功 trace 有但失败 trace 缺失的关键步骤，生成结构化分析报告。
 *
 * @author zhilin
 */
@Slf4j
public class TraceAnalyzerTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public TraceAnalyzerTools() {
        // 纯分析工具，无外部依赖
    }

    @Tool(
            name = "analyze_trial_traces",
            description = "对比成功和失败的 trial trace，生成结构化差异分析报告。"
                    + "输入成功 trace 列表和失败 trace 列表，输出：\n"
                    + "1. 成功路径的共同模式（成功 trace 都做了什么）\n"
                    + "2. 失败路径的共同模式（失败 trace 都缺了什么）\n"
                    + "3. 关键差异点（成功有但失败没有的步骤/决策）\n"
                    + "4. Skill 改进建议（应该加入或修改哪些指导）\n"
                    + "此工具不调用 LLM，只做结构化对比整理，Agent 自行推理差异根因。"
    )
    public String analyzeTrialTraces(
            @ToolParam(name = "successTraces",
                    description = "成功 trace 数组，每个元素包含 {label, steps, reward}。"
                            + "steps 是该 trace 的关键操作序列（字符串数组）") String successTracesJson,
            @ToolParam(name = "failureTraces",
                    description = "失败 trace 数组，格式同上") String failureTracesJson,
            @ToolParam(name = "currentSkill",
                    description = "当前 SKILL.md 内容，用于比对已有指导是否覆盖了关键步骤") String currentSkill
    ) {
        try {
            List<Map<String, Object>> successes = MAPPER.readValue(successTracesJson, List.class);
            List<Map<String, Object>> failures = MAPPER.readValue(failureTracesJson, List.class);

            StringBuilder report = new StringBuilder();
            report.append("## Trace 差异分析报告\n\n");

            // --- 1. 概览 ---
            report.append("### 概览\n");
            report.append("- 成功 trace: ").append(successes.size()).append(" 条\n");
            report.append("- 失败 trace: ").append(failures.size()).append(" 条\n\n");

            // --- 2. 成功路径共同模式 ---
            report.append("### 成功路径共同模式\n");
            if (successes.isEmpty()) {
                report.append("⚠️ 没有成功的 trace！当前策略全面失败，需要大幅调整 skill。\n");
            } else {
                Map<String, Integer> stepFreq = new LinkedHashMap<>();
                for (Map<String, Object> trace : successes) {
                    List<String> steps = (List<String>) trace.getOrDefault("steps", List.of());
                    for (String step : steps) {
                        String key = step.trim();
                        stepFreq.merge(key, 1, Integer::sum);
                    }
                }
                int totalSuccess = successes.size();
                report.append("成功 trace 中高频步骤（出现在 ≥50% trace 中）：\n\n");
                stepFreq.entrySet().stream()
                        .filter(e -> e.getValue() >= totalSuccess * 0.5)
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(e -> report.append("- [").append(e.getValue()).append("/").append(totalSuccess)
                                .append("] ").append(e.getKey()).append("\n"));
                report.append("\n");
            }

            // --- 3. 失败路径共同模式 ---
            report.append("### 失败路径共同模式\n");
            if (failures.isEmpty()) {
                report.append("✅ 没有失败的 trace！当前策略已接近完美，微调即可。\n");
            } else {
                Map<String, Integer> failStepFreq = new LinkedHashMap<>();
                for (Map<String, Object> trace : failures) {
                    List<String> steps = (List<String>) trace.getOrDefault("steps", List.of());
                    for (String step : steps) {
                        String key = step.trim();
                        failStepFreq.merge(key, 1, Integer::sum);
                    }
                }
                int totalFail = failures.size();
                report.append("失败 trace 中高频步骤（可能代表走了弯路或遗漏）：\n\n");
                failStepFreq.entrySet().stream()
                        .filter(e -> e.getValue() >= totalFail * 0.5)
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(e -> report.append("- [").append(e.getValue()).append("/").append(totalFail)
                                .append("] ").append(e.getKey()).append("\n"));

                // 失败 trace 的奖励分布
                report.append("\n失败 trace 奖励分布：\n");
                for (Map<String, Object> trace : failures) {
                    String label = (String) trace.getOrDefault("label", "unnamed");
                    Object reward = trace.getOrDefault("reward", "N/A");
                    report.append("- ").append(label).append(": reward=").append(reward).append("\n");
                }
                report.append("\n");
            }

            // --- 4. 关键差异 ---
            report.append("### 关键差异点（成功有，失败缺）\n");
            report.append("请 Agent 基于以上数据推理：\n");
            report.append("1. 成功 trace 中哪些关键决策是失败 trace 没有做出的？\n");
            report.append("2. 失败 trace 是否走了多余的弯路？哪些步骤应该跳过？\n");
            report.append("3. 当前 Skill 中是否已有相关指导？如果缺失，这正是需要补充的 Δ。\n\n");

            // --- 5. 当前 Skill 覆盖度分析 ---
            if (currentSkill != null && !currentSkill.isBlank()) {
                report.append("### 当前 Skill 覆盖度\n");
                report.append("当前 Skill 长度: ").append(currentSkill.length()).append(" 字符\n\n");
                report.append("请 Agent 检查当前 Skill 是否已覆盖成功路径中的关键步骤，"
                        + "以及是否有不必要的指导导致了失败路径的弯路。\n");
            }

            return report.toString();

        } catch (Exception e) {
            log.error("[TraceAnalyzer] 解析 trace 数据失败", e);
            return "❌ Trace 分析失败: " + e.getMessage()
                    + "\n请确认 successTraces 和 failureTraces 是合法的 JSON 数组。";
        }
    }

    @Tool(
            name = "build_trace_summary",
            description = "将单个 trial 的执行记录构建为标准 trace 格式。"
                    + "用于在 trial 完成后快速生成 trace 摘要，供 analyze_trial_traces 消费。"
    )
    public String buildTraceSummary(
            @ToolParam(name = "label", description = "trace 标签（如 'trial-1-pass', 'trial-3-fail'）") String label,
            @ToolParam(name = "passed", description = "是否通过验证（true/false）") boolean passed,
            @ToolParam(name = "reward", description = "奖励分数（0.0~1.0）") double reward,
            @ToolParam(name = "keySteps", description = "关键操作序列（JSON 字符串数组）") String keyStepsJson,
            @ToolParam(name = "errorMessage", description = "失败原因（仅失败时填写，成功填空字符串）") String errorMessage
    ) {
        try {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("label", label);
            trace.put("passed", passed);
            trace.put("reward", reward);
            trace.put("steps", MAPPER.readValue(keyStepsJson, List.class));
            if (errorMessage != null && !errorMessage.isBlank()) {
                trace.put("error", errorMessage);
            }
            return MAPPER.writeValueAsString(trace);
        } catch (Exception e) {
            return "❌ 构建 trace 摘要失败: " + e.getMessage();
        }
    }
}
