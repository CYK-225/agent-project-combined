package org.example.agent.Sp.tool.masterSPAgentTool;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dal.entity.DishEntity;
import org.example.agent.Sp.dal.service.DishService;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoEasy;
import org.example.agent.Sp.dataModel.BaseDataModel.Score;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
@Scope("prototype")
@Slf4j
@Data
public class SupplyPlanDishIdTools {

    @Resource
    private DishService dishService;

    @Tool(description = "使用菜品名称查询菜品。当需要向菜单添加新菜品时，必须先调用此工具将菜名转换为完整的数据映射(Map)。", name = "GetDishIdByNameTool")
    public HashMap<Long, DishInfoAndScore> getDishIdByName(
            @ToolParam(description = "需要查询的菜品名称列表（精确匹配）", required = true,name="dish_names") List<String> dishNames
    ) {
        try {
        log.info("进入菜名查询工具，查询菜品: {}", dishNames);
        HashMap<Long, DishInfoAndScore> resultMap = new HashMap<>();

        if (dishNames == null || dishNames.isEmpty()) {
            return resultMap;
        }

        // 查询数据库
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (dishNames != null && !dishNames.isEmpty()) {
            queryWrapper.and(q -> {
                for (String name : dishNames) {
                    q.or(DishEntity::getName).like(name);
                }
            });
        }
        List<DishEntity> dishEntities = dishService.list(queryWrapper);

        // 封装为 DishInfoAndScore
        for (DishEntity entity : dishEntities) {
            DishInfoEasy easy = new DishInfoEasy();
            easy.setId(entity.getId());
            easy.setName(entity.getName());
            easy.setDishType(entity.getDishType());
            easy.setSpicyLevel(entity.getSpicyLevel() != null ? entity.getSpicyLevel() : 0);

            // 用户主动指定添加的菜品，给最高分意愿 100 分
            Score score = new Score();
            score.setAllScore(100);
            score.setAttributeScore(100);
            score.setSalesScore(0);
            score.setHistoricalRatingScore(0);
            score.setPredictedRevenueScore(0);

            DishInfoAndScore infoAndScore = new DishInfoAndScore();
            infoAndScore.setDishId(entity.getId());
            infoAndScore.setDishInfo(easy);
            infoAndScore.setScore(score);

            resultMap.put(entity.getId(), infoAndScore);
        }

        return resultMap;
        } catch (Exception e) {
            log.error("菜品ID查询工具执行失败", e);
            return new HashMap<>();
        }
    }
}