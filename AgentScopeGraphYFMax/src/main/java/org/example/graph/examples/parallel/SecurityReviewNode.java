package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.Map;

/**
 * 安全审查节点 — 并行审查分支之一。
 * 通过 @NodeAction 注册，供并行审查图按名称引用。
 */
@NodeAction(value = "security-review", description = "安全审查：检查漏洞、权限、敏感数据")
public class SecurityReviewNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return Map.of("securityScore", 92, "securityReview", "安全审查[" + input + "]: 未发现高危漏洞");
    }
}
