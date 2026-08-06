package org.example.graph.workflow.pattern;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.Builder;
import lombok.Getter;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.EdgeConditionPool;
import org.example.graph.workflow.core.NodeActionPool;

import java.util.List;
import java.util.Map;

/**
 * 条件分支模式：
 * source -> [condition] -> branchA | branchB | ... -> END
 *
 * <p>支持两种构建方式：
 * <ul>
 *   <li><b>直接传入动作</b>：提供 sourceAction + routingAction + branchActions（旧方式，保持兼容）</li>
 *   <li><b>池感知</b>：提供 nodeActionPool + edgeConditionPool，由池按名称解析（推荐）</li>
 * </ul>
 *
 * <p>池感知用法示例：
 * <pre>
 * ConditionalGraphPattern.builder()
 *     .name("input-router")
 *     .description("按输入类型路由")
 *     .sourceNode("dispatcher")
 *     .routingConditionName("type-router")
 *     .routeMap(Map.of("question", "question-handler", "command", "command-handler"))
 *     .nodeActionPool(nodeActionPool)
 *     .edgeConditionPool(edgeConditionPool)
 *     .build()
 *     .apply(builder);
 * </pre>
 */
@Getter
@Builder
public class ConditionalGraphPattern implements GraphPattern {

    private final String name;
    private final String description;
    private final String sourceNode;
    private final Map<String, String> routeMap;  // 条件值 -> 目标节点名称

    /** 直接传入方式 */
    private final AsyncNodeAction sourceAction;
    private final AsyncEdgeAction routingAction;
    private final Map<String, AsyncNodeAction> branchActions;  // 节点名称 -> 动作

    /** 池感知方式 */
    private final String routingConditionName;
    private final NodeActionPool nodeActionPool;
    private final EdgeConditionPool edgeConditionPool;

    @Override
    public PatternResult apply(GraphBuilder builder) throws GraphStateException {
        boolean usePool = nodeActionPool != null;

        AsyncNodeAction resolvedSource;
        AsyncEdgeAction resolvedRouting;

        if (usePool) {
            resolvedSource = nodeActionPool.get(sourceNode);
            resolvedRouting = edgeConditionPool.get(
                    routingConditionName != null ? routingConditionName : sourceNode);
        } else {
            if (sourceAction == null || routingAction == null) {
                throw new IllegalArgumentException("必须提供 sourceAction/routingAction 或 nodeActionPool/edgeConditionPool");
            }
            resolvedSource = sourceAction;
            resolvedRouting = routingAction;
        }

        // 添加源节点
        builder.addNode(sourceNode, resolvedSource);

        // 添加条件边
        var ceb = builder.addConditionalEdges(sourceNode, resolvedRouting);
        for (Map.Entry<String, String> entry : routeMap.entrySet()) {
            ceb.route(entry.getKey(), entry.getValue());
        }
        ceb.done();

        // 添加分支节点（不连接到 END，由调用方决定）
        List<String> branchNameList = routeMap.values().stream().distinct().toList();
        for (String branchName : branchNameList) {
            if (usePool) {
                builder.addNode(branchName, nodeActionPool);
            } else {
                AsyncNodeAction action = branchActions.get(branchName);
                if (action == null) {
                    throw new IllegalArgumentException("分支节点 '" + branchName + "' 未在 branchActions 中找到");
                }
                builder.addNode(branchName, action);
            }
        }

        return PatternResult.builder()
                .entryNode(sourceNode)
                .terminalNodes(branchNameList)
                .build();
    }
}
