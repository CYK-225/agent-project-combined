package org.example.graph.examples.subgraph;

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
 * 子图示例：研究子图（search -> synthesize -> END）
 * 可被父图通过两种方式引用：
 *
 * <p>方式 A — StateGraph（父图编译）：
 * <pre>
 *   StateGraph sub = new ResearchSubgraph(components).buildStateGraph();
 *   builder.addSubgraphNode("research", sub);
 * </pre>
 *
 * <p>方式 B — CompiledGraph（独立编译，通过 GraphPoolManager）：
 * <pre>
 *   CompiledGraph sub = components.pool().getGraph("research-sub");
 *   builder.addSubgraphNode("research", sub);
 * </pre>
 */
@GraphDefinition(
        name = "research-sub",
        description = "研究子图：搜索 -> 综合",
        group = "subgraphs",
        checkpointStrategy = "memory"
)
public class ResearchSubgraph extends AbstractGraphTemplate {

    public ResearchSubgraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addNode("search", state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("rawData", "搜索结果: " + state.value("query").orElse(""));
                    return CompletableFuture.completedFuture(state.updateState(update));
                })
                .addEdge("synthesize")
                .addNode("synthesize", state -> {
                    String rawData = (String) state.value("rawData").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("insights", "综合分析: " + rawData);
                    return CompletableFuture.completedFuture(state.updateState(update));
                })
                .addEdge(StateGraph.END);
    }
}
