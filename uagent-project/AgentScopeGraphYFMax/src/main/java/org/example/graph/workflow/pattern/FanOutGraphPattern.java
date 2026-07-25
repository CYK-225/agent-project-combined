package org.example.graph.workflow.pattern;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.Builder;
import lombok.Getter;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.NodeActionPool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * 扇出并行模式：
 * fanout -> [branch1, branch2, ...] -> merge -> END
 *
 * <p>支持两种构建方式：
 * <ul>
 *   <li><b>直接传入动作</b>：提供 branchActions + mergeAction（旧方式，保持兼容）</li>
 *   <li><b>池感知</b>：提供 branchNames + mergeNodeName + nodeActionPool，由池按名称解析（推荐）</li>
 * </ul>
 *
 * <p>池感知用法示例：
 * <pre>
 * FanOutGraphPattern.builder()
 *     .name("parallel-search")
 *     .description("多路并行搜索")
 *     .fanoutNodeName("dispatcher")
 *     .branchNames(List.of("web-search", "db-search", "cache-search"))
 *     .mergeNodeName("aggregator")
 *     .nodeActionPool(nodeActionPool)
 *     .build()
 *     .apply(builder);
 * </pre>
 */
@Getter
@Builder
public class FanOutGraphPattern implements GraphPattern {

    private final String name;
    private final String description;
    private final String fanoutNodeName;
    private final String mergeNodeName;

    /** 分支名称 -> 动作（直接传入方式） */
    private final Map<String, AsyncNodeAction> branchActions;
    private final AsyncNodeAction mergeAction;

    /** 池感知方式：分支名称列表，由池按名称解析 */
    private final List<String> branchNames;
    private final NodeActionPool nodeActionPool;

    @Override
    public PatternResult apply(GraphBuilder builder) throws GraphStateException {
        boolean usePool = nodeActionPool != null;

        Map<String, AsyncNodeAction> resolvedBranches;
        AsyncNodeAction resolvedMerge;

        if (usePool) {
            List<String> names = branchNames != null ? branchNames :
                    (branchActions != null ? List.copyOf(branchActions.keySet()) : null);
            if (names == null || names.isEmpty()) {
                throw new IllegalArgumentException("池感知模式必须提供 branchNames 或 branchActions 的 key");
            }
            resolvedBranches = new LinkedHashMap<>();
            for (String branchName : names) {
                resolvedBranches.put(branchName, nodeActionPool.get(branchName));
            }
            resolvedMerge = nodeActionPool.get(mergeNodeName);
        } else {
            if (branchActions == null || branchActions.isEmpty()) {
                throw new IllegalArgumentException("必须提供 branchActions 或 nodeActionPool");
            }
            resolvedBranches = branchActions;
            resolvedMerge = mergeAction;
        }

        builder.addParallelBranches(fanoutNodeName, resolvedBranches, mergeNodeName, resolvedMerge);

        return PatternResult.builder()
                .entryNode(fanoutNodeName)
                .terminalNodes(List.of(mergeNodeName))
                .build();
    }
}
