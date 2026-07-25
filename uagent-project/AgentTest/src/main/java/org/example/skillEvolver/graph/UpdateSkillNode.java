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
import org.example.skillEvolver.service.EvolverSkillVersionService;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 更新节点（LLM 驱动，移植自 SkillClaw execution.py）。
 * <p>
 * 基于 SkillClaw 的保守编辑原则：
 * - 4 种决策：improve_skill / optimize_description / skip / create_skill
 * - 保守编辑约束：最小改动、不重写、不加空泛建议
 * - JSON 结构化输出
 * <p>
 * 合成后自动持久化到 evolver_skill_version 表。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-update-skill", description = "LLM Skill 合成：保守编辑模式，基于分析报告智能更新 SKILL.md")
public class UpdateSkillNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 核心 system prompt — 移植自 SkillClaw execution.py _EVOLVE_FROM_SESSIONS_SYSTEM，
     * 适配 SkillEvolver 的 trial 场景。
     */
    private static final String EVOLVE_SYSTEM_PROMPT = """
            You are a skill engineer for SkillEvolver's skill evolution system.

            You are given evidence from multiple trial runs that all involved \
            the skill being evolved. Each trial contains a step-by-step trajectory \
            (tool calls and outcomes) and a pass/fail result.

            Your task: edit the ORIGINAL skill so it better compresses execution \
            knowledge for future runs. Treat the trial evidence as environment \
            feedback that helps refine, validate, and extend the skill over time.

            Analyze the trial evidence alongside the current skill content, then \
            decide the best course of action:

            1. **improve_skill** - The skill content needs targeted edits based on the \
            trial evidence (for example missing guidance, outdated information, or \
            unclear instructions). Produce the updated skill.

            2. **skip** - The skill is working well enough, or the evidence is too weak \
            or ambiguous to justify changes. No action needed.

            ## Editing principles (for improve_skill)

            - Treat the CURRENT skill as the source of truth, not as a rough draft to be rewritten.
            - Read the original skill first, then the trial evidence.
            - Default to targeted edits, not rewrites.
            - If multiple trials point to the same section being wrong or incomplete, edit that section.
            - If failures are only corner cases, add the missing checks or clarify constraints without changing unrelated sections.
            - Preserve the original structure, heading order, terminology, and effective guidance, especially parts supported by successful trials.
            - Only rewrite an entire section if the evidence shows that section is materially wrong.
            - If the skill contains concrete details (API endpoints, ports, payload schemas, tool names, command patterns) that are factually correct, KEEP them even if the agent did not use them well. These details are the skill's core value.

            ## Hard constraints

            - Do NOT casually change task API contracts, ports, endpoints, output paths, payload formats, or required filenames.
            - Do NOT remove core capabilities, API references, command patterns, or tool-usage examples unrelated to the observed failures.
            - Do NOT turn the skill into a different skill with a different purpose.
            - Do NOT rewrite the whole skill from scratch.
            - Do NOT impose a new template, new mandatory section structure, or a different writing style unless the evidence requires it.
            - Do NOT add generic best-practice guidance (for example rate-limit handling, retry logic, state management, or caching) that the agent should handle on its own. Only add such guidance if the specific environment has quirks that the agent cannot be expected to discover independently.

            ## Conservative editing mode

            - Prefer preserving existing section headings and ordering.
            - If a successful trial supports a section, leave that section untouched unless failure evidence explicitly contradicts it.
            - Prefer tightening or clarifying an existing section over adding a brand-new section.
            - Do not introduce a new section unless it directly addresses a documented failure.
            - When adding content, prefer inline additions to existing sections over creating new top-level sections.

            ## When to skip

            Prefer skip when:
            - All trials passed with no errors or ambiguity.
            - The evidence is too weak or too noisy to support targeted edits.
            - The skill already covers the identified failure modes.
            - The trials failed for reasons unrelated to the skill (e.g. runtime errors, environment issues).

            ## Output format

            Return EXACTLY one JSON object (no markdown fences, no extra text):

            If action is improve_skill:
            ```
            {
              "action": "improve_skill",
              "content": "<full updated SKILL.md content with frontmatter>",
              "edit_summary": "<brief description of what changed and why>"
            }
            ```

            If action is skip:
            ```
            {
              "action": "skip",
              "rationale": "<why skipping>"
            }
            ```
            """;

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String analysisReport = (String) state.value("analysisReport").orElse("");
        String currentSkill = (String) state.value("currentSkill").orElse("");
        String instruction = (String) state.value("taskInstruction").orElse("");
        String taskId = (String) state.value("taskId").orElse("unknown");
        int iteration = (int) state.value("currentIteration").orElse(0);
        Double passRate = (Double) state.value("trialPassRate").orElse(0.0);
        int successCount = (int) state.value("successCount").orElse(0);
        int failureCount = (int) state.value("failureCount").orElse(0);
        String successTracesJson = (String) state.value("successTracesJson").orElse("[]");
        String failureTracesJson = (String) state.value("failureTracesJson").orElse("[]");
        Double bestReward = (Double) state.value("bestReward").orElse(0.0);

        // 保存旧版 Skill（供 VerifySkillNode 回滚对比用）
        String previousSkill = currentSkill;

        log.info("[UpdateSkillNode] 迭代 {}: 通过 LLM 评估是否需要更新 Skill (通过率={}, 成功={}, 失败={})",
                iteration, passRate, successCount, failureCount);

        // --- 构建 user prompt ---
        String taskPrompt = buildEvolvePrompt(instruction, currentSkill, analysisReport,
                iteration, passRate, successCount, failureCount,
                successTracesJson, failureTracesJson);

        AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);

        EvolverReasonerAgent.setContext(
                new EvolverReasonerAgent.ReasonerContext(EVOLVE_SYSTEM_PROMPT, taskPrompt));

        try {
            String threadId = "evolver-updater-" + iteration + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "EvolverReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = (response != null) ? response.getTextContent() : "";

            // --- 解析 JSON 响应 ---
            JsonNode result = parseJsonResponse(llmResponse);
            String action = result.path("action").asText("skip");

            Map<String, Object> output = new LinkedHashMap<>();

            if ("skip".equals(action)) {
                String rationale = result.path("rationale").asText("Skill is working well enough");

                // 保护：如果 currentSkill 为空（第一轮），不允许 skip，必须创建初始 skill
                if (currentSkill.isBlank()) {
                    log.warn("[UpdateSkillNode] currentSkill 为空，强制从 skip → create_skill");
                    action = "create_skill";
                } else {
                    log.info("[UpdateSkillNode] LLM 决定 SKIP: {}", rationale);
                    output.put("currentSkill", currentSkill);  // 保留原样
                    output.put("previousSkill", previousSkill);  // 供 VerifySkillNode 回滚用
                    output.put("currentIteration", iteration + 1);
                    output.put("skillVersion", "v" + (iteration + 1));
                    output.put("updateAction", "skip");
                    output.put("editSummary", rationale);
                    output.put("updateTimestamp", System.currentTimeMillis());
                    return output;
                }
            }

            // improve_skill
            String updatedSkill = result.path("content").asText("");
            String editSummary = result.path("edit_summary").asText("targeted edit");

            // 兜底：如果 JSON 里没有 content，尝试直接用 LLM 输出
            if (updatedSkill.isBlank()) {
                updatedSkill = cleanMarkdownResponse(llmResponse);
            }

            // 兜底：内容太短则保留原样
            if (updatedSkill.isBlank() || updatedSkill.length() < 50) {
                log.warn("[UpdateSkillNode] LLM 返回内容过短，保留当前 skill");
                updatedSkill = currentSkill;
                action = "skip";
            }

            int nextIteration = iteration + 1;
            String versionLabel = "v" + nextIteration;
            log.info("[UpdateSkillNode] Skill 已更新 v{} ({} 字符), editSummary={}",
                    nextIteration, updatedSkill.length(), editSummary);

            // 持久化到数据库
            persistVersion(taskId, iteration, versionLabel, updatedSkill,
                    passRate, bestReward, analysisReport, successTracesJson, failureTracesJson);

            // 跨迭代最优版本追踪
            String prevBestContent = (String) state.value("bestSkillContent").orElse("");
            String prevBestVersion = (String) state.value("bestSkillVersion").orElse("");
            double bestRewardEver = (Double) state.value("bestRewardEver").orElse(0.0);

            if (!"skip".equals(action) && bestReward > bestRewardEver) {
                output.put("bestSkillContent", updatedSkill);
                output.put("bestSkillVersion", versionLabel);
                output.put("bestRewardEver", bestReward);
                log.info("[UpdateSkillNode] 新最优版本: {} (reward={} > {})",
                        versionLabel, String.format("%.3f", bestReward), String.format("%.3f", bestRewardEver));
            } else if (prevBestContent.isEmpty()) {
                output.put("bestSkillContent", updatedSkill);
                output.put("bestSkillVersion", versionLabel);
                output.put("bestRewardEver", bestReward);
            } else {
                output.put("bestSkillContent", prevBestContent);
                output.put("bestSkillVersion", prevBestVersion);
                output.put("bestRewardEver", bestRewardEver);
            }

            output.put("currentSkill", updatedSkill);
            output.put("previousSkill", previousSkill);  // 供 VerifySkillNode 回滚用
            output.put("currentIteration", nextIteration);
            output.put("skillVersion", versionLabel);
            output.put("updateAction", action);
            output.put("editSummary", editSummary);
            output.put("updateTimestamp", System.currentTimeMillis());
            return output;

        } finally {
            EvolverReasonerAgent.clearContext();
        }
    }

    // ==================== Prompt 构建 ====================

    private String buildEvolvePrompt(String instruction, String currentSkill, String analysisReport,
                                      int iteration, Double passRate, int successCount, int failureCount,
                                      String successTracesJson, String failureTracesJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Skill Evolution Task\n\n");

        sb.append("## Task Instruction\n").append(instruction).append("\n\n");

        sb.append("## Current Skill (v").append(iteration).append(")\n");
        if (currentSkill.isBlank()) {
            sb.append("*(No skill yet — this is the first iteration, you MUST create the initial skill)*\n\n");
        } else {
            sb.append("```markdown\n").append(currentSkill).append("\n```\n\n");
        }

        sb.append("## Trial Results Summary\n");
        sb.append("- Passed: ").append(successCount).append(" / ").append(successCount + failureCount);
        sb.append(" (").append(String.format("%.1f%%", passRate * 100)).append(")\n");
        sb.append("- Iteration: ").append(iteration).append("\n\n");

        sb.append("## Analysis Report\n").append(analysisReport).append("\n\n");

        // 成功 trial 的轨迹摘要（截断到合理长度）
        sb.append("## Successful Trial Traces\n");
        sb.append(truncateJsonArray(successTracesJson, 3000)).append("\n\n");

        sb.append("## Failed Trial Traces\n");
        sb.append(truncateJsonArray(failureTracesJson, 3000)).append("\n\n");

        sb.append("Based on the evidence above, decide: improve_skill or skip?\n");
        return sb.toString();
    }

    // ==================== JSON 解析 ====================

    private JsonNode parseJsonResponse(String llmResponse) {
        // 先尝试直接解析
        try {
            return MAPPER.readTree(extractJsonObject(llmResponse));
        } catch (Exception e) {
            log.warn("[UpdateSkillNode] JSON 解析失败，fallback 到 skip: {}", e.getMessage());
            // 返回 skip 的默认 JSON
            try {
                return MAPPER.readTree("{\"action\":\"skip\",\"rationale\":\"JSON parse failed\"}");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) return "{}";
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        // 找到第一个 { 和最后一个 }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = cleaned.substring(start, end + 1);
            // 修复常见 JSON 问题：尾随逗号（}  或 ] 前的逗号）
            json = json.replaceAll(",\\s*([}\\]])", "$1");
            // 修复 LLM 在 JSON 字符串值中插入的裸换行
            json = json.replaceAll("(?<=\"[^\"\\\\]*)\\n", " ");
            return json;
        }
        return cleaned;
    }

    private String truncateJsonArray(String json, int maxChars) {
        if (json == null || json.length() <= maxChars) return json;
        return json.substring(0, maxChars) + "...(截断)";
    }

    private String cleanMarkdownResponse(String text) {
        if (text == null) return "";
        String cleaned = text.trim();
        if (cleaned.startsWith("```markdown")) {
            cleaned = cleaned.substring("```markdown".length());
        } else if (cleaned.startsWith("```md")) {
            cleaned = cleaned.substring("```md".length());
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    // ==================== 数据库持久化 ====================

    private void persistVersion(String taskId, int iteration, String versionLabel,
                                 String skillMarkdown, Double passRate, double meanReward,
                                 String analysis, String successTracesJson, String failureTracesJson) {
        try {
            EvolverSkillVersionService versionService =
                    ApplicationContextProvider.getBean(EvolverSkillVersionService.class);
            versionService.saveVersion(taskId, iteration, versionLabel,
                    skillMarkdown, passRate, meanReward, analysis, successTracesJson, failureTracesJson);
            log.info("[UpdateSkillNode] 版本持久化成功: {}, meanReward={}", versionLabel, String.format("%.3f", meanReward));
        } catch (Exception e) {
            log.error("[UpdateSkillNode] 版本持久化失败: {}", versionLabel, e);
        }
    }
}
