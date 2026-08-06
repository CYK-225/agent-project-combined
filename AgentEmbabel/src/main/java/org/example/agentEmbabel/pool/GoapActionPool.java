package org.example.agentEmbabel.pool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.agentEmbabel.annotation.GoapAction;
import org.example.agentEmbabel.model.GoapActionDef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GOAP 动作池。
 * 启动时自动扫描所有 @GoapAction 注解的类，建立名称 → 动作定义映射。
 *
 * <p>用法：
 * <pre>
 * // 获取动作定义
 * GoapActionDef action = goapActionPool.get("gather-ingredients");
 *
 * // 获取所有可用动作
 * List&lt;GoapActionDef&gt; allActions = goapActionPool.getAll();
 * </pre>
 */
@Slf4j
@Component
public class GoapActionPool {

    private final ApplicationContext applicationContext;

    /** 名称 → 动作定义 */
    private final Map<String, GoapActionDef> actionMap = new ConcurrentHashMap<>();

    @Value("${goap.scan-packages:org.example}")
    private String scanPackages;

    public GoapActionPool(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("GoapActionPool 初始化中...");
        scanAndRegister();
        log.info("GoapActionPool 初始化完成，已注册 {} 个 GOAP 动作。", actionMap.size());
    }

    // ==================== 公共 API ====================

    /**
     * 根据名称获取动作定义。
     *
     * @param name 动作名称
     * @return 动作定义
     * @throws IllegalArgumentException 名称未注册时抛出
     */
    public GoapActionDef get(String name) {
        GoapActionDef action = actionMap.get(name);
        if (action == null) {
            throw new IllegalArgumentException(
                    "GOAP 动作未注册: " + name + "。已注册: " + actionMap.keySet());
        }
        return action;
    }

    /**
     * 获取所有已注册的动作定义。
     */
    public List<GoapActionDef> getAll() {
        return new ArrayList<>(actionMap.values());
    }

    /**
     * 检查指定名称的动作是否已注册。
     */
    public boolean exists(String name) {
        return actionMap.containsKey(name);
    }

    /**
     * 获取已注册的动作名称集合。
     */
    public Set<String> getRegisteredNames() {
        return Collections.unmodifiableSet(actionMap.keySet());
    }

    /**
     * 获取在当前状态下可执行的动作列表。
     *
     * @param currentState 当前世界状态
     * @return 可执行的动作列表
     */
    public List<GoapActionDef> getExecutableActions(org.example.agentEmbabel.model.WorldState currentState) {
        return actionMap.values().stream()
                .filter(action -> action.isExecutable(currentState))
                .toList();
    }

    // ==================== 内部方法 ====================

    private void scanAndRegister() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(GoapAction.class));

        for (String pkg : scanPackages.split(",")) {
            String trimmed = pkg.trim();
            if (trimmed.isEmpty()) continue;

            var candidates = scanner.findCandidateComponents(trimmed);
            for (var bd : candidates) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    GoapAction annotation = clazz.getAnnotation(GoapAction.class);
                    if (annotation == null) continue;

                    GoapActionDef def = parseAnnotation(clazz, annotation);
                    actionMap.put(def.getName(), def);

                    log.debug("已注册 GOAP 动作: {} → {}（cost={}）",
                            def.getName(), clazz.getSimpleName(), def.getCost());
                } catch (ClassNotFoundException e) {
                    log.error("加载 GOAP 动作类失败: {}", bd.getBeanClassName(), e);
                }
            }
        }
    }

    private GoapActionDef parseAnnotation(Class<?> clazz, GoapAction annotation) {
        String name = resolveName(clazz, annotation);
        return GoapActionDef.builder()
                .name(name)
                .description(annotation.description())
                .preconditions(annotation.preconditions())
                .effects(annotation.effects())
                .cost(annotation.cost())
                .agentName(annotation.agentName())
                .nodeActionName(annotation.nodeActionName())
                .build();
    }

    private String resolveName(Class<?> clazz, GoapAction annotation) {
        if (!annotation.value().isEmpty()) return annotation.value();
        return clazz.getSimpleName();
    }
}
