package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;
import org.example.graph.workflow.pattern.FanOutGraphPattern;

import java.util.List;

/**
 * 池化引用的扇出模式工作流 — 通过 {@link FanOutGraphPattern} + NodeActionPool 构建。
 *
 * <p>与 {@link PoolParallelAnalysisGraph}（手动调用 addParallelBranches）的区别：
 * <ul>
 *   <li>本例使用 {@link FanOutGraphPattern} 预制拓扑模式</li>
 *   <li>通过 branchNames + nodeActionPool 让模式自动从池中解析节点</li>
 *   <li>图定义类的 buildGraph 更简洁，拓扑细节封装在 Pattern 中</li>
 * </ul>
 *
 * <p>拓扑：fanout -> [financial-analysis, technical-analysis, market-analysis] -> report-synthesis -> END
 *
 * <p>依赖的 @NodeAction 注册类（与 PoolParallelAnalysisGraph 相同，节点可跨图复用）：
 * <ul>
 *   <li>{@link FinancialAnalysisNode} — "financial-analysis"</li>
 *   <li>{@link TechnicalAnalysisNode} — "technical-analysis"</li>
 *   <li>{@link MarketAnalysisNode}   — "market-analysis"</li>
 *   <li>{@link ReportSynthesisNode}  — "report-synthesis"</li>
 * </ul>
 */
@GraphDefinition(
        name = "pool-fanout-pattern",
        description = "通过 FanOutGraphPattern + 池引用构建并行扇出拓扑",
        group = "examples",
        checkpointStrategy = "memory"
)
public class PoolFanOutPatternGraph extends AbstractGraphTemplate {

    public PoolFanOutPatternGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 使用 FanOutGraphPattern 池感知模式：
        //   branchNames  — 并行分支的节点名称列表（必须在 @NodeAction 中注册）
        //   mergeNodeName — 合并节点名称（同样必须在 @NodeAction 中注册）
        //   nodeActionPool — 由 components.nodeActions() 提供
        FanOutGraphPattern.builder()
                .name("parallel-analysis")
                .description("多维并行分析")
                .fanoutNodeName("fanout")
                .branchNames(List.of("financial-analysis", "technical-analysis", "market-analysis"))
                .mergeNodeName("report-synthesis")
                .nodeActionPool(components.nodeActions())
                .build()
                .apply(builder)
                .connectToEnd(builder);
    }
}
