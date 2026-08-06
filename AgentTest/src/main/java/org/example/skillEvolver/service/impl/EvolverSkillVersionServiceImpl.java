package org.example.skillEvolver.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.skillEvolver.entity.EvolverSkillVersionEntity;
import org.example.skillEvolver.mapper.EvolverSkillVersionMapper;
import org.example.skillEvolver.service.EvolverSkillVersionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.example.skillEvolver.entity.table.EvolverSkillVersionEntityTableDef.EVOLVER_SKILL_VERSION_ENTITY;

/**
 * Skill 版本快照服务实现。
 *
 * @author zhilin
 */
@Slf4j
@Service
@DS("postgresql-agent_test")
public class EvolverSkillVersionServiceImpl extends ServiceImpl<EvolverSkillVersionMapper, EvolverSkillVersionEntity>
        implements EvolverSkillVersionService {

    private JdbcTemplate jdbcTemplate;

    public EvolverSkillVersionServiceImpl(
            com.baomidou.dynamic.datasource.DynamicRoutingDataSource dynamicDataSource) {
        try {
            ItemDataSource ds = (ItemDataSource) dynamicDataSource.getDataSource("postgresql-agent_test");
            if (ds != null) {
                this.jdbcTemplate = new JdbcTemplate(ds.getRealDataSource());
            }
        } catch (Exception e) {
            log.warn("[EvolverSkillVersion] 获取数据源失败: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void ensureColumnTypes() {
        if (jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE agent_test.evolver_skill_version " +
                    "ALTER COLUMN success_traces TYPE TEXT, " +
                    "ALTER COLUMN failure_traces TYPE TEXT");
            log.info("[EvolverSkillVersion] 列类型已调整为 TEXT");
        } catch (Exception e) {
            log.debug("[EvolverSkillVersion] 列类型检查: {}", e.getMessage());
        }
    }

    @Override
    public EvolverSkillVersionEntity saveVersion(String taskId, int iteration, String versionLabel,
                                                  String skillMarkdown, Double passRate, Double meanReward,
                                                  String analysis, String successTraces, String failureTraces) {
        EvolverSkillVersionEntity entity = EvolverSkillVersionEntity.builder()
                .versionId(taskId + "-" + versionLabel)
                .taskId(taskId)
                .iteration(iteration)
                .versionLabel(versionLabel)
                .variantIndex(0) // 合并版
                .skillMarkdown(skillMarkdown)
                .passRate(passRate)
                .meanReward(meanReward)
                .analysis(analysis)
                .successTraces(successTraces)
                .failureTraces(failureTraces)
                .isBest(false)
                .createdAt(LocalDateTime.now())
                .build();
        save(entity);
        log.info("[EvolverSkillVersion] 已保存 {} ({} 字符, passRate={})", versionLabel, skillMarkdown.length(), passRate);
        return entity;
    }

    @Override
    public boolean markAsBest(String taskId, String versionLabel) {
        // 先清除同任务所有最佳标记
        List<EvolverSkillVersionEntity> all = list(QueryWrapper.create()
                .where(EVOLVER_SKILL_VERSION_ENTITY.TASK_ID.eq(taskId)));
        for (EvolverSkillVersionEntity e : all) {
            if (e.getIsBest()) {
                e.setIsBest(false);
                updateById(e);
            }
        }
        // 设置新的最佳
        EvolverSkillVersionEntity best = getOne(QueryWrapper.create()
                .where(EVOLVER_SKILL_VERSION_ENTITY.TASK_ID.eq(taskId))
                .and(EVOLVER_SKILL_VERSION_ENTITY.VERSION_LABEL.eq(versionLabel)));
        if (best != null) {
            best.setIsBest(true);
            return updateById(best);
        }
        return false;
    }
}
