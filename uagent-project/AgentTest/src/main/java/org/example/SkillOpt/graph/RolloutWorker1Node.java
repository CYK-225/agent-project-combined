package org.example.skillOpt.graph;

import lombok.extern.slf4j.Slf4j;
import org.example.graph.workflow.annotation.NodeAction;

/**
 * Rollout Worker 1。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-rollout-1", description = "Rollout worker 1 — 前向传播")
public class RolloutWorker1Node extends RolloutWorkerNode {
    @Override
    protected int getVariantIndex() { return 1; }
}
