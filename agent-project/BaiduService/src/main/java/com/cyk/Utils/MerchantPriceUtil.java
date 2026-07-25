package com.cyk.Utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
public class MerchantPriceUtil {

    /**
     * 计算商家的客单价统计信息（均价、最低价、最高价）
     */
    public static JSONObject calculateAveragePrice(JSONArray results) {
        log.info("开始计算商家价格统计信息");

        // 默认返回值封装
        JSONObject statResult = JSONUtil.createObj()
                .set("average_price", 0.0)
                .set("min_price", null)
                .set("max_price", null);

        // 1. 基础校验：如果数组为空，直接返回默认值
        if (results == null || results.isEmpty()) {
            return statResult;
        }

        double totalPrice = 0.0;
        int validPriceCount = 0;

        // 初始化最大值和最小值（使用包装类以便判断是否被赋过值）
        Double minPrice = Double.MAX_VALUE;
        Double maxPrice = Double.MIN_VALUE;

        // 2. 遍历每一个商家节点
        for (int i = 0; i < results.size(); i++) {
            JSONObject merchant = results.getJSONObject(i);

            if (merchant != null && merchant.containsKey("detail_info")) {
                JSONObject detailInfo = merchant.getJSONObject("detail_info");

                if (detailInfo != null && detailInfo.containsKey("price")) {
                    String priceStr = detailInfo.getStr("price");

                    if (StrUtil.isNotBlank(priceStr)) {
                        try {
                            double price = Double.parseDouble(priceStr);

                            // 过滤掉价格为 0 或大于 50 的异常数据 (按你原有的业务规则)
                            if (price > 0 && price < 50) {
                                totalPrice += price;
                                validPriceCount++;

                                // 动态更新最低价和最高价
                                minPrice = Math.min(minPrice, price);
                                maxPrice = Math.max(maxPrice, price);
                            }
                        } catch (NumberFormatException e) {
                            log.debug("解析商家价格异常，跳过此条数据。脏数据内容: {}", priceStr);
                        }
                    }
                }
            }
        }

        // 3. 如果没有任何有效的价格数据，直接返回
        if (validPriceCount == 0) {
            return statResult;
        }

        // 4. 计算平均值并保留两位小数
        double average = totalPrice / validPriceCount;
        BigDecimal bg = new BigDecimal(average);
        double finalAvg = bg.setScale(2, RoundingMode.HALF_UP).doubleValue();

        // 5. 将计算好的均价、最低价(转为整数)、最高价(转为整数)写入并返回
        return statResult
                .set("average_price", finalAvg)
                .set("min_price", minPrice.intValue()) // 数据库要求是 integer
                .set("max_price", maxPrice.intValue());
    }
}