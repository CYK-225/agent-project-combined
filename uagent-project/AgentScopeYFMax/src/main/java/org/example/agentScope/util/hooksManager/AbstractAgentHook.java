package org.example.agentScope.util.hooksManager;

import io.agentscope.core.hook.*;
import reactor.core.publisher.Mono;

/**
 * 终极 Hook 骨架：支持自定义优先级，并路由了 AgentScope 的全部 12 个生命周期事件。
 */
public abstract class AbstractAgentHook implements Hook, AgentHookToolkit {

    // 优先级控制：数字越小，越先执行 (AgentScope 默认 5)
    private final int priority;

    public AbstractAgentHook(int priority) {
        this.priority = priority;
    }

    public AbstractAgentHook() {
        this(5); // 默认优先级
    }

    @Override
    public int priority() {
        return this.priority;
    }

    @Override
    public final <T extends HookEvent> Mono<T> onEvent(T event) {
        // 使用 Java 模式匹配彻底消除强制类型转换
        if (event instanceof PreCallEvent e) {
            handlePreCall(e);
        } else if (event instanceof PostCallEvent e) {
            handlePostCall(e);
        } else if (event instanceof PreReasoningEvent e) {
            handlePreReasoning(e);
        } else if (event instanceof PostReasoningEvent e) {
            handlePostReasoning(e);
        } else if (event instanceof ReasoningChunkEvent e) {
            handleReasoningChunk(e);
        } else if (event instanceof PreActingEvent e) {
            handlePreActing(e);
        } else if (event instanceof PostActingEvent e) {
            handlePostActing(e);
        } else if (event instanceof ActingChunkEvent e) {
            handleActingChunk(e);
        } else if (event instanceof PreSummaryEvent e) {
            handlePreSummary(e);
        } else if (event instanceof PostSummaryEvent e) {
            handlePostSummary(e);
        } else if (event instanceof SummaryChunkEvent e) {
            handleSummaryChunk(e);
        } else if (event instanceof ErrorEvent e) {
            handleError(e);
        }
        return Mono.just(event);
    }

    // ==========================================
    // 供子类按需重写的生命周期锚点 (空实现)
    // ==========================================
    /**
     * <b>事件类型</b>：PreCallEvent <br/>
     * <b>时机</b>：智能体调用前 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：智能体开始处理之前（可修改输入消息）
     *
     * @param event PreCallEvent
     */
    protected void handlePreCall(PreCallEvent event) {

    }

    /**
     * <b>事件类型</b>：PostCallEvent <br/>
     * <b>时机</b>：智能体调用后 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：智能体完成响应之后（可修改最终消息）
     *
     * @param event PostCallEvent
     */
    protected void handlePostCall(PostCallEvent event) {}

    /**
     * <b>事件类型</b>：PreReasoningEvent <br/>
     * <b>时机</b>：推理前 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：LLM 推理之前（可修改输入消息）
     *
     * @param event PreReasoningEvent
     */
    protected void handlePreReasoning(PreReasoningEvent event) {}

    /**
     * <b>事件类型</b>：PostReasoningEvent <br/>
     * <b>时机</b>：推理后 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：LLM 推理完成之后（可修改推理结果）
     *
     * @param event PostReasoningEvent
     */
    protected void handlePostReasoning(PostReasoningEvent event) {}

    /**
     * <b>事件类型</b>：ReasoningChunkEvent <br/>
     * <b>时机</b>：推理流式期间 <br/>
     * <b>可修改</b>：❌ <br/>
     * <b>描述</b>：流式推理的每个块（仅通知）
     *
     * @param event ReasoningChunkEvent
     */
    protected void handleReasoningChunk(ReasoningChunkEvent event) {}

    /**
     * <b>事件类型</b>：PreActingEvent <br/>
     * <b>时机</b>：工具执行前 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：工具执行之前（可修改工具参数）
     *
     * @param event PreActingEvent
     */
    protected void handlePreActing(PreActingEvent event) {}

    /**
     * <b>事件类型</b>：PostActingEvent <br/>
     * <b>时机</b>：工具执行后 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：工具执行之后（可修改工具结果）
     *
     * @param event PostActingEvent
     */
    protected void handlePostActing(PostActingEvent event) {}

    /**
     * <b>事件类型</b>：ActingChunkEvent <br/>
     * <b>时机</b>：工具流式期间 <br/>
     * <b>可修改</b>：❌ <br/>
     * <b>描述</b>：工具执行进度块（仅通知）
     *
     * @param event ActingChunkEvent
     */
    protected void handleActingChunk(ActingChunkEvent event) {}

    /**
     * <b>事件类型</b>：PreSummaryEvent <br/>
     * <b>时机</b>：摘要生成前 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：达到最大迭代次数时，摘要生成之前
     *
     * @param event PreSummaryEvent
     */
    protected void handlePreSummary(PreSummaryEvent event) {}

    /**
     * <b>事件类型</b>：PostSummaryEvent <br/>
     * <b>时机</b>：摘要生成后 <br/>
     * <b>可修改</b>：✅ <br/>
     * <b>描述</b>：摘要生成完成之后（可修改摘要结果）
     *
     * @param event PostSummaryEvent
     */
    protected void handlePostSummary(PostSummaryEvent event) {}

    /**
     * <b>事件类型</b>：SummaryChunkEvent <br/>
     * <b>时机</b>：摘要流式期间 <br/>
     * <b>可修改</b>：❌ <br/>
     * <b>描述</b>：摘要流式生成的每个块（仅通知）
     *
     * @param event SummaryChunkEvent
     */
    protected void handleSummaryChunk(SummaryChunkEvent event) {}

    /**
     * <b>事件类型</b>：ErrorEvent <br/>
     * <b>时机</b>：发生错误时 <br/>
     * <b>可修改</b>：❌ <br/>
     * <b>描述</b>：发生错误时（仅通知）
     *
     * @param event ErrorEvent
     */
    protected void handleError(ErrorEvent event) {}
}
