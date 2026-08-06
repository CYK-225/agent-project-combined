package org.example.sliders.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;

import java.util.List;

/**
 * SLIDERS 顺序管道 Graph
 * <p>
 * chunker → schema → extractor → reconciler → answer → END
 * <p>
 * 每个节点通过 NodeActionPool 解析对应的 @NodeAction，
 * 节点内部调用 ReActAgent 执行 LLM + Tools 推理。
 */
@Slf4j
@GraphDefinition(
        name = "sliders-pipeline",
        description = "SLIDERS 长文档结构化问答流水线",
        group = "sliders",
        checkpointStrategy = "memory",
        recursionLimit = 50
)
public class SlidersGraph extends AbstractGraphTemplate {

    public SlidersGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        List<String> nodes = List.of(
                "sliders-chunker",
                "sliders-schema",
                "sliders-extractor",
                "sliders-reconciler",
                "sliders-answer"
        );

        // 注册所有节点（从 NodeActionPool 解析）
        for (String node : nodes) {
            builder.addNode(node, components.nodeActions());
        }

        // 入口边：START → chunker
        builder.addEdge(com.alibaba.cloud.ai.graph.StateGraph.START, nodes.get(0));

        // 串联边：chunker → schema → extractor → reconciler → answer
        for (int i = 0; i < nodes.size() - 1; i++) {
            builder.addEdge(nodes.get(i), nodes.get(i + 1));
        }

        // answer → END
        builder.addEdge(nodes.get(nodes.size() - 1), com.alibaba.cloud.ai.graph.StateGraph.END);
    }
}
