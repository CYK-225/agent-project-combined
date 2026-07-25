package org.example.graph.examples.basics;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 输入验证节点 — 独立类示例。
 * 通过 @NodeAction 注册到 NodeActionPool，可在图定义中按名称引用。
 */
@NodeAction(value = "validate", description = "输入验证：校验输入非空并清理")
public class ValidateNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.isBlank()) {
            throw new IllegalArgumentException("输入不能为空");
        }
        return Map.of(
                "validated", true,
                "cleanInput", input.trim()
        );
    }
}
