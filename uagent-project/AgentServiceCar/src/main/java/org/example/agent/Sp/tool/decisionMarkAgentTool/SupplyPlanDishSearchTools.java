package org.example.agent.Sp.tool.decisionMarkAgentTool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dal.service.SupplyPlanDoService;
import org.example.agent.Sp.dataModel.BaseDataModel.*;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Scope("prototype")
@Slf4j
@Service
@Data
//TODO 更换成对应的Prompt模板

public class SupplyPlanDishSearchTools {
    @Resource
    private SupplyPlanDoService supplyPlanDoService;

    /**
     * DishInfo → DishInfoEasy 转换器。
     * 直接用 MapStruct 原生 Mappers.getMapper()，绕过 Spring Converter 注册问题。
     */
    private final DishInfoToDishInfoEasyMapper dishInfoToEasyMapper = Mappers.getMapper(DishInfoToDishInfoEasyMapper.class);


    @Tool(description = "根据四个维度（" +
            "菜品属性维度" +
            "，菜品历史销售维度" +
            "，菜品历史用户评分维度" +
            "，菜品预测营收维度）查询菜品信息，强制开启菜品属性维度，可续开启其他维度。返回 DishInfo 列表。" +
            "严格按照格式输出",
            name="DishSearchTool"
    )
    public List<DishInfoAndScore> searchDish(
            SupplyPlanModelEvent supplyPlanModelEvent,
            DecisionMarkEvent decisionMarkEvent,
//            @ToolParam(description="思考过程提示词，记录你进行菜单参数构建的思考过程，必须输出json格式的数据",required = true,name="thought_process") String thoughtProcess,
            @ToolParam(description = "菜品名称（菜品属性维度），精确匹配，不支持模糊搜索", required = false,name="name") List<String> name,
            @ToolParam(description = "菜品类型（菜品属性维度），[大荤，小荤，纯素]", required = true,name="dish_type") List<String> dishType,
            @ToolParam(description = "菜品辣度（菜品属性维度），[不辣，微辣，中辣，无辣不欢]", required = true,name="spiciness_level") List<String> spicinessLevel,
            @ToolParam(description = "菜品工艺（菜品属性维度）(请按照提示词提供的数组填写)", required = true,name="flavor") List<String> flavor,
            @ToolParam(description = "菜品食材（菜品属性维度）(请按照提示词提供的数组填写)，", required = false,name="main_ingredient") List<String>  mainIngredient,
            @ToolParam(description = "最低菜品成本，单位为元/公斤（菜品属性维度）", required = false,name="min_cost_price") Double minCostPrice,
            @ToolParam(description = "最高菜品成本，单位为元/公斤（菜品属性维度）", required = false,name="max_cost_price") Double maxCostPrice,
            @ToolParam(description = "最低菜品价格，单位为元/公斤（菜品属性维度）", required = false,name="min_price") Double minPrice,
            @ToolParam(description = "最高菜品价格，单位为元/公斤（菜品属性维度）", required = false,name="max_price") Double maxPrice,
            @ToolParam(description = "启用菜品人均消耗量筛选(菜品预测营收维度)", required = false,name="enable_avg_consumption") Boolean  enableAvgConsumption,
            @ToolParam(description = "启用菜品人均货损量筛选(菜品预测营收维度)", required = false,name="enable_predicted_revenue") Boolean  enablePredictedRevenue,
            @ToolParam(description = "启用菜品历史综合喜好评分倒序筛选（菜品历史用户评分维度）", required = false,name="enable_historical_rating") Boolean  enableHistoricalRating,
            @ToolParam(description = "启用菜品历史送达消耗率倒序筛选（菜品历史销售维度）", required = false,name="enable_historical_sales") Boolean  enableHistoricalSales,
            @ToolParam(description = "历史送达消耗率开始时间（菜品历史销售维度），格式为yyyy-MM-dd", required = false,name = "historical_sales_start_date") LocalDate historicalSalesStartDate,
            @ToolParam(description = "历史送达消耗率结束时间（菜品历史销售维度），格式为yyyy-MM-dd", required = false,name = "historical_sales_end_date") LocalDate historicalSalesEndDate,
            @ToolParam(description = "【属性维度】权重（对应菜品口味、食材等），数值1-5，默认为1", required = false,name = "dimension_weights_attribute") Integer dimensionWeightsAttribute,
            @ToolParam(description = "【销售维度】权重（对应历史销量、消耗率），数值1-5，默认为1", required = false,name = "dimension_weights_sales") Integer dimensionWeightsSales,
            @ToolParam(description = "【用户维度】权重（对应历史评分、好评率），数值1-5，默认为1", required = false,name = "dimension_weights_users") Integer dimensionWeightsUsers,
            @ToolParam(description = "【预测维度】权重（对应预测营收、货损），数值1-5，默认为1", required = false,name = "dimension_weights_revenue") Integer dimensionWeightsRevenue,
            @ToolParam(description = "需要搜索的菜品数量最大上限（1~8）", required = true,name = "top_k") int topK
    ) {
        // ★ Pipeline 秒退：返回 dummy 菜品（ID=-1），由 DynamicPickDishPromptHook 注入规则
        if (decisionMarkEvent != null && decisionMarkEvent.isPipelineHint()) {
            log.info(">>> [DishSearchTool] Pipeline hint → 秒退，返回 dummy 菜品(ID=-1)，规则注入中");
            DishInfoAndScore dummy = new DishInfoAndScore();
            dummy.setDishId(-1L);
            return List.of(dummy);
        }

        log.info("DishSearchTool start");
        // 1. 防御性获取 Map (处理 supplyPlanModelEvent 为 null 或 map 为 null 的情况)
        Map<Long, DishInfoAndScore> searchDishList = (supplyPlanModelEvent != null) ? decisionMarkEvent.getSearchDishList() :  null;
        Map<Long, DishInfoAndScore> whiteDishMap = (supplyPlanModelEvent != null) ? supplyPlanModelEvent.getWhiteDishMap() :  null;
        Map<Long, String> blackDishMap = (supplyPlanModelEvent != null) ? supplyPlanModelEvent.getBlackDishMap() :  null;

        // 2. 初始化堆 (必须先指定比较器)
        // 此时 minHeap 是空的，这很安全
        PriorityQueue<DishInfoAndScore> minHeap = new PriorityQueue<>(
                Comparator.comparingDouble(DishInfoAndScore::getAllScore)
        );

        //获取黑名单+白名单已有的id
        // 1. 先初始化为空列表，确保 notInId 永远不为 null，后续使用安全
        List<Long> notInId = new ArrayList<>();
        // 2. 处理第一个 Map (局部变量)
        if (whiteDishMap != null) {
            notInId.addAll(whiteDishMap.keySet());
        }
        // 3. 处理第二个 Map (Event中的变量)
        // 注意：必须先判断 supplyPlanModelEvent 本身不为 null，再判断 getWhiteDishMap()
        if (blackDishMap != null) {
            notInId.addAll(blackDishMap.keySet());
        }
        //4.处理第四个 Map (Event中的变量)
        if (searchDishList != null) {
            notInId.addAll(searchDishList.keySet());
        }

        // 设定权重
        int attributeWeight=dimensionWeightsAttribute!=null?dimensionWeightsAttribute:1;
        int salesWeight= dimensionWeightsSales!=null?dimensionWeightsSales:1;
        int ratingWeight= dimensionWeightsUsers!=null?dimensionWeightsUsers:1;
        int revenueWeight=dimensionWeightsRevenue!=null?dimensionWeightsRevenue:1;

        List<String> nameNotNull=name!=null ? name:new ArrayList<>();
        List<String> mainIngredientNotNull=mainIngredient!=null ? mainIngredient:new ArrayList<>();
        List<String> spicinessLevelNotNull=spicinessLevel!=null ? spicinessLevel:new ArrayList<>();
        List<String> flavorNotNull=flavor!=null ? flavor:new ArrayList<>();
        List<String> dishTypeNotNull=dishType!=null ? dishType:new ArrayList<>();


        // 使用 try-with-resources 确保结构化并发：代码块结束时自动关闭资源并等待所有线程完成
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // --- 任务1: 菜品属性查询 (核心基础，总是执行) ---
            Double finalMinCostPrice =minCostPrice!=null ? minCostPrice:0.0;
            Double finalMaxCostPrice =maxCostPrice!=null ? maxCostPrice:99999.0;;
            Double finalMinPrice = minPrice!=null ? minPrice:0.0;
            Double finalMaxPrice = maxPrice!=null ? maxPrice:99999.0;
            var attributesFuture = CompletableFuture.supplyAsync(() ->
                    convertToDishInfoAndScoreMap(supplyPlanDoService.getDishService().searchDishesByAttributes(
                            nameNotNull, dishTypeNotNull, spicinessLevelNotNull, flavorNotNull, mainIngredientNotNull,
                            finalMinCostPrice, finalMaxCostPrice, finalMinPrice, finalMaxPrice, notInId) ,"属性",attributeWeight
                    ), executor);

            // --- 任务2: 历史销售查询 (条件执行) ---

            var salesFuture = CompletableFuture.supplyAsync(() -> {
                if (Boolean.TRUE.equals(enableHistoricalSales)) {
                    // 将日期处理逻辑放入线程内部，避免并发修改外部变量
                    LocalDate[] dates = processDateRange(historicalSalesStartDate, historicalSalesEndDate);
                    return convertToDishInfoAndScoreMap(supplyPlanDoService.getDailyProcurementPlanService()
                            .searchDishesByHistoricalSales(dates[0], dates[1], notInId),"销售", salesWeight
                    ) ;
                }
                return new HashMap<Long,DishInfoAndScore>(); // 未启用返回 null
            }, executor);

            // --- 任务3: 历史评分查询 (条件执行) ---
            var ratingFuture = CompletableFuture.supplyAsync(() -> {
                if (Boolean.TRUE.equals(enableHistoricalRating)) {
                    return convertToDishInfoAndScoreMap(supplyPlanDoService.getDishPreferencesService()
                            .searchDishesByHistoricalRating(notInId),"用户", ratingWeight
                    );
                }
                return new HashMap<Long,DishInfoAndScore>();
            }, executor);

            // --- 任务4: 预测营收/消耗查询 (条件执行) ---
            // 预处理布尔值，防止空指针
            final boolean isAvgCon = enableAvgConsumption != null && enableAvgConsumption;
            final boolean isPredRev = enablePredictedRevenue != null && enablePredictedRevenue;

            var candidateFuture = CompletableFuture.supplyAsync(() -> {
                // 照搬你提供的逻辑：只有当两个都为 false 时才查询 (注: 请确认这里业务逻辑是否符合预期)
                if (!isAvgCon && !isPredRev) {
                    return convertToDishInfoAndScoreMap(supplyPlanDoService.getDishAttributesService()
                            .searchDishesByCandidate(false, false, notInId),"预测", revenueWeight
                    ) ;
                }
                return new HashMap<Long,DishInfoAndScore>();
            }, executor);

            // --- 阻塞等待所有任务完成 (虚拟线程挂起，不消耗系统线程) ---
            CompletableFuture.allOf(attributesFuture, salesFuture, ratingFuture, candidateFuture).join();

            // --- 简单的交集结果合并 ---
            //将id转换成DishInfoAnd，计算各个维度分数
            Map<Long,DishInfoAndScore> attributesFutureMap = attributesFuture.get(); // 获取基础集合
            Map<Long, DishInfoAndScore> salesFutureMap = salesFuture.get();
            Map<Long, DishInfoAndScore> ratingFutureMap   = ratingFuture.get();
            Map<Long, DishInfoAndScore> candidateFutureMap = candidateFuture.get();
            log.info(STR."各维度结果：属性\{attributesFutureMap.size()}");
            log.info(STR."各维度结果：销售\{salesFutureMap.size()}");
            log.info(STR."各维度结果：用户\{ratingFutureMap.size()}");
            log.info(STR."各维度结果：预测\{candidateFutureMap.size()}");
            // --- 1. 取所有维度结果的并集（合并分数） ---
            Map<Long, DishInfoAndScore> allMergedResult = new HashMap<>(attributesFutureMap);

            // 合并函数：如果菜品已存在，则调用 merge 方法累加分数
            BiFunction<DishInfoAndScore, DishInfoAndScore, DishInfoAndScore> scoreMerger = (oldVal, newVal) -> oldVal.merge(newVal);

            salesFutureMap.forEach((k, v) -> allMergedResult.merge(k, v, scoreMerger));
            ratingFutureMap.forEach((k, v) -> allMergedResult.merge(k, v, scoreMerger));
            candidateFutureMap.forEach((k, v) -> allMergedResult.merge(k, v, scoreMerger));

            log.info(STR."并集汇总总数：\{allMergedResult.size()}");

            if (allMergedResult.isEmpty()) {
                HashMap<Long,DishInfoAndScore> andScoreHashMap = decisionMarkEvent.getSearchDishList();
                decisionMarkEvent.setSearchDishList(andScoreHashMap != null ? andScoreHashMap : new HashMap<>());
                throw new RuntimeException("未找到符合条件的菜品，请调整搜索条件后重试");
            }

            // --- 2. 批量获取所有并集菜品的详细信息，用于属性校验 ---
            List<Long> allDishIds = new ArrayList<>(allMergedResult.keySet());
            List<DishInfo> fullDishInfoList = supplyPlanDoService.getDishService().getDishInfoByIds(allDishIds);
            log.info("获取到的所有菜品的信息"+fullDishInfoList);

            // --- 3. 严格校验并过滤：只保留 辣度 和 荤素类别 符合输入的菜品 ---
            Map<Long, DishInfoAndScore> finalResult = new HashMap<>();
            for (DishInfo dishInfo : fullDishInfoList) {
                // 校验荤素类别 (DishType)
                if (!dishTypeNotNull.isEmpty() && !dishTypeNotNull.contains(dishInfo.getDishType())) {
                    continue;
                }

                // 校验辣度 (SpicinessLevel)
                // 注意：请确保 dishInfo.getSpicinessLevel() 这里的 getter 名字与你的实体类一致
                if (!spicinessLevelNotNull.isEmpty() && !spicinessLevelNotNull.contains(mapSpiciness(dishInfo.getSpicyLevel()))) {
                    continue;
                }
                // 校验通过，写入结果并提前转换好 DishInfoEasy，省去后面的二次查询
                DishInfoAndScore dishScore = allMergedResult.get(dishInfo.getId());
                dishScore.setDishInfo(dishInfoToEasyMapper.convert(dishInfo));
                finalResult.put(dishInfo.getId(), dishScore);
            }

            log.info(STR."属性校验过滤后余量：\{finalResult.size()}");

            if (finalResult.isEmpty()) {
                HashMap<Long,DishInfoAndScore> andScoreHashMap = decisionMarkEvent.getSearchDishList();
                decisionMarkEvent.setSearchDishList(andScoreHashMap != null ? andScoreHashMap : new HashMap<>());
                throw new RuntimeException("所选维度中没有菜品能满足您指定的【辣度】或【荤素】条件，请放宽条件后重试");
            }

            // --- 4. 排序，根据总分选出前 Top K 的菜品 ---
            if(topK < 1 || topK > 9) {
                topK = 5; //默认值
            }

            for (DishInfoAndScore dish : finalResult.values()) {
                // 堆未满，直接加入 (原逻辑使用 topK * 3 作为缓冲池大小)
                if (minHeap.size() < topK * 3) {
                    minHeap.offer(dish);
                } else {
                    // 只有当前菜品分数 > 堆里最差的那个 (堆顶)，才进行替换
                    if (minHeap.peek() != null && dish.getAllScore() > minHeap.peek().getAllScore()) {
                        minHeap.poll();
                        minHeap.offer(dish);
                    }
                }
            }
            log.info(STR."堆排序进入数量：\{minHeap.size()}");

            // 将堆中的元素转为结果列表，并按分数降序排列
            List<DishInfoAndScore> resultList = new ArrayList<>(minHeap);
            resultList.sort(Comparator.comparingDouble(DishInfoAndScore::getAllScore).reversed());

            // --- 5. 写入事件 (因为前面已经塞入了 DishInfoEasy，这里直接返回即可) ---
            HashMap<Long,DishInfoAndScore> andScoreHashMap = decisionMarkEvent.getSearchDishList();
            HashMap<Long, DishInfoAndScore> resultMap = (HashMap<Long, DishInfoAndScore>) resultList.stream()
                    .collect(Collectors.toMap(
                            DishInfoAndScore::getDishId,
                            Function.identity()
                    ));

            if (andScoreHashMap != null) {
                andScoreHashMap.putAll(resultMap);
            } else {
                andScoreHashMap = resultMap;
            }
            decisionMarkEvent.setSearchDishList(andScoreHashMap);
            log.info(STR."最终输出搜索到的菜品为：\{resultList.size()}");

            return resultList;

        } catch (Exception e) {
            log.error("菜品搜索工具执行失败", e);
            return List.of();
        }
    }

    private Map<Long, DishInfoAndScore> convertToDishInfoAndScoreMap(List<Long> dishIdList, String type, int weight) {
        if (dishIdList == null || dishIdList.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, DishInfoAndScore> dishInfoAndScoreMap = new HashMap<>();
        dishIdList.forEach(a -> {
            DishInfoAndScore dishInfoAndScore = new DishInfoAndScore();
            dishInfoAndScore.setScore(new Score());
            dishInfoAndScore.setDishInfo(new DishInfoEasy());
            dishInfoAndScore.setDishId(a);
            switch (type) {
                case "属性" -> dishInfoAndScore.getScore().setAttributeScore(weight);
                case "销售" -> dishInfoAndScore.getScore().setSalesScore(weight);
                case "用户" -> dishInfoAndScore.getScore().setHistoricalRatingScore(weight);
                case "预测" -> dishInfoAndScore.getScore().setPredictedRevenueScore(weight);
            }
            dishInfoAndScore.getScore().setAllScoreByAdd();
            dishInfoAndScoreMap.put(a, dishInfoAndScore);
        });
        return dishInfoAndScoreMap;
    }

    private LocalDate[] processDateRange(LocalDate start, LocalDate end) {
        if (end == null) {
            end = LocalDate.now().plusDays(1);
        }
        if (start == null) {
            start = end.minusMonths(1);
        }
        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }
        return new LocalDate[]{start, end};
    }
    /**
     * 辣度映射：文字转数字 (String -> Integer)
     *
     * @param desc 辣度描述
     * @return 对应的数字级别
     */
    public static Integer mapSpiciness(String desc) {
        if (desc == null) return null; // 防御性编程，防止空指针

        return switch (desc) {
            case "不辣" -> 0;
            case "微辣" -> 1;
            case "中辣" -> 2;
            case "无辣不欢" -> 3;
            default -> throw new IllegalArgumentException("非法的辣度描述：" + desc);
        };
    }

    /**
     * 辣度映射：数字转文字 (Integer -> String)
     *
     * @param level 辣度级别
     * @return 对应的文字描述
     */
    public static String mapSpiciness(Integer level) {
        if (level == null) return null; // 防御性编程，防止空指针

        return switch (level) {
            case 0 -> "不辣";
            case 1 -> "微辣";
            case 2 -> "中辣";
            case 3 -> "无辣不欢";
            default -> throw new IllegalArgumentException("非法的辣度级别：" + level);
        };
    }
}
