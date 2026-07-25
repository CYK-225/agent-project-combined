package org.example.skillOpt.graph;

import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.annotation.NodeAction;

/**
 * Rollout Worker 4。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-rollout-4", description = "Rollout worker 4")
public class RolloutWorker4Node extends RolloutWorkerNode {
    @Override
    protected int getVariantIndex() { return 4; }
}
