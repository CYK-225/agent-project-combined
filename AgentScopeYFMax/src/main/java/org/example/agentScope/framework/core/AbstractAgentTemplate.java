package org.example.agentScope.framework.core;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.session.SessionManager;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.config.AgentPersistenceHook;
import org.example.agentScope.mas.mailbox.core.MailboxCenter;
import org.example.agentScope.mas.mailbox.core.MailboxState;
import org.example.agentScope.mas.reActAgent.AgentConfigPo;
import org.example.agentScope.mas.reActAgent.ReActAgentFactory;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.example.agentScope.util.hooksManager.HookListFactory.buildHookList;


/**
 * Agent 模板抽象类
 * <p>
 * 继承此类并配合 {@link AgentDefinition} 注解，实现声明式 Agent 定义。
 * </p>
 *
 * @author AgentScope-Team
 * @version 4.2 (无状态 + Toolkit/SkillBox 分离版)
 */
@Log4j2

public abstract class AbstractAgentTemplate {

    @Getter
    protected final AgentComponentFacade components;

    private String threadID;

    private Toolkit toolkit;

    /** 邮箱状态（enableMail=true 时由框架创建，纯内存对象） */
    @Getter
    private MailboxState mailboxState;

    /** 邮局中心（Spring @Component 单例，通过 AgentComponentFacade 获取） */
    @Getter
    private MailboxCenter mailboxCenter;

    /** Agent 池管理器（用于查询可通信的 Agent） */
    @Getter
    private AgentPoolManager agentPoolManager;

    /**
     * 获取 Git Skill 仓库地址（惰性自获取）。
     * <p>
     * 优先级：动态注册表（SkillRepoRegistry） > 注解静态值（@AgentDefinition.skillRepoUrl）
     * 只在子类 setupSkills() 中调用时才解析，buildAgent 不主动介入。
     */
    protected String getSkillRepoUrl() {
        String name = resolveAgentName();
        // 1. 动态注册表优先
        String fromRegistry = components.skillRepoRegistry().getRepoUrl(name);
        if (fromRegistry != null && !fromRegistry.isBlank()) {
            return fromRegistry;
        }
        // 2. 注解静态值兜底
        String fromAnnotation = this.getClass().getAnnotation(AgentDefinition.class).skillRepoUrl();
        return (fromAnnotation != null && !fromAnnotation.isEmpty()) ? fromAnnotation : null;
    }

    /** 惰性解析 agentName */
    private String resolveAgentName() {
        AgentDefinition def = this.getClass().getAnnotation(AgentDefinition.class);
        if (def != null) {
            if (!def.value().isEmpty()) return def.value();
            if (!def.name().isEmpty()) return def.name();
        }
        return this.getClass().getSimpleName();
    }


    protected AbstractAgentTemplate(AgentComponentFacade components) {
        this.components = components;
        this.agentPoolManager = components.agentPoolManager();
    }

    // ==================== 子类必须实现的抽象方法 ====================



    protected abstract String setupSysPrompt();
    protected abstract Model setupCustomModel();

    // ==================== 核心组件配置（独立分离的设计） ====================

    /** 返回独立组装的工具箱 */
    protected Toolkit setupTools() {
        return Objects.requireNonNullElseGet(toolkit, Toolkit::new);
    }

    protected String setupSysPrompt(String skillName){
        return "";
    }


    /**
     * 返回独立组装的技能盒。
     * <p>
     * 如果配置了 skillRepoUrl（注解或动态绑定），自动加载远程 Git Skill；
     * 如果配置了 classpathResourcePath（注解），自动加载 classpath Skill；
     * 两者可同时使用，合并到同一个 SkillBox。
     * 否则返回 null，不干扰 Agent 构建。子类可覆盖以自定义。
     */
    protected SkillBox setupSkills( ) {
        String gitUrl = getSkillRepoUrl();
        String classpath = getClasspathResourcePath();

        // 两个都没配 → 不创建 SkillBox
        if ((gitUrl == null || gitUrl.isBlank()) &&
            (classpath == null || classpath.isBlank())) {
            return null;
        }

        var builder = components.skillBox().create(getToolkit());

        // Git Skill（支持会话隔离）
        if (gitUrl != null && !gitUrl.isBlank()) {
            String[] patterns = getSkillPatterns();
            // 获取会话标识（如果存在）
            String sessionId = this.threadID;
            if (sessionId != null && !sessionId.isBlank()) {
                // 使用会话隔离版本
                if (patterns != null && patterns.length > 0) {
                    builder.addGitSkillsWithSession(gitUrl, sessionId, true, patterns);
                } else {
                    builder.addGitSkillsWithSession(gitUrl, sessionId, true);
                }
            } else {
                // 使用非会话隔离版本
                if (patterns != null && patterns.length > 0) {
                    builder.addGitSkills(gitUrl, true, patterns);
                } else {
                    builder.addGitSkills(gitUrl);
                }
            }
        }

        // Classpath Skill（只读 Skill + 代码执行）
        if (classpath != null && !classpath.isBlank()) {
            String[] classpathPatterns = getClasspathSkillNames();
            if (classpathPatterns != null && classpathPatterns.length > 0) {
                builder.addClasspathSkills(classpath, true, classpathPatterns);
            } else {
                builder.addClasspathSkills(classpath);
            }
        }

        return builder.buildSkillBox();
    }

    /**
     * 获取 Skill 过滤正则（惰性自获取）。
     * <p>
     * 优先级：动态注册表（SkillRepoRegistry） > 注解静态值（@AgentDefinition.skillNames）
     * 每个元素支持正则表达式，精确名称也能匹配。子类可覆盖。
     */
    protected String[] getSkillPatterns() {
        String name = resolveAgentName();
        // 1. 动态注册表优先

        String[] fromRegistry = components.skillRepoRegistry().getSkillPatterns(name);

        if (fromRegistry != null && fromRegistry.length > 0) {
            return fromRegistry;
        }
        // 2. 注解静态值兜底
        AgentDefinition def = this.getClass().getAnnotation(AgentDefinition.class);
        String[] fromAnnotation = (def != null) ? def.skillNames() : null;
        return (fromAnnotation != null && fromAnnotation.length > 0) ? fromAnnotation : null;
    }

    /**
     * 获取 Classpath Skill 资源路径（从注解读取）。
     */
    protected String getClasspathResourcePath() {
        AgentDefinition def = this.getClass().getAnnotation(AgentDefinition.class);
        String path = (def != null) ? def.classpathResourcePath() : null;
        return (path != null && !path.isBlank()) ? path : null;
    }

    /**
     * 获取 Classpath Skill 过滤正则（从注解读取）。
     */
    protected String[] getClasspathSkillNames() {
        AgentDefinition def = this.getClass().getAnnotation(AgentDefinition.class);
        String[] names = (def != null) ? def.classpathSkillNames() : null;
        return (names != null && names.length > 0) ? names : null;
    }

    protected PlanNotebook setupCustomPlan() { return new PlanNotebook.Builder().build(); }

    /** 返回独立组装的工具箱 */

    // ==================== 记忆系统配置 ====================
    // ... 省略部分不变的方法 (setupCustomMemory 等) ...

    /**
     * 初始化邮箱状态（enableMail=true 时创建）。
     * <p>
     * 在 buildAgent 之前调用，子类 setupSkills/setupCustomHooks 可通过
     * getMailboxState() / getMailboxCenter() 访问。
     * <p>
     * 幂等：如果已创建则跳过。
     */
    private void preInitMailbox(AgentDefinition definition) {
        if (definition.enableMail() && this.mailboxState == null) {
            this.mailboxState = new MailboxState();
            this.mailboxState.setOwnerName(resolveName(definition));
            // MailboxCenter 是 Spring @Component 单例
            this.mailboxCenter = components.mailboxCenter();
            log.info("📬 MailboxState created, MailboxCenter (singleton) acquired for agent: {}", resolveName(definition));
        }
    }

    protected void init() { }
    protected AutoContextMemory setupCustomMemory() { return null; }
    protected Memory setupCustomMemoryMode() { return null; }
    protected LongTermMemory setupLongTermMemory() { return null; }
    protected LongTermMemoryMode setupLongTermMemoryMode() { return null; }
    protected int maxStep() { return -1; }
    protected ExecutionConfig setupModelExecutionConfig() { return null; }
    protected ExecutionConfig setupToolExecutionConfig() { return null; }
    protected ToolExecutionContext setupToolExecutionContext() { return null; }
    protected List<Hook> setupCustomHooks() { return new ArrayList<>(); }
    protected void afterAgentBuilt(ReActAgent agent) { }
    protected StructuredOutputReminder setupStructuredOutputReminder(){
        return null;
    }

    protected Toolkit getToolkit(){
        if(toolkit==null){
            toolkit=setupTools();
        }
        return toolkit;
    }

    protected String getThreadID(){
        if(StringUtils.isNoneBlank(threadID)){
            return threadID;
        }else {
            throw new IllegalStateException("Thread ID 没设置. Ensure buildAgentWithSession is called with a valid threadId.");
        }
    }
    protected Boolean isUseStudio(){
        return false;
    }

    /**
     * 创建持久化 Hook
     * @return 持久化 Hook，返回 null 表示不使用 Hook
     */
    private List<Hook> createPersistenceHook(String threadId,SessionManager sessionManager) {
        List<Hook> hooks = new ArrayList<>();
        hooks.add(new AgentPersistenceHook( threadId,sessionManager));
        hooks.add(new AutoContextHook());
        return  hooks; // 默认不添加 Hook，子类按需实现
    }

    /**
     * 会话级 DTO 容器钩子。
     * <p>
     * 子类可重写此方法，返回一个注入了业务 DTO 的 {@link io.agentscope.core.state.StateModule}。
     * 返回值会被注册到 {@link SessionManager}，框架自动在 load / save 时
     * 调用其 {@code loadFrom} / {@code saveTo} 方法。
     * <p>
     * 默认返回 null，表示不注册额外组件。
     *
     * @return 会话级 StateModule 组件，或 null
     */
    protected StateModule setupSessionContext() {
        return null;
    }

    /**
     * 加载会话记忆
     * 子类可重写此方法以自定义记忆加载逻辑
     * @param agent Agent 实例
     * @param threadId 会话 ID
     */
    protected SessionManager loadSessionMemory(ReActAgent agent, String threadId) {
        SessionManager sessionManager = null;
        try {
            SessionManager sm = SessionManager.forSessionId(threadId)
                    .withSession(components.postgresSession())
                    .addComponent(agent)
                    .addComponent(agent.getMemory());

            // 注册子类提供的会话级组件（如业务 DTO 容器）
            StateModule sessionCtx = setupSessionContext();
            if (sessionCtx != null) {
                sm.addComponent(sessionCtx);
                log.info("✅ Registered session context component for agent: {}, threadId: {}",
                        agent.getName(), threadId);
            }

            sm.loadIfExists();
            sessionManager = sm;
            log.info("✅ Loaded memory for agent: {}, threadId: {}, messages: {}",
                    agent.getName(), threadId, agent.getMemory().getMessages().size());
        } catch (Exception e) {
            log.error("Failed to load memory for agent: {}, threadId: {}",
                    agent.getName(), threadId, e);
            // 降级处理，允许 Agent 以空记忆继续运行
        }
        return sessionManager;
    }
    /**
     * 挂载会话 Hook
     * 子类可重写此方法以自定义 Hook 挂载逻辑
     * @param agent Agent 实例
     * @param threadId 会话 ID
     */
    protected void attachSessionHooks(ReActAgent agent, String threadId,SessionManager sessionManager) {
        try {
            List<Hook> hook = createPersistenceHook( threadId,sessionManager);
            agent.getHooks().addAll(hook);
            log.debug("Attached persistence hook for agent: {}, threadId: {}",
                    agent.getName(), threadId);
        } catch (Exception e) {
            log.error("Failed to attach persistence hook for agent: {}, threadId: {}",
                    agent.getName(), threadId, e);
        }
    }
    // ==================== 核心构建方法 ====================

    /**
     * ✅ 构建带会话上下文的 Agent
     * 模板方法：实例化 -> 加载记忆 -> 挂载 Hook
     * @param threadId 会话 ID
     * @param definition Agent 定义
     * @return 完整的 Agent 实例
     */
// 向下兼容的 buildAgentWithSession
    public final ReActAgent buildAgentWithSession(String threadId, AgentDefinition definition) {
        return buildAgentWithSession(threadId, definition, null, null);
    }

    // 向下兼容的 buildAgentWithSession（无 override）
    public final ReActAgent buildAgentWithSession(String threadId, AgentDefinition definition,
                                                  ToolExecutionContext dynamicContext,
                                                  List<Hook> dynamicHooks) {
        return buildAgentWithSession(threadId, definition, dynamicContext, dynamicHooks, null);
    }

    /**
     * 构建带会话上下文的 Agent（支持 AgentConfigPo override）
     * <p>
     * 模板方法：实例化 -> 加载记忆 -> 挂载 Hook。
     * 外部传入的 override 通过 {@link AgentConfigPo#mergeOverrides} 覆盖模板默认配置。
     *
     * @param threadId       会话 ID
     * @param definition     Agent 定义
     * @param dynamicContext 动态工具执行上下文（可选）
     * @param dynamicHooks   动态 Hook 列表（可选）
     * @param override       外部配置覆盖（可选），为 null 时使用模板默认值
     * @return 完整的 Agent 实例
     */
    public final ReActAgent buildAgentWithSession(String threadId, AgentDefinition definition,
                                                  ToolExecutionContext dynamicContext,
                                                  List<Hook> dynamicHooks,
                                                  AgentConfigPo override) {
        if (definition == null) throw new IllegalArgumentException("AgentDefinition cannot be null.");
        this.threadID = threadId;

        // 在 buildAgent 之前初始化邮箱（子类 setupSkills/setupCustomHooks 可通过 getter 访问）
        preInitMailbox(definition);

        ReActAgent agent = buildAgent(definition, dynamicContext, dynamicHooks, override);

        if (definition.enablePersistence()) {
            SessionManager sessionManager = loadSessionMemory(agent, threadId);
            sessionManager.loadIfExists();
            attachSessionHooks(agent, threadId, sessionManager);
        }

        return agent;
    }
    // 向下兼容的 buildAgent
    public final ReActAgent buildAgent(AgentDefinition definition) {
        return buildAgent(definition, null, null);
    }

    public final ReActAgentFactory.AgentBuilderWrapper createAgentBuilder(AgentDefinition definition, ToolExecutionContext dynamicContext,
                                                                          List<Hook> dynamicHooks,String skillName) {
        if (definition == null) {
            throw new IllegalArgumentException("AgentDefinition cannot be null.");
        }

        AgentConfigPo config = new AgentConfigPo();
        config.setName(resolveName(definition));
        config.setDescription(definition.description());
        config.setSysPrompt(setupSysPrompt(skillName));

        // 1. 组装模型
        Model model = assembleModel(definition);
        if (model != null) {
            config.setModel(model);
        }

        // 2. 独立注册工具箱 (Toolkit)
        Toolkit toolkit = getToolkit();
        if (toolkit != null) {
            config.setToolkit(toolkit);
        }

        // 3. 独立注册技能盒 (SkillBox)
        SkillBox skillBox = setupSkills();
        if (skillBox != null) {
            // 假设 AgentConfigPo 已经提供了 setSkillBox 方法
            config.setSkillBox(skillBox);
        }

        // 4. 组装计划本
        if (definition.enablePlan()) {
            PlanNotebook plan = assemblePlan();
            if (plan != null) config.setPlanNotebook(plan);
        }

        // 5. 组装短期记忆
        if (definition.enableMemory()) {
            AutoContextMemory memory = assembleMemory();
            Memory mem = assembleMemoryMode();
            if (memory != null) {
                config.setMemory(memory);
            }else {
                if(mem != null) {
                    config.setMemory(mem);
                }
            }
        }

        // 6. 组装长程记忆
        if (definition.enableLongTermMemory()) {
            LongTermMemory longTermMemory = setupLongTermMemory();
            if (longTermMemory != null) config.setLongTermMemory(longTermMemory);

            LongTermMemoryMode memoryMode = setupLongTermMemoryMode();
            if (memoryMode != null) config.setLongTermMemoryMode(memoryMode);

        }

        // 7. 组装执行策略
        int customMaxStep = maxStep();
        config.setMaxIters(customMaxStep > 0 ? customMaxStep : definition.maxIters());
        config.setCheckRunning(definition.checkRunning());

        ExecutionConfig modelExecConfig = setupModelExecutionConfig();
        if (modelExecConfig != null) config.setModelExecutionConfig(modelExecConfig);

        ExecutionConfig toolExecConfig = setupToolExecutionConfig();
        if (toolExecConfig != null) config.setToolExecutionConfig(toolExecConfig);

        if (dynamicContext != null ) {
            config.setToolExecutionContext(dynamicContext);
        } else {
            // 如果没传，才使用静态配置的兜底
            ToolExecutionContext baseContext = setupToolExecutionContext();
            if (baseContext != null) config.setToolExecutionContext(baseContext);
        }


        // 8. 组装钩子 (追加传入的动态 Hook)
        List<Hook> allHooks = assembleHooks(definition);
        if (dynamicHooks != null && !dynamicHooks.isEmpty()) {
            allHooks.addAll(dynamicHooks);
        }
        config.setHooks(allHooks);

        //9.组装构建结构化输出+
        config.setStructuredOutputReminder(setupStructuredOutputReminder());

        // 9. 最终构建 Agent
        return ReActAgentFactory.create(config);
    }


    /**
     * 向下兼容的 buildAgent（无 override）
     */
    public final ReActAgent buildAgent(AgentDefinition definition, ToolExecutionContext dynamicContext,
                                       List<Hook> dynamicHooks) {
        return buildAgent(definition, dynamicContext, dynamicHooks, null);
    }

    /**
     * 构建 Agent（支持 AgentConfigPo override）
     * <p>
     * 模板方法提供默认配置，外部传入的 override 通过 {@link AgentConfigPo#mergeOverrides} 覆盖非 null 字段。
     *
     * @param definition    Agent 定义
     * @param dynamicContext 动态工具执行上下文（可选）
     * @param dynamicHooks   动态 Hook 列表（可选）
     * @param override       外部配置覆盖（可选），为 null 时使用模板默认值
     * @return 构建完成的 Agent 实例
     */
    public final ReActAgent buildAgent(AgentDefinition definition, ToolExecutionContext dynamicContext,
                                       List<Hook> dynamicHooks, AgentConfigPo override) {
        if (definition == null) {
            throw new IllegalArgumentException("AgentDefinition cannot be null.");
        }
        // 初始化方法
        init();

        AgentConfigPo config = new AgentConfigPo();
        config.setName(resolveName(definition));
        config.setDescription(definition.description());
        config.setSysPrompt(setupSysPrompt());

        // 1. 组装模型
        Model model = assembleModel(definition);
        if (model != null) {
            config.setModel(model);
        }

        // 2. 独立注册工具箱 (Toolkit)
        Toolkit toolkit = getToolkit();
        if (toolkit != null) {
            config.setToolkit(toolkit);
        }

        // 3. 独立注册技能盒 (SkillBox)
        SkillBox skillBox = setupSkills();
        if (skillBox != null) {
            config.setSkillBox(skillBox);
        }

        // 4. 组装计划本
        if (definition.enablePlan()) {
            PlanNotebook plan = assemblePlan();
            if (plan != null) config.setPlanNotebook(plan);
        }

        // 5. 组装短期记忆
        if (definition.enableMemory()) {
            AutoContextMemory memory = assembleMemory();
            Memory mem = assembleMemoryMode();
            if (memory != null) {
                config.setMemory(memory);
            } else {
                if (mem != null) {
                    config.setMemory(mem);
                }
            }
        }

        // 6. 组装长程记忆
        if (definition.enableLongTermMemory()) {
            LongTermMemory longTermMemory = setupLongTermMemory();
            if (longTermMemory != null) config.setLongTermMemory(longTermMemory);

            LongTermMemoryMode memoryMode = setupLongTermMemoryMode();
            if (memoryMode != null) config.setLongTermMemoryMode(memoryMode);
        }

        // 7. 组装执行策略
        int customMaxStep = maxStep();
        config.setMaxIters(customMaxStep > 0 ? customMaxStep : definition.maxIters());
        config.setCheckRunning(definition.checkRunning());

        ExecutionConfig modelExecConfig = setupModelExecutionConfig();
        if (modelExecConfig != null) config.setModelExecutionConfig(modelExecConfig);

        ExecutionConfig toolExecConfig = setupToolExecutionConfig();
        if (toolExecConfig != null) config.setToolExecutionConfig(toolExecConfig);

        if (dynamicContext != null) {
            config.setToolExecutionContext(dynamicContext);
        } else {
            // 如果没传，才使用静态配置的兜底
            ToolExecutionContext baseContext = setupToolExecutionContext();
            if (baseContext != null) config.setToolExecutionContext(baseContext);
        }

        // 8. 组装钩子 (追加传入的动态 Hook)
        List<Hook> allHooks = assembleHooks(definition);
        if (dynamicHooks != null && !dynamicHooks.isEmpty()) {
            allHooks.addAll(dynamicHooks);
        }
        config.setHooks(allHooks);

        // 9. 组装结构化输出
        config.setStructuredOutputReminder(setupStructuredOutputReminder());

        // ✅ 10. 合并外部 override（在 create 之前）
        if(override!=null)  config.mergeOverrides(override);

        // 11. 最终构建 Agent
        ReActAgent agent = ReActAgentFactory.create(config).build();
        afterAgentBuilt(agent);

        return agent;
    }

    // ==================== 私有组装方法 ====================

    private String resolveName(AgentDefinition definition) {
        if (!definition.value().isEmpty()) return definition.value();
        if (!definition.name().isEmpty()) return definition.name();
        return this.getClass().getSimpleName();
    }

    private Model assembleModel(AgentDefinition definition) {
        Model customModel = setupCustomModel();
        if (customModel != null) return customModel;

        String modelType = definition.modelType();
        if (modelType.isEmpty()) return null;

        return switch (definition.modelProvider()) {
            case DASHSCOPE -> components.model().dashScope().buildDashScopeModel(modelType);
            case OLLAMA -> components.model().ollama().buildOllamaModel(modelType);
        };
    }

    private AutoContextMemory assembleMemory() {
        return setupCustomMemory();
    }
    private Memory assembleMemoryMode() {
        return setupCustomMemoryMode();
    }

    private PlanNotebook assemblePlan() {
        PlanNotebook customPlan = setupCustomPlan();
        return customPlan != null ? customPlan : components.plan().builder().build();
    }

    private List<Hook> assembleHooks(AgentDefinition definition) {
        List<Hook> customHooks = setupCustomHooks();
        List<Hook> hooks = new ArrayList<>();

        if (customHooks != null && !customHooks.isEmpty()) {
            hooks.addAll(customHooks);
        }
        if (definition.hooksType() != null && !definition.hooksType().isEmpty()) {
            String hooksType = definition.hooksType();
            hooks.addAll(buildHookList(hooksType));
        }

        // ==========================================
        // 【2. 自动注入 Studio 原生 Hook】
        // ==========================================
        try {
            // 从全局单例获取 Client，直接实例化现成的 Hook 并加入列表
            // 这样所有继承该模板的 Agent 都会自动开启 Live Stream
            if (StudioManager.getClient() != null && isUseStudio()) {
                hooks.add(new StudioMessageHook(StudioManager.getClient()));
                log.debug("Attached StudioMessageHook to agent: {}", resolveName(definition));
            }
        } catch (Exception e) {
            log.warn("Failed to attach StudioMessageHook. Is StudioManager initialized?", e);
        }

        return hooks;
    }

    /**
     * 从 {@link MailboxCenter} 移除注册，此后发给该 Agent 的邮件将走离线落盘。
     * <p>
     * 应在 Agent 销毁 / 会话结束时调用。
     */
    public void unregisterMailbox() {
        if (this.mailboxCenter != null && this.mailboxState != null && this.threadID != null) {
            this.mailboxCenter.unregister(this.threadID, this.mailboxState.getOwnerName());
        }
    }
}