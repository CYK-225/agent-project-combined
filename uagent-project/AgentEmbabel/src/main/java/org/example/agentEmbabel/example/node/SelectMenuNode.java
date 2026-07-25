package org.example.agentEmbabel.example.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;

import java.util.*;

/**
 * 筛选并选中菜品节点。
 * 从候选菜品中，按类型配比（大荤:小荤:纯素 = 3:3:4）选出最终菜单。
 */
@Slf4j
@NodeAction(value = "goap-select-menu", description = "按营养配比筛选最终菜品")
public class SelectMenuNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) state.value("candidateDishes").orElse(List.of());

        log.info("[GOAP-Node] 筛选菜品: {} 个候选", candidates.size());

        // 按类型分组
        List<Map<String, Object>> bigMeat = new ArrayList<>();
        List<Map<String, Object>> smallMeat = new ArrayList<>();
        List<Map<String, Object>> veggie = new ArrayList<>();

        for (Map<String, Object> dish : candidates) {
            String type = (String) dish.get("type");
            switch (type) {
                case "大荤" -> bigMeat.add(dish);
                case "小荤" -> smallMeat.add(dish);
                case "纯素" -> veggie.add(dish);
            }
        }

        // 按配比 3:3:4 选出最终菜单（最多10道菜）
        List<Map<String, Object>> selectedMenu = new ArrayList<>();
        bigMeat.stream().limit(3).forEach(selectedMenu::add);
        smallMeat.stream().limit(3).forEach(selectedMenu::add);
        veggie.stream().limit(4).forEach(selectedMenu::add);

        // 计算总成本
        double totalCost = selectedMenu.stream()
                .mapToDouble(d -> (double) d.get("cost"))
                .sum();

        log.info("[GOAP-Node] 选中 {} 道菜, 总成本={}", selectedMenu.size(), totalCost);

        return Map.of(
                "menuSelected", "true",
                "selectedMenu", selectedMenu,
                "menuSize", selectedMenu.size(),
                "totalCost", totalCost,
                "bigMeatCount", Math.min(bigMeat.size(), 3),
                "smallMeatCount", Math.min(smallMeat.size(), 3),
                "veggieCount", Math.min(veggie.size(), 4)
        );
    }
}
