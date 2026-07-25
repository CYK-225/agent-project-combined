package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 审查汇总节点 — 并行审查的合并节点。
 * 收集代码、安全、性能三项审查结果，计算综合评分。
 */
@NodeAction(value = "review-summary", description = "审查汇总：合并代码/安全/性能审查结果并给出综合评分")
public class ReviewSummaryNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        int code = (int) state.value("codeScore").orElse(0);
        int sec = (int) state.value("securityScore").orElse(0);
        int perf = (int) state.value("perfScore").orElse(0);
        int avg = (code + sec + perf) / 3;

        String codeR = (String) state.value("codeReview").orElse("");
        String secR = (String) state.value("securityReview").orElse("");
        String perfR = (String) state.value("perfReview").orElse("");

        String verdict = avg >= 80 ? "通过" : "不通过";
        return Map.of(
                "result", "综合评审[" + verdict + "] 均分=" + avg + "\n  " + codeR + "\n  " + secR + "\n  " + perfR,
                "approved", avg >= 80
        );
    }
}
