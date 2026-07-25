package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 技术分析节点 — 并行分支之一。
 * 从池中引用，可被多个图复用。
 */
@NodeAction(value = "technical-analysis", description = "技术维度分析：评估架构、性能、可维护性")
public class TechnicalAnalysisNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("technical", "技术分析[" + input + "]: 架构优秀，性能达标");
    }
}
