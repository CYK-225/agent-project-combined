package org.example.skillOpt.gate;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 验证门控结果。
 *
 * @author zhilin
 */
@Data
@AllArgsConstructor
public class GateResult {

    /** 是否接受候选 skill */
    private boolean accepted;

    /** 候选 skill 的验证分数 */
    private double score;

    /** 上一 epoch 的验证分数 */
    private double previousScore;

    /** 门控类型 */
    private String gateType;

    /** 判定原因 */
    private String reason;

    public static GateResult accept(double previousScore, double candidateScore, String gateType) {
        return new GateResult(true, candidateScore, previousScore, gateType,
                "Candidate score (" + String.format("%.4f", candidateScore) +
                ") > previous (" + String.format("%.4f", previousScore) + ")");
    }

    public static GateResult reject(double previousScore, double candidateScore, String gateType) {
        return new GateResult(false, candidateScore, previousScore, gateType,
                "Candidate score (" + String.format("%.4f", candidateScore) +
                ") <= previous (" + String.format("%.4f", previousScore) + "), rollback");
    }
}
