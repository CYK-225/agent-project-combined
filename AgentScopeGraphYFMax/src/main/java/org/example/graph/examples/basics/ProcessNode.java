package org.example.graph.examples.basics;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 数据处理节点 — 独立类示例。
 * 通过 @NodeAction 注册到 NodeActionPool。
 */
@NodeAction(value = "process", description = "数据处理：转换输入为大写")
public class ProcessNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("cleanInput").orElse("");
        return Map.of("processed", "已处理: " + input.toUpperCase());
    }
}
