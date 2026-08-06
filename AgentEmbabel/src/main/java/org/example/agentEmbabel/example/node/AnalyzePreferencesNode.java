package org.example.agentEmbabel.example.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.*;

/**
 * 分析用户偏好节点。
 * 从状态中读取已收集的需求，分析偏好并生成偏好报告。
 */
@Slf4j
@NodeAction(value = "goap-analyze-preferences", description = "分析用户口味偏好")
public class AnalyzePreferencesNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        List<String> preferences = (List<String>) state.value("preferences").orElse(List.of());
        String company = (String) state.value("parsedCompany").orElse("默认公司");

        log.info("[GOAP-Node] 分析偏好: company={}, preferences={}", company, preferences);

        // 模拟偏好分析结果
        Map<String, Object> preferenceProfile = new LinkedHashMap<>();
        preferenceProfile.put("spiceLevel", preferences.contains("spicy") ? 2 : 1);
        preferenceProfile.put("meatPreference", preferences.contains("meat") ? "high" : "medium");
        preferenceProfile.put("budgetConstraint", state.value("parsedBudget").orElse(15.0));
        preferenceProfile.put("tabooRate", "5%");
        preferenceProfile.put("cuisinePreference", "川菜, 粤菜, 家常菜");

        return Map.of(
                "preferencesAnalyzed", "true",
                "preferenceProfile", preferenceProfile,
                "spiceLevel", preferenceProfile.get("spiceLevel"),
                "cuisinePreference", preferenceProfile.get("cuisinePreference")
        );
    }
}
