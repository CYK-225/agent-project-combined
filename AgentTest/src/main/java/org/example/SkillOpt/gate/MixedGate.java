package org.example.skillOpt.gate;

import org.example.skillOpt.env.RolloutResult;

import java.util.List;

/**
 * Mixed Gate — 加权组合 hard 和 soft 分数，hard 权重更高。
 *
 * @author zhilin
 */
public class MixedGate implements EvaluateGate {

    private static final double ALPHA = 0.7; // hard score 权重

    @Override
    public GateResult evaluate(double previousScore, double candidateScore, List<RolloutResult> valResults) {
        if (candidateScore > previousScore) {
            return GateResult.accept(previousScore, candidateScore, getGateType());
        }
        return GateResult.reject(previousScore, candidateScore, getGateType());
    }

    /**
     * 计算 mixed score = alpha * hardScore + (1-alpha) * softScore
     */
    public static double computeMixedScore(double hardScore, double softScore) {
        return ALPHA * hardScore + (1.0 - ALPHA) * softScore;
    }

    @Override
    public String getGateType() {
        return "mixed";
    }
}
