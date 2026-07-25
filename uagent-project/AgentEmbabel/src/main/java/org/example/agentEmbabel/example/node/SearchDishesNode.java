package org.example.agentEmbabel.example.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.*;

/**
 * 搜索菜品候选节点。
 * 根据偏好分析结果，模拟搜索符合条件的菜品。
 */
@Slf4j
@NodeAction(value = "goap-search-dishes", description = "根据偏好搜索候选菜品")
public class SearchDishesNode extends SimpleNodeAction {

    // 模拟菜品库
    private static final List<Map<String, Object>> DISH_DATABASE = List.of(
            Map.of("id", 1L, "name", "红烧肉", "type", "大荤", "spicyLevel", 0, "cost", 12.5, "score", 4.5),
            Map.of("id", 2L, "name", "宫保鸡丁", "type", "大荤", "spicyLevel", 2, "cost", 10.0, "score", 4.3),
            Map.of("id", 3L, "name", "清蒸鲈鱼", "type", "大荤", "spicyLevel", 0, "cost", 18.0, "score", 4.7),
            Map.of("id", 4L, "name", "麻婆豆腐", "type", "小荤", "spicyLevel", 2, "cost", 6.5, "score", 4.2),
            Map.of("id", 5L, "name", "蒜蓉西兰花", "type", "纯素", "spicyLevel", 0, "cost", 5.0, "score", 4.0),
            Map.of("id", 6L, "name", "酸辣土豆丝", "type", "纯素", "spicyLevel", 1, "cost", 4.5, "score", 4.1),
            Map.of("id", 7L, "name", "鱼香肉丝", "type", "小荤", "spicyLevel", 1, "cost", 8.0, "score", 4.4),
            Map.of("id", 8L, "name", "白切鸡", "type", "大荤", "spicyLevel", 0, "cost", 15.0, "score", 4.6),
            Map.of("id", 9L, "name", "干煸四季豆", "type", "纯素", "spicyLevel", 1, "cost", 5.5, "score", 3.9),
            Map.of("id", 10L, "name", "水煮牛肉", "type", "大荤", "spicyLevel", 3, "cost", 16.0, "score", 4.5)
    );

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        int spiceLevel = (int) state.value("spiceLevel").orElse(1);
        double budget = (double) state.value("parsedBudget").orElse(15.0);

        log.info("[GOAP-Node] 搜索菜品: spiceLevel={}, budget={}", spiceLevel, budget);

        // 模拟搜索：按辣度和预算过滤
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> dish : DISH_DATABASE) {
            int dishSpicy = (int) dish.get("spicyLevel");
            double dishCost = (double) dish.get("cost");

            if (dishSpicy <= spiceLevel && dishCost <= budget * 1.5) {
                candidates.add(new LinkedHashMap<>(dish));
            }
        }

        // 按评分排序
        candidates.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

        log.info("[GOAP-Node] 搜索到 {} 个候选菜品", candidates.size());

        return Map.of(
                "dishesSearched", "true",
                "candidateDishes", candidates,
                "candidateCount", candidates.size()
        );
    }
}
