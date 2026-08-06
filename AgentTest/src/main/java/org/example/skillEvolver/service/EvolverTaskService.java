package org.example.skillEvolver.service;

import com.mybatisflex.core.service.IService;
import org.example.skillEvolver.entity.EvolverTaskEntity;

/**
 * Skill 进化任务服务。
 *
 * @author zhilin
 */
public interface EvolverTaskService extends IService<EvolverTaskEntity> {

    /**
     * 根据 taskId 查找任务
     */
    EvolverTaskEntity getByTaskId(String taskId);

    /**
     * 更新任务状态
     */
    boolean updateStatus(String taskId, String status);

    /**
     * 记录最佳 skill
     */
    boolean updateBestSkill(String taskId, String version, String content, Double reward);

    /**
     * 记录最终验证结果
     */
    boolean updateValidationResult(String taskId, Double passRate, Long tokenEstimate);

    /**
     * 标记任务失败
     */
    boolean markFailed(String taskId, String errorMessage);

    /**
     * 更新当前迭代轮次
     */
    boolean updateCurrentIteration(String taskId, int iteration);

    /**
     * 更新任务的 verifier 规则（LLM 自动生成后回写）
     */
    boolean updateVerifier(String taskId, String verifier);
}
