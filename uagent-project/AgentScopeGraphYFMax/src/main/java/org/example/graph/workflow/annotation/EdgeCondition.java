package org.example.graph.workflow.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 条件边路由声明注解。
 * 将类标记为可被 EdgeConditionPool 扫描注册的边动作。
 * 继承 @Component 以实现 Spring Bean 自动注册。
 *
 * <p>用法：
 * <pre>
 * @EdgeCondition(value = "type-router", description = "输入类型路由")
 * public class TypeRouterEdge extends SimpleEdgeAction { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface EdgeCondition {

    /** 边唯一名称。为空时默认使用类的简单名称。 */
    String value() default "";

    /** 边描述，用于可观测性和文档。 */
    String description() default "";

    /**
     * 实例化模式：
     * "prototype" = 每次调用创建新实例（默认）
     * "singleton" = 共享单例
     */
    String scope() default "prototype";
}
