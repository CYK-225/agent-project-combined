package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.entity.SkillOptTrainingJobEntity;
import org.example.skillOpt.service.SkillOptTrainingJobService;

import java.util.*;

/**
 * Epoch 准备节点 — 采样训练/验证 batch，保存 epoch 开始时的 skill 快照。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-epoch-setup", description = "准备当前 epoch 的训练/验证 batch")
public class EpochSetupNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int currentEpoch = (int) state.value("currentEpoch").orElse(0);
        String currentSkill = (String) state.value("currentSkill").orElse("");

        log.info("[EpochSetupNode] jobId={}, epoch={}, skillLength={}",
                jobId, currentEpoch, currentSkill.length());

        // 从 state 获取原始数据
        List<Map<String, Object>> trainBatchRaw =
                (List<Map<String, Object>>) state.value("trainBatchRaw").orElse(List.of());
        List<Map<String, Object>> valBatchRaw =
                (List<Map<String, Object>>) state.value("valBatchRaw").orElse(List.of());

        // 更新数据库中的 currentEpoch
        try {
            SkillOptTrainingJobService jobService =
                    ApplicationContextProvider.getBean(SkillOptTrainingJobService.class);
            jobService.updateCurrentEpoch(jobId, currentEpoch);
        } catch (Exception e) {
            log.warn("[EpochSetupNode] 更新 epoch 失败: {}", e.getMessage());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("trainBatch", trainBatchRaw);
        output.put("valBatch", valBatchRaw);
        output.put("epochSkill", currentSkill); // epoch 开始时的 skill 快照（用于验证门控回滚）

        return output;
    }
}
