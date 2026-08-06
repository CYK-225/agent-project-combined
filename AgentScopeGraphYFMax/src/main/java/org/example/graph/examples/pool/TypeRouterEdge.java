package org.example.graph.examples.pool;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.edge.SimpleEdgeAction;
import org.example.graph.workflow.annotation.EdgeCondition;

/**
 * 输入类型路由边 — 独立类示例。
 * 通过 @EdgeCondition 注册到 EdgeConditionPool。
 */
@EdgeCondition(value = "type-router", description = "输入类型路由：根据输入特征路由到不同处理器")
public class TypeRouterEdge extends SimpleEdgeAction {

    @Override
    protected String execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.contains("?")) {
            return "question-handler";
        } else if (input.startsWith("!")) {
            return "command-handler";
        } else {
            return "default-handler";
        }
    }
}
