package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 性能审查节点 — 并行审查分支之一。
 * 通过 @NodeAction 注册，供并行审查图按名称引用。
 */
@NodeAction(value = "performance-review", description = "性能审查：检查响应时间、资源占用、并发能力")
public class PerformanceReviewNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("perfScore", 78, "perfReview", "性能审查[" + input + "]: 平均响应 120ms，内存占用合理");
    }
}
