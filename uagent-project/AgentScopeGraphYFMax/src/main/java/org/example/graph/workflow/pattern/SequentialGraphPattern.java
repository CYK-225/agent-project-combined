package org.example.graph.workflow.pattern;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.Builder;
import lombok.Getter;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.NodeActionPool;

import java.util.Collections;
import java.util.List;

/**
 * 顺序流水线模式：node1 -> node2 -> ... -> nodeN -> END。
 *
 * <p>支持两种构建方式：
 * <ul>
 *   <li><b>直接传入动作</b>：提供 nodeNames + nodeActions（旧方式，保持兼容）</li>
 *   <li><b>池感知</b>：仅提供 nodeNames + nodeActionPool，由池按名称解析动作（推荐）</li>
 * </ul>
 *
 * <p>池感知用法示例：
 * <pre>
 * SequentialGraphPattern.builder()
 *     .name("qa-pipeline")
 *     .description("问答流水线")
 *     .nodeNames(List.of("validate", "analyze", "answer"))
 *     .nodeActionPool(nodeActionPool)
 *     .build()
 *     .apply(builder);
 * </pre>
 */
@Getter
@Builder
public class SequentialGraphPattern implements GraphPattern {

    private final String name;
    private final String description;
    private final List<String> nodeNames;
    private final List<AsyncNodeAction> nodeActions;
    private final NodeActionPool nodeActionPool;

    @Override
    public PatternResult apply(GraphBuilder builder) throws GraphStateException {
        if (nodeNames == null || nodeNames.isEmpty()) {
            throw new IllegalArgumentException("nodeNames 不能为空");
        }

        boolean usePool = nodeActionPool != null;
        if (!usePool && nodeActions == null) {
            throw new IllegalArgumentException("必须提供 nodeActions 或 nodeActionPool");
        }
        if (!usePool && nodeNames.size() != nodeActions.size()) {
            throw new IllegalArgumentException("nodeNames 和 nodeActions 长度必须一致");
        }

        String firstNode = nodeNames.get(0);
        String prevNode = null;
        for (int i = 0; i < nodeNames.size(); i++) {
            String current = nodeNames.get(i);
            if (usePool) {
                builder.addNode(current, nodeActionPool);
            } else {
                builder.addNode(current, nodeActions.get(i));
            }
            if (prevNode != null) {
                builder.addEdge(prevNode, current);
            }
            prevNode = current;
        }

        return PatternResult.builder()
                .entryNode(firstNode)
                .terminalNodes(prevNode != null ? List.of(prevNode) : List.of())
                .build();
    }
}
