package org.example.agentScope.util.hooksManager;

import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import io.agentscope.core.model.GenerateOptions;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Hook 通用工具集 —— 抽取各 Hook 中重复出现的模式为默认方法。
 * <p>
 * 任何 Hook 只需 {@code implements AgentHookToolkit} 即可获得全部能力。
 * <p>
 * <b>方法索引：</b>
 * <pre>
 * ── System Prompt 注入 ───────────────────────────
 *   injectSystemPrompt(PreReasoningEvent, String)
 *   injectSystemPrompt(PreReasoningEvent, String, String)
 *   injectSystemPrompt(PreCallEvent, String)
 *   injectSystemPrompt(PreCallEvent, String, String)
 *
 * ── 文本清洗 ────────────────────────────────────
 *   cleanResponseText(PostReasoningEvent, UnaryOperator)
 *   cleanThinkingTags(PostReasoningEvent)
 *   cleanChineseParentheses(PostReasoningEvent)
 *   stripTagContent(PostReasoningEvent, String)
 *
 * ── ToolResult 操作 ──────────────────────────────
 *   extractResultText(ToolResultBlock)
 *   enrichToolResult(ToolResultBlock, String)
 *
 * ── ToolUse 操作 ────────────────────────────────
 *   extractToolName(PreActingEvent)
 *   extractToolName(PostActingEvent)
 *   copyToolUse(PreActingEvent)                         — 获取预填充全部字段的 Builder 副本
 *   modifyToolInput(PreActingEvent, Map)                — 只改 input
 *   modifyToolName(PreActingEvent, String)              — 只改 name
 *   modifyToolUse(PreActingEvent, String, Map)          — 改 name + input
 *   modifyToolUse(PreActingEvent, UnaryOperator<Builder>) — 通用：Builder 自定义
 *
 * ── 校验与流程控制 ──────────────────────────────
 *   stopIf(PostReasoningEvent, boolean, String)
 *   retryWith(PostReasoningEvent, String)
 *
 * ── 日志 ────────────────────────────────────────
 *   logPhase(HookEvent, String)
 *
 * ── 模型参数快捷调整 ────────────────────────────
 *   adjustTemperature(PreReasoningEvent, double)
 *   adjustMaxTokens(PreReasoningEvent, int)
 *   adjustModelParams(PreReasoningEvent, Double, Integer)
 * </pre>
 *
 */

public interface AgentHookToolkit {
    // 手动声明 Logger（注意：它在接口中会隐式变成 public static final）
    Logger log = LoggerFactory.getLogger(AgentHookToolkit.class);

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║                    System Prompt 注入                            ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 在推理阶段（PreReasoning），将提示词安全注入到 System Prompt 区域末尾。
     * <p>
     * <b>注入策略：</b>扫描消息列表，找到连续 SYSTEM 消息的最后一个位置，
     * 在其后插入新的 SYSTEM 消息。如果不存在 SYSTEM 消息，则插入到最前面。
     * <p>
     * <b>典型场景：</b>
     * <ul>
     *   <li>注入动态时间上下文</li>
     *   <li>注入菜单编排规则</li>
     *   <li>注入工具执行结果摘要</li>
     * </ul>
     *
     * @param event      PreReasoningEvent 事件
     * @param promptText 要注入的提示词文本，null 或空白时跳过
     */
    default void injectSystemPrompt(PreReasoningEvent event, String promptText) {
        if (promptText == null || promptText.isBlank()) return;

        List<Msg> currentInputs = new ArrayList<>(event.getInputMessages());
        Msg dynamicPrompt = Msg.builder()
                .role(MsgRole.SYSTEM)
                .content(List.of(TextBlock.builder().text(promptText).build()))
                .build();

        int insertIndex = 0;
        for (int i = 0; i < currentInputs.size(); i++) {
            if (currentInputs.get(i).getRole() == MsgRole.SYSTEM) {
                insertIndex = i + 1;
            } else {
                break;
            }
        }
        currentInputs.add(insertIndex, dynamicPrompt);
        event.setInputMessages(currentInputs);
    }

    /**
     * 带标签包装的推理阶段 System Prompt 注入。
     * <p>
     * 将提示词包裹在 {@code <tagName>...</tagName>} 中，便于 LLM 识别注入来源。
     *
     * <pre>{@code
     * // 输出: <system_notification>\n当前时间 2026-04-29 10:30\n</system_notification>
     * injectSystemPrompt(event, timeText, "system_notification");
     * }</pre>
     *
     * @param event      PreReasoningEvent 事件
     * @param promptText 要注入的提示词文本
     * @param tagName    XML 标签名（不含尖括号）
     */
    default void injectSystemPrompt(PreReasoningEvent event, String promptText, String tagName) {
        if (promptText == null || promptText.isBlank()) return;
        String tagged = "<" + tagName + ">\n" + promptText + "\n</" + tagName + ">";
        injectSystemPrompt(event, tagged);
    }

    /**
     * 在调用阶段（PreCall），将提示词安全注入到 System Prompt 区域末尾。
     * <p>
     * <b>与 PreReasoning 版本的区别：</b>
     * PreCall 发生在 Agent 接收输入的最早期（Prompt 构建之前），
     * 适合注入全局性的上下文；PreReasoning 更靠近模型调用，适合注入临时上下文。
     *
     * @param event      PreCallEvent 事件
     * @param promptText 要注入的提示词文本，null 或空白时跳过
     */
    default void injectSystemPrompt(PreCallEvent event, String promptText) {
        if (promptText == null || promptText.isBlank()) return;

        List<Msg> currentInputs = new ArrayList<>(event.getInputMessages());
        Msg dynamicPrompt = Msg.builder()
                .role(MsgRole.SYSTEM)
                .content(List.of(TextBlock.builder().text(promptText).build()))
                .build();

        int insertIndex = 0;
        for (int i = 0; i < currentInputs.size(); i++) {
            if (currentInputs.get(i).getRole() == MsgRole.SYSTEM) {
                insertIndex = i + 1;
            } else {
                break;
            }
        }
        currentInputs.add(insertIndex, dynamicPrompt);
        event.setInputMessages(currentInputs);
    }

    /**
     * 带标签包装的调用阶段 System Prompt 注入。
     *
     * @param event      PreCallEvent 事件
     * @param promptText 要注入的提示词文本
     * @param tagName    XML 标签名
     */
    default void injectSystemPrompt(PreCallEvent event, String promptText, String tagName) {
        if (promptText == null || promptText.isBlank()) return;
        String tagged = "<" + tagName + ">\n" + promptText + "\n</" + tagName + ">";
        injectSystemPrompt(event, tagged);
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║                       文本清洗                                   ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 通用文本清洗框架 —— 基于策略模式。
     * <p>
     * 遍历模型回复中的所有 {@link TextBlock}，对文本应用清洗函数，
     * 非文本块（如 ToolUseBlock）原样保留。
     * <p>
     * <b>只在内容确实被修改时</b>才重建 Msg 对象（避免不必要的对象创建）。
     *
     * <pre>{@code
     * // 示例：清除 <thinking> 标签
     * cleanResponseText(event, text ->
     *     text.replaceAll("(?s)<thinking>.*?</thinking>", "").trim()
     * );
     * }</pre>
     *
     * @param event       PostReasoningEvent 事件
     * @param textCleaner 文本清洗函数，接收原文返回清洗后的文本
     */
    default void cleanResponseText(PostReasoningEvent event, UnaryOperator<String> textCleaner) {
        Msg responseMsg = event.getReasoningMessage();
        if (responseMsg == null || responseMsg.getContent() == null) return;

        List<ContentBlock> safeBlocks = new ArrayList<>();
        boolean isModified = false;

        for (ContentBlock block : responseMsg.getContent()) {
            if (block instanceof TextBlock textBlock) {
                String originalText = textBlock.getText();
                String safeText = textCleaner.apply(originalText);
                if (!originalText.equals(safeText)) {
                    isModified = true;
                }
                safeBlocks.add(TextBlock.builder().text(safeText.trim()).build());
            } else {
                safeBlocks.add(block);
            }
        }

        if (isModified) {
            Msg safeMsg = Msg.builder()
                    .id(responseMsg.getId())
                    .name(responseMsg.getName())
                    .role(responseMsg.getRole())
                    .content(safeBlocks)
                    .build();
            event.setReasoningMessage(safeMsg);
        }
    }

    /**
     * 一键清除常见 thinking/analysis 标签及其内容。
     * <p>
     * 清除范围：
     * <ul>
     *   <li>{@code <thinking>...</thinking>}</li>
     *   <li>{@code <think>...</think>}</li>
     *   <li>{@code <analysis>...</analysis>}</li>
     * </ul>
     *
     * @param event PostReasoningEvent 事件
     */
    default void cleanThinkingTags(PostReasoningEvent event) {
        cleanResponseText(event, text ->
                text.replaceAll("(?s)<thinking>.*?</thinking>", "")
                    .replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("(?s)<analysis>.*?</analysis>", "")
                    .trim()
        );
    }

    /**
     * 清除中文圆括号及其包裹的内容（兜底方案）。
     * <p>
     * 匹配 {@code （...）} 格式的中文括号内容，用于清洗模型的内心独白。
     * <b>注意：</b>此方法较激进，可能误删有意义的括号内容，建议作为兜底手段。
     *
     * @param event PostReasoningEvent 事件
     */
    default void cleanChineseParentheses(PostReasoningEvent event) {
        cleanResponseText(event, text ->
                text.replaceAll("（[^）]*）", "").trim()
        );
    }

    /**
     * 清除指定 XML 标签及其包裹的内容。
     * <p>
     * 通用的标签内容清除器，支持任意标签名。
     *
     * <pre>{@code
     * stripTagContent(event, "thinking");      // 清除 <thinking>...</thinking>
     * stripTagContent(event, "internal_note"); // 清除 <internal_note>...</internal_note>
     * }</pre>
     *
     * @param event   PostReasoningEvent 事件
     * @param tagName 要清除的标签名（不含尖括号）
     */
    default void stripTagContent(PostReasoningEvent event, String tagName) {
        if (tagName == null || tagName.isBlank()) return;
        String regex = "(?s)<" + tagName + ">.*?</" + tagName + ">";
        cleanResponseText(event, text -> text.replaceAll(regex, "").trim());
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║                     ToolResult 操作                              ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 从 {@link ToolResultBlock} 中提取纯文本内容。
     * <p>
     * 遍历输出块列表，拼接所有 {@link TextBlock} 的文本。
     * 非文本块被忽略。
     *
     * @param result 工具执行结果块
     * @return 拼接后的纯文本；result 为 null 时返回空字符串
     */
    default String extractResultText(ToolResultBlock result) {
        if (result == null || result.getOutput() == null) return "";
        return result.getOutput().stream()
                .filter(block -> block instanceof TextBlock)
                .map(block -> ((TextBlock) block).getText())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 向工具执行结果追加系统提示（阅后即焚型注入）。
     * <p>
     * <b>机制：</b>在原始工具输出文本末尾拼接 {@code \n\n[系统状态提示] hint}，
     * 让 LLM 在下一轮推理时能感知到异常状态或引导信息。
     * <p>
     * <b>典型场景：</b>
     * <ul>
     *   <li>工具返回空结果 → 追加"请尝试其他方式"</li>
     *   <li>工具执行异常 → 追加重试建议</li>
     *   <li>检测到死循环 → 追加强制跳出指令</li>
     * </ul>
     *
     * <pre>{@code
     * if (tracker.needsIntervention()) {
     *     event.setToolResult(enrichToolResult(event.getToolResult(), tracker.buildHint()));
     * }
     * }</pre>
     *
     * @param original 原始工具结果块
     * @param hint     要追加的提示文本
     * @return 包含追加内容的新 ToolResultBlock；hint 为空时返回原始对象
     */
    default ToolResultBlock enrichToolResult(ToolResultBlock original, String hint) {
        if (original == null || hint == null || hint.isBlank()) return original;
        String enriched = extractResultText(original) + "\n\n[系统状态提示] " + hint;
        return ToolResultBlock.of(original.getId(), original.getName(),
                ToolResultBlock.text(enriched).getOutput());
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║                     ToolUse 操作                                 ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 从 PreActingEvent 中提取工具名称。
     *
     * @param event PreActingEvent 事件
     * @return 工具函数名
     */
    default String extractToolName(PreActingEvent event) {
        return event.getToolUse().getName();
    }

    /**
     * 从 PostActingEvent 中提取工具名称。
     *
     * @param event PostActingEvent 事件
     * @return 工具函数名
     */
    default String extractToolName(PostActingEvent event) {
        return event.getToolUse().getName();
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║           ToolUse 安全重建（保留全部 5 个原始属性）               ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 从原始 {@link ToolUseBlock} 创建一个<b>预填充全部字段</b>的 Builder 副本。
     * <p>
     * 调用方可在返回的 Builder 上自由修改任意字段，然后 {@code build()} 并
     * {@code event.setToolUse(...)}，无需担心遗漏 {@code id}、{@code content}、
     * {@code metadata} 等属性。
     *
     * <pre>{@code
     * // 示例：只改 input，其余原样保留
     * event.setToolUse(copyToolUse(event).input(newInput).build());
     *
     * // 示例：改 name + metadata
     * event.setToolUse(copyToolUse(event)
     *         .name("newTool")
     *         .metadata(Map.of("key", "val"))
     *         .build());
     * }</pre>
     *
     * @param event PreActingEvent 事件
     * @return 预填充了原始 id / name / input / content / metadata 的 Builder
     */
    default ToolUseBlock.Builder copyToolUse(PreActingEvent event) {
        ToolUseBlock original = event.getToolUse();
        return ToolUseBlock.builder()
                .id(original.getId())
                .name(original.getName())
                .input(original.getInput())
                .content(original.getContent())
                .metadata(original.getMetadata());
    }

    /**
     * 只修改 {@code input}（工具参数），其余属性原样保留。
     * <p>
     * <b>典型场景：</b>Hook 在 PreActing 阶段校验并过滤工具参数（如食材白名单过滤）。
     *
     * <pre>{@code
     * Map<String, Object> filtered = new LinkedHashMap<>(original.getInput());
     * filtered.put("flavor", validFlavors);
     * modifyToolInput(event, filtered);
     * }</pre>
     *
     * @param event    PreActingEvent 事件
     * @param newInput 新的工具参数 Map
     */
    default void modifyToolInput(PreActingEvent event, Map<String, Object> newInput) {
        event.setToolUse(copyToolUse(event).input(newInput).build());
    }

    /**
     * 只修改 {@code name}（工具名），其余属性原样保留。
     * <p>
     * <b>典型场景：</b>动态路由——根据上下文将工具调用重定向到另一个工具。
     *
     * @param event   PreActingEvent 事件
     * @param newName 新的工具名
     */
    default void modifyToolName(PreActingEvent event, String newName) {
        event.setToolUse(copyToolUse(event).name(newName).build());
    }

    /**
     * 同时修改 {@code name} 和 {@code input}，其余属性原样保留。
     *
     * @param event   PreActingEvent 事件
     * @param newName 新的工具名
     * @param newInput 新的工具参数 Map
     */
    default void modifyToolUse(PreActingEvent event, String newName, Map<String, Object> newInput) {
        event.setToolUse(copyToolUse(event).name(newName).input(newInput).build());
    }

    /**
     * 通用修改 —— 通过 {@link UnaryOperator} 自定义 Builder，保留全部原始属性。
     * <p>
     * <b>最灵活的重载</b>，适合需要同时修改多个属性的场景。
     *
     * <pre>{@code
     * modifyToolUse(event, builder -> builder
     *         .name("renamedTool")
     *         .input(newInput)
     *         .metadata(Map.of("validated", true))
     * );
     * }</pre>
     *
     * @param event      PreActingEvent 事件
     * @param customizer Builder 自定义函数，接收已预填充原始属性的 Builder，返回修改后的 Builder
     */
    default void modifyToolUse(PreActingEvent event, UnaryOperator<ToolUseBlock.Builder> customizer) {
        event.setToolUse(customizer.apply(copyToolUse(event)).build());
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║                   校验与流程控制                                  ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 条件熔断 —— 满足条件时立即终止 Agent 运行。
     * <p>
     * <b>典型场景：</b>检测到模型输出包含敏感词、格式严重错误、安全违规等。
     *
     * <pre>{@code
     * // 检测到不安全内容时熔断
     * stopIf(event,
     *     content.contains("错误指令") || content.contains("忽略之前的"),
     *     "检测到不安全内容"
     * );
     * }</pre>
     *
     * @param event   PostReasoningEvent 事件
     * @param trigger 触发条件（true 时执行 stopAgent）
     * @param reason  熔断原因（记录到日志）
     * @return 是否执行了熔断
     */
    default boolean stopIf(PostReasoningEvent event, boolean trigger, String reason) {
        if (trigger) {
            log.warn(">>> [Hook 熔断] {} → stopAgent()", reason);
            event.stopAgent();
            return true;
        }
        return false;
    }

    /**
     * 引导重试 —— 向历史中注入提示消息，强制模型重新推理。
     * <p>
     * <b>典型场景：</b>
     * <ul>
     *   <li>模型输出格式不符合 JSON Schema → 要求重试</li>
     *   <li>模型未调用必要的工具 → 提醒补全</li>
     *   <li>工具返回空结果 → 要求换一种方式</li>
     * </ul>
     *
     * <pre>{@code
     * if (!isValidJson(reasoningMsg)) {
     *     retryWith(event, "你的回复格式不对，请确保使用 JSON 格式重试一次。");
     * }
     * }</pre>
     *
     * @param event   PostReasoningEvent 事件
     * @param hintMsg 注入的提示文本（告诉模型哪里出了问题、该如何修正）
     */
    default void retryWith(PostReasoningEvent event, String hintMsg) {
        if (hintMsg == null || hintMsg.isBlank()) return;
        Msg hint = Msg.builder()
                .role(MsgRole.SYSTEM)
                .textContent(hintMsg)
                .build();
        log.info(">>> [Hook 重试] gotoReasoning: {}", hintMsg);
        event.gotoReasoning(hint);
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║                         日志                                     ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 统一格式的生命周期日志。
     * <p>
     * 输出格式: {@code >>> [Hook 阶段名] 智能体: xxx}
     *
     * <pre>{@code
     * logPhase(event, "推理前准备");
     * // 输出: >>> [Hook 推理前准备] 智能体: DecisionAgent
     * }</pre>
     *
     * @param event Hook 事件（用于获取 Agent 名称）
     * @param phase 阶段描述
     */
    default void logPhase(HookEvent event, String phase) {
        log.info(">>> [Hook {}] 智能体: {}", phase, event.getAgent().getName());
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║                   模型参数快捷调整                                ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 快捷调整模型温度参数。
     * <p>
     * <b>典型场景：</b>上下文过长时降温以保证输出稳定性；
     * 创意任务时升温以增加多样性。
     *
     * @param event       PreReasoningEvent 事件
     * @param temperature 温度值（0.0 ~ 2.0）
     */
    default void adjustTemperature(PreReasoningEvent event, double temperature) {
        GenerateOptions opts = event.getEffectiveGenerateOptions();
        event.setGenerateOptions(GenerateOptions.builder()
                .temperature(temperature)
                .maxTokens(opts != null ? opts.getMaxTokens() : null)
                .build());
    }

    /**
     * 快捷调整最大 Token 数。
     *
     * @param event     PreReasoningEvent 事件
     * @param maxTokens 最大 Token 数
     */
    default void adjustMaxTokens(PreReasoningEvent event, int maxTokens) {
        GenerateOptions opts = event.getEffectiveGenerateOptions();
        event.setGenerateOptions(GenerateOptions.builder()
                .temperature(opts != null ? opts.getTemperature() : null)
                .maxTokens(maxTokens)
                .build());
    }

    /**
     * 组合调整模型参数（仅覆盖非 null 的字段）。
     *
     * <pre>{@code
     * // 只调温度，maxTokens 保持原值
     * adjustModelParams(event, 0.1, null);
     *
     * // 同时调整
     * adjustModelParams(event, 0.3, 4000);
     * }</pre>
     *
     * @param event       PreReasoningEvent 事件
     * @param temperature 温度值（null 时不修改）
     * @param maxTokens   最大 Token 数（null 时不修改）
     */
    default void adjustModelParams(PreReasoningEvent event, Double temperature, Integer maxTokens) {
        GenerateOptions current = event.getEffectiveGenerateOptions();
        Double finalTemp = temperature != null ? temperature
                : (current != null ? current.getTemperature() : null);
        Integer finalTokens = maxTokens != null ? maxTokens
                : (current != null ? current.getMaxTokens() : null);
        event.setGenerateOptions(GenerateOptions.builder()
                .temperature(finalTemp)
                .maxTokens(finalTokens)
                .build());
    }
}
