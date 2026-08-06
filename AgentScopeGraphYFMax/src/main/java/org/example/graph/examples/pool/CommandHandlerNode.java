package org.example.graph.examples.pool;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 命令处理节点 — 独立类示例。
 */
@NodeAction(value = "command-handler", description = "命令处理器：解析并执行命令")
public class CommandHandlerNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("answer", "执行命令: " + input.replace("!", ""));
    }
}
