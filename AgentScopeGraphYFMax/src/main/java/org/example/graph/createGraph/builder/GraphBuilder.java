package org.example.graph.createGraph.builder;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 图构建器（充血模型）。
 * 统一负责：节点创建、边创建、子图操作、并行操作。
 *
 * <p>所有 add* 方法返回 this，支持流式链式调用。
 * 单参数 addEdge(target) 隐式使用上一次 addNode 的节点名作为源。
 *
 * <p>用法：
 * <pre>
 * builder.addNode("a", action)
 *        .addEdge("b")              // a -> b（隐式）
 *        .addNode("b", action)
 *        .addEdge("c")              // b -> c（隐式）
 *        .addNode("c", action)
 *        .addEdge(StateGraph.END);  // c -> END
 * </pre>
 */
@Slf4j
public class GraphBuilder {

    @Getter
    private final StateGraph stateGraph;
    private final BaseCheckpointSaver checkpointSaver;

    /** 最后添加的节点名，用于单参数 addEdge(target) 的隐式源节点 */
    private String lastNodeName;

    public GraphBuilder(StateGraph stateGraph, BaseCheckpointSaver checkpointSaver) {
        this.stateGraph = stateGraph;
        this.checkpointSaver = checkpointSaver;
    }

    // ==================== 节点操作 ====================

    /**
     * 添加节点。内部记录 lastNodeName。
     */
    public GraphBuilder addNode(String name, AsyncNodeAction action) throws GraphStateException {
        stateGraph.addNode(name, action);
        this.lastNodeName = name;
        return this;
    }

    /**
     * 将子图作为节点添加（StateGraph）。
     */
    public GraphBuilder addSubgraphNode(String name, StateGraph subGraph) throws GraphStateException {
        stateGraph.addNode(name, subGraph);
        this.lastNodeName = name;
        return this;
    }

    /**
     * 将子图作为节点添加（CompiledGraph）。
     */
    public GraphBuilder addSubgraphNode(String name, CompiledGraph subGraph) throws GraphStateException {
        stateGraph.addNode(name, subGraph);
        this.lastNodeName = name;
        return this;
    }

    // ==================== 边操作 ====================

    /**
     * 添加边：lastNodeName -> target（隐式源节点）。
     * 必须在 addNode 之后调用。
     */
    public GraphBuilder addEdge(String target) throws GraphStateException {
        if (lastNodeName == null) {
            throw new IllegalStateException("没有可作为源的节点，请先调用 addNode()");
        }
        stateGraph.addEdge(lastNodeName, target);
        return this;
    }

    /**
     * 添加边：source -> target（显式指定）。
     */
    public GraphBuilder addEdge(String source, String target) throws GraphStateException {
        stateGraph.addEdge(source, target);
        return this;
    }

    /**
     * 从 lastNodeName 添加条件边。返回 ConditionalEdgeBuilder。
     */
    public ConditionalEdgeBuilder addConditionalEdges(AsyncEdgeAction routingAction) {
        if (lastNodeName == null) {
            throw new IllegalStateException("没有可作为源的节点，请先调用 addNode()");
        }
        return new ConditionalEdgeBuilder(this, lastNodeName, routingAction);
    }

    /**
     * 从指定节点添加条件边。返回 ConditionalEdgeBuilder。
     */
    public ConditionalEdgeBuilder addConditionalEdges(String source, AsyncEdgeAction routingAction) {
        return new ConditionalEdgeBuilder(this, source, routingAction);
    }

    // ==================== 并行操作 ====================

    /**
     * 全参数版本：添加并行扇出拓扑 fanoutNode -> [branch1, branch2, ...] -> mergeNode。
     *
     * <p>fanoutAction / mergeAction 为 null 时表示该节点已存在（由调用方提前 addNode），
     * 此时仅连边而不创建节点，避免重复注册导致 StateGraph 报错。
     *
     * @param fanoutNodeName 扇出源节点名称
     * @param fanoutAction   扇出节点动作；null 表示节点已存在，跳过创建
     * @param branches       分支名 -> 动作映射
     * @param mergeNodeName  合并节点名称
     * @param mergeAction    合并节点动作；null 表示节点已存在，跳过创建
     */
    public GraphBuilder addParallelBranches(
            String fanoutNodeName,
            AsyncNodeAction fanoutAction,
            Map<String, AsyncNodeAction> branches,
            String mergeNodeName,
            AsyncNodeAction mergeAction) throws GraphStateException {

        // 1. 扇出节点：非 null 时创建，null 则假定已存在
        if (fanoutAction != null) {
            addNode(fanoutNodeName, fanoutAction);
        }

        // 2. 每个分支节点 + 边
        for (Map.Entry<String, AsyncNodeAction> entry : branches.entrySet()) {
            String branchName = entry.getKey();
            addNode(branchName, entry.getValue());
            addEdge(fanoutNodeName, branchName);  // 扇出 → 分支
            addEdge(branchName, mergeNodeName);   // 分支 → 合并
        }

        // 3. 合并节点：非 null 时创建，null 则假定已存在
        if (mergeAction != null) {
            addNode(mergeNodeName, mergeAction);
        }

        log.info("已构建并行拓扑: {} -> [{}] -> {} (fanout={}, merge={})",
                fanoutNodeName,
                String.join(", ", branches.keySet()),
                mergeNodeName,
                fanoutAction != null ? "新建" : "已有",
                mergeAction != null ? "新建" : "已有");

        return this;
    }

    /**
     * 重载方法 2：便捷版本（向下兼容）
     * 内部默认使用状态透传逻辑，并委托给全参数方法。
     *
     * @param fanoutNodeName 扇出源节点名称
     * @param branches       分支名 -> 动作映射
     * @param mergeNodeName  合并节点名称
     * @param mergeAction    合并节点动作
     */
    public GraphBuilder addParallelBranches(
            String fanoutNodeName,
            Map<String, AsyncNodeAction> branches,
            String mergeNodeName,
            AsyncNodeAction mergeAction) throws GraphStateException {

        AsyncNodeAction defaultFanoutAction = state ->
                CompletableFuture.completedFuture(new HashMap<>(state.data()));

        return addParallelBranches(
                fanoutNodeName,
                defaultFanoutAction,
                branches,
                mergeNodeName,
                mergeAction
        );
    }

    // ==================== 池感知操作 ====================

    /**
     * 通过节点动作池添加节点。
     * 从池中按名称获取 AsyncNodeAction 实例，保证名称一致性。
     *
     * @param nodeName 节点名称（必须在 NodeActionPool 中已注册）
     * @param pool     节点动作池
     */
    public GraphBuilder addNode(String nodeName, org.example.graph.workflow.core.NodeActionPool pool)
            throws GraphStateException {
        AsyncNodeAction action = pool.get(nodeName);
        return addNode(nodeName, action);
    }

    /**
     * 通过边动作池添加条件边。
     * 从池中按名称获取 AsyncEdgeAction 实例。
     *
     * @param edgeName 边名称（必须在 EdgeConditionPool 中已注册）
     * @param pool     边动作池
     */
    public ConditionalEdgeBuilder addConditionalEdges(String edgeName,
                                                       org.example.graph.workflow.core.EdgeConditionPool pool) {
        AsyncEdgeAction action = pool.get(edgeName);
        return addConditionalEdges(edgeName, action);
    }

    /**
     * 从隐式源节点（lastNodeName）添加条件边，通过池获取。
     *
     * @param edgeName 边名称（必须在 EdgeConditionPool 中已注册）
     * @param pool     边动作池
     */
    public ConditionalEdgeBuilder addConditionalEdges(String edgeName,
                                                       org.example.graph.workflow.core.EdgeConditionPool pool,
                                                       boolean useLastNode) {
        AsyncEdgeAction action = pool.get(edgeName);
        return addConditionalEdges(action);
    }

    // ==================== 访问 ====================

    public BaseCheckpointSaver getCheckpointSaver() {
        return checkpointSaver;
    }
}
