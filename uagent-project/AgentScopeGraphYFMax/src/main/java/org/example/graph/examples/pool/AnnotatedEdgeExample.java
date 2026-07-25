package org.example.graph.examples.pool;

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
 * @EdgeCondition 注解使用示例 — 通过 EdgeConditionPool 引用。
 *
 * <p>拓扑：dispatcher -> [type-router] -> question/command/default-handler -> result -> END
 */
@GraphDefinition(
        name = "annotated-edge-demo",
        description = "@EdgeCondition 池化引用示例：类型路由分发",
        group = "examples"
)
public class AnnotatedEdgeExample extends AbstractGraphTemplate {

    public AnnotatedEdgeExample(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {

        builder.addNode("dispatcher", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("dispatched", true);
                    return CompletableFuture.completedFuture(update);
                })
                .addConditionalEdges("type-router", components.edgeConditions())
                .route("question-handler", "question-handler")
                .route("command-handler", "command-handler")
                .route("default-handler", "default-handler")
                .done()
                .addNode("question-handler", components.nodeActions())
                .addEdge("result")
                .addNode("command-handler", components.nodeActions())
                .addEdge("result")
                .addNode("default-handler", components.nodeActions())
                .addEdge("result")
                .addNode("result", state -> {
                    String answer = (String) state.value("answer").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("output", "[结果] " + answer);
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }
}
