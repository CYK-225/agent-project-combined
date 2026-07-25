package com.cyk.Service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.cyk.Utils.DeliveryTimeUtil;
import com.cyk.Utils.MerchantLocationUtil;
import com.cyk.Utils.MerchantPriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class GetMerchantListService {

    @Value("${baidu.map.ak}")
    private String ak;

    private final String Search_Url = "https://api.map.baidu.com/place/v2/search";

    private final String RouteCalculate_Url  = "https://api.map.baidu.com/routematrix/v2/riding";

    public JSONObject getMerchantCount(String BuildingLocation, String Radius) {

        // 基础校验 (推荐用 Hutool 的 StrUtil.isBlank 防止空字符串和 null)
        if (StrUtil.isBlank(BuildingLocation) || StrUtil.isBlank(Radius)) {
            throw new RuntimeException("请传入BuildingLocation和Radius");
        }

        // 1. 调用独立出来的循环获取商家方法
        JSONArray allResults = fetchAllMerchants(BuildingLocation, Radius);
        int totalCount = allResults.size();

        // 2. 统计平均价格等指标
        JSONObject priceStats = MerchantPriceUtil.calculateAveragePrice(allResults);
        log.info("该区域内商家的平均客单价为: {} 元, 最低: {}, 最高: {}",
                priceStats.getDouble("average_price"),
                priceStats.getInt("min_price"),
                priceStats.getInt("max_price"));

        // 3. 统计距离与配送时间(配送时将半径延长至三公里)
        JSONArray distanceResults = fetchAllMerchants(BuildingLocation,"3000");

        log.info("搜索半径 {} ， 搜索结果 {}", 3000, distanceResults);

        log.info("开始提取坐标");
        String formattedLocations = MerchantLocationUtil.extractLocations(distanceResults);
        log.info("提取出的坐标串: {}", formattedLocations);
        Map<String, Object> routeCalculateParamMap = new HashMap<>();
        routeCalculateParamMap.put("origins", BuildingLocation);
        routeCalculateParamMap.put("destinations", formattedLocations);
        routeCalculateParamMap.put("output", "json");
        routeCalculateParamMap.put("ak", this.ak);
        String routeCalculateResult = HttpUtil.get(this.RouteCalculate_Url,routeCalculateParamMap);

        log.info("开始调用百度地图 API 【批量算路】 结果：{}" , routeCalculateResult);

        JSONObject routeCalculateOutput = JSONUtil.parseObj(routeCalculateResult);

        log.info("开始计算配送时间,{}", routeCalculateResult);

        Double deltaTime = DeliveryTimeUtil.calculateAverageDeliveryTime(routeCalculateOutput);
        log.info("该区域内商家平均配送时间: {} 分钟", deltaTime);

        // 4. 重新组装一个包含【总数】和【所有数据】的 JSON 对象返回给上层
        JSONObject finalOutput = new JSONObject();
        finalOutput.set("status", 0);
        finalOutput.set("message", "ok");
        finalOutput.set("total_count", totalCount);
        finalOutput.set("average_price", priceStats.getDouble("average_price"));
        finalOutput.set("min_price", priceStats.getInt("min_price"));
        finalOutput.set("max_price", priceStats.getInt("max_price"));
        finalOutput.set("average_delivery_time", deltaTime);
        finalOutput.set("results", allResults);     // 包含所有页的超级大数组

        return finalOutput;
    }











    /**
     * 循环分页获取指定中心点和半径内的所有商家信息
     *
     * @param location 中心坐标
     * @param radius   搜索半径
     * @return 包含所有商家信息的 JSONArray
     */
    private JSONArray fetchAllMerchants(String location, String radius) {
        Map<String, Object> paramMap = new HashMap<>();
        // 你可以把 query 细化，比如 "餐饮|餐车|美食"
        paramMap.put("query", "餐饮");
        paramMap.put("location", location);
        paramMap.put("radius", radius);
        paramMap.put("radius_limit", "true"); // 严格限制在半径内，避免数据越界
        paramMap.put("output", "json");
        paramMap.put("page_size", 20); // 单页最大 20 条
        paramMap.put("scope", 2);
        paramMap.put("ak", this.ak);

        JSONArray allResults = new JSONArray(); // 用于存放所有汇总的商家数据
        int pageNum = 0; // 百度 API 的页码是从 0 开始的！

        log.info("==> 开始循环获取商家数据, 检索坐标: {}, 检索半径: {}", location, radius);

        // 开启循环获取
        while (true) {
            // 每次请求前更新页码
            paramMap.put("page_num", pageNum);

            log.info("正在请求第 {} 页 (page_num={})...", pageNum + 1, pageNum);

            // 1. 发送请求
            String result;
            try {
                result = HttpUtil.get(this.Search_Url, paramMap);
            } catch (Exception e) {
                log.error("调用百度地图API网络请求异常: {}", e.getMessage(), e);
                throw new RuntimeException("请求百度地图服务失败，请检查网络");
            }

            // 2. 解析当前页结果
            JSONObject output = JSONUtil.parseObj(result);
            int status = output.getInt("status");

            if (status != 0) {
                String errorMsg = output.getStr("message");
                log.error("百度地图API业务报错! status: {}, message: {}", status, errorMsg);
                throw new RuntimeException("百度地图 API 调用失败: " + errorMsg);
            }

            // 3. 获取当前页的 results 数组
            JSONArray currentResults = output.getJSONArray("results");

            // 如果返回的数据为空，直接结束循环（兜底保护）
            if (currentResults == null || currentResults.isEmpty()) {
                break;
            }

            // 4. 将当前页的数据追加到总集合中
            allResults.addAll(currentResults);

            // 5. 核心判断：如果当前页拿到的数据不到 20 条，说明到底了，结束循环！
            if (currentResults.size() < 20) {
                break;
            }

            // 如果满 20 条，页码 +1，进入下一轮循环继续拿
            pageNum++;
        }

        log.info("<== 商家数据获取完毕，共请求了 {} 页，总计获取到 {} 条商家信息", pageNum + 1, allResults.size());
        return allResults;
    }
}