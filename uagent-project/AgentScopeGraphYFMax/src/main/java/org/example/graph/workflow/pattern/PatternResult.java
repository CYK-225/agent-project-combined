package org.example.graph.workflow.pattern;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.Builder;
import lombok.Getter;
import org.example.graph.createGraph.builder.GraphBuilder;

import java.util.List;

/**
 * Pattern 应用结果 — 描述已创建拓扑的入口和终端节点。
 *
 * <p>用于模式组合：
 * <ul>
 *   <li>{@code entryNode} — 第一个创建的节点，上游通过此节点连接到本模式</li>
 *   <li>{@code terminalNodes} — 需要连接到下游的节点列表</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 * // 简单：应用到 END
 * PatternResult r = pattern.apply(builder);
 * r.connectToEnd(builder);
 *
 * // 组合：Pattern A → Pattern B
 * PatternResult r1 = patternA.apply(builder);
 * PatternResult r2 = patternB.apply(builder);
 * r1.connectTo(r2.getEntryNode(), builder);
 * r2.connectToEnd(builder);
 * </pre>
 */
@Getter
@Builder
public class PatternResult {

    /** 模式创建的第一个节点（入口）。 */
    private final String entryNode;

    /** 需要连接到下游的终端节点列表。 */
    private final List<String> terminalNodes;

    /** 将所有终端节点连接到 END。 */
    public PatternResult connectToEnd(GraphBuilder builder) throws GraphStateException {
        for (String node : terminalNodes) {
            builder.addEdge(node, StateGraph.END);
        }
        return this;
    }

    /** 将所有终端节点连接到指定目标节点。 */
    public PatternResult connectTo(String target, GraphBuilder builder) throws GraphStateException {
        for (String node : terminalNodes) {
            builder.addEdge(node, target);
        }
        return this;
    }
}
