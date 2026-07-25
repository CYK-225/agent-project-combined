package org.example.skillOpt.graph;

import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.annotation.NodeAction;

/**
 * Rollout Worker 3。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-rollout-3", description = "Rollout worker 3")
public class RolloutWorker3Node extends RolloutWorkerNode {
    @Override
    protected int getVariantIndex() { return 3; }
}
