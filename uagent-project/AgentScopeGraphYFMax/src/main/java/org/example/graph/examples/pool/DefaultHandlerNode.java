package org.example.graph.examples.pool;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 默认处理节点 — 独立类示例。
 */
@NodeAction(value = "default-handler", description = "默认处理器：直接接收输入")
public class DefaultHandlerNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("answer", "收到: " + input);
    }
}
