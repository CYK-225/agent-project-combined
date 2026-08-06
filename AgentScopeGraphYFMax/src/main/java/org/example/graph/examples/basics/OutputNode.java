package org.example.graph.examples.basics;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 结果输出节点 — 独立类示例。
 * 通过 @NodeAction 注册到 NodeActionPool。
 */
@NodeAction(value = "output", description = "结果输出：格式化最终结果")
public class OutputNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String processed = (String) state.value("processed").orElse("");
        return Map.of("result", "[" + System.currentTimeMillis() + "] " + processed);
    }
}
