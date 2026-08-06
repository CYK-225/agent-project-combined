package org.example.graph.examples.conditional;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 人类反馈工作流：
 * draft -> [humanReview: 中断] -> finalize | re-draft -> END
 */
@GraphDefinition(
        name = "human-approval",
        description = "带人类审批步骤的工作流",
        group = "examples",
        checkpointStrategy = "postgres",
        interruptBefore = {"humanReview"}
)
public class HumanApprovalGraph extends AbstractGraphTemplate {

    public HumanApprovalGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 无 then() 的连续链式调用
        builder.addNode("draft", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("draft", "AI 生成的提案...");
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge("humanReview")
                .addNode("humanReview", state -> {
                    String feedback = (String) state.value("humanFeedback").orElse("approved");
                    Map<String, Object> update = new HashMap<>();
                    update.put("approved", "approved".equals(feedback));
                    return CompletableFuture.completedFuture(update);
                })
                .addConditionalEdges(new ApprovalRouterEdge())
                .route("finalize", "finalize")
                .route("draft", "draft")
                .done()
                .addNode("finalize", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("result", "已定稿");
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }
}
