package org.example.skillOpt.env;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用动作 — 解析 Agent 输出得到的结构化工具调用。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolAction {

    /** 工具名称 */
    private String toolName;

    /** 工具输入参数（JSON 格式或结构化） */
    private String toolInput;

    /** 原始 Agent 输出（用于调试） */
    private String rawOutput;

    /** Agent 的推理过程（如果有） */
    private String reasoning;
}
