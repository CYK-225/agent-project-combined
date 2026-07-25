package org.example.skillOpt.env;

import java.util.List;
import java.util.Map;

/**
 * 环境适配器接口 — 抽象不同 benchmark 的 rollout 执行和评分逻辑。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li><b>单轮模式</b>：Agent 直接返回最终答案，无工具调用</li>
 *   <li><b>多轮模式</b>：Agent 可调用工具，收集完整轨迹</li>
 * </ul>
 *
 * @author zhilin
 */
public interface EnvAdapter {

    /**
     * 适配器类型标识
     */
    String getAdapterType();

    /**
     * 是否支持工具调用（多轮 rollout）
     */
    default boolean supportsToolCalling() {
        return false;
    }

    /**
     * 准备训练 batch
     */
    List<Map<String, Object>> prepareTrainBatch(Object rawData, int batchSize);

    /**
     * 准备验证 batch
     */
    List<Map<String, Object>> prepareValBatch(Object rawData, int batchSize);

    /**
     * 执行单次 rollout（由 Node 调用，内部可使用 Agent）
     */
    RolloutResult executeRollout(RolloutContext context);

    /**
     * 执行多轮 rollout（带工具调用）。
     * <p>
     * 默认实现回退到单轮模式。
     */
    default RolloutResult executeToolRollout(RolloutContext context, int maxSteps) {
        return executeRollout(context);
    }

    /**
     * 精确匹配分数（0.0 或 1.0）
     */
    double hardScore(RolloutResult result);

    /**
     * 部分信用分数（0.0~1.0）
     */
    double softScore(RolloutResult result);

    /**
     * 构建 rollout 提示词
     */
    String buildRolloutPrompt(Map<String, Object> taskInstance, String skill);

    /**
     * 获取可用工具列表（用于提示 Agent）。
     * 默认返回空列表。
     */
    default List<String> getAvailableTools() {
        return List.of();
    }

    /**
     * 解析 Agent 输出的动作，判断是否是工具调用。
     * 默认返回 null（非工具调用）。
     */
    default ToolAction parseToolAction(String agentOutput) {
        return null;
    }

    /**
     * 执行工具动作。
     * 默认返回错误提示。
     */
    default ToolFeedback executeToolAction(ToolAction action, Map<String, Object> context) {
        return new ToolFeedback(false, "Tool calling not supported", null);
    }
}
