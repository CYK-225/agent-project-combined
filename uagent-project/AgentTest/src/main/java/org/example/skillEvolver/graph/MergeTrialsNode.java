package org.example.skillEvolver.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.*;

/**
 * Trial 结果合并节点。
 * <p>
 * 在 FanOut 并行执行后，将 K 个 explore 节点产出的 trialResult_{i}
 * 合并为统一的 trialResults 列表，供 Analyze 节点消费。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-merge-trials", description = "合并 K 个并行 trial 的结果", scope = "singleton")
public class MergeTrialsNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        int k = (int) state.value("nExploration").orElse(4);
        List<Map<String, Object>> allResults = new ArrayList<>();

        for (int i = 1; i <= k; i++) {
            String key = "trialResult_" + i;
            Optional<Object> result = state.value(key);
            result.ifPresent(r -> {
                if (r instanceof Map) {
                    allResults.add((Map<String, Object>) r);
                }
            });
        }

        // 计算汇总
        long passCount = allResults.stream()
                .filter(r -> "PASSED".equals(r.getOrDefault("status", "")))
                .count();
        double passRate = allResults.isEmpty() ? 0.0 : (double) passCount / allResults.size();

        log.info("[MergeTrialsNode] 合并 {} 个 trial 结果, passRate={:.2f}", allResults.size(), passRate);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("trialResults", allResults);
        output.put("trialPassRate", passRate);
        output.put("trialCount", allResults.size());
        return output;
    }
}
