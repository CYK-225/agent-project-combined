package org.example.agentEmbabel.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GOAP 动作注解。
 * 标记在类上，声明一个 GOAP 原子动作。
 *
 * <p>开发者只需定义：
 * <ul>
 *   <li>preconditions - 前置条件（如 {"hasIngredients": "true"}）</li>
 *   <li>effects - 执行效果（如 {"mealReady": "true"}）</li>
 *   <li>agentName 或 nodeActionName - 关联的执行器</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 * &#64;GoapAction(
 *     value = "gather-ingredients",
 *     description = "收集食材",
 *     preconditions = {"hasRecipe: true"},
 *     effects = {"hasIngredients: true"},
 *     cost = 1.0,
 *     agentName = "IngredientGatherAgent"
 * )
 * public class GatherIngredientsAction { }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface GoapAction {

    /**
     * 动作名称。为空时使用类名。
     */
    String value() default "";

    /**
     * 动作描述。
     */
    String description() default "";

    /**
     * 前置条件。
     * 格式：{"key:value", "key2:value2"}
     * 只有当世界状态满足所有前置条件时，才能执行此动作。
     */
    String[] preconditions() default {};

    /**
     * 执行效果。
     * 格式：{"key:value", "key2:value2"}
     * 执行完成后，世界状态将更新这些效果。
     */
    String[] effects() default {};

    /**
     * 动作代价。A* 规划时用于计算路径代价。
     */
    double cost() default 1.0;

    /**
     * 关联的 Agent 名称（引用 @AgentDefinition）。
     * 执行时通过 AgentPoolManager.getAgent(agentName) 获取 Agent 实例。
     */
    String agentName() default "";

    /**
     * 关联的 NodeAction 名称（引用 @NodeAction）。
     * 执行时通过 NodeActionPool.get(nodeActionName) 获取节点动作实例。
     * 优先级高于 agentName。
     */
    String nodeActionName() default "";
}
