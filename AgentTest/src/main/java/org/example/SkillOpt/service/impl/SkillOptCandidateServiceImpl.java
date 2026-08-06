package org.example.skillOpt.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.skillOpt.entity.SkillOptCandidateEntity;
import org.example.skillOpt.mapper.SkillOptCandidateMapper;
import org.example.skillOpt.service.SkillOptCandidateService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.example.skillOpt.entity.table.SkillOptCandidateEntityTableDef.SKILL_OPT_CANDIDATE_ENTITY;

/**
 * @author zhilin
 */
@Slf4j
@Service
@DS("postgresql-agent_test")
public class SkillOptCandidateServiceImpl extends ServiceImpl<SkillOptCandidateMapper, SkillOptCandidateEntity>
        implements SkillOptCandidateService {

    @Override
    public List<SkillOptCandidateEntity> listByJobId(String jobId) {
        return list(QueryWrapper.create()
                .where(SKILL_OPT_CANDIDATE_ENTITY.JOB_ID.eq(jobId))
                .orderBy(SKILL_OPT_CANDIDATE_ENTITY.EPOCH, true));
    }

    @Override
    public void saveCandidate(String jobId, int epoch, String candidateSkill, String editsApplied,
                              Double validationScore, Boolean gateAccepted, String rejectionReason) {
        SkillOptCandidateEntity entity = SkillOptCandidateEntity.builder()
                .jobId(jobId)
                .epoch(epoch)
                .candidateSkill(candidateSkill)
                .editsApplied(editsApplied)
                .validationScore(validationScore)
                .gateAccepted(gateAccepted)
                .rejectionReason(rejectionReason)
                .isBest(false)
                .createdAt(LocalDateTime.now())
                .build();
        save(entity);
        log.info("[SkillOptCandidate] 候选保存: jobId={}, epoch={}, accepted={}", jobId, epoch, gateAccepted);
    }

    @Override
    public void markBest(String jobId, int epoch) {
        // 先清除该 job 之前的 best 标记
        listByJobId(jobId).forEach(c -> {
            if (Boolean.TRUE.equals(c.getIsBest())) {
                c.setIsBest(false);
                updateById(c);
            }
        });
        // 标记新的 best
        SkillOptCandidateEntity candidate = getOne(QueryWrapper.create()
                .where(SKILL_OPT_CANDIDATE_ENTITY.JOB_ID.eq(jobId))
                .and(SKILL_OPT_CANDIDATE_ENTITY.EPOCH.eq(epoch)));
        if (candidate != null) {
            candidate.setIsBest(true);
            updateById(candidate);
        }
    }
}
