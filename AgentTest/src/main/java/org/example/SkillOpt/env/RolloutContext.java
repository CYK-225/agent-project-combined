package org.example.skillOpt.env;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Rollout 执行上下文。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolloutContext {

    /** 训练任务 ID */
    private String jobId;

    /** 当前 epoch */
    private int epoch;

    /** Worker 序号（1-based） */
    private int variantIndex;

    /** 当前 skill markdown */
    private String skillMarkdown;

    /** 单个任务实例 */
    private Map<String, Object> taskInstance;

    /** 隔离工作区路径 */
    private String workDir;
}
