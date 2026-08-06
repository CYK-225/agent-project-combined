package org.example.skillOpt.schedule;

/**
 * LR 调度器接口 — 控制 每个 epoch 允许的编辑数量（类比深度学习中的学习率）。
 *
 * @author zhilin
 */
public interface LRScheduler {

    /**
     * 计算当前 epoch 的编辑预算。
     *
     * @param currentEpoch 当前 epoch（0-based）
     * @param maxEpochs    最大 epoch 数
     * @param baseBudget   基础编辑预算（来自配置）
     * @return 本 epoch 允许的最大编辑数
     */
    int computeEditBudget(int currentEpoch, int maxEpochs, int baseBudget);

    /**
     * 调度器名称
     */
    String getName();
}
