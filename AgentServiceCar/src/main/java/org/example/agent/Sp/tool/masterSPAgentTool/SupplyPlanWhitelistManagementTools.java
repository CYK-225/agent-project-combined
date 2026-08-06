package org.example.agent.Sp.tool.masterSPAgentTool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.common.commonUtils.JsonToLLMUtils.toJson;

@Component
@Scope("prototype")
@Slf4j
@Service
@Data
public class SupplyPlanWhitelistManagementTools {

    @Tool(description = "在当前候选域（白名单）中，管理（添加/移除）指定菜品。", name="WhitelistManagementTool")
    public String manageWhitelist(
            SupplyPlanModelEvent event,
            @ToolParam(description = "操作类型，[add,remove]", required = true,name="operation") String operation,
            @ToolParam(description = "要移除的菜品ID列表。仅在remove操作时填写", required = false,name = "dish_ids") List<Long> dishIds,
            @ToolParam(description = "要添加的菜品数据映射。仅在add操作时填写，直接把 GetDishIdByNameTool 返回的 JSON 对象原样传入", required = false,name = "in_map") Map<Long, DishInfoAndScore> inMap
    ) {
        try {
            log.info("进入管理白名单工具方法");
            log.info(STR."operation:\{operation}, dishIds:\{toJson(dishIds)}");

            // 使用最新的上下文参数取值
            HashMap<Long, DishInfoAndScore> whiteDishMap = (event.getWhiteDishMap() != null) ? event.getWhiteDishMap() : new HashMap<>();
            HashMap<Long, String> blackDishMap = (event.getBlackDishMap() != null) ? event.getBlackDishMap() : new HashMap<>();

            if (operation.equals("add")) {
                if (inMap == null || inMap.isEmpty()) {
                    return "添加失败：传入的菜品数据为空。请确保先调用了GetDishIdByNameTool。";
                }

                // 过滤掉已经在黑名单或白名单中的菜品，防止冲突
                inMap.entrySet().removeIf(entry -> blackDishMap.containsKey(entry.getKey()) || whiteDishMap.containsKey(entry.getKey()));

                whiteDishMap.putAll(inMap);
                event.setWhiteDishMap(whiteDishMap);
                event.setBlackDishMap(blackDishMap);
                return "成功向白名单中添加指定菜品";

            } else if (operation.equals("remove")) {
                if (whiteDishMap.isEmpty()) {
                    return "白名单为空，无需操作";
                }
                if (dishIds != null) {
                    dishIds.stream()
                            .filter(whiteDishMap.keySet()::contains)
                            .forEach(a -> {
                                        whiteDishMap.remove(a);
                                        blackDishMap.merge(a, "被移出白名单,自动加入黑名单",
                                                (oldValue, newValue) -> STR."\{oldValue}; \{newValue}");
                                    }
                            );
                    event.setBlackDishMap(blackDishMap);
                    event.setWhiteDishMap(whiteDishMap);
                }
                return "成功从白名单中移除指定菜品";
            }

            return "操作类型只能是add或remove";
        } catch (Exception e) {
            log.error("白名单管理工具执行失败", e);
            return "工具执行失败: " + e.getMessage();
        }
    }
}