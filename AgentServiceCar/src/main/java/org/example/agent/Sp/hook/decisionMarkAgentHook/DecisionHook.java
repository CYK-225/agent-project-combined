package org.example.agent.Sp.hook.decisionMarkAgentHook;

import io.agentscope.core.hook.PreReasoningEvent;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.agent.Sp.prompt.decisionMarkAgentPrompt.SupplyPlanMenuManagementToolsResultPrompt;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;
import org.example.agentScope.util.hooksManager.SessionContext;

/**
 * 决策标记 Hook —— 在推理前注入菜单管理工具的结构化报告。
 * <p>
 * 通过 {@link SessionContext} 获取 {@link SupplyPlanModelEvent}，
 * 使用 {@link org.example.agentScope.util.hooksManager.AgentHookToolkit#injectSystemPrompt}
 * 完成 System Prompt 注入。
 *
 * @author zhilin
 */
@Slf4j
public class DecisionHook extends AbstractAgentHook {

    private final SessionContext sessionContext;

    /**
     * 推荐构造方式：通过 SessionContext 注入 DTO。
     *
     * @param sessionContext 会话上下文
     */
    public DecisionHook(SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    /**
     * 兼容旧调用方式：直接传入 SupplyPlanModelEvent。
     *
     * @param supplyPlanModelEvent 供给计划模型事件
     */
    public DecisionHook(SupplyPlanModelEvent supplyPlanModelEvent) {
        SessionContext sc = new SessionContext();
        sc.add(supplyPlanModelEvent);
        this.sessionContext = sc;
    }

    @Override
    protected void handlePreReasoning(PreReasoningEvent event) {
        SupplyPlanModelEvent supplyEvent = sessionContext.get(SupplyPlanModelEvent.class);
        if (supplyEvent == null) {
            log.warn(">>> [DecisionHook] SessionContext 中缺少 SupplyPlanModelEvent");
            return;
        }

        var prompt = new SupplyPlanMenuManagementToolsResultPrompt();
        String text = prompt.generateStructureReport(supplyEvent);

        if (text == null || text.isBlank()) {
            log.warn(">>> [DecisionHook] 获取到内容为空的提示词，请检查！");
            return;
        }

        log.debug(">>> [DecisionHook] 获取到即将发送给大模型的 Payload ：{}", text);

        // 使用 toolkit 默认方法注入（原 20 行内联逻辑 → 1 行）
        injectSystemPrompt(event, text);

        log.info(">>> [DecisionHook] 已在请求前成功注入提示词上下文。");
    }
}
