package org.example.skillOpt.env;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多轮轨迹中的单步记录 — 对应 SkillOpt 源码中的 step_record。
 * <p>
 * 记录 Agent 的一次推理 + 工具调用（如果有）。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepRecord {

    /** 步骤序号 */
    private int stepIndex;

    /** Agent 的推理过程 */
    private String reasoning;

    /** Agent 输出的动作（可能是工具调用，也可能是最终答案） */
    private String action;

    /** 是否是工具调用 */
    private boolean isToolCall;

    /** 工具名称（如果是工具调用） */
    private String toolName;

    /** 工具输入（如果是工具调用） */
    private String toolInput;

    /** 工具返回结果（如果是工具调用） */
    private String toolResult;

    /** 工具是否成功 */
    private boolean toolSuccess;

    /** Agent 的原始输出 */
    private String rawAgentOutput;

    /** 时间戳 */
    private long timestamp;

    /**
     * 格式化为 Markdown（用于 trace 输出）
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("### Step ").append(stepIndex + 1).append("\n\n");

        if (reasoning != null && !reasoning.isBlank()) {
            sb.append("**Reasoning**: ").append(reasoning).append("\n\n");
        }

        if (isToolCall) {
            sb.append("**Tool Call**: `").append(toolName).append("`\n");
            sb.append("- Input: ").append(truncate(toolInput, 500)).append("\n");
            sb.append("- Result: ").append(truncate(toolResult, 500)).append("\n");
            sb.append("- Success: ").append(toolSuccess ? "Yes" : "No").append("\n");
        } else {
            sb.append("**Final Answer**: ").append(truncate(action, 500)).append("\n");
        }

        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
