package org.example.skillOpt.service;

import com.mybatisflex.core.service.IService;
import org.example.skillOpt.entity.SkillOptTrainingJobEntity;

/**
 * SkillOpt 训练任务服务。
 *
 * @author zhilin
 */
public interface SkillOptTrainingJobService extends IService<SkillOptTrainingJobEntity> {

    SkillOptTrainingJobEntity getByJobId(String jobId);

    boolean updateStatus(String jobId, String status);

    boolean updateBestSkill(String jobId, String content, int bestEpoch, Double validationScore);

    boolean markCompleted(String jobId, String bestSkill, int bestEpoch, Double bestScore, String metaSkill);

    boolean markFailed(String jobId, String errorMessage);

    boolean updateCurrentEpoch(String jobId, int epoch);
}
