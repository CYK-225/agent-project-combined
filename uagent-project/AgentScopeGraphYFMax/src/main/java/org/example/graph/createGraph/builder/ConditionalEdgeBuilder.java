package org.example.graph.createGraph.builder;

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import java.util.HashMap;
import java.util.Map;

/**
 * 条件边路由的流式构建器。
 * 将路由动作的输出字符串映射到目标节点名称。
 */
public class ConditionalEdgeBuilder {

    private final GraphBuilder graphBuilder;
    private final String sourceNode;
    private final AsyncEdgeAction routingAction;
    private final Map<String, String> routeMap = new HashMap<>();

    public ConditionalEdgeBuilder(GraphBuilder graphBuilder, String sourceNode,
                                  AsyncEdgeAction routingAction) {
        this.graphBuilder = graphBuilder;
        this.sourceNode = sourceNode;
        this.routingAction = routingAction;
    }

    /**
     * 将路由结果字符串映射到目标节点。
     */
    public ConditionalEdgeBuilder route(String resultValue, String targetNode) {
        routeMap.put(resultValue, targetNode);
        return this;
    }

    /**
     * 完成条件边构建，返回 GraphBuilder。
     * 之后可直接链式调用 addNode / addEdge 等。
     */
    public GraphBuilder done() throws GraphStateException {
        graphBuilder.getStateGraph().addConditionalEdges(sourceNode, routingAction, routeMap);
        return graphBuilder;
    }
}
