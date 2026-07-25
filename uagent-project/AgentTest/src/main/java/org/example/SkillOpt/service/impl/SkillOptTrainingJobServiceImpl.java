package org.example.skillOpt.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.skillOpt.entity.SkillOptTrainingJobEntity;
import org.example.skillOpt.mapper.SkillOptTrainingJobMapper;
import org.example.skillOpt.service.SkillOptTrainingJobService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.example.skillOpt.entity.table.SkillOptTrainingJobEntityTableDef.SKILL_OPT_TRAINING_JOB_ENTITY;

/**
 * @author zhilin
 */
@Slf4j
@Service
@DS("postgresql-agent_test")
public class SkillOptTrainingJobServiceImpl extends ServiceImpl<SkillOptTrainingJobMapper, SkillOptTrainingJobEntity>
        implements SkillOptTrainingJobService {

    private JdbcTemplate jdbcTemplate;

    public SkillOptTrainingJobServiceImpl(
            com.baomidou.dynamic.datasource.DynamicRoutingDataSource dynamicDataSource) {
        try {
            ItemDataSource ds = (ItemDataSource) dynamicDataSource.getDataSource("postgresql-agent_test");
            if (ds != null) {
                this.jdbcTemplate = new JdbcTemplate(ds.getRealDataSource());
            }
        } catch (Exception e) {
            log.warn("[SkillOptJob] 获取数据源失败: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void ensureColumnTypes() {
        if (jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE agent_test.skillopt_training_job " +
                    "ALTER COLUMN train_data TYPE TEXT, " +
                    "ALTER COLUMN val_data TYPE TEXT, " +
                    "ALTER COLUMN best_skill_content TYPE TEXT");
            log.info("[SkillOptJob] 列类型已调整为 TEXT");
        } catch (Exception e) {
            log.debug("[SkillOptJob] 列类型检查: {}", e.getMessage());
        }
    }

    @Override
    public SkillOptTrainingJobEntity getByJobId(String jobId) {
        return getOne(QueryWrapper.create()
                .where(SKILL_OPT_TRAINING_JOB_ENTITY.JOB_ID.eq(jobId)));
    }

    @Override
    public boolean updateStatus(String jobId, String status) {
        SkillOptTrainingJobEntity entity = getByJobId(jobId);
        if (entity == null) return false;
        entity.setStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }

    @Override
    public boolean updateBestSkill(String jobId, String content, int bestEpoch, Double validationScore) {
        SkillOptTrainingJobEntity entity = getByJobId(jobId);
        if (entity == null) return false;
        entity.setBestSkillContent(content);
        entity.setBestSkillEpoch(bestEpoch);
        entity.setBestValidationScore(validationScore);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }

    @Override
    public boolean markCompleted(String jobId, String bestSkill, int bestEpoch, Double bestScore, String metaSkill) {
        SkillOptTrainingJobEntity entity = getByJobId(jobId);
        if (entity == null) return false;
        entity.setStatus("COMPLETED");
        entity.setBestSkillContent(bestSkill);
        entity.setBestSkillEpoch(bestEpoch);
        entity.setBestValidationScore(bestScore);
        entity.setFinalMetaSkill(metaSkill);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }

    @Override
    public boolean markFailed(String jobId, String errorMessage) {
        SkillOptTrainingJobEntity entity = getByJobId(jobId);
        if (entity == null) return false;
        entity.setStatus("FAILED");
        entity.setUpdatedAt(LocalDateTime.now());
        log.error("[SkillOptJob] 任务 {} 失败: {}", jobId, errorMessage);
        return updateById(entity);
    }

    @Override
    public boolean updateCurrentEpoch(String jobId, int epoch) {
        SkillOptTrainingJobEntity entity = getByJobId(jobId);
        if (entity == null) return false;
        entity.setCurrentEpoch(epoch);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }
}
