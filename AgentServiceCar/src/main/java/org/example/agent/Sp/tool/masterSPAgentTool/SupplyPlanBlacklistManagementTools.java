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

@Component
@Scope("prototype")
@Slf4j
@Service
@Data
public class SupplyPlanBlacklistManagementTools {

    @Tool(description = "使用菜品id，在当前黑名单域中，管理（添加/移除）指定菜品。", name="BlacklistManagementTool")
    public String manageBlacklist(
            SupplyPlanModelEvent event,
            @ToolParam(description = "操作类型，[add,remove]", required = true,name="operation") String operation,
            @ToolParam(description = "菜品ID列表和添加或移除黑名单的原因", required = true,name="dish_ids") HashMap<Long,String> dishIds
    ) {
        try {
            log.info("进入管理黑名单工具方法");

        // 使用最新的上下文参数取值
        HashMap<Long, DishInfoAndScore> whiteDishMap = (event.getWhiteDishMap() != null) ? event.getWhiteDishMap() : new HashMap<>();
        HashMap<Long, String> blackDishMap = (event.getBlackDishMap() != null) ? event.getBlackDishMap() : new HashMap<>();

        if(operation.equals("add")){
            blackDishMap.putAll(dishIds);
            dishIds.keySet().forEach(whiteDishMap::remove);
            event.setWhiteDishMap(whiteDishMap);
            event.setBlackDishMap(blackDishMap);
            return "成功向黑名单中添加指定菜品";
        }
        else if ("remove".equals(operation)) {
            // 判空保护：确保两个 Map 都不为空，否则会有 NPE
            if (event.getBlackDishMap() != null && dishIds != null) {
                // 这行代码会从 blackDishMap 中移除所有在 dishIds 里出现的 key
                blackDishMap.keySet().removeAll(dishIds.keySet());
                return "成功从黑名单中移除指定菜品";
            }
            event.setWhiteDishMap(whiteDishMap);
            event.setBlackDishMap(blackDishMap);
        }

        return "操作类型只能是add或remove";
        } catch (Exception e) {
            log.error("黑名单管理工具执行失败", e);
            return "工具执行失败: " + e.getMessage();
        }
    }
}