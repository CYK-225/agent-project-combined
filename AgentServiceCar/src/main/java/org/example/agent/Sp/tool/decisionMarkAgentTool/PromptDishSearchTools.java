package org.example.agent.Sp.tool.decisionMarkAgentTool;

import io.agentscope.core.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dal.entity.DishEntity;
import org.example.agent.Sp.dal.entity.IngredientEntity;
import org.example.agent.Sp.dal.service.DishService;
import org.example.agent.Sp.dal.service.IngredientService;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;

import static com.mybatisflex.core.query.QueryMethods.distinct;
import static org.example.agent.Sp.dal.entity.table.DishEntityTableDef.DISH_ENTITY;
import static org.example.agent.Sp.dal.entity.table.IngredientEntityTableDef.INGREDIENT_ENTITY;

@Slf4j
//TODO 更换成对应的Prompt模板
@Component
public class PromptDishSearchTools {
    @Resource
    private DishService dishService;
    @Resource
    private IngredientService ingredientService;
    @Tool(description = "【核心触发器】当你决定开始为用户挑选菜品时，必须首先调用此工具获取高级挑菜规则。",
            name="getDishSearchPrompt"
    )
    public String getDishSearchPrompt(SupplyPlanModelEvent event, DecisionMarkEvent decisionMarkEvent) {
        log.info("test调用getDishSearchPrompt");

        try {

// 1. 静态导入 MyBatis-Flex 的 distinct 方法


// --- 下面是修复后的业务逻辑 ---

            if (decisionMarkEvent.getValidMainIngredients() == null) {
                decisionMarkEvent.setValidMainIngredients(
                        ingredientService.queryChain()
                                // 【修复】使用表常量.字段常量
                                .select(distinct(INGREDIENT_ENTITY.INGREDIENT_NAME))
                                .from(INGREDIENT_ENTITY)
                                .listAs(String.class)
                                .stream()
                                .filter(Objects::nonNull) // 过滤掉 null 值，防止 join 报错
                                .collect(Collectors.joining(","))
                );
            }
            log.info("食材信息");
// 给上下文设置食材等信息
            if (decisionMarkEvent.getValidFlavors() == null) {
                decisionMarkEvent.setValidFlavors(
                        dishService.queryChain()
                                // 【修复】使用表常量.字段常量
                                .select(distinct(DISH_ENTITY.COOKING_METHOD))
                                .from(DISH_ENTITY)
                                .listAs(String.class)
                                .stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(","))
                );
            }

            if (decisionMarkEvent.getValidDishTypes() == null) {
                decisionMarkEvent.setValidDishTypes(
                        dishService.queryChain()
                                // 【修复】使用表常量.字段常量
                                .select(distinct(DISH_ENTITY.DISH_TYPE))
                                .from(DISH_ENTITY)
                                .listAs(String.class)
                                .stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(","))
                );
            }

            if (decisionMarkEvent.getValidSpicinessLevel() == null) {
                decisionMarkEvent.setValidSpicinessLevel(
                        dishService.queryChain()
                                // 【修复】使用表常量.字段常量
                                .select(distinct(DISH_ENTITY.SPICY_LEVEL))
                                .from(DISH_ENTITY)
                                .listAs(String.class)
                                .stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(","))
                );
            }
        }catch (Exception e){
            log.error("查询菜品信息失败，错误信息:{}",e.getMessage());
        }
//TODO 性能问题，食材会不会太多,要不要限制，或者根据选择动态注入


        log.info(">>> 工具调用: start_picking_dishes (准备注入复杂提示词)");
        return "系统已进入专业搜索菜品模式，请严格根据系统新注入的规则，在下一步中输出你的挑选结果。";
    }
}

