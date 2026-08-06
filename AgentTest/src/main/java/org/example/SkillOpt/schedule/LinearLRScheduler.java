package org.example.skillOpt.schedule;

/**
 * 线性衰减调度器 — 从 baseBudget 线性衰减到 1。
 *
 * @author zhilin
 */
public class LinearLRScheduler implements LRScheduler {

    @Override
    public int computeEditBudget(int currentEpoch, int maxEpochs, int baseBudget) {
        if (maxEpochs <= 1) return baseBudget;
        int minBudget = 1;
        double ratio = 1.0 - (double) currentEpoch / (maxEpochs - 1);
        int budget = minBudget + (int) Math.round((baseBudget - minBudget) * ratio);
        return Math.max(1, budget);
    }

    @Override
    public String getName() {
        return "linear";
    }
}
