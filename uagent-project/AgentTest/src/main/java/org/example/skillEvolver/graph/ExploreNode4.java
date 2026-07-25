package org.example.skillEvolver.graph;

import org.example.graph.workflow.annotation.NodeAction;

/**
 * Trial 执行节点 - 变体 4
 */
@NodeAction(value = "evolver-explore-4", description = "Trial 执行节点 - 变体 4")
public class ExploreNode4 extends ExploreNode {
    @Override
    protected int getVariantIndex() { return 4; }
}
