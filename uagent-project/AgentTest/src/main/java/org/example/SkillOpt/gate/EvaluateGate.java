package org.example.skillOpt.gate;

import org.example.skillOpt.env.RolloutResult;

import java.util.List;

/**
 * 验证门控接口 — 判断候选 skill 是否优于当前 skill。
 *
 * @author zhilin
 */
public interface EvaluateGate {

    /**
     * 评估候选 skill 是否应该被接受。
     *
     * @param previousScore  上一 epoch 的验证分数
     * @param candidateScore 候选 skill 的验证分数
     * @param valResults     验证集 rollout 结果
     * @return 门控结果
     */
    GateResult evaluate(double previousScore, double candidateScore, List<RolloutResult> valResults);

    /**
     * 门控类型标识
     */
    String getGateType();
}
