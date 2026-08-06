package org.example.agentScope.framework.core;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.tool.ToolExecutionContext;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.config.CustomThreadSessionManager;
import org.example.agentScope.mas.msgHub.MsgAgentPool;
import org.example.agentScope.mas.reActAgent.AgentConfigPo;
import org.example.agentScope.mas.reActAgent.ReActAgentFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPoolManager {

    private final ApplicationContext applicationContext;
    private final CustomThreadSessionManager customThreadSessionManager;
    private final SkillRepoRegistry skillRepoRegistry;

    @Getter
    private final Map<String, AgentMetadata> metadataRegistry = new ConcurrentHashMap<>();
    private final Map<String, ReActAgent> agentCache = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Set<String>> groupIndex = new ConcurrentHashMap<>();

    private static final String BASE_PACKAGE = "org.example";

    @PostConstruct
    public void init() {
        log.info("AgentPoolManager initializing...");
        scanAndRegisterAgents();
        autoRegisterThreadAwareFactories();
        log.info("AgentPoolManager initialized. Registered {} agents.", metadataRegistry.size());
    }

    private void scanAndRegisterAgents() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(AgentDefinition.class));

        Set<org.springframework.beans.factory.config.BeanDefinition> candidates =
                scanner.findCandidateComponents(BASE_PACKAGE);

        for (org.springframework.beans.factory.config.BeanDefinition bd : candidates) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                AgentDefinition definition = clazz.getAnnotation(AgentDefinition.class);

                if (!definition.active()) continue;
                if (!AbstractAgentTemplate.class.isAssignableFrom(clazz)) continue;

                registerAgentMetadata(clazz, definition);
            } catch (ClassNotFoundException e) {
                log.error("Failed to load agent class: {}", bd.getBeanClassName(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void registerAgentMetadata(Class<?> clazz, AgentDefinition definition) {
        String agentName = resolveAgentName(clazz, definition);

        // ✅ 安全校验：启用持久化时，scope 必须为 prototype
        if (definition.enablePersistence() && !"prototype".equalsIgnoreCase(definition.scope())) {
            throw new IllegalStateException(
                    String.format("Agent [%s] enables persistence but scope is not prototype. " +
                            "Persistence requires prototype scope to isolate sessions.", agentName));
        }

        AgentMetadata metadata = AgentMetadata.builder()
                .name(agentName)
                .description(definition.description())
                .group(definition.group())
                .lazy(definition.lazy())
                .scope(definition.scope())
                .enablePersistence(definition.enablePersistence())
                .persistenceStrategy(definition.persistenceStrategy())
                .templateClass((Class<? extends AbstractAgentTemplate>) clazz)
                .definition(definition)
                .priority(definition.priority())
                .build();

        metadataRegistry.put(agentName, metadata);
        groupIndex.computeIfAbsent(definition.group(), k -> ConcurrentHashMap.newKeySet()).add(agentName);
    }

    private String resolveAgentName(Class<?> clazz, AgentDefinition definition) {
        if (!definition.value().isEmpty()) return definition.value();
        if (!definition.name().isEmpty()) return definition.name();
        return clazz.getSimpleName();
    }

    /**
     * ✅ 自动注册 Thread-Aware Factory
     * 委托给 Template 的 buildAgentWithSession 方法
     * 同时处理 @AgentDefinition(skillRepoUrl) 静态配置
     */
    private void autoRegisterThreadAwareFactories() {
        for (AgentMetadata metadata : metadataRegistry.values()) {
            if (metadata.isEnablePersistence()) {
                String agentName = metadata.getName();

                // 静态配置：注解中的 skillRepoUrl + skillNames 写入 Registry
                String staticRepoUrl = metadata.getDefinition().skillRepoUrl();
                if (!staticRepoUrl.isEmpty()) {
                    String[] skillPatterns = metadata.getDefinition().skillNames();
                    skillRepoRegistry.putIfAbsent(agentName, staticRepoUrl,
                            skillPatterns.length > 0 ? skillPatterns : null);
                }

                // 注册 Factory 闭包
                customThreadSessionManager.registerThreadAwareFactory(
                        agentName,
                        threadId -> {
                            AbstractAgentTemplate template = applicationContext.getBean(metadata.getTemplateClass());
                            return template.buildAgentWithSession(threadId, metadata.getDefinition());
                        }
                );
                log.info("Auto-registered thread-aware factory for agent: {}", agentName);
            }
        }
    }


    public ReActAgentFactory.AgentBuilderWrapper getAgentBuilder(String name, ToolExecutionContext context, List<Hook> dynamicHooks,String skillName) {
        AgentMetadata metadata = metadataRegistry.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("Agent not found: " + name);
        }
        return instantiateAgentBuilder(metadata, context, dynamicHooks,skillName);
    }


    // 1. 无参获取 (向下兼容)
    public ReActAgent getAgent(String name) {
        return getAgent(name, null, null);
    }

    // 2. 仅传入单一上下文 (向下兼容)
    public ReActAgent getAgent(String name, ToolExecutionContext context) {
        return getAgent(name, context, null);
    }

    // 3. 向下兼容：同时接收单一 Context 和 Hooks
    public ReActAgent getAgent(String name, ToolExecutionContext context, List<Hook> dynamicHooks) {
        return getAgent(name, context, dynamicHooks, null);
    }

    // 4. 【最终形态】同时接收 Context、Hooks 和 AgentConfigPo override
    public ReActAgent getAgent(String name, ToolExecutionContext context,
                               List<Hook> dynamicHooks, AgentConfigPo override) {
        AgentMetadata metadata = metadataRegistry.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("Agent not found: " + name);
        }

        // 安全校验：单例模式下禁止注入动态状态
        boolean hasDynamicState = (context != null) || (dynamicHooks != null && !dynamicHooks.isEmpty())
                || (override != null);
        if (hasDynamicState && !"prototype".equalsIgnoreCase(metadata.getScope())) {
            log.warn("安全警告: Agent [{}] 是单例模式，注入动态 Context、Hook 或 Override 会引发并发串线！建议使用 prototype", name);
        }

        if ("prototype".equalsIgnoreCase(metadata.getScope())) {
            return instantiateAgent(metadata, context, dynamicHooks, override);
        }

        return agentCache.computeIfAbsent(name, k -> instantiateAgent(metadata, context, dynamicHooks, override));
    }

    // ==================== 带会话的 Agent 获取 ========================

    /**
     * 获取带会话上下文的 Agent（适用于 enablePersistence=true 的 Agent）
     * <p>
     * 内部调用 {@code template.buildAgentWithSession(threadId, definition)}，
     * 会自动初始化邮箱（如果 enableMail=true）、加载会话记忆、挂载持久化 Hook。
     *
     * @param name     Agent 名称
     * @param threadId 会话 ID
     * @return 带会话的 Agent 实例
     */
    public ReActAgent getAgentWithSession(String name, String threadId) {
        return getAgentWithSession(name, threadId, null, null);
    }

    /**
     * 获取带会话上下文的 Agent（向下兼容，无 override）
     *
     * @param name          Agent 名称
     * @param threadId      会话 ID
     * @param context       工具执行上下文（可选）
     * @param dynamicHooks  动态 Hook（可选）
     * @return 带会话的 Agent 实例
     */
    public ReActAgent getAgentWithSession(String name, String threadId,
                                          ToolExecutionContext context, List<Hook> dynamicHooks) {
        return getAgentWithSession(name, threadId, context, dynamicHooks, null);
    }

    /**
     * 获取带会话上下文的 Agent（支持 AgentConfigPo override）
     * <p>
     * 外部传入的 override 通过 {@link AgentConfigPo#mergeOverrides} 覆盖模板默认配置。
     *
     * @param name          Agent 名称
     * @param threadId      会话 ID
     * @param context       工具执行上下文（可选）
     * @param dynamicHooks  动态 Hook（可选）
     * @param override      外部配置覆盖（可选），为 null 时使用模板默认值
     * @return 带会话的 Agent 实例
     */
    public ReActAgent getAgentWithSession(String name, String threadId,
                                          ToolExecutionContext context, List<Hook> dynamicHooks,
                                          AgentConfigPo override) {
        AgentMetadata metadata = metadataRegistry.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("Agent not found: " + name);
        }

        try {
            AbstractAgentTemplate template = applicationContext.getBean(metadata.getTemplateClass());
            return template.buildAgentWithSession(threadId, metadata.getDefinition(), context, dynamicHooks, override);
        } catch (Exception e) {
            log.error("Failed to instantiate agent with session: {}, threadId: {}", name, threadId, e);
            throw new RuntimeException("Failed to instantiate agent with session: " + name, e);
        }
    }

    // 4. 实例化
    private ReActAgent instantiateAgent(AgentMetadata metadata, ToolExecutionContext context,
                                        List<Hook> dynamicHooks, AgentConfigPo override) {
        try {
            AbstractAgentTemplate template = applicationContext.getBean(metadata.getTemplateClass());
            return template.buildAgent(metadata.getDefinition(), context, dynamicHooks, override);
        } catch (Exception e) {
            log.error("Failed to instantiate agent: {}", metadata.getName(), e);
            throw new RuntimeException("Failed to instantiate agent: " + metadata.getName(), e);
        }
    }

    private ReActAgentFactory.AgentBuilderWrapper instantiateAgentBuilder(AgentMetadata metadata, ToolExecutionContext context, List<Hook> dynamicHooks,String skillName) {
        try {
            AbstractAgentTemplate template = applicationContext.getBean(metadata.getTemplateClass());
            return template.createAgentBuilder(metadata.getDefinition(), context, dynamicHooks,skillName);
        } catch (Exception e) {
            log.error("Failed to instantiate agent: {}", metadata.getName(), e);
            throw new RuntimeException("Failed to instantiate agent: " + metadata.getName(), e);
        }
    }


    // ==================== 工具方法 ====================
    public List<ReActAgent> getAgentsByGroup(String group) {
        Set<String> agentNames = groupIndex.get(group);
        if (agentNames == null || agentNames.isEmpty()) return new ArrayList<>();
        return agentNames.stream()
                .sorted(Comparator.comparingInt(a -> Optional.ofNullable(metadataRegistry.get(a))
                        .map(AgentMetadata::getPriority).orElse(0)))
                .map(this::getAgent)
                .collect(Collectors.toList());
    }

    public MsgAgentPool buildGroupPool(String group) {
        List<ReActAgent> agents = getAgentsByGroup(group);
        if (agents.isEmpty()) throw new IllegalArgumentException("Group not found or empty: " + group);
        return MsgAgentPool.of(agents.toArray(new ReActAgent[0]));
    }

    public void evictCache(String name) {
        agentCache.remove(name);
        log.debug("Evicted cache for singleton agent: {}", name);
    }

    public void evictAllCache() {
        agentCache.clear();
        log.info("All singleton agent caches evicted.");
    }
    /**
     * 按分组获取已注册的 Agent 名称及其描述信息，并排除指定的多个分组
     *
     * @param excludedGroups 需要排除的 Agent 分组名称集合
     * @return Map<String, Map<String, String>> 结构为：Map<分组名称, Map<Agent名称, Agent描述>>
     */
    public Map<String, Map<String, String>> getGroupedAgentDescriptionsExcluding(Collection<String> excludedGroups) {
        return metadataRegistry.values().stream()
                // 1. 过滤掉属于 excludedGroups 列表中的分组的 Agent
                .filter(metadata -> excludedGroups == null || !excludedGroups.contains(metadata.getGroup()))
                // 2. 按分组进行 GroupingBy 聚合
                .collect(Collectors.groupingBy(
                        // 外层 Map 的 Key：分组名称 (防御性处理，如果 group 为空则归入 "default" 组)
                        metadata -> (metadata.getGroup() != null && !metadata.getGroup().isEmpty()) ? metadata.getGroup() : "default",
                        // 内层 Map 的 Value：将该分组下的 Agent 收集为 Map<Agent名称, Agent描述>
                        Collectors.toMap(
                                AgentMetadata::getName,
                                metadata -> metadata.getDescription() != null ? metadata.getDescription() : "",
                                (existing, replacement) -> existing
                        )
                ));
    }
    @lombok.Data
    @lombok.Builder
    public static class AgentMetadata {
        private String name;
        private String description;
        private String group;
        private boolean lazy;
        private String scope;
        private boolean enablePersistence;
        private String persistenceStrategy;
        private Class<? extends AbstractAgentTemplate> templateClass;
        private AgentDefinition definition;
        private int priority;
    }
}