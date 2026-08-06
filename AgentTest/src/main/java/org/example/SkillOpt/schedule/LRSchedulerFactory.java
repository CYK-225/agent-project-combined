package org.example.skillOpt.schedule;

/**
 * LR 调度器工厂。
 *
 * @author zhilin
 */
public class LRSchedulerFactory {

    private LRSchedulerFactory() {
    }

    /**
     * 根据类型创建调度器。
     *
     * @param type constant / linear / cosine / autonomous
     */
    public static LRScheduler create(String type) {
        if (type == null) return new CosineLRScheduler();
        return switch (type.toLowerCase().trim()) {
            case "constant" -> new ConstantLRScheduler();
            case "linear" -> new LinearLRScheduler();
            case "cosine" -> new CosineLRScheduler();
            case "autonomous" -> new AutonomousLRScheduler();
            default -> new CosineLRScheduler();
        };
    }
}
