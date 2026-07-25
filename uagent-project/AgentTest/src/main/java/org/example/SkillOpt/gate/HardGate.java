package org.example.skillOpt.gate;

import org.example.skillOpt.env.RolloutResult;

import java.util.List;

/**
 * Hard Gate — 基于精确匹配分数严格改进判定。
 *
 * @author zhilin
 */
public class HardGate implements EvaluateGate {

    @Override
    public GateResult evaluate(double previousScore, double candidateScore, List<RolloutResult> valResults) {
        // 严格大于才算接受
        if (candidateScore > previousScore) {
            return GateResult.accept(previousScore, candidateScore, getGateType());
        }
        return GateResult.reject(previousScore, candidateScore, getGateType());
    }

    @Override
    public String getGateType() {
        return "hard";
    }
}
