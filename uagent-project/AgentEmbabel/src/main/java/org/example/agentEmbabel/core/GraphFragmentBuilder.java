package org.example.agentEmbabel.core;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentEmbabel.model.GoapActionDef;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.NodeActionPool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 图片段构建器。
 * 负责将 GOAP Action 转换为可执行的 StateGraph 片段。
 *
 * <p>支持三种转换模式：
 * <ul>
 *   <li>引用 @NodeAction：通过 NodeActionPool 获取已注册的节点动作</li>
 *   <li>引用 @AgentDefinition：通过 AgentPoolManager 获取 Agent 实例并创建适配器</li>
 *   <li>直接执行：当 action 既没有 nodeActionName 也没有 agentName 时，创建空操作节点</li>
 * </ul>
 */
@Slf4j
@Component
public class GraphFragmentBuilder {

    private final NodeActionPool nodeActionPool;
    private final AgentPoolManager agentPoolManager;

    public GraphFragmentBuilder(NodeActionPool nodeActionPool, AgentPoolManager agentPoolManager) {
        this.nodeActionPool = nodeActionPool;
        this.agentPoolManager = agentPoolManager;
    }

    /**
     * 将 GOAP Action 转换为可执行的 StateGraph 片段。
     *
     * @param action GOAP 动作定义
     * @return 可执行的 StateGraph
     * @throws GraphStateException 图构建失败时抛出
     */
    public StateGraph build(GoapActionDef action) throws GraphStateException {
        log.debug("构建图片段: action={}", action.getName());

        GraphBuilder builder = new GraphBuilder(new StateGraph(), null);
        AsyncNodeAction nodeAction = resolveNodeAction(action);

        builder.addNode("action", nodeAction)
               .addEdge(StateGraph.END);

        log.debug("图片段构建完成: action={}, nodeType={}",
                action.getName(), getNodeActionType(action));

        return builder.getStateGraph();
    }

    /**
     * 解析动作的节点执行器。
     * 优先使用 nodeActionName，其次使用 agentName。
     */
    private AsyncNodeAction resolveNodeAction(GoapActionDef action) {
        // 方式1: 引用已注册的 @NodeAction
        if (action.getNodeActionName() != null && !action.getNodeActionName().isEmpty()) {
            if (nodeActionPool.exists(action.getNodeActionName())) {
                log.debug("使用 NodeAction: {}", action.getNodeActionName());
                return nodeActionPool.get(action.getNodeActionName());
            }
            log.warn("NodeAction 未注册: {}", action.getNodeActionName());
        }

        // 方式2: 引用 @AgentDefinition，创建 AgentNode 适配器
        if (action.getAgentName() != null && !action.getAgentName().isEmpty()) {
            log.debug("使用 AgentDefinition: {}", action.getAgentName());
            return createAgentNode(action.getAgentName());
        }

        // 方式3: 创建空操作节点（用于测试或占位）
        log.debug("创建空操作节点: {}", action.getName());
        return createNoOpNode(action.getName());
    }

    /**
     * 创建 Agent 节点适配器。
     * 将 AgentPoolManager.getAgent() 调用包装为 AsyncNodeAction。
     */
    private AsyncNodeAction createAgentNode(String agentName) {
        return state -> {
            log.debug("执行 Agent 节点: agentName={}", agentName);

            String input = (String) state.value("input").orElse("");
            String threadId = (String) state.value("threadId").orElse(null);

            ReActAgent agent;
            if (threadId != null && !threadId.isEmpty()) {
                agent = agentPoolManager.getAgentWithSession(agentName, threadId);
            } else {
                agent = agentPoolManager.getAgent(agentName);
            }

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(input).build()))
                    .build();

            Msg response = agent.call(userMsg).block();
            String output = response != null ? response.getTextContent() : "";

            log.debug("Agent 节点执行完成: agentName={}, outputLength={}",
                    agentName, output.length());

            return CompletableFuture.completedFuture(
                    state.updateState(Map.of("output", output))
            );
        };
    }

    /**
     * 创建空操作节点。
     * 用于测试或当动作没有关联的执行器时。
     */
    private AsyncNodeAction createNoOpNode(String actionName) {
        return state -> {
            log.debug("执行空操作节点: actionName={}", actionName);
            return CompletableFuture.completedFuture(
                    state.updateState(Map.of("actionExecuted", actionName))
            );
        };
    }

    /**
     * 获取节点动作类型（用于日志）。
     */
    private String getNodeActionType(GoapActionDef action) {
        if (action.getNodeActionName() != null && !action.getNodeActionName().isEmpty()) {
            return "NodeAction:" + action.getNodeActionName();
        }
        if (action.getAgentName() != null && !action.getAgentName().isEmpty()) {
            return "Agent:" + action.getAgentName();
        }
        return "NoOp";
    }
}
