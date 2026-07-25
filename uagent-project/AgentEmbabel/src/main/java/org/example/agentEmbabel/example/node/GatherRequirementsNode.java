package org.example.agentEmbabel.example.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.*;

/**
 * 收集用户需求节点。
 * 从状态中读取用户输入的需求文本，解析并结构化输出。
 */
@Slf4j
@NodeAction(value = "goap-gather-requirements", description = "收集并结构化用户需求")
public class GatherRequirementsNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String userQuery = (String) state.value("userQuery").orElse("");
        String company = (String) state.value("company").orElse("悠饭总部");
        int headcount = (int) state.value("headcount").orElse(50);
        double budget = (double) state.value("budget").orElse(15.0);

        log.info("[GOAP-Node] 收集需求: company={}, headcount={}, budget={}, query={}",
                company, headcount, budget, userQuery);

        // 模拟需求解析：提取偏好关键词
        List<String> preferences = new ArrayList<>();
        if (userQuery.contains("辣")) preferences.add("spicy");
        if (userQuery.contains("清淡")) preferences.add("light");
        if (userQuery.contains("荤")) preferences.add("meat");
        if (userQuery.contains("素")) preferences.add("vegetarian");
        if (preferences.isEmpty()) {
            preferences.addAll(List.of("balanced", "nutritious"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requirementsGathered", "true");
        result.put("preferences", preferences);
        result.put("parsedCompany", company);
        result.put("parsedHeadcount", headcount);
        result.put("parsedBudget", budget);
        result.put("mealDate", java.time.LocalDate.now().toString());
        return result;
    }
}
