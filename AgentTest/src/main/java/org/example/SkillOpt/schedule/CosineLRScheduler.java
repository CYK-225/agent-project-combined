package org.example.skillOpt.schedule;

/**
 * 余弦退火调度器 — 初期衰减慢、后期衰减快。
 *
 * @author zhilin
 */
public class CosineLRScheduler implements LRScheduler {

    @Override
    public int computeEditBudget(int currentEpoch, int maxEpochs, int baseBudget) {
        if (maxEpochs <= 1) return baseBudget;
        int minBudget = 1;
        double progress = (double) currentEpoch / (maxEpochs - 1);
        double cosine = 0.5 * (1.0 + Math.cos(Math.PI * progress));
        int budget = minBudget + (int) Math.round((baseBudget - minBudget) * cosine);
        return Math.max(1, budget);
    }

    @Override
    public String getName() {
        return "cosine";
    }
}
