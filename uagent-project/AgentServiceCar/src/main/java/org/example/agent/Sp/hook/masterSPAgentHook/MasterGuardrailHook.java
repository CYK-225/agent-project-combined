package org.example.agent.Sp.hook.masterSPAgentHook;

import io.agentscope.core.hook.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;
import org.example.agentScope.util.hooksManager.SessionContext;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 总控 Agent 护栏 Hook。
 * <p>
 * 在推理前注入动态时间感知，在推理后清洗"内心戏"标签（阅后即焚）。
 * 使用 {@link org.example.agentScope.util.hooksManager.AgentHookToolkit} 默认方法
 * 消除重复的注入和清洗代码。
 *
 * @author zhilin
 */
@Slf4j
public class MasterGuardrailHook extends AbstractAgentHook {

    private final SessionContext sessionContext;

    /**
     * 推荐构造方式：通过 SessionContext 获取 DTO。
     *
     * @param sessionContext 会话上下文
     */
    public MasterGuardrailHook(SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    /**
     * 兼容旧调用方式：直接传入 DTO。
     *
     * @param supplyPlanModelEvent 供给计划模型事件
     * @param decisionMarkEvent    决策标记事件
     */
    public MasterGuardrailHook(SupplyPlanModelEvent supplyPlanModelEvent, DecisionMarkEvent decisionMarkEvent) {
        this.sessionContext = new SessionContext();
        this.sessionContext.add(supplyPlanModelEvent);
        this.sessionContext.add(decisionMarkEvent);
    }

    // ======================== PreReasoning: 动态注入时间感知 ========================

    @Override
    protected void handlePreReasoning(PreReasoningEvent event) {
        String currentTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String dynamicContext = "【系统动态注入(阅后即焚)】: 当前系统时间是 "
                + currentTime
                + "。请在对话中保持对时间的感知（如晚上打招呼说晚上好）。";

        // ★ 原 15 行注入逻辑 → 1 行（带标签包装）
        injectSystemPrompt(event, dynamicContext, "system_notification");

        logPhase(event, "已在 Payload 中隐式注入系统时间上下文");
    }

    // ======================== PostReasoning: "内心戏"清洗兜底 ========================

    @Override
    protected void handlePostReasoning(PostReasoningEvent event) {
        // ★ 原 20 行清洗逻辑 → 1 行
        cleanThinkingTags(event);
    }

    // ======================== PostActing: 工具执行后状态检查（可选） ========================

    @Override
    protected void handlePostActing(PostActingEvent event) {
        // 如果需要从 SessionContext 获取 DTO 做工具执行后检查，在这里补充
        // SupplyPlanModelEvent supplyEvent = sessionContext.get(SupplyPlanModelEvent.class);
    }
}
