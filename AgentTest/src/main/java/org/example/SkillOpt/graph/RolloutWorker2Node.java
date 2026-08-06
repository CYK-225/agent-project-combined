package org.example.skillOpt.graph;

import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.annotation.NodeAction;

/**
 * Rollout Worker 2。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-rollout-2", description = "Rollout worker 2")
public class RolloutWorker2Node extends RolloutWorkerNode {
    @Override
    protected int getVariantIndex() { return 2; }
}
