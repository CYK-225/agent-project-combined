package org.example.graph.examples.parallel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;
import org.example.graph.workflow.pattern.ConditionalGraphPattern;
import org.example.graph.workflow.pattern.FanOutGraphPattern;
import org.example.graph.workflow.pattern.PatternResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 模式组合示例 — 多个 Pattern 拼接构建完整工作流。
 *
 * <p>本例展示 Pattern 的核心价值：灵活组合。
 * 将 FanOutGraphPattern（并行审查）和 ConditionalGraphPattern（审批决策）串联，
 * 构建一个「并行审查 → 综合评审 → 审批决策」的完整工作流。
 *
 * <p>拓扑：
 * <pre>
 *   validate → fanout → [code-review, security-review, performance-review] → review-summary
 *                                                                                      ↓
 *            ←─────────────────────────────────────────────────────────── judge ─┤
 *                                                                              ↓            ↓
 *                                                                           [approved]   [rejected]
 *                                                                              ↓            ↓
 *                                                                           finalize    draft
 * </pre>
 *
 * <p>关键模式：
 * <ul>
 *   <li>FanOutGraphPattern.apply() 返回 PatternResult，merge 节点是终端</li>
 *   <li>通过 result.connectTo() 将 merge 连接到下一个 Pattern 的入口</li>
 *   <li>最终 Pattern 的终端通过 result.connectToEnd() 连接到 END</li>
 * </ul>
 *
 * <p>依赖的 @NodeAction / @EdgeCondition：
 * <ul>
 *   <li>code-review / security-review / performance-review / review-summary（并行审查节点）</li>
 *   <li>approval-router（审批路由边）</li>
 * </ul>
 */
@GraphDefinition(
        name = "composed-pattern",
        description = "模式组合示例：FanOut + Conditional 串联构建完整工作流",
        group = "examples",
        checkpointStrategy = "memory"
)
public class ComposedPatternGraph extends AbstractGraphTemplate {

    public ComposedPatternGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // ── 步骤 1：输入验证（手动 addNode，作为整个流程的入口）──
        builder.addNode("validate", state -> {
            String input = (String) state.value("input").orElse("");
            if (input.isBlank()) {
                throw new IllegalArgumentException("输入不能为空");
            }
            Map<String, Object> update = new HashMap<>();
            update.put("validated", true);
            return CompletableFuture.completedFuture(update);
        });

        // ── 步骤 2：并行审查（FanOutGraphPattern）──
        PatternResult reviewResult = FanOutGraphPattern.builder()
                .name("parallel-review")
                .description("并行三维度审查")
                .fanoutNodeName("fanout")
                .branchNames(List.of("code-review", "security-review", "performance-review"))
                .mergeNodeName("review-summary")
                .nodeActionPool(components.nodeActions())
                .build()
                .apply(builder);

        // validate → fanout（手动连接入口）
        builder.addEdge("validate", reviewResult.getEntryNode());

        // ── 步骤 3：审批决策（ConditionalGraphPattern，混合模式）──
        // 路由动作从 EdgeConditionPool 获取（池引用），节点动作直接传入（不经过池）
        // 这展示了 Pattern 的灵活性：同一个 Pattern 内可以混合池引用和直接传入
        PatternResult approvalResult = ConditionalGraphPattern.builder()
                .name("approval")
                .description("审批决策")
                .sourceNode("judge")
                .sourceAction(state -> {
                    int avg = (int) state.value("codeScore").orElse(0)
                            + (int) state.value("securityScore").orElse(0)
                            + (int) state.value("perfScore").orElse(0);
                    avg = avg / 3;
                    Map<String, Object> update = new HashMap<>();
                    update.put("avgScore", avg);
                    return CompletableFuture.completedFuture(update);
                })
                .routingAction(components.edgeConditions().get("approval-router"))
                .routeMap(Map.of(
                        "finalize", "finalize",
                        "draft", "draft"
                ))
                .branchActions(Map.of(
                        "finalize", state -> {
                            Map<String, Object> update = new HashMap<>();
                            update.put("output", "审批通过，已完成");
                            return CompletableFuture.completedFuture(update);
                        },
                        "draft", state -> {
                            Map<String, Object> update = new HashMap<>();
                            update.put("output", "审批未通过，需修改");
                            return CompletableFuture.completedFuture(update);
                        }
                ))
                .build()
                .apply(builder);

        // review-summary → judge（FanOut 终端 → Conditional 入口）
        reviewResult.connectTo(approvalResult.getEntryNode(), builder);

        // 两个分支都连接到 END
        approvalResult.connectToEnd(builder);
    }
}
