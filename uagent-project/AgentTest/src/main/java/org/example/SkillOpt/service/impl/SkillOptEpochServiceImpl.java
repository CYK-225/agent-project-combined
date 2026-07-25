package org.example.skillOpt.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.skillOpt.entity.SkillOptEpochEntity;
import org.example.skillOpt.mapper.SkillOptEpochMapper;
import org.example.skillOpt.service.SkillOptEpochService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.example.skillOpt.entity.table.SkillOptEpochEntityTableDef.SKILL_OPT_EPOCH_ENTITY;

/**
 * @author zhilin
 */
@Slf4j
@Service
@DS("postgresql-agent_test")
public class SkillOptEpochServiceImpl extends ServiceImpl<SkillOptEpochMapper, SkillOptEpochEntity>
        implements SkillOptEpochService {

    @Override
    public List<SkillOptEpochEntity> listByJobId(String jobId) {
        return list(QueryWrapper.create()
                .where(SKILL_OPT_EPOCH_ENTITY.JOB_ID.eq(jobId))
                .orderBy(SKILL_OPT_EPOCH_ENTITY.EPOCH, true));
    }

    @Override
    public SkillOptEpochEntity getByJobIdAndEpoch(String jobId, int epoch) {
        return getOne(QueryWrapper.create()
                .where(SKILL_OPT_EPOCH_ENTITY.JOB_ID.eq(jobId))
                .and(SKILL_OPT_EPOCH_ENTITY.EPOCH.eq(epoch)));
    }

    @Override
    public void saveEpoch(String jobId, int epoch, String skillBefore, String skillAfter, String candidateSkill,
                          Double trainHardScore, Double trainSoftScore, Double validationScore,
                          Double previousValidationScore, Boolean gateAccepted, String gateType,
                          Integer editBudget, Integer actualEditCount, String protectedRegions,
                          String metaSkill, String epochSummary) {
        SkillOptEpochEntity entity = SkillOptEpochEntity.builder()
                .jobId(jobId)
                .epoch(epoch)
                .skillBefore(skillBefore)
                .skillAfter(skillAfter)
                .candidateSkill(candidateSkill)
                .trainHardScore(trainHardScore)
                .trainSoftScore(trainSoftScore)
                .validationScore(validationScore)
                .previousValidationScore(previousValidationScore)
                .gateAccepted(gateAccepted)
                .gateType(gateType)
                .editBudget(editBudget)
                .actualEditCount(actualEditCount)
                .protectedRegions(protectedRegions)
                .metaSkill(metaSkill)
                .epochSummary(epochSummary)
                .createdAt(LocalDateTime.now())
                .build();
        save(entity);
        log.info("[SkillOptEpoch] Epoch {} 保存成功, jobId={}, gateAccepted={}", epoch, jobId, gateAccepted);
    }
}
