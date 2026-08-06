package org.example.skillOpt.schedule;

/**
 * 固定学习率调度器 — 每个 epoch 相同的编辑预算。
 *
 * @author zhilin
 */
public class ConstantLRScheduler implements LRScheduler {

    @Override
    public int computeEditBudget(int currentEpoch, int maxEpochs, int baseBudget) {
        return baseBudget;
    }

    @Override
    public String getName() {
        return "constant";
    }
}
