package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;

import java.util.HashMap;
import java.util.Map;

/**
 * 池化引用的并行审查工作流。
 *
 * <p>场景：对一份提交进行代码质量、安全性、性能三维度并行审查，
 * 最后汇总给出综合评分。所有节点均通过 @NodeAction 池化注册。
 *
 * <p>拓扑：submit -> [code-review, security-review, performance-review] -> review-summary -> END
 *
 * <p>本例展示的核心模式：
 * <ul>
 *   <li>并行扇出的分支节点全部通过 builder.addNode(name, pool) 引用</li>
 *   <li>addParallelBranches 中使用便捷重载（4 参数版本），fanout 使用默认透传</li>
 *   <li>合并节点（review-summary）同样从池中获取</li>
 * </ul>
 *
 * <p>依赖的 @NodeAction 注册类：
 * <ul>
 *   <li>{@link CodeReviewNode}       — "code-review"</li>
 *   <li>{@link SecurityReviewNode}   — "security-review"</li>
 *   <li>{@link PerformanceReviewNode} — "performance-review"</li>
 *   <li>{@link ReviewSummaryNode}    — "review-summary"</li>
 * </ul>
 */
@GraphDefinition(
        name = "pool-parallel-review",
        description = "池化引用的并行代码审查工作流",
        group = "examples",
        checkpointStrategy = "memory"
)
public class PoolParallelReviewGraph extends AbstractGraphTemplate {

    public PoolParallelReviewGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 构建并行分支映射：key=分支名, value=从池获取的节点动作
        Map<String, AsyncNodeAction> reviewBranches = new HashMap<>();
        reviewBranches.put("code-review", components.nodeActions().get("code-review"));
        reviewBranches.put("security-review", components.nodeActions().get("security-review"));
        reviewBranches.put("performance-review", components.nodeActions().get("performance-review"));

        builder
                // 便捷重载（4 参数）：fanout 使用默认状态透传，无需显式创建分发节点
                .addParallelBranches(
                        "submit",                               // fanout 节点名（自动创建，透传状态）
                        reviewBranches,                          // 并行分支（全部从池获取）
                        "review-summary",                        // merge 节点名
                        components.nodeActions().get("review-summary")  // merge 动作（从池获取）
                )
                .addEdge(StateGraph.END);
    }
}
