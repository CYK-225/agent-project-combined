package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 代码质量审查节点 — 并行审查分支之一。
 * 通过 @NodeAction 注册，供并行审查图按名称引用。
 */
@NodeAction(value = "code-review", description = "代码质量审查：检查编码规范、复杂度、可读性")
public class CodeReviewNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("codeScore", 85, "codeReview", "代码审查[" + input + "]: 编码规范良好，圈复杂度适中");
    }
}
