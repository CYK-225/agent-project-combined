package org.example.skillOpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SkillOpt 配置。
 *
 * @author zhilin
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "skillopt")
public class SkillOptConfig {

    /** 默认最大 epoch 数 */
    private int defaultMaxEpochs = 5;

    /** 默认 batch size（每 epoch rollout 数量） */
    private int defaultBatchSize = 4;

    /** 默认基础编辑预算 */
    private int defaultEditBudgetBase = 5;

    /** 默认 LR 调度器类型 */
    private String defaultLrScheduler = "cosine";

    /** 默认验证门控类型 */
    private String defaultGateType = "mixed";

    /** 默认环境适配器类型 */
    private String defaultEnvAdapter = "llm-qa";

    /** Rollout 超时（分钟） */
    private int rolloutTimeoutMinutes = 5;

    /** Reflect mini-batch 大小 */
    private int reflectMiniBatchSize = 8;

    /** Skill 输出目录 */
    private String skillOutputDir = "./evolved-skills";

    /** Autonomous 调度器使用的模型别名 */
    private String autonomousSchedulerModel = "思考";
}
