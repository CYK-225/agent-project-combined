package org.example.skillEvolver.graph;

import org.example.graph.workflow.annotation.NodeAction;

/**
 * Trial 执行节点 - 变体 3
 */
@NodeAction(value = "evolver-explore-3", description = "Trial 执行节点 - 变体 3")
public class ExploreNode3 extends ExploreNode {
    @Override
    protected int getVariantIndex() { return 3; }
}
