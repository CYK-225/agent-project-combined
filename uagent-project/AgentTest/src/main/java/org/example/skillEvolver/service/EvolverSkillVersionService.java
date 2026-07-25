package org.example.skillEvolver.service;

import com.mybatisflex.core.service.IService;
import org.example.skillEvolver.entity.EvolverSkillVersionEntity;

/**
 * Skill 版本快照服务。
 *
 * @author zhilin
 */
public interface EvolverSkillVersionService extends IService<EvolverSkillVersionEntity> {

    /**
     * 保存一轮迭代的版本快照（含分析报告）
     */
    EvolverSkillVersionEntity saveVersion(String taskId, int iteration, String versionLabel,
                                          String skillMarkdown, Double passRate, Double meanReward,
                                          String analysis, String successTraces, String failureTraces);

    /**
     * 标记最佳版本
     */
    boolean markAsBest(String taskId, String versionLabel);
}
