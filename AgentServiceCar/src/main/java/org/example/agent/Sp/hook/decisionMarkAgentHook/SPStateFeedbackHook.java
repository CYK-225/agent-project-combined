package org.example.agent.Sp.hook.decisionMarkAgentHook;

import io.agentscope.core.hook.*;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.common.commonUtils.CollectionValidator;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;
import org.example.agentScope.util.hooksManager.SessionContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SP 模块状态反馈钩子（v2 — 继承 {@link AbstractAgentHook}）。
 * <p>
 * <b>职责：</b>
 * <ol>
 *   <li><b>PreActing</b>：工具执行前，校验 {@code DishSearchTool} 的参数
 *       ({@code flavor}、{@code main_ingredient}) 是否为
 *       {@link DecisionMarkEvent} 中白名单的子集，非法值就地过滤并记录审计日志。</li>
 *   <li><b>PostActing</b>：工具执行后记录状态，异常时通过 {@code enrichToolResult} 注入干预提示。</li>
 *   <li><b>PostReasoning</b>：严重异常时通过 {@code retryWith} 强制纠正。</li>
 *   <li><b>PreCall</b>：注入执行状态上下文。</li>
 * </ol>
 * <p>
 * 使用 {@link CollectionValidator} 进行集合校验，零外部依赖。
 *
 * @author zhilin
 * @since 2026.04.24
 */
@Slf4j
public class SPStateFeedbackHook extends AbstractAgentHook {

    private static final String DISH_SEARCH_TOOL = "DishSearchTool";

    private final DecisionMarkEvent decisionMarkEvent;
    private final ThreadLocal<SPStateTracker> stateTracker =
            ThreadLocal.withInitial(SPStateTracker::new);

    /**
     * 推荐构造方式：通过 SessionContext 获取 DTO。
     *
     * @param sessionContext 会话上下文容器
     */
    public SPStateFeedbackHook(SessionContext sessionContext) {
        this.decisionMarkEvent = sessionContext.get(DecisionMarkEvent.class);
    }

    /**
     * 兼容无 DTO 场景（仅保留状态追踪能力，跳过参数校验）。
     */
    public SPStateFeedbackHook() {
        this.decisionMarkEvent = null;
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║  PreActing: DishSearchTool 参数校验（flavor / main_ingredient）  ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 工具执行前拦截：仅对 {@code DishSearchTool} 生效。
     * <p>
     * 将 AI 传入的 {@code flavor}（工艺）和 {@code main_ingredient}（食材）
     * 分别与 {@link DecisionMarkEvent} 中的白名单做子集校验，
     * 非法值就地移除，保证 AI 输出始终是合法数据的子集。
     *
     * @param event PreActingEvent
     */
    @Override
    protected void handlePreActing(PreActingEvent event) {
        if (decisionMarkEvent == null) return;

        ToolUseBlock toolUse = event.getToolUse();
        if (!DISH_SEARCH_TOOL.equals(toolUse.getName())) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) toolUse.getInput();
        if (args == null || args.isEmpty()) return;

        // 创建可变副本，后续可能修改
        Map<String, Object> mutableArgs = new LinkedHashMap<>(args);
        boolean modified = false;

        // ── 校验 flavor（工艺） ──
        if (validateAndFilter(
                mutableArgs, "flavor",
                decisionMarkEvent.getValidFlavors())) {
            modified = true;
        }

        // ── 校验 main_ingredient（食材） ──
        if (validateAndFilter(
                mutableArgs, "main_ingredient",
                decisionMarkEvent.getValidMainIngredients())) {
            modified = true;
        }

        if (modified) {
            // ★ 使用 toolkit 方法安全重建，自动保留 id / name / content / metadata
            modifyToolInput(event, mutableArgs);
        }
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║  PostActing: 工具执行后记录状态                                  ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    @Override
    protected void handlePostActing(PostActingEvent event) {
        String toolName = extractToolName(event);
        String resultText = extractResultText(event.getToolResult());

        // ★ 检测 Pipeline 秒退：只跳过被拦截工具的失败记录，不影响其他工具
        if (decisionMarkEvent != null && decisionMarkEvent.isPipelinePhase1Completed()) {
            if (toolName.equals(decisionMarkEvent.getInterceptedToolName())) {
                log.debug("[SPStateFeedback] 检测到 Pipeline 秒退，跳过状态记录: tool={}", toolName);
                return;
            }
        }

        SPStateTracker tracker = stateTracker.get();
        tracker.recordToolResult(toolName, resultText);

        log.debug("[SPStateFeedback] step={}, tool={}, status={}",
                tracker.getCurrentStep(), toolName, tracker.getLastToolStatus());

        if (tracker.needsIntervention()) {
            String hint = tracker.buildInterventionHint();
            log.warn("[SPStateFeedback] 检测到异常: tool={}, hint={}", toolName, hint);
            event.setToolResult(enrichToolResult(event.getToolResult(), hint));
        }
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║  PostReasoning: 清理 ThreadLocal                                ║
    // ╚═══════════════════════════════════════════════════════════════════╝
    // 注意：不使用 retryWith/gotoReasoning，因为 event.setToolResult() 的修改
    // 不会同步到 gotoReasoning 验证所读取的消息历史，会导致 Missing ToolResult 异常。
    // 异常提示已通过 enrichToolResult 注入到工具结果中，LLM 会在下一轮推理时看到。

    @Override
    protected void handlePostReasoning(PostReasoningEvent event) {
        try {
            // 清理 ThreadLocal
        } finally {
            stateTracker.remove();
        }
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║  PreCall: 注入执行状态上下文                                     ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    @Override
    protected void handlePreCall(PreCallEvent event) {
        SPStateTracker tracker = stateTracker.get();
        String stateContext = tracker.buildStateContext();

        if (stateContext != null && !stateContext.isEmpty()) {
            injectSystemPrompt(event, stateContext, "sp_state_feedback");
            log.debug("[SPStateFeedback] 已注入执行状态上下文: {}", stateContext);
        }
    }

    // ╔═══════════════════════════════════════════════════════════════════╗
    // ║  私有工具方法                                                    ║
    // ╚═══════════════════════════════════════════════════════════════════╝

    /**
     * 校验工具参数中指定 key 的值是否为白名单的子集，非法值就地过滤。
     *
     * @param args     工具参数 Map（会被就地修改）
     * @param paramKey 要校验的参数名（如 "flavor"）
     * @param csv      逗号分隔的白名单字符串（来自 DecisionMarkEvent）
     * @return true 表示发生了过滤（有非法值被移除）
     */
    private boolean validateAndFilter(Map<String, Object> args, String paramKey, String csv) {
        List<String> values = toStringList(args.get(paramKey));
        if (values == null || values.isEmpty()) return false;

        Set<String> validSet = parseCsv(csv);
        if (validSet.isEmpty()) return false;

        // 创建可变列表用于就地过滤
        List<String> mutableValues = new ArrayList<>(values);
        Set<String> invalid = CollectionValidator.retainIntersection(mutableValues, validSet);

        if (!invalid.isEmpty()) {
            log.warn("[SPStateFeedback] DishSearchTool.{} 非法值已过滤({}个): {}",
                    paramKey, invalid.size(), invalid);
            args.put(paramKey, mutableValues);
            return true;
        }
        return false;
    }

    /**
     * 将逗号分隔的字符串解析为 Set，自动 trim 并过滤空值。
     *
     * @param csv 逗号分隔的字符串，如 "炒,炖,蒸"
     * @return 去重后的合法值集合；输入为 null/空时返回空集合
     */
    private static Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 将工具参数值安全地转换为 {@code List<String>}。
     * <p>
     * 兼容 LLM 传入的多种格式：List、单个 String、String[]。
     *
     * @param value 工具参数原始值
     * @return 字符串列表；无法转换时返回 null
     */
    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        if (value instanceof String[] arr) {
            return Arrays.asList(arr);
        }
        if (value instanceof String s && !s.isBlank()) {
            // LLM 偶尔传单个字符串而非数组
            return List.of(s);
        }
        return null;
    }
}
