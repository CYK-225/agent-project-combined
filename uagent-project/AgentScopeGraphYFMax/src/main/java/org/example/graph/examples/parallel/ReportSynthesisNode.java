package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 报告综合节点 — 并行扇出的合并节点。
 * 收集各维度分析结果，生成综合报告。
 */
@NodeAction(value = "report-synthesis", description = "报告综合：合并各维度分析结果")
public class ReportSynthesisNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String fin = (String) state.value("financial").orElse("无数据");
        String tech = (String) state.value("technical").orElse("无数据");
        String mkt = (String) state.value("market").orElse("无数据");
        return Map.of("result", "综合报告:\n  " + fin + "\n  " + tech + "\n  " + mkt);
    }
}
