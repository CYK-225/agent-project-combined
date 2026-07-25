package org.example.agent.Sp.hook.decisionMarkAgentHook;

import io.agentscope.core.hook.*;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;
import org.example.agentScope.util.hooksManager.SessionContext;

/**
 * 统一流水线守卫 Hook —— 负责拦截和秒退。
 * <p>
 * <b>职责：</b>
 * <ul>
 *   <li>PreActing：设置 pipelineHint=true → 工具返回 dummy(dishId=-1)</li>
 *   <li>PostActing：检测到秒退后，构建规则存入 DTO</li>
 * </ul>
 * <p>
 * <b>提示词注入</b>由 {@link DynamicPickDishPromptHook} 在 PreReasoning 阶段完成。
 * <p>
 * <b>完整流程：</b>
 * <pre>
 * LLM → DishSearchTool(脏参数)
 *   → PreActing: 设置 pipelineHint=true
 *   → 工具返回 dummy(dishId=-1)
 *   → PostActing: 构建规则存入 DTO
 *   → PreReasoning: DynamicPickDishPromptHook 注入规则（含 -1 ID 说明）
 *   → LLM 推理：看到 dummy 结果 + 规则 → 重新调用 DishSearchTool(合理参数)
 *   → 正常执行
 * </pre>
 *
 * @author zhilin
 */
@Slf4j
public class PipelinePhaseGuardHook extends AbstractAgentHook {

    private static final String TOOL_SEARCH    = "DishSearchTool";
    private static final String TOOL_MENU      = "MenuManagementTool";
    private static final String TOOL_BLACKLIST = "manageBlacklistTool";

    private final DecisionMarkEvent decisionMarkEvent;

    /** 是否处于 Phase 1 → Phase 2 的等待中 */
    private boolean waitingPhase2;

    public PipelinePhaseGuardHook(SessionContext sessionContext) {
        this.decisionMarkEvent = sessionContext.get(DecisionMarkEvent.class);
    }

    public PipelinePhaseGuardHook() {
        this.decisionMarkEvent = null;
    }

    // ═══════════════ PreActing: 设置标记，通知工具秒退 ═══════════════

    @Override
    protected void handlePreActing(PreActingEvent event) {
        String toolName = extractToolName(event);
        if (!isManagedTool(toolName)) return;

        // 规则已注入 → 判断是否同工具
        if (waitingPhase2) {
            if (toolName.equals(decisionMarkEvent != null ? decisionMarkEvent.getInterceptedToolName() : null)) {
                // 同工具 → Phase 2 放行
                log.info(">>> [PipelineGuard] {} Phase 2 调用 → 放行", toolName);
                return;
            } else {
                // 不同工具 → 清除状态，重新开始
                log.info(">>> [PipelineGuard] 切换工具 {} → 清除 Pipeline 状态", toolName);
                resetState();
                // 继续往下走，不设置 pipelineHint（不同工具不需要拦截）
                return;
            }
        }

        // Phase 1 拦截：设置标记，通知工具返回 dummy(dishId=-1)
        if (decisionMarkEvent != null) {
            decisionMarkEvent.setPipelineHint(true);
        }
        
        log.info(">>> [PipelineGuard] {} Phase 1 → 已设置拦截标记", toolName);
    }

    // ═══════════════ PostActing: 检测到秒退后，注入提示信息 + 构建规则 ═══════════════

    @Override
    protected void handlePostActing(PostActingEvent event) {
        String toolName = extractToolName(event);
        if (!isManagedTool(toolName)) return;

        // Phase 2 完成 → 保持状态，等待不同工具调用时清除
        if (waitingPhase2) {
            log.info(">>> [PipelineGuard] {} Phase 2 完成 → 保持状态，等待切换", toolName);
            return;
        }

        // Phase 1 秒退 → 检测到 pipelineHint 标记后，构建规则存入 DTO
        // 工具已返回 dummy(dishId=-1)，LLM 会看到结果，规则由 DynamicPickDishPromptHook 注入
        if (decisionMarkEvent != null && decisionMarkEvent.isPipelineHint()) {
            // 构建规则
            String rules = buildExpertRules(toolName);
            decisionMarkEvent.setPendingPipelineRules(rules);
            // 标记 Phase 1 完成
            decisionMarkEvent.setPipelinePhase1Completed(true);
            // 记录被拦截的工具名
            decisionMarkEvent.setInterceptedToolName(toolName);
            // 清除 pipelineHint 标记
            decisionMarkEvent.setPipelineHint(false);
            // 标记等待 Phase 2
            waitingPhase2 = true;
            
            log.info(">>> [PipelineGuard] {} Phase 1 秒退完成 → 规则已构建，拦截工具={}", toolName, toolName);
        }
    }

    // ═══════════════ 专家规则构建 ═══════════════

    private String buildExpertRules(String toolName) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n═══ 专家规则（阅后即焚）═══\n");
        sb.append("⚠️ 重要：工具返回的菜品 dishId=-1 表示「正在为你注入专家规则」，不是搜索失败，请勿将-1视为真实菜品，也无需重试搜索。请直接阅读以下规则，然后用正确的参数重新调用 ").append(toolName).append("。\n\n");

        // 根据工具类型注入对应的专家提示词
        String expertPrompt = getExpertPromptByTool(toolName);
        if (expertPrompt != null && !expertPrompt.isBlank()) {
            sb.append(expertPrompt);
        } else {
            // 降级处理：注入基础参数参考
            if (decisionMarkEvent != null) {
                sb.append("【可用参数参考】\n");
                appendIfNotNull(sb, "菜品类型", decisionMarkEvent.getValidDishTypes());
                appendIfNotNull(sb, "辣度",     decisionMarkEvent.getValidSpicinessLevel());
                appendIfNotNull(sb, "工艺",     decisionMarkEvent.getValidFlavors());
                appendIfNotNull(sb, "食材",     decisionMarkEvent.getValidMainIngredients());
            }
        }

        sb.append("\n═══ 请根据以上规则重新调用 ").append(toolName).append(" ═══");
        return sb.toString();
    }

    /**
     * 根据工具类型获取对应的专家提示词
     */
    private String getExpertPromptByTool(String toolName) {
        if (decisionMarkEvent == null) return null;

        try {
            if (TOOL_SEARCH.equals(toolName)) {
                // 菜品搜索专家：SupplyPlanDishSearchProptcraft
                return getSearchExpertPrompt();
            } else if (TOOL_MENU.equals(toolName) || TOOL_BLACKLIST.equals(toolName)) {
                // 菜品挑选专家：SupplyPlanMenuManagementReportPrompt
                return getMenuManagementExpertPrompt();
            }
        } catch (Exception e) {
            log.warn("[PipelineGuard] 构建专家提示词失败: tool={}, error={}", toolName, e.getMessage());
        }

        return null;
    }

    /**
     * 获取菜品搜索专家提示词
     */
    private String getSearchExpertPrompt() {
        return """
                【菜品推荐算法专家规则】
                
                ## 核心物理定律
                1. 必须维护3个小荤，2个大荤，3个纯素的菜单结构。
                2. 用户没有提及具体菜名，不允许擅自填充菜名。
                3. StandardDishTypes，StandSpicinessLevel，StandardFlavors，StandardMainIngredients 这四个参数必须来源<StandardLibrary>。
                
                ## 标准库
                %s
                
                ## 搜索规则
                1. 参数值必须来自上方标准库，禁止编造。
                2. 搜索结果必须交给管理专家审核，不能直接输出。
                3. 参数格式：JSON 数组，如 ["炒","炖"]。
                4. top_k 范围 1~8。
                5. mainIngredient 列表长度要足够（建议 5-10 个以上），充分利用 IN 查询的广度。
                
                ## 维度权重
                - 属性维度 (dimensionWeightsAttribute)：对应菜品口味、食材等。
                - 销售维度 (dimensionWeightsSales)：对应历史销量、消耗率。
                - 用户维度 (dimensionWeightsUsers)：对应历史评分、好评率。
                - 预测维度 (dimensionWeightsRevenue)：对应预测营收、货损。
                """.formatted(getStandardLibrary());
    }

    /**
     * 获取菜品挑选专家提示词
     */
    private String getMenuManagementExpertPrompt() {
        return """
                【菜单编排管理专家规则】
                
                ## 核心物理定律
                1. 必须维护3个小荤，2个大荤，3个纯素的菜单结构。
                2. 用户没有提及具体菜名，不允许擅自填充菜名。
                3. 参数必须来源<StandardLibrary>。
                
                ## 管理规则
                1. operation 只能是 "add" 或 "remove"。
                2. dish_ids 为数字数组，如 [101,202,303]。
                3. 添加时菜品必须来自搜索结果。
                4. 移除时自动加入黑名单。
                
                ## 决策逻辑
                1. 优先补充菜单结构缺口（如缺大荤、缺素菜）。
                2. 考虑菜品多样性（烹饪方式、食材不重复）。
                3. 符合季节性饮食逻辑。
                
                ## 标准库
                %s
                """.formatted(getStandardLibrary());
    }

    /**
     * 获取标准库内容
     */
    private String getStandardLibrary() {
        StringBuilder sb = new StringBuilder();
        if (decisionMarkEvent != null) {
            appendIfNotNull(sb, "菜品类型", decisionMarkEvent.getValidDishTypes());
            appendIfNotNull(sb, "辣度",     decisionMarkEvent.getValidSpicinessLevel());
            appendIfNotNull(sb, "工艺",     decisionMarkEvent.getValidFlavors());
            appendIfNotNull(sb, "食材",     decisionMarkEvent.getValidMainIngredients());
        }
        return sb.toString();
    }

    private static void appendIfNotNull(StringBuilder sb, String label, String value) {
        if (value != null) sb.append("  - ").append(label).append(": ").append(value).append("\n");
    }

    // ═══════════════ 辅助 ═══════════════

    private boolean isManagedTool(String toolName) {
        return TOOL_SEARCH.equals(toolName)
                || TOOL_MENU.equals(toolName)
                || TOOL_BLACKLIST.equals(toolName);
    }

    private void resetState() {
        waitingPhase2 = false;
        if (decisionMarkEvent != null) {
            decisionMarkEvent.setPipelineHint(false);
            decisionMarkEvent.setPipelinePhase1Completed(false);
            decisionMarkEvent.setPendingPipelineRules(null);
        }
    }
}
