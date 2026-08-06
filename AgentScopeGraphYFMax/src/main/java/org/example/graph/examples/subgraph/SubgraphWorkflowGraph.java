package org.example.graph.examples.subgraph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
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
 * 子图工作流示例：
 * preprocess -> [research 子图: search -> synthesize] -> postprocess -> END
 *
 * <p>展示两种子图引用方式（可二选一）：
 * <ul>
 *   <li>方式 A：StateGraph 子图（父图编译，轻量）</li>
 *   <li>方式 B：CompiledGraph 子图（独立编译，通过 GraphPoolManager）</li>
 * </ul>
 */
@GraphDefinition(
        name = "subgraph-workflow",
        description = "带子图的模块化工作流",
        group = "examples",
        checkpointStrategy = "memory"
)
public class SubgraphWorkflowGraph extends AbstractGraphTemplate {

    public SubgraphWorkflowGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {

        // ========= 构建子图（二选一） =========

        // 方式 A：StateGraph 子图 — 父图编译，适合简单嵌套
        StateGraph researchSg = new ResearchSubgraph(components).buildStateGraph();

        // 方式 B：CompiledGraph 子图 — 独立编译，适合复用（取消注释替换方式 A）
         CompiledGraph researchCg = components.pool().getGraph("research-sub");

        // ========= 父图拓扑 =========

        // preprocess → research（子图）→ postprocess → END
        builder.addNode("preprocess", state -> {
                    String input = (String) state.value("input").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("query", "预处理(" + input + ")");
                    return CompletableFuture.completedFuture(state.updateState(update));
                })
                .addEdge("research")
                // 方式 A：StateGraph
                .addSubgraphNode("research", researchSg)
                // 方式 B：CompiledGraph（取消上方注释，替换此行）
                // .addSubgraphNode("research", researchCg)
                .addEdge("postprocess")
                .addNode("postprocess", state -> {
                    String insights = (String) state.value("insights").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("output", "最终报告: " + insights);
                    return CompletableFuture.completedFuture(state.updateState(update));
                })
                .addEdge(StateGraph.END);
    }
}
