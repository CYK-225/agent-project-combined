package org.example.skillOpt.service;

import com.mybatisflex.core.service.IService;
import org.example.skillOpt.entity.SkillOptEpochEntity;

import java.util.List;

/**
 * SkillOpt Epoch 服务。
 *
 * @author zhilin
 */
public interface SkillOptEpochService extends IService<SkillOptEpochEntity> {

    List<SkillOptEpochEntity> listByJobId(String jobId);

    SkillOptEpochEntity getByJobIdAndEpoch(String jobId, int epoch);

    void saveEpoch(String jobId, int epoch, String skillBefore, String skillAfter, String candidateSkill,
                   Double trainHardScore, Double trainSoftScore, Double validationScore,
                   Double previousValidationScore, Boolean gateAccepted, String gateType,
                   Integer editBudget, Integer actualEditCount, String protectedRegions,
                   String metaSkill, String epochSummary);
}
