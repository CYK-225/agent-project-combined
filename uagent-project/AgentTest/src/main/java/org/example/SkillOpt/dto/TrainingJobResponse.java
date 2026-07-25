package org.example.skillOpt.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 训练任务状态响应 DTO。
 *
 * @author zhilin
 */
@Data
public class TrainingJobResponse {

    private String jobId;
    private String taskDescription;
    private String status;
    private String envAdapterType;
    private String lrSchedulerType;
    private String gateType;
    private Integer currentEpoch;
    private Integer maxEpochs;
    private Integer batchSize;
    private Integer editBudgetBase;
    private String bestSkillContent;
    private Integer bestSkillEpoch;
    private Double bestValidationScore;
    private String finalMetaSkill;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
