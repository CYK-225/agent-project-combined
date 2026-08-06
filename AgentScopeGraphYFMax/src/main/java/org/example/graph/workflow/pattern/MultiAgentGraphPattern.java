package org.example.graph.workflow.pattern;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.Builder;
import lombok.Getter;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.EdgeConditionPool;
import org.example.graph.workflow.core.NodeActionPool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多智能体协作模式：
 * agent1 -> agent2 -> ... -> judge -> [continue -> agent1 | end -> END]
 *
 * <p>用于辩论、审查和迭代式多智能体工作流。
 *
 * <p>支持两种构建方式：
 * <ul>
 *   <li><b>直接传入动作</b>：提供 agentActions + judgeAction + judgeRoutingAction（旧方式，保持兼容）</li>
 *   <li><b>池感知</b>：提供 agentNames + judgeNode + nodeActionPool + edgeConditionPool（推荐）</li>
 * </ul>
 *
 * <p>池感知用法示例：
 * <pre>
 * MultiAgentGraphPattern.builder()
 *     .name("debate")
 *     .description("双人辩论 + 裁判")
 *     .agentNames(List.of("proponent", "opponent"))
 *     .judgeNode("judge")
 *     .judgeConditionName("debate-verdict")
 *     .firstAgent("proponent")
 *     .nodeActionPool(nodeActionPool)
 *     .edgeConditionPool(edgeConditionPool)
 *     .build()
 *     .apply(builder);
 * </pre>
 */
@Getter
@Builder
public class MultiAgentGraphPattern implements GraphPattern {

    private final String name;
    private final String description;
    private final String judgeNode;
    private final String firstAgent;

    /** 直接传入方式：智能体名称 -> 动作（保持插入顺序） */
    private final Map<String, AsyncNodeAction> agentActions;
    private final AsyncNodeAction judgeAction;
    private final AsyncEdgeAction judgeRoutingAction;

    /** 池感知方式：按顺序的智能体名称列表 */
    private final List<String> agentNames;
    private final String judgeConditionName;
    private final NodeActionPool nodeActionPool;
    private final EdgeConditionPool edgeConditionPool;

    /** "end" 路由的目标节点。null = END（向后兼容）。 */
    private final String tailNode;

    @Override
    public PatternResult apply(GraphBuilder builder) throws GraphStateException {
        boolean usePool = nodeActionPool != null;

        List<String> resolvedAgentNames;
        if (usePool) {
            resolvedAgentNames = agentNames != null ? agentNames :
                    (agentActions != null ? List.copyOf(agentActions.keySet()) : null);
            if (resolvedAgentNames == null || resolvedAgentNames.isEmpty()) {
                throw new IllegalArgumentException("池感知模式必须提供 agentNames 或 agentActions 的 key");
            }
        } else {
            if (agentActions == null || agentActions.isEmpty()) {
                throw new IllegalArgumentException("必须提供 agentActions 或 nodeActionPool");
            }
            resolvedAgentNames = List.copyOf(agentActions.keySet());
        }

        // 添加智能体节点
        String prevAgent = null;
        for (String agentName : resolvedAgentNames) {
            if (usePool) {
                builder.addNode(agentName, nodeActionPool);
            } else {
                builder.addNode(agentName, agentActions.get(agentName));
            }
            if (prevAgent != null) {
                builder.addEdge(prevAgent, agentName);
            }
            prevAgent = agentName;
        }

        // 添加裁判节点
        if (usePool) {
            builder.addNode(judgeNode, nodeActionPool);
        } else {
            builder.addNode(judgeNode, judgeAction);
        }

        // 将最后一个智能体连接到裁判
        if (prevAgent != null) {
            builder.addEdge(prevAgent, judgeNode);
        }

        // 裁判路由
        AsyncEdgeAction routingAction;
        if (usePool) {
            String condName = judgeConditionName != null ? judgeConditionName : judgeNode;
            routingAction = edgeConditionPool.get(condName);
        } else {
            routingAction = judgeRoutingAction;
        }

        String endTarget = tailNode != null ? tailNode : StateGraph.END;
        builder.addConditionalEdges(judgeNode, routingAction)
                .route("continue", firstAgent != null ? firstAgent : prevAgent)
                .route("end", endTarget)
                .done();

        return PatternResult.builder()
                .entryNode(resolvedAgentNames.get(0))
                .terminalNodes(tailNode != null ? List.of(tailNode) : List.of())
                .build();
    }
}
