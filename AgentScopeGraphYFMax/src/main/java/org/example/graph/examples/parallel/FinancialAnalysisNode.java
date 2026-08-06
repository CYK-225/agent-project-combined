package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 财务分析节点 — 并行分支之一。
 * 从池中引用，可被多个图复用。
 */
@NodeAction(value = "financial-analysis", description = "财务维度分析：评估成本、ROI、预算")
public class FinancialAnalysisNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("financial", "财务分析[" + input + "]: ROI 23%, 预算合理");
    }
}
