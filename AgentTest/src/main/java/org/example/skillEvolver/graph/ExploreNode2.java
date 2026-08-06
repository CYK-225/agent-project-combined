package org.example.skillEvolver.graph;

import org.example.graph.workflow.annotation.NodeAction;

/**
 * Trial 执行节点 - 变体 2
 */
@NodeAction(value = "evolver-explore-2", description = "Trial 执行节点 - 变体 2")
public class ExploreNode2 extends ExploreNode {
    @Override
    protected int getVariantIndex() { return 2; }
}
