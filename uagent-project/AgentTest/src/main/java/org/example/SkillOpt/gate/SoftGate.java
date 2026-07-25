package org.example.skillOpt.gate;

import org.example.skillOpt.env.RolloutResult;

import java.util.List;

/**
 * Soft Gate — 基于加权 soft score 改进判定，带 2% 阈值避免微小波动。
 *
 * @author zhilin
 */
public class SoftGate implements EvaluateGate {

    private static final double MARGIN = 0.02;

    @Override
    public GateResult evaluate(double previousScore, double candidateScore, List<RolloutResult> valResults) {
        if (candidateScore > previousScore * (1.0 + MARGIN)) {
            return GateResult.accept(previousScore, candidateScore, getGateType());
        }
        return GateResult.reject(previousScore, candidateScore, getGateType());
    }

    @Override
    public String getGateType() {
        return "soft";
    }
}
