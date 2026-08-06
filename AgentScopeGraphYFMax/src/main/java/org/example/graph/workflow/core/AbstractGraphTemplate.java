package org.example.graph.workflow.core;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;

import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;

/**
 * 图工作流的模板方法基类。
 * <p>
 * 子类必须实现：
 *   buildGraph(GraphBuilder)  — 定义节点和边
 *   initialState()            — 提供初始状态
 * </p>
 * 可选重写（默认值来自 @GraphDefinition）：
 *   setupStateKeyFactory()    — 自定义 KeyStrategyFactory
 *   setupCheckpointSaver()    — 自定义检查点保存器
 *   setupCompileConfig()      — 自定义 CompileConfig 构建器
 *   afterGraphCompiled()      — 编译后钩子
 *   init()                    — 构造后钩子
 */
@Slf4j
public abstract class AbstractGraphTemplate {

    @Getter
    protected final GraphComponentFacade components;

    protected AbstractGraphTemplate(GraphComponentFacade components) {
        this.components = components;
    }

    // ==================== 必须实现的抽象方法 ====================

    /**
     * 构建图拓扑。在 compileGraph() 期间恰好被调用一次。
     * 接收一个预配置的 GraphBuilder；子类添加节点和边。
     */
    protected abstract void buildGraph(GraphBuilder builder) throws GraphStateException;

    /**
     * 返回 invoke/stream 的初始状态。
     */
    protected abstract OverAllState initialState();

    // ==================== 可选重写点 ====================

    /**
     * 重写以提供自定义 KeyStrategyFactory。
     * 默认：所有键使用 ReplaceStrategy。
     */
    protected KeyStrategyFactory setupStateKeyFactory() {
        return components.stateKey().defaultFactory();
    }

    /**
     * 重写以提供自定义检查点保存器。
     * 默认：从 @GraphDefinition.checkpointStrategy() 解析。
     */
    protected BaseCheckpointSaver setupCheckpointSaver(GraphDefinition def) {
        return components.checkpoint().create(def.checkpointStrategy());
    }

    /**
     * 重写以自定义超出 @GraphDefinition 默认值的 CompileConfig。
     * 默认：应用注解中的 recursionLimit、interruptBefore、interruptAfter。
     */
    protected CompileConfig.Builder setupCompileConfig(GraphDefinition def) {
        CompileConfig.Builder cb = CompileConfig.builder()
                .recursionLimit(def.recursionLimit());

        if (def.interruptBefore().length > 0) {
            cb.interruptBefore(def.interruptBefore());
        }
        if (def.interruptAfter().length > 0) {
            cb.interruptAfter(def.interruptAfter());
        }
        return cb;
    }

    /**
     * 图编译完成后、返回调用者之前调用的钩子。
     */
    protected void afterGraphCompiled(CompiledGraph graph) {}

    /**
     * 构造后初始化钩子（每次编译调用一次）。
     */
    protected void init() {}

    // ==================== 模板方法（final，编排） ====================

    /**
     * 编译图。这是负责编排的模板方法：
     * 1. 解析注解元数据
     * 2. 创建 KeyStrategyFactory
     * 3. 创建检查点保存器
     * 4. 使用键策略创建 StateGraph
     * 5. 委托给子类的 buildGraph()
     * 6. 应用 CompileConfig（注入检查点）
     * 7. 编译并返回
     */
    public final CompiledGraph compileGraph(GraphDefinition definition) throws GraphStateException {
        if (definition == null) {
            throw new IllegalArgumentException("GraphDefinition cannot be null.");
        }

        init();

        // 1. 状态键工厂
        KeyStrategyFactory keyStrategyFactory = setupStateKeyFactory();

        // 2. 检查点保存器（IoC 注入，非死代码）
        BaseCheckpointSaver checkpointSaver = setupCheckpointSaver(definition);

        // 3. 使用上游 API 创建 StateGraph
        StateGraph stateGraph = new StateGraph(
                definition.name().isEmpty() ? this.getClass().getSimpleName() : definition.name(),
                keyStrategyFactory);

        // 4. 构建 GraphBuilder 包装器
        GraphBuilder builder = new GraphBuilder(stateGraph, checkpointSaver);

        // 5. 委托给子类（定义节点 + 边）
        buildGraph(builder);

        // 6. 从注解 + 子类重写生成 CompileConfig
        CompileConfig.Builder compileConfigBuilder = setupCompileConfig(definition);

        // 将检查点注入编译配置
        if (checkpointSaver != null) {
            SaverConfig saverConfig = SaverConfig.builder()
                    .register(checkpointSaver)
                    .build();
            compileConfigBuilder.saverConfig(saverConfig);
        }

        // 7. 编译
        CompiledGraph compiledGraph = stateGraph.compile(compileConfigBuilder.build());

        afterGraphCompiled(compiledGraph);

        log.info("Graph [{}] compiled successfully. Checkpoint: {}",
                definition.name().isEmpty() ? this.getClass().getSimpleName() : definition.name(),
                checkpointSaver != null ? checkpointSaver.getClass().getSimpleName() : "none");

        return compiledGraph;
    }

    /**
     * 便捷方法：使用此类上的 @GraphDefinition 注解进行编译。
     */
    public final CompiledGraph compileGraph() throws GraphStateException {
        GraphDefinition def = getClass().getAnnotation(GraphDefinition.class);
        if (def == null) {
            throw new IllegalStateException(
                    "Class " + getClass().getSimpleName() + " must be annotated with @GraphDefinition");
        }
        return compileGraph(def);
    }

    /**
     * 构建 StateGraph 但不编译。
     * 用于子图场景：父图将返回的 StateGraph 通过 addSubgraphNode() 添加为节点，
     * 由父图统一编译。
     *
     * <p>用法：
     * <pre>
     *   // 方式 A：StateGraph 子图（父图编译）
     *   StateGraph sub = new ResearchSubgraph(components)
     *       .buildStateGraph(new ResearchSubgraph().getDefinition());
     *   builder.addSubgraphNode("research", sub);
     *
     *   // 方式 B：CompiledGraph 子图（独立编译）
     *   CompiledGraph sub = components.pool().getGraph("research-sub");
     *   builder.addSubgraphNode("research", sub);
     * </pre>
     */
    public final StateGraph buildStateGraph(GraphDefinition definition) throws GraphStateException {
        if (definition == null) {
            throw new IllegalArgumentException("GraphDefinition 不能为空。");
        }
        init();
        KeyStrategyFactory keyFactory = setupStateKeyFactory();
        String name = definition.name().isEmpty() ? this.getClass().getSimpleName() : definition.name();
        StateGraph stateGraph = new StateGraph(name, keyFactory);
        GraphBuilder builder = new GraphBuilder(stateGraph, null);
        buildGraph(builder);
        return stateGraph;
    }

    /**
     * 便捷方法：使用此类上的 @GraphDefinition 注解构建 StateGraph。
     */
    public final StateGraph buildStateGraph() throws GraphStateException {
        GraphDefinition def = getClass().getAnnotation(GraphDefinition.class);
        if (def == null) {
            throw new IllegalStateException(
                    "Class " + getClass().getSimpleName() + " must be annotated with @GraphDefinition");
        }
        return buildStateGraph(def);
    }

    /**
     * 获取此类上的 @GraphDefinition 注解。
     */
    public GraphDefinition getDefinition() {
        GraphDefinition def = getClass().getAnnotation(GraphDefinition.class);
        if (def == null) {
            throw new IllegalStateException(
                    "Class " + getClass().getSimpleName() + " must be annotated with @GraphDefinition");
        }
        return def;
    }
}
