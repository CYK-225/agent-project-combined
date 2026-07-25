package org.example.agent.Sp.dataModel;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.agentscope.core.state.State;
import lombok.Data;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;

import java.time.LocalDateTime;
import java.util.HashMap;

/**
 * 决策标记事件，承载搜索结果与筛选条件。
 * <p>
 * 实现 {@link State} 标记接口，支持通过 {@link SessionContext} 自动持久化到 Session。
 */
@Data
public class DecisionMarkEvent implements State {
    // 原始检索出来的菜品信息key为id,value为具体的值
    @JsonPropertyDescription("原始检索出来的菜品信息,key为id,value为菜品的值(类型为DishInfoAndScore)，只允许搜索模式的时候编辑")
    private HashMap<Long, DishInfoAndScore> searchDishList=new HashMap<>();

    /**
     * 工艺可选列表（从数据库中获取）
     */
    String validFlavors;
    /**
     * 辣度等级（从数据库中获取）
     */
    String validSpicinessLevel;
    /**
     * 食材类型（从数据库中获取）
     */
    String validDishTypes;
    /**
     * 食材获取（从数据库中获取）
     */
    String validMainIngredients;

    /**
     * Pipeline 守卫标志。
     * <p>
     * Hook 在 PreActing 设为 true → 工具方法首行检查 → 秒退。
     * 工具检查后自行清除（设为 false）。
     * <p>
     * 对 LLM 完全不可见：不在 @ToolParam 中，不暴露给工具 schema。
     */
    private boolean pipelineHint;

    /**
     * Pipeline Phase 1 完成标志。
     * <p>
     * 工具秒退后设为 true → DynamicPickDishPromptHook 在 PreReasoning 检测到后注入专家规则。
     * 仅在不同工具调用时清除。
     */
    private boolean pipelinePhase1Completed;

    /**
     * 被拦截的工具名称。
     * <p>
     * Phase 1 时记录被拦截的工具名，用于：
     * <ul>
     *   <li>PreActing: 判断是否同工具（同工具放行，不同工具清除状态）</li>
     *   <li>SPStateFeedbackHook: 精确跳过该工具的失败记录</li>
     * </ul>
     */
    private String interceptedToolName;

    /**
     * 待注入的 Pipeline 专家规则。
     * <p>
     * PipelinePhaseGuardHook 在 PostActing 构建规则后存入此字段，
     * DynamicPickDishPromptHook 在 PreReasoning 读取并注入。
     */
    private String pendingPipelineRules;

    //消息发送时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime messageSendTime;

}
