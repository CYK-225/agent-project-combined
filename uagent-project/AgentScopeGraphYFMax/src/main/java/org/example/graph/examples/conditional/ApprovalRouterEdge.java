package org.example.graph.examples.conditional;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.edge.SimpleEdgeAction;
import org.example.graph.workflow.annotation.EdgeCondition;

/**
 * 审批路由边 — 独立类示例。
 * 根据 approved 状态路由到 finalize 或 re-draft。
 */
@EdgeCondition(value = "approval-router", description = "审批路由：根据审批结果路由")
public class ApprovalRouterEdge extends SimpleEdgeAction {

    @Override
    protected String execute(OverAllState state) throws Exception {
        Boolean approved = (Boolean) state.value("approved").orElse(false);
        return approved ? "finalize" : "draft";
    }
}
