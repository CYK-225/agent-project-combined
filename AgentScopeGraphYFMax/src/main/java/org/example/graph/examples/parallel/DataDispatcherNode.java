package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 数据分发节点 — 并行扇出场景的起始节点。
 * 接收原始输入，做初步清洗后传递给各并行分支。
 */
@NodeAction(value = "data-dispatcher", description = "数据分发：清洗输入并标记分发状态")
public class DataDispatcherNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of(
                "dispatched", true,
                "cleanInput", input.trim(),
                "dispatchCount", 3
        );
    }
}
