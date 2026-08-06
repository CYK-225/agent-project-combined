package org.example.graph.workflow.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 图节点动作声明注解。
 * 将类标记为可被 NodeActionPool 扫描注册的节点动作。
 * 继承 @Component 以实现 Spring Bean 自动注册。
 *
 * <p>用法：
 * <pre>
 * @NodeAction(value = "validate", description = "输入验证节点")
 * public class ValidateNode extends SimpleNodeAction { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface NodeAction {

    /** 节点唯一名称。为空时默认使用类的简单名称。 */
    String value() default "";

    /** 节点描述，用于可观测性和文档。 */
    String description() default "";

    /**
     * 实例化模式：
     * "prototype" = 每次调用创建新实例（默认）
     * "singleton" = 共享单例
     */
    String scope() default "prototype";
}
