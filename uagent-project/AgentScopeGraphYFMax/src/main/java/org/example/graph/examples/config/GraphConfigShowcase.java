package org.example.graph.examples.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.createGraph.builder.RunnableConfigBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 图配置全选项展示 — 演示 @GraphDefinition、AbstractGraphTemplate、GraphBuilder、
 * RunnableConfigBuilder 的所有可配置项。
 *
 * <p>本文件既是可运行的示例，也是配置参考文档。
 * 每个配置项都附有说明、取值范围和使用场景。
 *
 * <h2>一、@GraphDefinition 注解配置项</h2>
 *
 * <h3>1. 基础标识</h3>
 * <pre>
 * name               图的唯一标识名称。GraphPoolManager 按此名称注册和查找。
 *                    为空时使用类名。必填建议。
 *                    用法：components.pool().getGraph("config-showcase")
 *
 * value              name 的简写形式（@GraphDefinition("xxx") 等同于 name="xxx"）
 *
 * description        人工可读的描述。用于日志、监控、GraphPoolManager.listAll()。
 *
 * group              分组名称。GraphPoolManager.getGraphsByGroup(group) 批量获取。
 *                    默认 "default"。自定义分组便于按业务领域管理图。
 * </pre>
 *
 * <h3>2. 生命周期</h3>
 * <pre>
 * lazy               延迟实例化。true（默认）= 仅注册元数据，首次调用才编译；
 *                    false = 应用启动时立即编译。适合启动时预验证图完整性。
 *
 * active             启用/禁用。false = 启动时跳过此图。用于灰度或临时下线。
 * </pre>
 *
 * <h3>3. 实例化策略</h3>
 * <pre>
 * scope              "prototype"（默认）= 每次调用创建新实例，避免状态污染；
 *                    "singleton" = 共享实例，适合无状态的纯函数图。
 *
 * priority           分组内的优先级（值越小优先级越高）。
 *                    用于 getGraphsByGroup() 返回结果的排序。
 * </pre>
 *
 * <h3>4. 检查点</h3>
 * <pre>
 * checkpointStrategy 持久化策略：
 *                    "memory"   = MemorySaver（进程内，重启丢失）  — 开发/测试
 *                    "postgres" = MyBatisFlexCheckpointSaver（PG） — 生产环境
 *                    ""         = 无检查点                         — 一次性图
 * </pre>
 *
 * <h3>5. 编译配置</h3>
 * <pre>
 * recursionLimit     最大递归深度。防止条件循环无限执行。
 *                    默认 25。复杂的循环图可适当增大。
 *
 * interruptBefore    执行前中断的节点名称数组（人机交互）。
 *                    图执行到这些节点前暂停，等待外部 resume。
 *
 * interruptAfter     执行后中断的节点名称数组。
 *                    节点执行完毕后暂停，适合审批场景。
 * </pre>
 *
 * <h3>6. 高级特性</h3>
 * <pre>
 * enableStreaming     是否启用流式输出。true = 支持 stream() 返回 Flux。
 *                     默认 false。
 *
 * parallelism         并行执行的线程池大小。0（默认）= 使用系统默认值。
 *                     通过 RunnableConfigBuilder.addParallelNodeExecutor() 自定义。
 * </pre>
 *
 * <h2>二、AbstractGraphTemplate 可重写方法</h2>
 * <pre>
 * setupStateKeyFactory()   状态键策略工厂。默认所有键使用 ReplaceStrategy。
 *                          可自定义（如某些键使用 AppendStrategy 累积列表）。
 *
 * setupCheckpointSaver()   自定义检查点保存器。默认从注解解析。
 *                          可在此注入自定义的 BaseCheckpointSaver 实现。
 *
 * setupCompileConfig()     自定义 CompileConfig。默认应用注解中的值。
 *                          可添加额外的编译选项。
 *
 * afterGraphCompiled()     编译后钩子。可用于注册监控、打印拓扑等。
 *
 * init()                   构造后初始化钩子。每次编译调用一次。
 * </pre>
 *
 * <h2>三、GraphBuilder 操作</h2>
 * <pre>
 * // 节点操作
 * addNode(name, action)             直接添加节点动作
 * addNode(name, pool)               从 NodeActionPool 按名称获取并添加
 * addSubgraphNode(name, subGraph)   嵌入子图（StateGraph 或 CompiledGraph）
 *
 * // 边操作
 * addEdge(target)                   隐式源节点（使用上一次 addNode 的节点名）
 * addEdge(source, target)           显式指定源→目标
 *
 * // 条件边操作
 * addConditionalEdges(action)                   隐式源 + 路由动作
 * addConditionalEdges(source, action)            显式源 + 路由动作
 * addConditionalEdges(edgeName, pool)            从 EdgeConditionPool 获取路由动作
 * addConditionalEdges(edgeName, pool, useLast)   池获取 + 可选隐式源
 *
 * // 并行操作
 * addParallelBranches(fanoutName, fanoutAction, branches, mergeName, mergeAction)
 *     完整版：5 参数。fanoutAction/mergeAction 传 null 表示节点已存在。
 * addParallelBranches(fanoutName, branches, mergeName, mergeAction)
 *     便捷版：4 参数。fanout 使用默认状态透传。
 * </pre>
 *
 * <h2>四、RunnableConfigBuilder 运行时配置</h2>
 * <pre>
 * RunnableConfigBuilder.create()
 *     .threadId("thread-1")                    检查点线程标识
 *     .checkPointId("cp-001")                  从指定检查点恢复（时间旅行）
 *     .metadata(Map.of("userId", "u1"))        附带元数据
 *     .addParallelNodeExecutor(executor)       自定义并行线程池
 *     .build()
 * </pre>
 */
@GraphDefinition(
        // ── 基础标识 ──────────────────────────────────────────
        name = "config-showcase",
        description = "图配置全选项展示（参考文档 + 可运行示例）",
        group = "examples",

        // ── 生命周期 ──────────────────────────────────────────
        lazy = true,          // true=首次调用时编译, false=启动时立即编译
        active = true,        // false=启动时跳过此图定义

        // ── 实例化策略 ────────────────────────────────────────
        scope = "prototype",  // "prototype"=每次新建, "singleton"=共享实例
        priority = 0,         // 分组内排序（值越小优先级越高）

        // ── 检查点 ────────────────────────────────────────────
        checkpointStrategy = "memory",
        // "memory"   = 进程内 MemorySaver（开发/测试）
        // "postgres" = PostgreSQL 持久化（生产环境）
        // ""         = 无检查点

        // ── 编译配置 ──────────────────────────────────────────
        recursionLimit = 25,
        // 条件循环最大递归深度。超限抛 GraphStateException。
        // 简单链路可设小（如 10），复杂循环图可增大（如 50）。

        interruptBefore = {},
        // 在指定节点执行前暂停。适合人机交互场景。
        // 例：interruptBefore = {"approval"} → 执行到 approval 前暂停等待 resume

        interruptAfter = {},
        // 在指定节点执行后暂停。
        // 例：interruptAfter = {"analysis"} → analysis 完成后暂停

        // ── 高级特性 ──────────────────────────────────────────
        enableStreaming = false,
        // true = 支持 graphEngine.stream() 返回 Flux<NodeOutput>

        parallelism = 0
        // 并行线程池大小。0 = 使用系统默认。
        // 实际线程池通过 RunnableConfigBuilder.addParallelNodeExecutor() 设置
)
public class GraphConfigShowcase extends AbstractGraphTemplate {

    public GraphConfigShowcase(GraphComponentFacade components) {
        super(components);
    }

    // ==================== 必须实现的抽象方法 ====================

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder
                // ── addNode: 直接传入 AsyncNodeAction ──
                .addNode("start", state -> {
                    String input = (String) state.value("input").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("step1", "处理: " + input);
                    return CompletableFuture.completedFuture(update);
                })

                // ── addNode: 从 NodeActionPool 按名称引用 ──
                // 等价于 builder.addNode("validate", components.nodeActions().get("validate"))
                .addNode("validate", components.nodeActions())

                // ── addEdge: 隐式源节点（使用上一次 addNode 的 "validate"） ──
                .addEdge("process")

                .addNode("process", components.nodeActions())

                // ── addConditionalEdges: 从 EdgeConditionPool 按名称引用 ──
                // 路由动作从池获取，源节点为 "process"（由 addConditionalEdges 的第一个参数决定）
                .addConditionalEdges("type-router", components.edgeConditions())
                .route("question-handler", "question-handler")
                .route("command-handler", "command-handler")
                .route("default-handler", "default-handler")
                .done()

                // ── 池引用的分支节点 ──
                .addNode("question-handler", components.nodeActions())
                .addEdge("result")
                .addNode("command-handler", components.nodeActions())
                .addEdge("result")
                .addNode("default-handler", components.nodeActions())
                .addEdge("result")

                .addNode("result", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("output", "[完成]");
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }

    // ==================== 可选重写（展示所有钩子点） ====================

    /**
     * 自定义状态键策略。
     *
     * <p>默认：所有键使用 ReplaceStrategy（新值覆盖旧值）。
     * <p>示例场景：消息列表使用 AppendStrategy 累积追加。
     *
     * <pre>
     * return components.stateKey().builder()
     *     .addStrategy("messages", new AppendStrategy())  // messages 键追加而非覆盖
     *     .build();
     * </pre>
     */
    @Override
    protected KeyStrategyFactory setupStateKeyFactory() {
        return components.stateKey().defaultFactory();
    }

    /**
     * 自定义检查点保存器。
     *
     * <p>默认：从 @GraphDefinition.checkpointStrategy() 解析。
     * <p>重写此方法可注入自定义的 BaseCheckpointSaver 实现。
     */
    @Override
    protected BaseCheckpointSaver setupCheckpointSaver(GraphDefinition def) {
        return components.checkpoint().create(def.checkpointStrategy());
    }

    /**
     * 自定义 CompileConfig。
     *
     * <p>默认：应用注解中的 recursionLimit、interruptBefore、interruptAfter。
     * <p>重写可添加额外的编译选项。
     */
    @Override
    protected CompileConfig.Builder setupCompileConfig(GraphDefinition def) {
        return CompileConfig.builder()
                .recursionLimit(def.recursionLimit());
    }

    /**
     * 编译后钩子。
     *
     * <p>可用于：
     * <ul>
     *   <li>注册监控指标</li>
     *   <li>打印拓扑结构</li>
     *   <li>预热缓存</li>
     * </ul>
     */
    @Override
    protected void afterGraphCompiled(CompiledGraph graph) {
        // 示例：打印编译完成信息
        // log.info("Graph compiled: nodeCount={}", graph.getNodeCount());
    }

    // ==================== 运行时配置示例（RunnableConfigBuilder） ====================

    /**
     * RunnableConfigBuilder 使用示例。
     *
     * <p>配置项说明：
     * <ul>
     *   <li>threadId — 检查点线程标识。同一 threadId 下的状态可追溯。</li>
     *   <li>checkPointId — 指定从哪个检查点恢复（时间旅行）。</li>
     *   <li>metadata — 附带元数据，传递给图执行上下文。</li>
     *   <li>addParallelNodeExecutor — 自定义并行线程池。</li>
     * </ul>
     *
     * <pre>
     * // 基础调用
     * RunnableConfig config = RunnableConfigBuilder.create()
     *     .threadId("session-001")
     *     .build();
     * components.engine().invoke(compiledGraph, initialState, config);
     *
     * // 从历史检查点恢复（时间旅行）
     * RunnableConfig config = RunnableConfigBuilder.create()
     *     .threadId("session-001")
     *     .checkPointId("cp-abc123")
     *     .build();
     *
     * // 附带元数据
     * RunnableConfig config = RunnableConfigBuilder.create()
     *     .threadId("session-001")
     *     .metadata(Map.of("userId", "user-42", "env", "staging"))
     *     .build();
     *
     * // 自定义并行线程池
     * Executor pool = Executors.newFixedThreadPool(8);
     * RunnableConfig config = RunnableConfigBuilder.create()
     *     .threadId("session-001")
     *     .addParallelNodeExecutor(pool)
     *     .build();
     * </pre>
     */

    // ==================== GraphEngine 调用方式 ====================

    /**
     * GraphEngine 提供的执行和状态查询方法。
     *
     * <pre>
     * // 同步执行
     * components.engine().invoke(compiledGraph, state, "thread-1");
     * components.engine().invoke(compiledGraph, state, "thread-1", "cp-001");
     * components.engine().invoke(compiledGraph, state, runnableConfig);
     *
     * // 流式执行
     * Flux&lt;NodeOutput&gt; flux = components.engine().stream(compiledGraph, state, "thread-1");
     *
     * // 状态查询
     * StateSnapshot snapshot = components.engine().getState(compiledGraph, "thread-1");
     *
     * // 时间旅行
     * List&lt;CheckpointSummary&gt; history = components.engine().listCheckpoints(compiledGraph, "thread-1");
     * StateSnapshot target = components.engine().getCheckpoint(compiledGraph, "thread-1", "cp-xxx");
     * CompiledGraph forked = components.engine().branchFromCheckpoint(compiledGraph, target);
     *
     * // 注入人工反馈
     * components.engine().updateState(compiledGraph, config, Map.of("approved", true));
     *
     * // 修改历史状态并重新执行
     * components.engine().updateAndResume(compiledGraph, snapshot, Map.of("input", "修改后的输入"));
     * </pre>
     */
}
