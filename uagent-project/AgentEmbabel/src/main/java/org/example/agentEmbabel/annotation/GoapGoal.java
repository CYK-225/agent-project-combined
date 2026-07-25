package org.example.agentEmbabel.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GOAP 目标注解。
 * 标记在类上，声明一个 GOAP 目标。
 *
 * <p>开发者只需定义：
 * <ul>
 *   <li>conditions - 目标达成条件（如 {"mealReady": "true"}）</li>
 *   <li>priority - 优先级（数字越大优先级越高）</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 * &#64;GoapGoal(
 *     value = "prepare-meal",
 *     description = "准备一顿饭",
 *     conditions = {"mealReady: true"},
 *     priority = 10
 * )
 * public class PrepareMealGoal { }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface GoapGoal {

    /**
     * 目标名称。为空时使用类名。
     */
    String value() default "";

    /**
     * 目标描述。
     */
    String description() default "";

    /**
     * 目标达成条件。
     * 格式：{"key:value", "key2:value2"}
     * 当世界状态满足所有条件时，目标达成。
     */
    String[] conditions() default {};

    /**
     * 优先级。数字越大优先级越高。
     * 当存在多个目标时，优先级高的目标会被优先规划。
     */
    int priority() default 0;
}
