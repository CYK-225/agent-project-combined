package org.example.graph.examples.pool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;

/**
 * @NodeAction 注解使用示例 — 通过 NodeActionPool 引用。
 *
 * <p>所有节点均为独立类，通过 @NodeAction 注册到 NodeActionPool。
 * 图定义类通过 components.nodeActions().get("name") 按名称获取实例。
 *
 * <p>拓扑：validate -> process -> output -> END
 *
 * <p>优势：
 * <ul>
 *   <li>节点名称由 @NodeAction 注解定义，保证一致性</li>
 *   <li>节点类可复用于多个图</li>
 *   <li>prototype 模式每次新建，避免状态污染</li>
 *   <li>引用不存在的节点名称时启动报错</li>
 * </ul>
 */
@GraphDefinition(
        name = "annotated-node-demo",
        description = "@NodeAction 池化引用示例：validate -> process -> output",
        group = "examples"
)
public class AnnotatedNodeExample extends AbstractGraphTemplate {

    public AnnotatedNodeExample(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        // 通过 NodeActionPool 按名称引用节点
        // 名称必须与 @NodeAction(value = "xxx") 一致
        builder.addNode("validate", components.nodeActions())  // ValidateNode
                .addEdge("process")
                .addNode("process", components.nodeActions())   // ProcessNode
                .addEdge("output")
                .addNode("output", components.nodeActions())    // OutputNode
                .addEdge(StateGraph.END);
    }
}
