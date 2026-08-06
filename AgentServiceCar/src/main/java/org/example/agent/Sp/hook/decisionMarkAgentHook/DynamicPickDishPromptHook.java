package org.example.agent.Sp.hook.decisionMarkAgentHook;

import io.agentscope.core.hook.*;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.agentScope.util.hooksManager.AgentHookToolkit;
import org.example.agentScope.util.hooksManager.SessionContext;
import reactor.core.publisher.Mono;

/**
 * 动态菜品选择提示词 Hook。
 * <p>
 * 根据工具调用动态切换 MENU / SEARCH 专家模式，
 * 在推理前注入对应的专家规则和运行时上下文，
 * 在推理后清洗掉注入的标签内容（阅后即焚）。
 * <p>
 * 使用 {@link AgentHookToolkit} 默认方法替代重复的注入和清洗逻辑。
 *
 * @author zhilin
 */
@Slf4j
public class DynamicPickDishPromptHook implements Hook, AgentHookToolkit {

    private enum Mode { NONE, MENU, SEARCH }

    // ========== 线程隔离的可变状态 ==========

    private final ThreadLocal<Mode> currentMode = ThreadLocal.withInitial(() -> Mode.NONE);
    private final ThreadLocal<String> pendingToolResultContext = new ThreadLocal<>();

    // ========== 共享的事件引用（由构造器注入，Hook 内只读） ==========

    private final SupplyPlanModelEvent supplyPlanModelEvent;
    private final DecisionMarkEvent decisionMarkEvent;

    // ========== 静态规则模板（不可变） ==========

    private static final String MENU_EXPERT_PROMPT =
            "【高级菜单编排专家】(当前已激活)\n" +
            "1. 必须平衡菜品的烹饪方式（如一蒸、一炒、一汤）。\n" +
            "2. 严禁出现食材重复（如两道菜都含豆腐）。\n" +
            "3. 必须符合季节性饮食逻辑。";

    private static final String SEARCH_EXPERT_PROMPT =
            "【高级搜索核验专家】(当前已激活)\n" +
            "1. 必须优先核实菜品的卡路里（800kcal阈值）与过敏原。\n" +
            "2. 搜索逻辑必须闭环：若结果含糊，必须再次调用 search 补充。\n" +
            "3. 必须标注数据来源的可靠性。";

    // ========== 构造器 ==========

    /**
     * 推荐构造方式：通过 SessionContext 获取 DTO。
     */
    public DynamicPickDishPromptHook(SessionContext sessionContext) {
        this.supplyPlanModelEvent = sessionContext.get(SupplyPlanModelEvent.class);
        this.decisionMarkEvent = sessionContext.get(DecisionMarkEvent.class);
    }

    /**
     * 直接传入 DTO（兼容旧调用方式）。
     */
    public DynamicPickDishPromptHook(SupplyPlanModelEvent supplyPlanModelEvent, DecisionMarkEvent decisionMarkEvent) {
        this.supplyPlanModelEvent = supplyPlanModelEvent;
        this.decisionMarkEvent = decisionMarkEvent;
    }

    /**
     * 无参构造器（DecisionMarkAgent.setupCustomHints 中使用）。
     */
    public DynamicPickDishPromptHook() {
        this.supplyPlanModelEvent = null;
        this.decisionMarkEvent = null;
    }

    // ========== 兼容原有 builder 调用方式 ==========

    public static DynamicPickDishPromptHookBuilder builder() {
        return new DynamicPickDishPromptHookBuilder();
    }

    public static class DynamicPickDishPromptHookBuilder {
        private SupplyPlanModelEvent supplyPlanModelEvent;
        private DecisionMarkEvent decisionMarkEvent;

        public DynamicPickDishPromptHookBuilder supplyPlanModelEvent(SupplyPlanModelEvent event) {
            this.supplyPlanModelEvent = event;
            return this;
        }

        public DynamicPickDishPromptHookBuilder decisionMarkEvent(DecisionMarkEvent event) {
            this.decisionMarkEvent = event;
            return this;
        }

        public DynamicPickDishPromptHook build() {
            return new DynamicPickDishPromptHook(supplyPlanModelEvent, decisionMarkEvent);
        }
    }

    // ========== Hook 事件处理 ==========

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreActingEvent e) {
            handlePreActing(e);
        } else if (event instanceof PostActingEvent e) {
            handlePostActing(e);
        } else if (event instanceof PreReasoningEvent e) {
            handlePreReasoning(e);
        } else if (event instanceof PostReasoningEvent e) {
            handlePostReasoning(e);
        }
        return Mono.just(event);
    }

    // ======================== PreActing: 模式切换 ========================

    private void handlePreActing(PreActingEvent event) {
        String toolName = extractToolName(event);

        if ("menuprompt".equals(toolName)) {
            currentMode.set(Mode.MENU);
            log.info(">>> [Hook状态] 切换至 MENU 模式");
        } else if ("searchprompt".equals(toolName)) {
            currentMode.set(Mode.SEARCH);
            log.info(">>> [Hook状态] 切换至 SEARCH 模式");
        } else {
            currentMode.set(Mode.NONE);
            log.info(">>> [Hook状态] 已卸载其他模式");
        }
    }

    // ======================== PostActing: 捕获工具返回 ========================

    private void handlePostActing(PostActingEvent event) {
        String toolName = extractToolName(event);
        Msg resultMsg = event.getToolResultMsg();

        if (resultMsg != null && ("menu".equals(toolName) || "search".equals(toolName))) {
            pendingToolResultContext.set(String.format(
                    "【%s 现在报告】：%s\n" +
                    "【推理指令】：基于当前已激活的专家规则，对此数据进行逻辑推演。然后输出合理的参数，然后调用工具。",
                    toolName.toUpperCase(), resultMsg.getContent().toString()));
            log.info(">>> [Hook] 已捕获 {} 数据，准备单次注入", toolName);
        }
    }

    // ======================== PreReasoning: 注入专家规则 + 运行时上下文 ========================

    private void handlePreReasoning(PreReasoningEvent event) {
        Mode mode = currentMode.get();
        String context = pendingToolResultContext.get();

        // ★ 检测 Pipeline Phase 1 完成 → 注入 Pipeline 专家规则
        if (decisionMarkEvent != null && decisionMarkEvent.isPipelinePhase1Completed()) {
            String pipelineRules = decisionMarkEvent.getPendingPipelineRules();
            if (pipelineRules != null && !pipelineRules.isBlank()) {
                injectSystemPrompt(event, pipelineRules, "pipeline_expert_rules");
                log.info(">>> [DynamicPickDish] Pipeline 规则已注入到 LLM 输入");
                // 注入后清除标记，让 Phase 2 放行
                // 注意：不在这里清除，由 PipelinePhaseGuardHook 在 Phase 2 完成后清除
            } else {
                log.warn(">>> [DynamicPickDish] pipelinePhase1Completed=true 但 pendingPipelineRules 为空");
            }
        } else if (decisionMarkEvent == null) {
            log.warn(">>> [DynamicPickDish] decisionMarkEvent 为 null，无法检测 Pipeline 状态");
        }

        // 只有在非 NONE 模式或有待处理上下文时才注入
        if (mode == Mode.NONE && context == null) return;

        StringBuilder sb = new StringBuilder();

        if (mode == Mode.MENU) {
            sb.append(MENU_EXPERT_PROMPT).append("\n");
        } else if (mode == Mode.SEARCH) {
            sb.append(SEARCH_EXPERT_PROMPT).append("\n");
        }

        if (context != null) {
            sb.append(context);
        }

        // ★ 原 12 行注入逻辑 → 1 行（带标签包装）
        injectSystemPrompt(event, sb.toString(), "runtime_expert_rule");
        logPhase(event, "已注入专家规则");
    }

    // ======================== PostReasoning: 阅后即焚清洗 ========================

    private void handlePostReasoning(PostReasoningEvent event) {
        try {
            // ★ 原 20 行 cleanResponse → 逐个 stripTagContent
            stripTagContent(event, "runtime_expert_rule");
            stripTagContent(event, "menu_expert_rule");
            stripTagContent(event, "search_expert_rule");
            stripTagContent(event, "runtime_context");

            // 销毁单次上下文
            if (pendingToolResultContext.get() != null) {
                pendingToolResultContext.remove();
                log.debug(">>> [Hook] 单次上下文已销毁");
            }
        } finally {
            // 清理 ThreadLocal，防止线程池复用时的内存泄漏和状态污染
            currentMode.remove();
            pendingToolResultContext.remove();
        }
    }
}
