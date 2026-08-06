package org.example.graph.examples.parallel;

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
 * 并行分析工作流：
 * fanout -> [financial, technical, market] -> synthesizer -> END
 *
 * 三个分析分支并行执行，汇总节点合并结果。
 */
@GraphDefinition(
        name = "parallel-analysis",
        description = "并行多维分析工作流",
        group = "examples",
        checkpointStrategy = "memory"
)
public class ParallelAnalysisGraph extends AbstractGraphTemplate {

    public ParallelAnalysisGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 使用 GraphBuilder 内置的并行操作（一行完成扇出拓扑）
        builder
                .addParallelBranches(
                "fanout",
                Map.of(
                    "financial", state -> {
                        String input = (String) state.value("input").orElse("");
                        Map<String, Object> update = new HashMap<>();
                        update.put("financial", "财务分析: " + input);
                        return CompletableFuture.completedFuture(state.updateState(update));
                    },
                    "technical", state -> {
                        String input = (String) state.value("input").orElse("");
                        Map<String, Object> update = new HashMap<>();
                        update.put("technical", "技术分析: " + input);
                        return CompletableFuture.completedFuture(state.updateState(update));
                    },
                    "market", state -> {
                        String input = (String) state.value("input").orElse("");
                        Map<String, Object> update = new HashMap<>();
                        update.put("market", "市场分析: " + input);
                        return CompletableFuture.completedFuture(state.updateState(update));
                    }
                ),
                "synthesizer",
                state -> {
                    String fin = (String) state.value("financial").orElse("");
                    String tech = (String) state.value("technical").orElse("");
                    String mkt = (String) state.value("market").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("result", "综合报告:\n  " + fin + "\n  " + tech + "\n  " + mkt);
                    return CompletableFuture.completedFuture(state.updateState(update));
                }
        ).addEdge(StateGraph.END);
    }
}
