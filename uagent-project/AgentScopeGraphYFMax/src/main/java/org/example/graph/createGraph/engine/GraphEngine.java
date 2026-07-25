package org.example.graph.createGraph.engine;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.builder.RunnableConfigBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.Executors;

/**
 * 图执行的中央运行时。
 * 提供执行、流式输出、状态管理、时光旅行完整 API。
 *
 * <p>并行执行使用虚拟线程（需 spring.threads.virtual.enabled=true）。
 *
 * <p>上游 CompiledGraph 真实 API 签名：
 * <pre>
 *   Optional<OverAllState> invoke(OverAllState, RunnableConfig)
 *   Flux<NodeOutput> stream(Map<String, Object>, RunnableConfig)
 *   Collection&lt;StateSnapshot&gt; getStateHistory(RunnableConfig)
 *   StateSnapshot getState(RunnableConfig)
 *   RunnableConfig updateState(RunnableConfig, Map&lt;String, Object&gt;)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphEngine {

    private final GraphEngineProperties properties;

    // ==================== 执行 ====================

    /**
     * 调用编译后的图。
     *
     * @param graph         编译后的图
     * @param state         初始状态
     * @param threadId      用于检查点持久化的线程ID
     * @param checkPointId  可选的检查点ID，用于时光旅行/恢复
     * @return 最终状态，若无结果返回 null
     */
    public OverAllState invoke(CompiledGraph graph, OverAllState state,
                               String threadId, String checkPointId) throws GraphStateException {
        RunnableConfig config = buildRunnableConfig(threadId, checkPointId);
        return graph.invoke(state, config).orElse(null);
    }

    /**
     * 使用自定义 RunnableConfig 调用。
     */
    public OverAllState invoke(CompiledGraph graph, OverAllState state,
                               RunnableConfig config) throws GraphStateException {
        return graph.invoke(state, config).orElse(null);
    }

    /**
     * 流式执行结果。
     */
    public Flux<NodeOutput> stream(CompiledGraph graph, OverAllState state, String threadId) {
        RunnableConfig config = buildRunnableConfig(threadId, null);
        return graph.stream(state.data(), config);
    }

    // ==================== 状态查询 ====================

    /**
     * 获取当前状态快照。
     */
    public StateSnapshot getState(CompiledGraph graph, RunnableConfig config) {
        return graph.getState(config);
    }

    /**
     * 获取当前状态快照（按线程ID）。
     */
    public StateSnapshot getState(CompiledGraph graph, String threadId) {
        RunnableConfig config = RunnableConfigBuilder.create().threadId(threadId).build();
        return graph.getState(config);
    }

    // ==================== 时光旅行 ====================

    /**
     * 获取状态历史快照集合（时光旅行核心 API）。
     */
    public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, RunnableConfig config) {
        return graph.getStateHistory(config);
    }

    /**
     * 获取状态历史快照集合（按线程ID）。
     */
    public Collection<StateSnapshot> getStateHistory(CompiledGraph graph, String threadId) {
        RunnableConfig config = RunnableConfigBuilder.create().threadId(threadId).build();
        return graph.getStateHistory(config);
    }

    /**
     * 列出线程的所有检查点摘要信息。
     *
     * @return 检查点列表，每项包含节点名、检查点ID、状态预览
     */
    public List<CheckpointSummary> listCheckpoints(CompiledGraph graph, String threadId) {
        Collection<StateSnapshot> history = getStateHistory(graph, threadId);
        List<CheckpointSummary> summaries = new ArrayList<>();
        for (StateSnapshot snapshot : history) {
            summaries.add(new CheckpointSummary(
                    snapshot.node(),
                    snapshot.config().checkPointId().orElse(""),
                    snapshot.next(),
                    snapshot.state()
            ));
        }
        return summaries;
    }

    /**
     * 获取指定检查点ID的快照。
     */
    public Optional<StateSnapshot> getCheckpoint(CompiledGraph graph, String threadId, String checkpointId) {
        Collection<StateSnapshot> history = getStateHistory(graph, threadId);
        return history.stream()
                .filter(s -> s.config().checkPointId().orElse("").equals(checkpointId))
                .findFirst();
    }

    /**
     * 从历史检查点分支执行（时光旅行 — 分支）。
     *
     * <p>获取指定快照的 RunnableConfig（含 checkpointId），从该状态重新执行图。
     * 执行结果不会覆盖原始分支，而是创建新的检查点链。
     *
     * @param graph    编译后的图
     * @param snapshot 目标历史快照
     * @return 分支执行后的最终状态
     */
    public OverAllState branchFromCheckpoint(CompiledGraph graph, StateSnapshot snapshot)
            throws GraphStateException {
        RunnableConfig config = snapshot.config();
        log.info("从检查点分支执行: node={}, checkpointId={}",
                snapshot.node(), config.checkPointId().orElse("unknown"));
        return graph.invoke(snapshot.state(), config).orElse(null);
    }

    /**
     * 更新历史检查点的状态并恢复执行（时光旅行 — 更新+恢复）。
     *
     * <p>先用 updateState 修改指定检查点的状态，再从更新后的检查点执行图。
     * 这是"修改过去并观察新结果"的核心能力。
     *
     * @param graph        编译后的图
     * @param snapshot     目标历史快照
     * @param updatedState 要更新的状态数据
     * @return 更新并恢复执行后的最终状态
     */
    public OverAllState updateAndResume(CompiledGraph graph, StateSnapshot snapshot,
                                        Map<String, Object> updatedState) throws Exception {
        RunnableConfig config = snapshot.config();
        log.info("更新检查点状态并恢复: node={}, checkpointId={}, 更新键={}",
                snapshot.node(), config.checkPointId().orElse("unknown"), updatedState.keySet());

        // 更新状态，获得新的 RunnableConfig
        RunnableConfig updatedConfig = graph.updateState(config, updatedState);

        // 从更新后的检查点重新执行
        return graph.invoke(snapshot.state(), updatedConfig).orElse(null);
    }

    /**
     * 更新状态（用于人机交互反馈注入）。
     */
    public RunnableConfig updateState(CompiledGraph graph, RunnableConfig config,
                                      Map<String, Object> updatedState) throws Exception {
        return graph.updateState(config, updatedState);
    }

    // ==================== 内部方法 ====================

    private RunnableConfig buildRunnableConfig(String threadId, String checkPointId) {
        RunnableConfigBuilder builder = RunnableConfigBuilder.create()
                .threadId(threadId);

        if (checkPointId != null) {
            builder.checkPointId(checkPointId);
        }

        // 并行执行使用虚拟线程（需 spring.threads.virtual.enabled=true）
        if (properties.isParallelExecutorEnabled()) {
            builder.addParallelNodeExecutor(Executors.newVirtualThreadPerTaskExecutor());
        }

        return builder.build();
    }

    // ==================== 数据类 ====================

    /**
     * 检查点摘要信息（时光旅行查询结果）。
     */
    public record CheckpointSummary(
            String node,
            String checkpointId,
            String nextNode,
            OverAllState state
    ) {}
}
