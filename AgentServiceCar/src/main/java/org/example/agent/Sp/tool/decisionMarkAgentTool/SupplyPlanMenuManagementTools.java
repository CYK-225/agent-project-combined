package org.example.agent.Sp.tool.decisionMarkAgentTool;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dal.entity.DishEntity;
import org.example.agent.Sp.dal.service.SupplyPlanDoService;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.agent.Sp.prompt.decisionMarkAgentPrompt.SupplyPlanMenuManagementToolsResultPrompt;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.agent.Sp.dal.entity.table.DishEntityTableDef.DISH_ENTITY;
import static org.example.common.commonUtils.JsonToLLMUtils.toJson;

@Component
@Scope("prototype")
@Slf4j
@Service
@Data
//TODO 更换成对应的Prompt模板

public class SupplyPlanMenuManagementTools {

    @Resource
    private SupplyPlanDoService supplyPlanDoService;

    @Tool(description = "使用菜品id，在当前候选域中，管理（添加/移除）指定菜品。",name="MenuManagementTool")
    public String manageDomain(
            SupplyPlanModelEvent event,
            DecisionMarkEvent decisionMarkEvent,
            @ToolParam(description = "操作类型，[add,remove]", required = true,name="operation") String operation,
            @ToolParam(description = "菜品ID列表,注意输出json格式的数据", required = true,name="dish_ids") List<Long> dishIds,
            @ToolParam(description="思考过程提示词，记录你进行候选域管理操作的思考过程，必须输出json格式的数据",required = true,name="thought_process") String thoughtProcess
    ) {
        // ★ Pipeline 秒退：返回文本提示，由 DynamicPickDishPromptHook 注入规则
        if (decisionMarkEvent != null && decisionMarkEvent.isPipelineHint()) {
            log.info(">>> [MenuManagementTool] Pipeline hint → 秒退，规则注入中");
            return "【系统提示】dishId=-1 表示正在注入菜品挑选专家规则，不是操作失败。请阅读规则后用正确参数重新调用。";
        }

        try{
            log.info("进入管理候选域工具方法");
            log.info(STR."operation:\{operation},dishIds:\{toJson(dishIds)}");

        String getNullIdResult=getNullId(dishIds);
        if(getNullIdResult!=null){
            return getNullIdResult;
        }
        //这里要加入黑名单域的校验，防止黑名单的菜品被加入候选域，同时添加失败了也要回报模型
        //重新校验白名单，当上个步骤加了黑名单后，白名单中可能有冲突的菜品，所以要删除冲突的菜品
        //这里传入id直接去searchDishList查询，同时移除搜索结果中的菜品
        HashMap<Long, DishInfoAndScore> searchDishList = decisionMarkEvent.getSearchDishList();
        HashMap<Long, DishInfoAndScore> whiteDishMap = (event.getWhiteDishMap() != null) ? event.getWhiteDishMap() : new HashMap<>();
        HashMap<Long, String> blackDishMap = (event.getBlackDishMap() != null) ? event.getBlackDishMap() : new HashMap<>();
        //初始化返回提示词
        SupplyPlanMenuManagementToolsResultPrompt resultPrompt = new SupplyPlanMenuManagementToolsResultPrompt();
        if (operation.equals("add")){
            // ── 分类统计：区分哪些能加、哪些被拒 ──
            List<Long> duplicateIds  = new ArrayList<>();  // 已在白名单中（重复添加）
            List<Long> blacklistedIds = new ArrayList<>(); // 在黑名单中（被拦截）
            List<Long> notFoundIds   = new ArrayList<>();  // 不在搜索结果中

            Map<Long, DishInfoAndScore> inMap = new LinkedHashMap<>();
            for (Long id : dishIds) {
                if (whiteDishMap.containsKey(id)) {
                    duplicateIds.add(id);
                } else if (blackDishMap.containsKey(id)) {
                    blacklistedIds.add(id);
                } else if (!searchDishList.containsKey(id)) {
                    notFoundIds.add(id);
                } else {
                    inMap.put(id, searchDishList.get(id));
                }
            }

            whiteDishMap.putAll(inMap);
            event.setWhiteDishMap(whiteDishMap);
            event.setBlackDishMap(blackDishMap);
            decisionMarkEvent.setSearchDishList(new HashMap<>());

            // ── 拼接返回报告 ──
            StringBuilder sb = new StringBuilder();
            sb.append("成功向候选域中添加 ").append(inMap.size()).append(" 道菜品");
            sb.append(resultPrompt.generateStructureReport(event));

            if (!duplicateIds.isEmpty()) {
                sb.append("\n⚠️ 重复添加（已在候选域中，已跳过）: ").append(duplicateIds);
            }
            if (!blacklistedIds.isEmpty()) {
                sb.append("\n⛔ 黑名单拦截（已被拉黑，已跳过）: ").append(blacklistedIds);
            }
            if (!notFoundIds.isEmpty()) {
                sb.append("\n❌ 未找到（不在搜索结果中，已跳过）: ").append(notFoundIds);
            }
            return sb.toString();
        } else if (operation.equals("remove")) {
            if(event.getWhiteDishMap()==null||event.getWhiteDishMap().isEmpty()){
                return "白名单为空，无需操作";
            }
            dishIds.stream()
                    .filter(whiteDishMap.keySet()::contains)

                    .forEach(a->{
                                whiteDishMap.remove(a);
                                blackDishMap.merge(a, "被移出候选域,自动加入黑名单",
                                        (oldValue, newValue) -> STR."\{oldValue}; \{newValue}");
                            }
                    );
            event.setBlackDishMap(blackDishMap);
            event.setWhiteDishMap(whiteDishMap);
            return STR."成功从候选域中移除指定菜品\{resultPrompt.generateStructureReport(event)}";
        }


        //获取事件中的候选域hashmap
        //根据操作类型，进行添加或移除
        //返回操作结果说明
            return "操作类型只能是add或remove";
        } catch (Exception e) {
            log.error("候选域管理工具执行失败", e);
            return "工具执行失败: " + e.getMessage();
        }
    }


    @Tool(description = "使用菜品id，在当前黑名单域中，管理（添加/移除）指定菜品。",    name="manageBlacklistTool"
    )
    public String manageBlacklist(
            SupplyPlanModelEvent event,
            DecisionMarkEvent decisionMarkEvent,
            @ToolParam(description = "操作类型，[add,remove]", required = true,name="operation") String operation,
            @ToolParam(description = "菜品ID列表和添加或移除黑名单的原因，都必须使用json格式", required = true,name="dish_ids") HashMap<Long,String> dishIds,
            @ToolParam(description="思考过程提示词，记录你进行黑名单管理操作的思考过程，必须输出json格式的数据",required = true,name="thought_process") String thoughtProcess
    ) {
        // ★ Pipeline 秒退：返回文本提示，由 DynamicPickDishPromptHook 注入规则
        if (decisionMarkEvent != null && decisionMarkEvent.isPipelineHint()) {
            log.info(">>> [manageBlacklistTool] Pipeline hint → 秒退，规则注入中");
            return "【系统提示】dishId=-1 表示正在注入菜品挑选专家规则，不是操作失败。请阅读规则后用正确参数重新调用。";
        }

        log.info("进入管理黑名单工具方法");
        String getNullIdResult=getNullId(dishIds.keySet().stream().toList());
        if(getNullIdResult!=null){
            return getNullIdResult;
        }
        //获取事件中的候选域hashmap
        HashMap<Long, DishInfoAndScore> whiteDishMap = (event.getWhiteDishMap() != null) ? event.getWhiteDishMap() : new HashMap<>();
        HashMap<Long,DishInfoAndScore>  searchDishList = decisionMarkEvent.getSearchDishList();
        //获取事件中的黑名单hashmap
        HashMap<Long, String> blackDishMap = (event.getBlackDishMap() != null) ? event.getBlackDishMap() : new HashMap<>();
        //根据操作类型，进行添加或移除
        if(operation.equals("add")){
            searchDishList.keySet().removeAll(dishIds.keySet());
            blackDishMap.putAll(dishIds);
            dishIds.keySet().forEach(whiteDishMap::remove);
            event.setWhiteDishMap(whiteDishMap);
            event.setBlackDishMap(blackDishMap);
            decisionMarkEvent.setSearchDishList(searchDishList);
            return "成功从黑名单中添加指定菜品";
        }
        else if ("remove".equals(operation)) {
            // 判空保护：确保两个 Map 都不为空，否则会有 NPE
            if (event.getBlackDishMap() != null) {
                // 这行代码会从 blackDishMap 中移除所有在 dishIds 里出现的 key
                blackDishMap.keySet().removeAll(dishIds.keySet());
                return "成功从黑名单中移除指定菜品";
            }
            event.setWhiteDishMap(whiteDishMap);
            event.setBlackDishMap(blackDishMap);
        }
        //返回操作结果说明
        return "操作类型只能是add或remove";
    }


    private String getNullId(List<Long> dishIds){
        // 1. 构造 QueryWrapper，注意这里推荐只 select 主键字段以提升性能
// 这里的 USER 是 MyBatis-Flex APT 自动生成的表结构类 (UserTableDef.USER)
        if (dishIds.isEmpty()){
            throw new RuntimeException("菜品ID列表不能为空");
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                // 2. 将 DishEntity::getId 替换为 DISH_ENTITY.ID
                .select(DISH_ENTITY.ID)
                // 3. 将 DishEntity.class 替换为 DISH_ENTITY
                .from(DISH_ENTITY)
                // 4. 将条件合并为 字段.in(值) 的标准写法
                .where(DISH_ENTITY.ID.in(dishIds));

        // 2. 使用 Mapper 查询单列数据，直接映射为 Long 的 List
        List<Long> existingIds = supplyPlanDoService.getDishService().getMapper().selectObjectListByQueryAs(queryWrapper, Long.class);


// --- 第二步：在内存中求差集（找出不存在的 ID） ---

        // 优化：将已存在的 ID 放入 Set 中，提高 contains 的时间复杂度 (O(N) -> O(1))
        Set<Long> existingIdSet = new HashSet<>(existingIds);

        // 3. 过滤出不存在于数据库的 ID
        List<Long> missingIds = dishIds.stream()
                .filter(id -> !existingIdSet.contains(id))
                .toList();

// missingIds 就是最终的结果：不存在于数据库的 ID 列表

        if(!missingIds.isEmpty()){
            return STR."以下菜品ID在数据库中不存在，无法进行操作，请检查：\{missingIds}";
        }
        return null;
    }
}
