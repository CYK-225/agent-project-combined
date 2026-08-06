package org.example.agentEmbabel.pool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.agentEmbabel.annotation.GoapGoal;
import org.example.agentEmbabel.model.GoapGoalDef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GOAP 目标池。
 * 启动时自动扫描所有 @GoapGoal 注解的类，建立名称 → 目标定义映射。
 *
 * <p>用法：
 * <pre>
 * // 获取目标定义
 * GoapGoalDef goal = goapGoalPool.get("prepare-meal");
 *
 * // 获取所有已注册目标（按优先级排序）
 * List&lt;GoapGoalDef&gt; allGoals = goapGoalPool.getAll();
 * </pre>
 */
@Slf4j
@Component
public class GoapGoalPool {

    private final ApplicationContext applicationContext;

    /** 名称 → 目标定义 */
    private final Map<String, GoapGoalDef> goalMap = new ConcurrentHashMap<>();

    @Value("${goap.scan-packages:org.example}")
    private String scanPackages;

    public GoapGoalPool(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("GoapGoalPool 初始化中...");
        scanAndRegister();
        log.info("GoapGoalPool 初始化完成，已注册 {} 个 GOAP 目标。", goalMap.size());
    }

    // ==================== 公共 API ====================

    /**
     * 根据名称获取目标定义。
     *
     * @param name 目标名称
     * @return 目标定义
     * @throws IllegalArgumentException 名称未注册时抛出
     */
    public GoapGoalDef get(String name) {
        GoapGoalDef goal = goalMap.get(name);
        if (goal == null) {
            throw new IllegalArgumentException(
                    "GOAP 目标未注册: " + name + "。已注册: " + goalMap.keySet());
        }
        return goal;
    }

    /**
     * 获取所有已注册的目标定义（按优先级降序排序）。
     */
    public List<GoapGoalDef> getAll() {
        return goalMap.values().stream()
                .sorted(Comparator.comparingInt(GoapGoalDef::getPriority).reversed())
                .toList();
    }

    /**
     * 检查指定名称的目标是否已注册。
     */
    public boolean exists(String name) {
        return goalMap.containsKey(name);
    }

    /**
     * 获取已注册的目标名称集合。
     */
    public Set<String> getRegisteredNames() {
        return Collections.unmodifiableSet(goalMap.keySet());
    }

    /**
     * 获取在当前状态下未达成的目标列表（按优先级降序排序）。
     *
     * @param currentState 当前世界状态
     * @return 未达成的目标列表
     */
    public List<GoapGoalDef> getUnachievedGoals(org.example.agentEmbabel.model.WorldState currentState) {
        return goalMap.values().stream()
                .filter(goal -> !goal.isAchieved(currentState))
                .sorted(Comparator.comparingInt(GoapGoalDef::getPriority).reversed())
                .toList();
    }

    /**
     * 获取最高优先级的未达成目标。
     *
     * @param currentState 当前世界状态
     * @return 最高优先级的未达成目标，如果所有目标都已达成返回 null
     */
    public GoapGoalDef getHighestPriorityGoal(org.example.agentEmbabel.model.WorldState currentState) {
        return goalMap.values().stream()
                .filter(goal -> !goal.isAchieved(currentState))
                .max(Comparator.comparingInt(GoapGoalDef::getPriority))
                .orElse(null);
    }

    // ==================== 内部方法 ====================

    private void scanAndRegister() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(GoapGoal.class));

        for (String pkg : scanPackages.split(",")) {
            String trimmed = pkg.trim();
            if (trimmed.isEmpty()) continue;

            var candidates = scanner.findCandidateComponents(trimmed);
            for (var bd : candidates) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    GoapGoal annotation = clazz.getAnnotation(GoapGoal.class);
                    if (annotation == null) continue;

                    GoapGoalDef def = parseAnnotation(clazz, annotation);
                    goalMap.put(def.getName(), def);

                    log.debug("已注册 GOAP 目标: {} → {}（priority={}）",
                            def.getName(), clazz.getSimpleName(), def.getPriority());
                } catch (ClassNotFoundException e) {
                    log.error("加载 GOAP 目标类失败: {}", bd.getBeanClassName(), e);
                }
            }
        }
    }

    private GoapGoalDef parseAnnotation(Class<?> clazz, GoapGoal annotation) {
        String name = resolveName(clazz, annotation);
        return GoapGoalDef.builder()
                .name(name)
                .description(annotation.description())
                .conditions(annotation.conditions())
                .priority(annotation.priority())
                .build();
    }

    private String resolveName(Class<?> clazz, GoapGoal annotation) {
        if (!annotation.value().isEmpty()) return annotation.value();
        return clazz.getSimpleName();
    }
}
