package org.example.agentScope.mas.reActAgent;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * ReAct 智能体工厂类 (ReActAgentFactory)。
 * <p>
 * 该工厂类提供基于配置对象 (POJO) 构建 {@link ReActAgent} 的能力。
 * 它采用延迟构建 (Lazy Build) 策略，允许通过链式调用动态修改配置，
 * 最终生成一个完全配置好的智能体实例。
 * </p>
 *
 * <h3>主要功能：</h3>
 * <ul>
 * <li><b>配置驱动：</b> 通过 {@link AgentConfigPo} 传入初始参数。</li>
 * <li><b>链式覆盖：</b> 支持在构建前覆盖 POJO 中的默认值。</li>
 * <li><b>纯净构建：</b> 仅负责创建 Agent，不涉及 AgentPool 的管理。</li>
 * </ul>

 * <p>AgentBuilderWrapper 配置属性清单：</p>
 * * <strong>核心组件配置</strong>
 * <ul>
 * <li>model: {@code Model} - 核心语言模型，决定智能体的推理和对话能力</li>
 * <li>toolkit: {@code Toolkit} - 工具箱，定义智能体可以调用的外部 API 或函数插件</li>
 *  <li>skillBox: {@code skillBox} - 技能箱，定义智能体的技能</li>
 * <li>planNotebook: {@code PlanNotebook} - 计划本，用于记录任务分解、执行步骤及中间状态</li>
 * </ul>
 * * <strong>记忆系统配置</strong>
 * <ul>
 * <li>memory: {@code Memory} - 短期记忆，存储当前会话的上下文对话历史</li>
 * <li>longTermMemory: {@code LongTermMemory} - 长程记忆，关联向量数据库，实现跨会话知识检索</li>
 * <li>longTermMemoryMode: {@code LongTermMemoryMode} - 长程记忆模式，定义记忆的读取或写入策略</li>
 * </ul>
 * * <strong>执行策略配置</strong>
 * <ul>
 * <li>maxIters: {@code int} - 最大迭代次数，限制智能体解决单个任务的思考步数上限</li>
 * <li>checkRunning: {@code boolean} - 运行状态检查标志，用于任务的并发控制或状态监控</li>
 * <li>modelExecutionConfig: {@code ExecutionConfig} - 模型执行参数，如 Temperature、Token 限制等</li>
 * <li>toolExecutionConfig: {@code ExecutionConfig} - 工具执行参数，如超时、重试次数等</li>
 * <li>toolExecutionContext: {@code ToolExecutionContext} - 工具执行上下文，传递运行时环境变量</li>
 * </ul>
 * * <strong>扩展与拦截</strong>
 * <ul>
 * <li>hooks: {@code List<Hook>} - 钩子函数列表，用于在执行周期的各个阶段注入自定义逻辑</li>
 * </ul>

 * @author AgentScope-Team
 * @version 3.0 (No-Pool Version)
 */
public class ReActAgentFactory {

    /**
     * 工厂入口方法。
     *
     * @param config 初始配置对象，包含构建所需的参数
     * @return AgentBuilderWrapper 构建器包装实例
     */
    public static AgentBuilderWrapper create(AgentConfigPo config) {
        return new AgentBuilderWrapper(config);
    }

    /**
     * 内部构建器包装类。
     * <p>
     * 维护一份配置副本 (effectiveConfig)，所有的链式修改都作用于该副本。
     * 只有调用 build() 时才会实例化底层的 ReActAgent.Builder。
     * </p>
     */
    public static class AgentBuilderWrapper {

        /* ==================== 状态持有 ==================== */

        /**
         * 有效配置对象（构建草稿）。
         * /* 所有的修改都会更新这个对象 */

        private final AgentConfigPo effectiveConfig;

        /* ==================== 构造函数 ==================== */

        /**
         * 初始化包装器。
         * <p>
         * 手动进行浅拷贝，创建一个新的配置实例供构建使用，避免修改传入的原始对象。
         * </p>
         */
        private AgentBuilderWrapper(AgentConfigPo source) {
            if (source == null) {
                throw new IllegalArgumentException("AgentConfigPo cannot be null.");
            }

            // 创建配置副本
            this.effectiveConfig = new AgentConfigPo();

            // 复制属性
            this.effectiveConfig.setName(source.getName());
            this.effectiveConfig.setDescription(source.getDescription());
            this.effectiveConfig.setSysPrompt(source.getSysPrompt());
            this.effectiveConfig.setModel(source.getModel());
            this.effectiveConfig.setToolkit(source.getToolkit());
            this.effectiveConfig.setMemory(source.getMemory());
            this.effectiveConfig.setMaxIters(source.getMaxIters());
            this.effectiveConfig.setCheckRunning(source.getCheckRunning());
            this.effectiveConfig.setModelExecutionConfig(source.getModelExecutionConfig());
            this.effectiveConfig.setToolExecutionConfig(source.getToolExecutionConfig());
            this.effectiveConfig.setPlanNotebook(source.getPlanNotebook());
            this.effectiveConfig.setToolExecutionContext(source.getToolExecutionContext());
            this.effectiveConfig.setLongTermMemory(source.getLongTermMemory());
            this.effectiveConfig.setLongTermMemoryMode(source.getLongTermMemoryMode());
            this.effectiveConfig.setHooks(source.getHooks());
            this.effectiveConfig.setSkillBox(source.getSkillBox());
            this.effectiveConfig.setStructuredOutputReminder(source.getStructuredOutputReminder());

        }

        /* ==================== 链式配置 (修改 effectiveConfig) ==================== */

        /**
         * 修改或设置模型。
         */
        public AgentBuilderWrapper model(Model model) {
            this.effectiveConfig.setModel(model);
            return this;
        }

        /**
         * 修改或设置工具箱。
         */
        public AgentBuilderWrapper toolkit(Toolkit toolkit) {
            this.effectiveConfig.setToolkit(toolkit);
            return this;
        }

        /**
         * 修改或设置记忆组件。
         */
        public AgentBuilderWrapper memory(Memory memory) {
            this.effectiveConfig.setMemory(memory);
            return this;
        }

        /**
         * 修改或设置最大迭代次数。
         */
        public AgentBuilderWrapper maxIters(int maxIters) {
            this.effectiveConfig.setMaxIters(maxIters);
            return this;
        }

        /**
         * 修改或设置检查运行状态标志。
         */
        public AgentBuilderWrapper checkRunning(boolean checkRunning) {
            this.effectiveConfig.setCheckRunning(checkRunning);
            return this;
        }

        /**
         * 修改或设置模型执行配置。
         */
        public AgentBuilderWrapper modelExecutionConfig(ExecutionConfig config) {
            this.effectiveConfig.setModelExecutionConfig(config);
            return this;
        }

        /**
         * 修改或设置工具执行配置。
         */
        public AgentBuilderWrapper toolExecutionConfig(ExecutionConfig config) {
            this.effectiveConfig.setToolExecutionConfig(config);
            return this;
        }

        /**
         * 修改或设置计划本。
         */
        public AgentBuilderWrapper planNotebook(PlanNotebook planNotebook) {
            this.effectiveConfig.setPlanNotebook(planNotebook);
            return this;
        }

        /**
         * 修改或设置工具执行上下文。
         */
        public AgentBuilderWrapper toolExecutionContext(ToolExecutionContext context) {
            this.effectiveConfig.setToolExecutionContext(context);
            return this;
        }
        /**
         * 修改或设置长程记忆组件。
         */
        public AgentBuilderWrapper longTermMemory(LongTermMemory longTermMemory) {
            this.effectiveConfig.setLongTermMemory(longTermMemory);
            return this;
        }
        /**
         * 修改或设置长程记忆模式。
         */
        public AgentBuilderWrapper longTermMemoryMode(LongTermMemoryMode longTermMemoryMode) {
            this.effectiveConfig.setLongTermMemoryMode(longTermMemoryMode);
            return this;
        }
        /**
         * hooks设置
         */
        public AgentBuilderWrapper hooks(List<Hook> hooks){
            this.effectiveConfig.setHooks(hooks);
            return this;
        }
        /**
         * skillBox
         */
        public AgentBuilderWrapper skill(SkillBox skillBox){
            this.effectiveConfig.setSkillBox(skillBox);
            return this;
        }
        public AgentBuilderWrapper structuredOutputReminder(StructuredOutputReminder structuredOutputReminder) {
            this.effectiveConfig.setStructuredOutputReminder(structuredOutputReminder);
            return this;
        }

        /* ==================== 核心构建逻辑 ==================== */

        /**
         * 构建 ReActAgent 实例。
         * <p>
         * 根据最终的 effectiveConfig 创建并返回 Agent 实例。
         * </p>
         *
         * @return 构建完成的 {@link ReActAgent} 实例
         * @throws IllegalStateException 如果缺少必要的配置（如 name）
         */
        public ReActAgent build() {
            // 1. 准备底层的 Builder 并填充参数
            ReActAgent.Builder internalBuilder = prepareInternalBuilder();

            // 2. 执行构建
            return internalBuilder.build();
        }

        /**
         * 内部辅助方法：将 effectiveConfig 转换为 ReActAgent.Builder。
         */
        private ReActAgent.Builder prepareInternalBuilder() {
            // 必填项校验
            if (effectiveConfig.getName() == null || effectiveConfig.getName().trim().isEmpty()) {
                throw new IllegalStateException("Agent name must be set before building.");
            }

            // 初始化 Builder
            ReActAgent.Builder builder = ReActAgent.builder()
                    .name(effectiveConfig.getName())
                    .description(effectiveConfig.getDescription())
                    .sysPrompt(effectiveConfig.getSysPrompt());

            // 仅当配置不为 null 时注入，避免覆盖 Builder 内部的默认值
            if (effectiveConfig.getModel() != null) {
                builder.model(effectiveConfig.getModel());
            }
            if (effectiveConfig.getToolkit() != null) {
                builder.toolkit(effectiveConfig.getToolkit());
            }
            if (effectiveConfig.getMemory() != null) {
                builder.memory(effectiveConfig.getMemory());
            }
            if (effectiveConfig.getMaxIters() > 0) {
                builder.maxIters(effectiveConfig.getMaxIters());
            }
            if (effectiveConfig.getCheckRunning() != null) {
                builder.checkRunning(effectiveConfig.getCheckRunning());
            }
            if (effectiveConfig.getModelExecutionConfig() != null) {
                builder.modelExecutionConfig(effectiveConfig.getModelExecutionConfig());
            }
            if (effectiveConfig.getToolExecutionConfig() != null) {
                builder.toolExecutionConfig(effectiveConfig.getToolExecutionConfig());
            }
            if (effectiveConfig.getPlanNotebook() != null) {
                builder.planNotebook(effectiveConfig.getPlanNotebook());
            }
            if (effectiveConfig.getToolExecutionContext() != null) {
                builder.toolExecutionContext(effectiveConfig.getToolExecutionContext());
            }
            if (effectiveConfig.getLongTermMemory() != null) {
                builder.longTermMemory(effectiveConfig.getLongTermMemory());
            }
            if (effectiveConfig.getLongTermMemoryMode() != null) {
                builder.longTermMemoryMode(effectiveConfig.getLongTermMemoryMode());

            }
            if (effectiveConfig.getHooks() != null) {
                builder.hooks(effectiveConfig.getHooks());
            }
            if(effectiveConfig.getSkillBox() != null) {
                builder.skillBox(effectiveConfig.getSkillBox());
            }
            if(effectiveConfig.getStructuredOutputReminder() != null) {
                builder.structuredOutputReminder(effectiveConfig.getStructuredOutputReminder());
            }


            return builder;
        }
    }
}
