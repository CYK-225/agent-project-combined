package org.example.agentScope.framework.annotation;


import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Agent 声明式定义注解
 * <p>
 * 用于标识一个类为 Agent 模板，交由 Spring 扫描并由 AgentPoolManager 自动管理。
 * 继承 Spring {@link Component}，使其成为 Spring Bean。
 * </p>
 *
 * <h3>核心特性：</h3>
 * <ul>
 *   <li><b>声明式定义</b>：通过注解声明 Agent 的元信息和能力配置</li>
 *   <li><b>分组管理</b>：支持按 group 批量获取和管理 Agent</li>
 *   <li><b>懒加载</b>：默认使用懒加载模式，首次调用时才实例化</li>
 *   <li><b>场景预设</b>：支持预设的模型场景（思考/工具/聊天）和钩子策略</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 极简形式 - 只需指定名称
 * @AgentDefinition("DataAnalyst")
 * public class DataAnalystAgent extends AbstractAgentTemplate {
 *     protected String setupSysPrompt() {
 *         return "你是一个数据分析师";
 *     }
 * }
 * //什么时候能用单例？ 只有当这个 Agent 彻底不配 Memory，且只做纯粹的“翻译”、“格式化校验”这类输入 A 输出 B 的纯函数型任务时。
 * // 完整形式 - 指定场景配置
 * @AgentDefinition(
 *     name = "DataAnalyst",
 *     group = "finance_team",
 *     description = "金融数据分析专家",
 *     modelType = "思考",          // 使用预设的思考模型配置
 *     hooksType = "log",           // 使用预设的日志钩子
 *     lazy = true,                 // 懒加载
 *     active = true,               // 激活状态
 *     maxIters = 10,               // 最大迭代次数
 *     enableMemory = true          // 启用记忆
 * )
 * public class DataAnalystAgent extends AbstractAgentTemplate {
 *     protected String setupSysPrompt() {
 *         return "你是一个金融数据分析师，擅长...";
 *     }
 *
 *     protected List<BaseTool> setupTools() {
 *         return List.of(new SearchTool(), new CalculateTool());
 *     }
 * }
 * }
 * </pre>
 *
 * @author AgentScope-Team
 * @version 4.0
 * @see AbstractAgentTemplate
 * @see AgentPoolManager
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AgentDefinition {

    /**
     * Agent 唯一名称。
     * <p>
     * 如果不填则默认使用类名。
     * 支持直接在 value 中指定，等同于 name。
     * </p>
     *
     * @return Agent 名称
     */
    String name() default "";

    /**
     * 同 name，提供简写形式。
     */
    String value() default "";

    /**
     * Agent 描述信息。
     * <p>
     * 用于在 MsgHub 或日志中展示 Agent 的职责说明。
     * </p>
     *
     * @return 描述信息，默认为空
     */
    String description() default "";

    /**
     * 分组名称。
     * <p>
     * 用于 MsgHub 或特定业务场景的批量加载。
     * 同一分组下的 Agent 可以通过 {@code AgentPoolManager.buildGroupPool(group)} 一次性获取。
     * </p>
     *
     * @return 分组名称，默认为 "default"
     */
    String group() default "default";

    /**
     * 是否启用懒加载。
     * <p>
     * <ul>
     *   <li><b>true:</b> 启动时不实例化底层 Agent，仅向池中注册元数据；第一次调用时真正创建。</li>
     *   <li><b>false:</b> 跟随 Spring 启动时立即创建并缓存。</li>
     * </ul>
     * </p>
     *
     * @return 是否懒加载，默认为 true
     */
    boolean lazy() default true;

    /**
     * 开关控制。
     * <p>
     * 设为 false 则容器启动时会忽略此 Agent，不会注册到池中。
     * </p>
     *
     * @return 是否激活，默认为 true
     */
    boolean active() default true;

    /**
     * 模型提供者。
     *
     * @return 模型提供者，默认 DashScope
     */
    ModelProvider modelProvider() default ModelProvider.DASHSCOPE;

    /**
     * 模型场景类型。
     * <p>
     * 使用预设在 ModelFactory 中的场景配置，无需手动创建 Model：
     * <ul>
     *   <li><b>"思考"</b>: 适用于复杂推理，temperature=0.5</li>
     *   <li><b>"工具"</b>: 适用于函数调用，temperature=0.1</li>
     *   <li><b>"聊天"</b>: 适用于开放对话，temperature=0.7</li>
     *   <li><b>""</b>: 使用默认配置或子类自定义</li>
     * </ul>
     * </p>
     *
     * @return 模型场景类型
     */
    String modelType() default "";

    /**
     * 模型提供者枚举
     */
    enum ModelProvider {
        DASHSCOPE,
        OLLAMA
    }

    /**
     * 钩子策略类型。
     * <p>
     * 使用预设在 HookListFactory 中的钩子组合：
     * <ul>
     *   <li><b>"log"</b>: 包含 PreCall、ReasoningChunk、ActingChunk，用于日志记录</li>
     *   <li><b>"Review"</b>: 包含推理审计、动作拦截等，用于风控</li>
     *   <li><b>"debug"</b>: 仅包含 ErrorHook，用于调试</li>
     *   <li><b>""</b>: 不添加任何钩子</li>
     * </ul>
     * </p>
     *
     * @return 钩子策略类型
     */
    String hooksType() default "";

    /**
     * 最大迭代次数。
     * <p>
     * 限制 Agent 在 ReAct 循环中的最大思考/行动次数。
     * </p>
     *
     * @return 最大迭代次数，默认 30
     */
    int maxIters() default 100;

    /**
     * 是否启用自动上下文记忆。
     * <p>
     * 启用后将使用 AutoContextMemory 进行对话上下文管理。
     * </p>
     *
     * @return 是否启用，默认 true
     */
    boolean enableMemory() default true;

    /**
     * 是否启用 Plan 能力。
     * <p>
     * 启用后将自动注入 PlanNotebook 进行任务规划。
     * </p>
     *
     * @return 是否启用，默认 false
     */
    boolean enablePlan() default false;

    /**
     * 优先级。
     * <p>
     * 用于在分组内排序或确定调用顺序。
     * 数值越小优先级越高。
     * </p>
     *
     * @return 优先级，默认 0
     */
    int priority() default 0;

    // ==================== 执行策略配置 ====================

    /**
     * 运行状态检查标志。
     * <p>
     * 用于任务的并发控制或状态监控。
     * </p>
     *
     * @return 是否检查运行状态，默认 true
     */
    boolean checkRunning() default true;

    // ==================== 长程记忆配置 ====================

    /**
     * 是否启用长程记忆。
     * <p>
     * 启用后将关联向量数据库，实现跨会话知识检索。
     * </p>
     *
     * @return 是否启用，默认 false
     */
    boolean enableLongTermMemory() default false;
    /**
     * Agent 的作用域：
     * "singleton" - 单例（默认），全局共享同一个实例和记忆
     * "prototype" - 多例，每次获取都会创建一个全新的独立实例
     */
    String scope() default "prototype";
    /**
     * 是否启用记忆持久化
     * 启用后，AgentPoolManager 会自动加载历史记忆并挂载持久化 Hook
     */
    boolean enablePersistence() default false;

    /**
     * 持久化策略（预留扩展）
     * 例如："postgres", "redis" 等
     */
    String persistenceStrategy() default "postgres";

    /**
     * 是否启用邮件通信能力。
     * <p>
     * 启用后，框架会自动：
     * <ul>
     *   <li>创建 MailboxState 内存邮箱</li>
     *   <li>注册到 MailboxCenter 中心化邮局</li>
     *   <li>从 mail_message 表恢复历史邮件</li>
     * </ul>
     * 子类在 setupSkills() 中使用 MailSkillFactory.createTools() 注册工具，
     * 在 setupCustomHooks() 中使用 MailSkillFactory.createHook() 注册通知 Hook。
     *
     * @return 是否启用，默认 false
     */
    boolean enableMail() default false;

    /**
     * Git Skill 仓库地址。
     * <p>
     * 启用后，框架在创建 Agent 时会自动：
     * <ul>
     *   <li>Clone/Pull 远程仓库到本地临时目录</li>
     *   <li>解析仓库中的 SKILL.md 文件为 AgentSkill</li>
     *   <li>注册到 SkillBox 并开启 codeExecution（Shell/Read/Write）</li>
     * </ul>
     * 支持 HTTPS 和 SSH 两种协议。
     * <p>
     * 运行时可通过 POST /api/skill-repo/bind 动态覆盖。
     *
     * @return Git 仓库地址，默认空（不启用）
     */
    String skillRepoUrl() default "";

    /**
     * 按正则表达式过滤加载的 Skill 名称。
     * <p>
     * 每个元素都是正则表达式，使用 {@link String#matches(String)} 匹配 Skill 名称。
     * 精确名称也能匹配（如 "data-analysis" 等价于正则精确匹配）。
     * <p>
     * 不指定则加载仓库中的所有 Skill。
     *
     * <h4>示例：</h4>
     * <ul>
     *   <li>{@code "data-analysis"} — 精确匹配</li>
     *   <li>{@code "data-.*"} — 匹配所有 data- 开头的 Skill</li>
     *   <li>{@code ".*security.*"} — 匹配名称含 security 的 Skill</li>
     * </ul>
     *
     * @return 正则表达式数组，默认空（加载全部）
     */
    String[] skillNames() default {};

    /**
     * Classpath Skill 资源路径。
     * <p>
     * 指定 classpath 下预打包 Skill 的资源目录，如 "skills"（对应 src/main/resources/skills/）。
     * 自动兼容标准 JAR 和 Spring Boot Fat JAR。
     * <p>
     * 启用后，框架在创建 Agent 时会自动：
     * <ul>
     *   <li>从 classpath 加载资源目录中的 SKILL.md</li>
     *   <li>注册到 SkillBox（只读，不开代码执行）</li>
     * </ul>
     * <p>
     * 可与 skillRepoUrl 同时使用，classpath skills 会和 git skills 合并加载。
     *
     * @return classpath 资源路径，默认空（不启用）
     */
    String classpathResourcePath() default "";

    /**
     * 按正则表达式过滤加载的 Classpath Skill 名称。
     * <p>
     * 语义与 {@link #skillNames()} 相同，但作用于 classpath 资源目录中的 Skill。
     * 不指定则加载 classpath 资源目录中的所有 Skill。
     *
     * @return 正则表达式数组，默认空（加载全部）
     */
    String[] classpathSkillNames() default {};
}
