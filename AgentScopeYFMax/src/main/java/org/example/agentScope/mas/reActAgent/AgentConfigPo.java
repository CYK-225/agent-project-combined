package org.example.agentScope.mas.reActAgent;


import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct Agent 配置对象 (Configuration POJO)。
 * <p>
 * 该类用于封装创建 ReActAgent 所需的所有参数。
 * 设计初衷是为了将配置数据（可能来自 YAML/JSON/数据库）与构建逻辑解耦。
 * </p>
 *
 * @author zzh
 * @version 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentConfigPo {

    /* ============================ 基础元数据 ============================ */

    /** 智能体的唯一名称 (必填) */
    private String name;

    /** 智能体的描述信息，用于多智能体协作时的自我介绍 */
    private String description;

    /** 系统提示词 (System Prompt)，定义智能体的人设和基础行为准则 */
    private String sysPrompt;

    /* ============================ 核心组件 ============================ */

    /**
     * 模型实例 (LLM)。
     * /* 注意：在实际工程中，这里也可以存模型名称字符串，由工厂类去查找 Bean */

    private Model model;

    /** 工具箱，包含智能体可调用的所有工具 */
    private Toolkit toolkit;

    /** 记忆组件，用于存储对话历史 */
    private Memory memory;

    /** hook组件 */
    private List<Hook> hooks;

    /* ============================ 执行控制 ============================ */

    /**
     * 最大思考/行动迭代次数。
     * /* 使用 Integer 包装类型，null 表示使用框架默认值 (通常是 10)
     */
    private Integer maxIters;

    /** 是否检查并发运行状态，默认为 true */
    private Boolean checkRunning;

    /** 模型调用的执行配置 (超时、重试等) */
    private ExecutionConfig modelExecutionConfig;

    /** 工具调用的执行配置 (超时、重试等) */
    private ExecutionConfig toolExecutionConfig;

    /* ============================ 高级功能 ============================ */

    /** 计划本 (PlanNotebook)，用于长程任务规划 */
    private PlanNotebook planNotebook;

    /** 工具执行上下文，用于传递用户信息等业务数据 */
    private ToolExecutionContext toolExecutionContext;

    /** 长期记忆组件，用于存储对话历史 */
    private LongTermMemory longTermMemory;

    /** 长期记忆模式 */
    private LongTermMemoryMode longTermMemoryMode;
    /** skillBox */
    private SkillBox skillBox;
    /**
     * 结构化输出
     */
    private StructuredOutputReminder structuredOutputReminder;

    /* ============================ Override 合并 ============================ */

    /**
     * 将 override 中的非 null 字段覆盖到当前 config（默认追加模式）。
     * <p>
     * 默认 {@code appendMode = true}，可集合化的字段（hooks、toolkit、skillBox）
     * 使用追加语义；不可追加的字段（sysPrompt、model 等）始终覆盖。
     *
     * @param override 外部传入的覆盖配置，为 null 时直接返回 this
     * @return this（支持链式调用）
     * @see #mergeOverrides(AgentConfigPo, boolean)
     */
    public AgentConfigPo mergeOverrides(AgentConfigPo override) {
        return mergeOverrides(override, true);
    }

    /**
     * 将 override 中的非 null 字段合并到当前 config。
     * <ul>
     *   <li>null override → 不做任何事</li>
     *   <li>null 字段 → 保持当前值不变</li>
     *   <li><b>不可追加的字段</b>（sysPrompt、model、memory 等）→ 始终覆盖</li>
     *   <li><b>可集合化的字段</b>（hooks、toolkit、skillBox）：
     *     <ul>
     *       <li>{@code appendMode = true} → 追加到已有集合之后</li>
     *       <li>{@code appendMode = false} → 整体替换</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param override    外部传入的覆盖配置，为 null 时直接返回 this
     * @param appendMode  true = 追加模式（默认），false = 覆盖模式
     * @return this（支持链式调用）
     */
    public AgentConfigPo mergeOverrides(AgentConfigPo override, boolean appendMode) {
        if (override == null) return this;

        // ==================== 基础元数据（始终覆盖） ====================
        if (override.getName() != null)                          this.name = override.getName();
        if (override.getDescription() != null)                   this.description = override.getDescription();
        if (override.getSysPrompt() != null)                     this.sysPrompt = override.getSysPrompt();

        // ==================== 核心组件 ====================
        if (override.getModel() != null)                         this.model = override.getModel();
        if (override.getMemory() != null)                        this.memory = override.getMemory();

        // hooks：追加 / 覆盖
        if (override.getHooks() != null && !override.getHooks().isEmpty()) {
            if (appendMode) {
                if (this.hooks == null) this.hooks = new ArrayList<>();
                this.hooks.addAll(override.getHooks());
            } else {
                this.hooks = new ArrayList<>(override.getHooks());
            }
        }

        // toolkit：无法提取已注册工具，始终整体替换
        if (override.getToolkit() != null) {
            this.toolkit = override.getToolkit();
        }

        // skillBox：追加 / 覆盖
        if (override.getSkillBox() != null) {
            if (appendMode && this.skillBox != null) {
                for (String skillId : override.getSkillBox().getAllSkillIds()) {
                    AgentSkill skill = override.getSkillBox().getSkill(skillId);
                    if (skill != null) {
                        this.skillBox.registerSkill(skill);
                    }
                }
            } else {
                this.skillBox = override.getSkillBox();
            }
        }

        // ==================== 执行控制（逐字段合并） ====================
        if (override.getMaxIters() != null)                      this.maxIters = override.getMaxIters();
        if (override.getCheckRunning() != null)                  this.checkRunning = override.getCheckRunning();
        if (override.getModelExecutionConfig() != null)           this.modelExecutionConfig = ExecutionConfig.mergeConfigs(override.getModelExecutionConfig(), this.modelExecutionConfig);
        if (override.getToolExecutionConfig() != null)            this.toolExecutionConfig = ExecutionConfig.mergeConfigs(override.getToolExecutionConfig(), this.toolExecutionConfig);

        // ==================== 高级功能 ====================
        if (override.getPlanNotebook() != null)                  this.planNotebook = override.getPlanNotebook();
        // toolExecutionContext：追加 / 覆盖
        if (override.getToolExecutionContext() != null) {
            if (appendMode && this.toolExecutionContext != null) {
                this.toolExecutionContext = ToolExecutionContext.merge(override.getToolExecutionContext(), this.toolExecutionContext);
            } else {
                this.toolExecutionContext = override.getToolExecutionContext();
            }
        }
        if (override.getLongTermMemory() != null)                 this.longTermMemory = override.getLongTermMemory();
        if (override.getLongTermMemoryMode() != null)             this.longTermMemoryMode = override.getLongTermMemoryMode();
        if (override.getStructuredOutputReminder() != null)       this.structuredOutputReminder = override.getStructuredOutputReminder();

        return this;
    }
}