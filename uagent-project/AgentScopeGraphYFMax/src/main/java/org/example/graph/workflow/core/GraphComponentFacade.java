package org.example.graph.workflow.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.example.graph.workflow.checkpoint.CheckpointFactory;
import org.example.graph.createGraph.engine.GraphEngine;
import org.example.graph.workflow.state.StateKeyFactory;
import org.springframework.stereotype.Component;

/**
 * 所有图组件的统一入口。
 * 通过构造函数注入到 AbstractGraphTemplate 的子类中。
 *
 * <p>用法：
 * <pre>
 *   components.stateKey().defaultFactory()               // KeyStrategyFactory
 *   components.checkpoint().create("postgres")            // BaseCheckpointSaver
 *   components.nodeActions().get("validate")              // AsyncNodeAction
 *   components.edgeConditions().get("type-router")        // AsyncEdgeAction
 *   components.pool().invokeGraph("my-graph", state, tid) // OverAllState
 *   components.engine().invoke(graph, state, config)      // OverAllState
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class GraphComponentFacade {
    private final StateKeyFactory stateKey;
    private final CheckpointFactory checkpoint;
    private final NodeActionPool nodeActions;
    private final EdgeConditionPool edgeConditions;
    private final GraphPoolManager pool;
    private final GraphEngine engine;
}
