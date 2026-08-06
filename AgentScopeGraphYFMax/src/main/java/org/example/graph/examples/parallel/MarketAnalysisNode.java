package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 市场分析节点 — 并行分支之一。
 * 从池中引用，可被多个图复用。
 */
@NodeAction(value = "market-analysis", description = "市场维度分析：评估竞品、用户需求、趋势")
public class MarketAnalysisNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("market", "市场分析[" + input + "]: 市场份额上升，趋势良好");
    }
}
