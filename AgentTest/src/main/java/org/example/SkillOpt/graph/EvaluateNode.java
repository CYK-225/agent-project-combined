package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.agent.SkillOptTargetAgent;
import org.example.skillOpt.env.EnvAdapter;
import org.example.skillOpt.env.RolloutResult;
import org.example.skillOpt.env.qa.QAEnvAdapter;
import org.example.skillOpt.gate.EvaluateGate;
import org.example.skillOpt.gate.GateResult;
import org.example.skillOpt.gate.HardGate;
import org.example.skillOpt.gate.MixedGate;
import org.example.skillOpt.gate.SoftGate;
import org.example.skillOpt.service.SkillOptCandidateService;
import org.example.skillOpt.service.SkillOptEpochService;

import java.util.*;

/**
 * 阶段 6: Evaluate — 验证集 rollout + 门控判定。
 * <p>
 * 类比深度学习中的验证/早停。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-evaluate", description = "阶段6: Evaluate — 验证集 rollout + 门控判定")
public class EvaluateNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);
        String candidateSkill = (String) state.value("candidateSkill").orElse("");
        String epochSkill = (String) state.value("epochSkill").orElse("");
        String bestSkill = (String) state.value("bestSkill").orElse("");
        String gateType = (String) state.value("gateType").orElse("mixed");
        Double bestValidationScore = (Double) state.value("bestValidationScore").orElse(0.0);
        Double previousValidationScore = (Double) state.value("previousValidationScore").orElse(0.0);

        List<Map<String, Object>> valBatch =
                (List<Map<String, Object>>) state.value("valBatch").orElse(List.of());

        log.info("[EvaluateNode] jobId={}, epoch={}, valBatchSize={}, gateType={}",
                jobId, epoch, valBatch.size(), gateType);

        // 在验证集上串行 rollout
        EnvAdapter envAdapter = new QAEnvAdapter();
        List<RolloutResult> valResults = new ArrayList<>();
        double totalHard = 0.0, totalSoft = 0.0;

        for (int i = 0; i < valBatch.size(); i++) {
            Map<String, Object> taskInstance = valBatch.get(i);
            String prompt = envAdapter.buildRolloutPrompt(taskInstance, candidateSkill);

            SkillOptTargetAgent.setContext(
                    new SkillOptTargetAgent.TargetContext(jobId, epoch, i + 1, candidateSkill, prompt));

            try {
                AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
                String threadId = "skillopt-eval-" + jobId + "-" + epoch + "-" + i;
                ReActAgent targetAgent = poolManager.getAgentWithSession(
                        "SkillOptTarget", threadId, null, List.of());

                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(List.of(TextBlock.builder().text(prompt).build()))
                        .build();

                Msg response = targetAgent.call(userMsg).block();
                String agentResponse = response != null ? response.getTextContent() : "";

                RolloutResult rr = RolloutResult.builder()
                        .variantIndex(i + 1)
                        .status("COMPLETED")
                        .agentResponse(agentResponse)
                        .taskInstance(taskInstance)
                        .build();
                rr.setHardScore(envAdapter.hardScore(rr));
                rr.setSoftScore(envAdapter.softScore(rr));
                rr.setStatus(rr.getHardScore() >= 1.0 ? "PASSED" : "FAILED");
                valResults.add(rr);

                totalHard += rr.getHardScore();
                totalSoft += rr.getSoftScore();
            } catch (Exception e) {
                log.warn("[EvaluateNode] 验证 rollout {} 失败: {}", i, e.getMessage());
            } finally {
                SkillOptTargetAgent.clearContext();
            }
        }

        // 计算验证分数
        int valTotal = valResults.size();
        double candidateValidationScore;
        if ("hard".equals(gateType)) {
            candidateValidationScore = valTotal > 0 ? totalHard / valTotal : 0.0;
        } else if ("soft".equals(gateType)) {
            candidateValidationScore = valTotal > 0 ? totalSoft / valTotal : 0.0;
        } else {
            // mixed: alpha * hard + (1-alpha) * soft
            double avgHard = valTotal > 0 ? totalHard / valTotal : 0.0;
            double avgSoft = valTotal > 0 ? totalSoft / valTotal : 0.0;
            candidateValidationScore = MixedGate.computeMixedScore(avgHard, avgSoft);
        }

        // 验证门控
        EvaluateGate gate = createGate(gateType);
        GateResult gateResult = gate.evaluate(previousValidationScore, candidateValidationScore, valResults);

        log.info("[EvaluateNode] 门控结果: accepted={}, candidateScore={:.4f}, previousScore={:.4f}",
                gateResult.isAccepted(), candidateValidationScore, previousValidationScore);

        // 根据门控结果更新 skill
        String newCurrentSkill;
        String newBestSkill = bestSkill;
        double newBestScore = bestValidationScore;

        if (gateResult.isAccepted()) {
            newCurrentSkill = candidateSkill;
            if (candidateValidationScore > bestValidationScore) {
                newBestSkill = candidateSkill;
                newBestScore = candidateValidationScore;
                log.info("[EvaluateNode] 更新 best skill, score={:.4f}", newBestScore);
            }
        } else {
            // 回滚到 epoch 开始时的 skill
            newCurrentSkill = epochSkill;
            log.info("[EvaluateNode] 门控拒绝，回滚 skill");
        }

        // 持久化 epoch 和 candidate
        persistEpochAndCandidate(state, jobId, epoch, epochSkill, newCurrentSkill, candidateSkill,
                candidateValidationScore, previousValidationScore, gateResult);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("currentSkill", newCurrentSkill);
        output.put("bestSkill", newBestSkill);
        output.put("bestValidationScore", newBestScore);
        output.put("previousValidationScore", candidateValidationScore);
        output.put("gateAccepted", gateResult.isAccepted());
        output.put("validationScore", candidateValidationScore);
        output.put("currentEpoch", epoch + 1);
        return output;
    }

    private EvaluateGate createGate(String type) {
        return switch (type) {
            case "hard" -> new HardGate();
            case "soft" -> new SoftGate();
            default -> new MixedGate();
        };
    }

    private void persistEpochAndCandidate(OverAllState state, String jobId, int epoch,
                                           String epochSkill, String newCurrentSkill, String candidateSkill,
                                           double validationScore, double previousValidationScore,
                                           GateResult gateResult) {
        try {
            // 保存 epoch 记录
            SkillOptEpochService epochService =
                    ApplicationContextProvider.getBean(SkillOptEpochService.class);
            Double avgHard = (Double) state.value("avgHardScore").orElse(0.0);
            Double avgSoft = (Double) state.value("avgSoftScore").orElse(0.0);
            Integer editBudget = (Integer) state.value("editBudget").orElse(5);
            Integer actualEditCount = (Integer) state.value("actualEditCount").orElse(0);
            String metaSkill = (String) state.value("metaSkill").orElse("");
            String protectedRegionsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(state.value("protectedRegions").orElse(List.of()));

            epochService.saveEpoch(jobId, epoch, epochSkill, newCurrentSkill, candidateSkill,
                    avgHard, avgSoft, validationScore, previousValidationScore,
                    gateResult.isAccepted(), gateResult.getGateType(),
                    editBudget, actualEditCount, protectedRegionsJson,
                    metaSkill, gateResult.getReason());

            // 保存候选 skill
            SkillOptCandidateService candidateService =
                    ApplicationContextProvider.getBean(SkillOptCandidateService.class);
            candidateService.saveCandidate(jobId, epoch, candidateSkill,
                    "see edit_log", validationScore, gateResult.isAccepted(),
                    gateResult.isAccepted() ? null : gateResult.getReason());

            // 如果是最佳，标记
            if (gateResult.isAccepted()) {
                Double bestValidationScore = (Double) state.value("bestValidationScore").orElse(0.0);
                if (validationScore > bestValidationScore) {
                    candidateService.markBest(jobId, epoch);
                }
            }
        } catch (Exception e) {
            log.warn("[EvaluateNode] 持久化失败: {}", e.getMessage());
        }
    }
}
