package org.example.skillOpt.service;

import com.mybatisflex.core.service.IService;
import org.example.skillOpt.entity.SkillOptCandidateEntity;

import java.util.List;

/**
 * SkillOpt 候选 Skill 服务。
 *
 * @author zhilin
 */
public interface SkillOptCandidateService extends IService<SkillOptCandidateEntity> {

    List<SkillOptCandidateEntity> listByJobId(String jobId);

    void saveCandidate(String jobId, int epoch, String candidateSkill, String editsApplied,
                       Double validationScore, Boolean gateAccepted, String rejectionReason);

    void markBest(String jobId, int epoch);
}
