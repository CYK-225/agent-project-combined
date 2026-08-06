package org.example.graph.workflow.core;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.engine.GraphEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 自动发现 @GraphDefinition 注解的类，注册元数据，
 * 并延迟实例化 CompiledGraph 实例。
 *
 * 提供底层（getGraph）和高层（invokeGraph, streamGraph）两种 API。
 */
@Slf4j
@Component
public class GraphPoolManager {

    private final ApplicationContext applicationContext;

    @Getter
    private final Map<String, GraphMetadata> metadataRegistry = new ConcurrentHashMap<>();
    private final Map<String, CompiledGraph> graphCache = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Set<String>> groupIndex = new ConcurrentHashMap<>();

    @Value("${graph.workflow.scan-packages:org.example}")
    private String scanPackages;

    @Autowired(required = false)
    private GraphEngine graphEngine;

    public GraphPoolManager(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("GraphPoolManager 初始化中（扫描包={}）...", scanPackages);
        scanAndRegisterGraphs();
        log.info("GraphPoolManager 初始化完成，已注册 {} 个图。", metadataRegistry.size());
    }

    private void scanAndRegisterGraphs() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(GraphDefinition.class));

        for (String pkg : scanPackages.split(",")) {
            String trimmed = pkg.trim();
            if (trimmed.isEmpty()) continue;

            Set<org.springframework.beans.factory.config.BeanDefinition> candidates =
                    scanner.findCandidateComponents(trimmed);

            for (org.springframework.beans.factory.config.BeanDefinition bd : candidates) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    GraphDefinition definition = clazz.getAnnotation(GraphDefinition.class);

                    if (definition == null || !definition.active()) continue;
                    if (!AbstractGraphTemplate.class.isAssignableFrom(clazz)) continue;

                    registerGraphMetadata(clazz, definition);
                } catch (ClassNotFoundException e) {
                    log.error("加载图类失败: {}", bd.getBeanClassName(), e);
                }
            }
        }
    }

    private void registerGraphMetadata(Class<?> clazz, GraphDefinition definition) {
        String graphName = resolveGraphName(clazz, definition);

        GraphMetadata metadata = GraphMetadata.builder()
                .name(graphName)
                .description(definition.description())
                .group(definition.group())
                .lazy(definition.lazy())
                .scope(definition.scope())
                .checkpointStrategy(definition.checkpointStrategy())
                .templateClass((Class<? extends AbstractGraphTemplate>) clazz)
                .definition(definition)
                .priority(definition.priority())
                .build();

        metadataRegistry.put(graphName, metadata);
        groupIndex.computeIfAbsent(definition.group(), k -> ConcurrentHashMap.newKeySet())
                .add(graphName);

        log.debug("已注册图元数据: {}（分组={}, 延迟={}）",
                graphName, definition.group(), definition.lazy());
    }

    private String resolveGraphName(Class<?> clazz, GraphDefinition definition) {
        if (!definition.value().isEmpty()) return definition.value();
        if (!definition.name().isEmpty()) return definition.name();
        return clazz.getSimpleName();
    }

    // ==================== 公共 API ====================

    /**
     * 根据名称获取或创建编译后的图。
     * 延迟加载：首次调用触发 compileGraph()。
     */
    public CompiledGraph getGraph(String name) {
        GraphMetadata metadata = metadataRegistry.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("未找到图: " + name);
        }

        if ("singleton".equalsIgnoreCase(metadata.getScope())) {
            return graphCache.computeIfAbsent(name, k -> instantiateGraph(metadata));
        }

        return instantiateGraph(metadata);
    }

    /**
     * 获取分组中的所有图，按优先级排序。
     */
    public List<CompiledGraph> getGraphsByGroup(String group) {
        Set<String> names = groupIndex.get(group);
        if (names == null || names.isEmpty()) return new ArrayList<>();
        return names.stream()
                .sorted(Comparator.comparingInt(n ->
                        Optional.ofNullable(metadataRegistry.get(n))
                                .map(GraphMetadata::getPriority).orElse(0)))
                .map(this::getGraph)
                .collect(Collectors.toList());
    }

    /**
     * 获取图的元数据而不实例化它。
     */
    public Optional<GraphMetadata> getMetadata(String name) {
        return Optional.ofNullable(metadataRegistry.get(name));
    }

    public void evictCache(String name) {
        graphCache.remove(name);
    }

    public void evictAllCache() {
        graphCache.clear();
    }

    // ==================== 高层执行 API ====================

    /**
     * 按名称调用图执行。
     * 便捷方法，组合了 getGraph() + graphEngine.invoke()。
     *
     * @param name     图名称（通过 @GraphDefinition 注册）
     * @param state    初始状态
     * @param threadId 用于检查点持久化的线程ID
     * @return 执行后的最终状态
     */
    public OverAllState invokeGraph(String name, OverAllState state, String threadId) throws GraphStateException {
        if (graphEngine == null) {
            throw new IllegalStateException("GraphEngine 不可用，无法调用图。");
        }
        CompiledGraph graph = getGraph(name);
        return graphEngine.invoke(graph, state, threadId, null);
    }

    /**
     * 按名称调用图执行，从指定检查点恢复。
     */
    public OverAllState invokeGraph(String name, OverAllState state, String threadId, String checkPointId)
            throws GraphStateException {
        if (graphEngine == null) {
            throw new IllegalStateException("GraphEngine 不可用，无法调用图。");
        }
        CompiledGraph graph = getGraph(name);
        return graphEngine.invoke(graph, state, threadId, checkPointId);
    }

    /**
     * 按名称流式执行图。
     */
    public Flux<?> streamGraph(String name, OverAllState state, String threadId) {
        if (graphEngine == null) {
            throw new IllegalStateException("GraphEngine 不可用，无法流式执行图。");
        }
        CompiledGraph graph = getGraph(name);
        return graphEngine.stream(graph, state, threadId);
    }

    // ==================== 内部方法 ====================

    private CompiledGraph instantiateGraph(GraphMetadata metadata) {
        try {
            AbstractGraphTemplate template = applicationContext.getBean(metadata.getTemplateClass());
            return template.compileGraph(metadata.getDefinition());
        } catch (GraphStateException e) {
            log.error("编译图失败: {}", metadata.getName(), e);
            throw new RuntimeException("编译图失败: " + metadata.getName(), e);
        }
    }

    @Getter
    @lombok.Builder
    public static class GraphMetadata {
        private String name;
        private String description;
        private String group;
        private boolean lazy;
        private String scope;
        private String checkpointStrategy;
        private Class<? extends AbstractGraphTemplate> templateClass;
        private GraphDefinition definition;
        private int priority;
    }
}
