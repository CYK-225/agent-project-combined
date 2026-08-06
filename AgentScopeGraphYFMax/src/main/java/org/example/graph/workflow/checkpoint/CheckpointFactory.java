package org.example.graph.workflow.checkpoint;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 根据策略名称生产 BaseCheckpointSaver 实例的工厂。
 * IoC 注入的保存器（如 MyBatisFlexCheckpointSaver）通过构造函数注入。
 */
@Component
public class CheckpointFactory {

    private final MyBatisFlexCheckpointSaver postgresSaver;
    private volatile MemorySaver memorySaver;

    public CheckpointFactory(@Autowired(required = false) MyBatisFlexCheckpointSaver postgresSaver) {
        this.postgresSaver = postgresSaver;
    }

    /**
     * 根据策略名称创建检查点保存器。
     * "memory"   -> MemorySaver（上游库，进程内，单例缓存）
     * "postgres" -> MyBatisFlexCheckpointSaver（IoC 注入，PostgreSQL）
     * "" 或 null -> null（无检查点）
     */
    public BaseCheckpointSaver create(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return null;
        }
        return switch (strategy) {
            case "memory" -> {
                if (memorySaver == null) {
                    memorySaver = new MemorySaver();
                }
                yield memorySaver;
            }
            case "postgres" -> {
                if (postgresSaver == null) {
                    throw new IllegalStateException(
                            "PostgreSQL 检查点保存器不可用，请检查数据库配置。");
                }
                yield postgresSaver;
            }
            default -> throw new IllegalArgumentException(
                    "未知的检查点策略: " + strategy);
        };
    }
}
