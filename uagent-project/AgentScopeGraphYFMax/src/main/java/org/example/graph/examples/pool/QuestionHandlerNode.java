package org.example.graph.examples.pool;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 问题处理节点 — 独立类示例。
 */
@NodeAction(value = "question-handler", description = "问题处理器：去除问号并生成回答")
public class QuestionHandlerNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("answer", "回答: " + input.replace("?", ""));
    }
}
