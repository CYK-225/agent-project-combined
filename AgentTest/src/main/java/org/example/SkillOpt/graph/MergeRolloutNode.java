package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.*;

/**
 * 合并 K 个 rollout 结果，计算汇总统计。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-merge-rollout", description = "合并 K 个 rollout 结果")
public class MergeRolloutNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);

        // 收集所有 worker 结果，过滤掉 SKIPPED 的
        List<Map<String, Object>> allResults = new ArrayList<>();
        int skippedCount = 0;
        for (int i = 1; i <= 4; i++) {
            Object result = state.value("rolloutResult_" + i).orElse(null);
            if (result instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                String status = (String) resultMap.getOrDefault("status", "FAILED");
                if ("SKIPPED".equals(status)) {
                    skippedCount++;
                    continue; // 跳过被标记为 SKIPPED 的结果
                }
                allResults.add(resultMap);
            }
        }

        if (skippedCount > 0) {
            log.info("[MergeRollout] 跳过了 {} 个 oversized worker 的结果", skippedCount);
        }

        // 分离 passed/failed
        List<Map<String, Object>> passedResults = new ArrayList<>();
        List<Map<String, Object>> failedResults = new ArrayList<>();
        double totalHard = 0.0, totalSoft = 0.0;
        int passCount = 0, failCount = 0;

        for (Map<String, Object> r : allResults) {
            double hardScore = ((Number) r.getOrDefault("hardScore", 0.0)).doubleValue();
            double softScore = ((Number) r.getOrDefault("softScore", 0.0)).doubleValue();
            String status = (String) r.getOrDefault("status", "FAILED");
            totalHard += hardScore;
            totalSoft += softScore;

            if ("PASSED".equals(status) || "PARTIAL".equals(status) || hardScore >= 1.0) {
                passedResults.add(r);
                passCount++;
            } else {
                failedResults.add(r);
                failCount++;
            }
        }

        int total = allResults.size();
        double avgHard = total > 0 ? totalHard / total : 0.0;
        double avgSoft = total > 0 ? totalSoft / total : 0.0;

        log.info("[MergeRollout] jobId={}, epoch={}, total={}, passed={}, failed={}, avgHard={:.2f}, avgSoft={:.2f}",
                jobId, epoch, total, passCount, failCount, avgHard, avgSoft);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rolloutResults", allResults);
        output.put("passedResults", passedResults);
        output.put("failedResults", failedResults);
        output.put("avgHardScore", avgHard);
        output.put("avgSoftScore", avgSoft);
        output.put("passCount", passCount);
        output.put("failCount", failCount);
        return output;
    }
}
