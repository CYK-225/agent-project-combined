package org.example.skillEvolver.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.skillEvolver.entity.EvolverTaskEntity;
import org.example.skillEvolver.mapper.EvolverTaskMapper;
import org.example.skillEvolver.service.EvolverTaskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.example.skillEvolver.entity.table.EvolverTaskEntityTableDef.EVOLVER_TASK_ENTITY;

/**
 * Skill 进化任务服务实现。
 *
 * @author zhilin
 */
@Slf4j
@Service
@DS("postgresql-agent_test")
public class EvolverTaskServiceImpl extends ServiceImpl<EvolverTaskMapper, EvolverTaskEntity>
        implements EvolverTaskService {

    private JdbcTemplate jdbcTemplate;

    public EvolverTaskServiceImpl(
            com.baomidou.dynamic.datasource.DynamicRoutingDataSource dynamicDataSource) {
        try {
            ItemDataSource ds = (ItemDataSource) dynamicDataSource.getDataSource("postgresql-agent_test");
            if (ds != null) {
                this.jdbcTemplate = new JdbcTemplate(ds.getRealDataSource());
            }
        } catch (Exception e) {
            log.warn("[EvolverTask] 获取数据源失败: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void ensureColumnTypes() {
        if (jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE agent_test.evolver_task " +
                    "ALTER COLUMN task_data TYPE TEXT, " +
                    "ALTER COLUMN verifier TYPE TEXT, " +
                    "ALTER COLUMN best_skill_content TYPE TEXT");
            log.info("[EvolverTask] jsonb 列类型已调整为 TEXT");
        } catch (Exception e) {
            log.debug("[EvolverTask] 列类型检查: {}", e.getMessage());
        }
    }

    @Override
    public EvolverTaskEntity getByTaskId(String taskId) {
        return getOne(QueryWrapper.create()
                .where(EVOLVER_TASK_ENTITY.TASK_ID.eq(taskId)));
    }

    @Override
    public boolean updateStatus(String taskId, String status) {
        EvolverTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }

    @Override
    public boolean updateBestSkill(String taskId, String version, String content, Double reward) {
        EvolverTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setBestSkillVersion(version);
        entity.setBestSkillContent(content);
        entity.setBestReward(reward);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }

    @Override
    public boolean updateValidationResult(String taskId, Double passRate, Long tokenEstimate) {
        EvolverTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setValidationPassRate(passRate);
        entity.setTokenEstimate(tokenEstimate);
        entity.setStatus("COMPLETED");
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }

    @Override
    public boolean markFailed(String taskId, String errorMessage) {
        EvolverTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setStatus("FAILED");
        entity.setUpdatedAt(LocalDateTime.now());
        log.error("[EvolverTask] 任务 {} 失败: {}", taskId, errorMessage);
        return updateById(entity);
    }

    @Override
    public boolean updateCurrentIteration(String taskId, int iteration) {
        EvolverTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setCurrentIteration(iteration);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }

    @Override
    public boolean updateVerifier(String taskId, String verifier) {
        EvolverTaskEntity entity = getByTaskId(taskId);
        if (entity == null) return false;
        entity.setVerifier(verifier);
        entity.setUpdatedAt(LocalDateTime.now());
        return updateById(entity);
    }
}
