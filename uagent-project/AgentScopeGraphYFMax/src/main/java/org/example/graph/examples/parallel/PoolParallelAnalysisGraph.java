package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 池化引用的并行分析工作流 — 对比 {@link ParallelAnalysisGraph}。
 *
 * <p>与 ParallelAnalysisGraph（内联 lambda）的区别：
 * <ul>
 *   <li>所有节点动作均通过 @NodeAction 注册到 NodeActionPool</li>
 *   <li>图定义通过 builder.addNode(name, pool) 按名称引用</li>
 *   <li>并行分支节点同样从池中获取，不需要在图定义中写业务逻辑</li>
 * </ul>
 *
 * <p>拓扑：data-dispatcher -> [financial-analysis, technical-analysis, market-analysis] -> report-synthesis -> END
 *
 * <p>依赖的 @NodeAction 注册类：
 * <ul>
 *   <li>{@link DataDispatcherNode}  — "data-dispatcher"</li>
 *   <li>{@link FinancialAnalysisNode} — "financial-analysis"</li>
 *   <li>{@link TechnicalAnalysisNode} — "technical-analysis"</li>
 *   <li>{@link MarketAnalysisNode}   — "market-analysis"</li>
 *   <li>{@link ReportSynthesisNode}  — "report-synthesis"</li>
 * </ul>
 */
@GraphDefinition(
        name = "pool-parallel-analysis",
        description = "池化引用的并行多维分析工作流（节点全部从池获取）",
        group = "examples",
        checkpointStrategy = "memory"
)
public class PoolParallelAnalysisGraph extends AbstractGraphTemplate {

    public PoolParallelAnalysisGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 关键区别：addParallelBranches 的 branches Map 的 value
        //   ParallelAnalysisGraph: 内联 lambda
        //   本例：通过 pool.get() 从池获取
        Map<String, AsyncNodeAction> poolBranches = new LinkedHashMap<>();
        poolBranches.put("financial-analysis", components.nodeActions().get("financial-analysis"));
        poolBranches.put("technical-analysis", components.nodeActions().get("technical-analysis"));
        poolBranches.put("market-analysis", components.nodeActions().get("market-analysis"));

        // 方式一：手动构建并行拓扑（精细控制每个节点）
        builder
                // 分发节点从池获取
                .addNode("data-dispatcher", components.nodeActions())
                .addNode("report-synthesis", components.nodeActions())
                // 并行分支 + 合并节点从池获取
                .addParallelBranches(
                        "data-dispatcher",         // fanout 节点（已存在，fanoutAction 传 null）
                        null,
                        poolBranches,
                        "report-synthesis",
                        null
                )
                .addEdge(StateGraph.END);
    }
}
