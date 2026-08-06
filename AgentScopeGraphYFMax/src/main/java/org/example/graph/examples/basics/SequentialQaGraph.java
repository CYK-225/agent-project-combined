package org.example.graph.examples.basics;

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
 * 简单顺序工作流：analyze -> answer -> END
 */
@GraphDefinition(
        name = "sequential-qa",
        description = "简单的问答流水线",
        group = "examples",
        checkpointStrategy = "memory"
)
public class SequentialQaGraph extends AbstractGraphTemplate {

    public SequentialQaGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 连续链式，无需 then()
        builder.addNode("analyze", state -> {
                    String question = (String) state.value("question").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("analysis", "已分析: " + question);
                    return CompletableFuture.completedFuture(state.updateState(update));
                })
                .addEdge("answer")                    // analyze → answer
                .addNode("answer", state -> {          // 注册 answer 节点
                    String analysis = (String) state.value("analysis").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("answer", "回答: " + analysis);
                    return CompletableFuture.completedFuture(state.updateState(update));
                })
                .addEdge(StateGraph.END);             // answer → END
    }
}
