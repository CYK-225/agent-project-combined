package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.service.SkillOptTrainingJobService;

import java.util.*;

/**
 * 最终持久化节点 — 选择最佳 skill，更新任务状态。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-finalize", description = "最终持久化：选择最佳 skill，完成任务")
public class FinalizeNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        String bestSkill = (String) state.value("bestSkill").orElse("");
        Double bestValidationScore = (Double) state.value("bestValidationScore").orElse(0.0);
        String metaSkill = (String) state.value("metaSkill").orElse("");

        log.info("[FinalizeNode] jobId={}, bestScore={:.4f}, skillLength={}",
                jobId, bestValidationScore, bestSkill.length());

        // 更新任务状态为完成
        try {
            SkillOptTrainingJobService jobService =
                    ApplicationContextProvider.getBean(SkillOptTrainingJobService.class);

            // 找到 best skill 的 epoch
            int bestEpoch = (int) state.value("currentEpoch").orElse(0) - 1;
            jobService.markCompleted(jobId, bestSkill, bestEpoch, bestValidationScore, metaSkill);

            log.info("[FinalizeNode] 任务已完成: jobId={}, bestEpoch={}, score={:.4f}",
                    jobId, bestEpoch, bestValidationScore);
        } catch (Exception e) {
            log.error("[FinalizeNode] 持久化失败: {}", e.getMessage());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("finalSkill", bestSkill);
        output.put("finalScore", bestValidationScore);
        output.put("status", "COMPLETED");
        return output;
    }
}
