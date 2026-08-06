package org.example.skillOpt.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * 提交训练请求 DTO。
 *
 * @author zhilin
 */
@Data
public class TrainingJobRequest {

    /** 任务描述 */
    @JsonAlias({"taskDescription"})
    private String taskDescription;

    /** 初始 Skill（可选，空则从零开始） */
    private String initialSkill;

    /** 训练数据 JSON（必填） */
    private String trainData;

    /** 验证数据 JSON（必填） */
    private String valData;

    /** 环境适配器类型，默认 llm-qa */
    @JsonAlias({"envAdapterType"})
    private String envAdapterType = "llm-qa";

    /** LR 调度器类型，默认 cosine */
    @JsonAlias({"lrSchedulerType"})
    private String lrSchedulerType = "cosine";

    /** 验证门控类型，默认 mixed */
    @JsonAlias({"gateType"})
    private String gateType = "mixed";

    /** 最大 epoch 数，默认 5 */
    @JsonAlias({"maxEpochs"})
    private Integer maxEpochs = 5;

    /** 每 epoch rollout 数量，默认 4 */
    @JsonAlias({"batchSize"})
    private Integer batchSize = 4;

    /** 基础编辑预算，默认 5 */
    @JsonAlias({"editBudgetBase"})
    private Integer editBudgetBase = 5;
}
