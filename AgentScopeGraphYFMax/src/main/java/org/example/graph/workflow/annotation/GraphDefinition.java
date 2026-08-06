package org.example.graph.workflow.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 图工作流声明式定义注解。
 * 将类标记为由 GraphPoolManager 管理的图模板。
 * 继承 @Component 以实现 Spring Bean 注册。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface GraphDefinition {

    /** 唯一图名称。为空时默认使用类的简单名称。 */
    String name() default "";

    /** name 的简写形式。 */
    String value() default "";

    /** 可观测性描述。 */
    String description() default "";

    /** 分组名称，用于通过 GraphPoolManager.getGraphsByGroup() 批量获取。 */
    String group() default "default";

    /** 延迟实例化：true = 仅注册元数据，首次调用时才构建。 */
    boolean lazy() default true;

    /** 启用/禁用标志。false = 启动时忽略。 */
    boolean active() default true;

    /** 原型（默认）= 每次调用创建新实例；单例 = 共享实例。 */
    String scope() default "prototype";

    /** 分组内的优先级排序（值越小优先级越高）。 */
    int priority() default 0;

    /**
     * 默认检查点策略：
     * "memory"   = MemorySaver（进程内，无持久化）
     * "postgres" = MyBatisFlexCheckpointSaver
     * ""         = 无检查点
     */
    String checkpointStrategy() default "memory";

    /** 此图编译配置的默认递归限制。 */
    int recursionLimit() default 25;

    /** 在执行前中断的节点列表（静态中断）。 */
    String[] interruptBefore() default {};

    /** 在执行后中断的节点列表（静态中断）。 */
    String[] interruptAfter() default {};

    /** 是否启用流式输出支持。 */
    boolean enableStreaming() default false;

    /** 并行节点执行的线程池大小。0 = 使用系统默认值。 */
    int parallelism() default 0;
}
