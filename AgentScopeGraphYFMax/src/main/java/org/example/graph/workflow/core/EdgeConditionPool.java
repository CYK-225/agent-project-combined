package org.example.graph.workflow.core;

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.annotation.EdgeCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 条件边动作池。
 * 启动时自动扫描所有 @EdgeCondition 注解的类，建立名称 → 类/实例映射。
 *
 * <p>支持两种实例化模式：
 * <ul>
 *   <li>prototype（默认）：每次 get() 创建新实例</li>
 *   <li>singleton：首次 get() 后缓存，后续共享</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 * // 获取实例
 * AsyncEdgeAction action = edgeConditionPool.get("type-router");
 *
 * // 检查是否存在
 * if (edgeConditionPool.exists("type-router")) { ... }
 *
 * // 列出所有已注册的边
 * Map&lt;String, String&gt; all = edgeConditionPool.listAll();
 * </pre>
 */
@Slf4j
@Component
public class EdgeConditionPool {

    private final ApplicationContext applicationContext;

    /** 名称 → 边动作类 */
    private final Map<String, Class<? extends AsyncEdgeAction>> classMap = new ConcurrentHashMap<>();

    /** 名称 → 描述 */
    private final Map<String, String> descriptionMap = new ConcurrentHashMap<>();

    /** 名称 → 实例化模式（prototype / singleton） */
    private final Map<String, String> scopeMap = new ConcurrentHashMap<>();

    /** 单例缓存（仅 scope=singleton 时使用） */
    private final Map<String, AsyncEdgeAction> singletonCache = new ConcurrentHashMap<>();

    @Value("${graph.workflow.scan-packages:org.example}")
    private String scanPackages;

    public EdgeConditionPool(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("EdgeConditionPool 初始化中...");
        scanAndRegister();
        log.info("EdgeConditionPool 初始化完成，已注册 {} 个边动作。", classMap.size());
    }

    // ==================== 公共 API ====================

    /**
     * 根据名称获取边动作实例。
     * prototype 模式每次新建，singleton 模式共享缓存。
     *
     * @param name 边名称
     * @return 边动作实例
     * @throws IllegalArgumentException 名称未注册时抛出
     */
    public AsyncEdgeAction get(String name) {
        Class<? extends AsyncEdgeAction> clazz = classMap.get(name);
        if (clazz == null) {
            throw new IllegalArgumentException(
                    "边动作未注册: " + name + "。已注册: " + classMap.keySet());
        }

        String scope = scopeMap.getOrDefault(name, "prototype");
        if ("singleton".equalsIgnoreCase(scope)) {
            return singletonCache.computeIfAbsent(name, k -> createInstance(clazz));
        }

        return createInstance(clazz);
    }

    /**
     * 检查指定名称的边动作是否已注册。
     */
    public boolean exists(String name) {
        return classMap.containsKey(name);
    }

    /**
     * 获取边动作的描述。
     */
    public String getDescription(String name) {
        return descriptionMap.getOrDefault(name, "");
    }

    /**
     * 列出所有已注册的边动作（名称 → 描述）。
     */
    public Map<String, String> listAll() {
        return Collections.unmodifiableMap(descriptionMap);
    }

    /**
     * 获取已注册的边名称集合。
     */
    public Set<String> getRegisteredNames() {
        return Collections.unmodifiableSet(classMap.keySet());
    }

    // ==================== 内部方法 ====================

    @SuppressWarnings("unchecked")
    private void scanAndRegister() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EdgeCondition.class));

        for (String pkg : scanPackages.split(",")) {
            String trimmed = pkg.trim();
            if (trimmed.isEmpty()) continue;

            var candidates = scanner.findCandidateComponents(trimmed);
            for (var bd : candidates) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    EdgeCondition annotation = clazz.getAnnotation(EdgeCondition.class);
                    if (annotation == null) continue;

                    if (!AsyncEdgeAction.class.isAssignableFrom(clazz)) {
                        log.warn("@EdgeCondition 注解的类 {} 未实现 AsyncEdgeAction，跳过", clazz.getName());
                        continue;
                    }

                    String name = resolveName(clazz, annotation);
                    classMap.put(name, (Class<? extends AsyncEdgeAction>) clazz);
                    descriptionMap.put(name, annotation.description());
                    scopeMap.put(name, annotation.scope());

                    log.debug("已注册边动作: {} → {}（scope={}）", name, clazz.getSimpleName(), annotation.scope());
                } catch (ClassNotFoundException e) {
                    log.error("加载边动作类失败: {}", bd.getBeanClassName(), e);
                }
            }
        }
    }

    private String resolveName(Class<?> clazz, EdgeCondition annotation) {
        if (!annotation.value().isEmpty()) return annotation.value();
        return clazz.getSimpleName();
    }

    private AsyncEdgeAction createInstance(Class<? extends AsyncEdgeAction> clazz) {
        try {
            try {
                return applicationContext.getBean(clazz);
            } catch (Exception e) {
                return clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("创建边动作实例失败: " + clazz.getName(), e);
        }
    }
}
