package com.cyk.Utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeliveryTimeUtil {

    // 电动车/摩托车预估配送速度：25 km/h
    private static final double SPEED_KM_PER_HOUR = 20;

    // 出餐及交接缓冲时间：40 分钟
    private static final int BUFFER_TIME_MINUTES = 40;

    /**
     * 解析批量算路结果，计算平均配送时间
     *
     * @param output 百度批量算路 API 返回的完整 JSONObject 对象
     * @return 预估平均配送时间（单位：分钟，向上取整）
     */
    public static Double calculateAverageDeliveryTime(JSONObject output) {
        // 1. 最外层基础校验
        if (output == null) {
            log.warn("传入的算路结果对象为空，返回默认缓冲时间");
            return (double) BUFFER_TIME_MINUTES;
        }

        // 2. 提取结果数组（兼容处理：批量算路通常叫 result，其他接口可能叫 results）
        JSONArray results = output.getJSONArray("result");
        if (results == null) {
            results = output.getJSONArray("results");
        }

        // 校验数组是否为空
        if (results == null || results.isEmpty()) {
            log.warn("算路结果数组(result/results)为空，无法计算配送时间，返回默认缓冲时间");
            return null;
        }

        double totalDistanceMeters = 0.0;
        int validCount = 0;

        // 3. 遍历提取距离 (单位：米)
        for (int i = 0; i < results.size(); i++) {
            JSONObject routeInfo = results.getJSONObject(i);

            if (routeInfo != null && routeInfo.containsKey("distance")) {
                try {
                    double distance = 0.0;

                    // 兼容处理：百度的 distance 有时是直接的数字，有时是一个嵌套对象 {"text":"1.2公里", "value":1200}
                    Object distanceObj = routeInfo.getObj("distance");
                    if (distanceObj instanceof JSONObject) {
                        distance = ((JSONObject) distanceObj).getDouble("value");
                    } else if (distanceObj instanceof Number) {
                        distance = routeInfo.getDouble("distance");
                    }

                    // 过滤掉异常数据（比如算路失败导致距离为 0 或负数）
                    if (distance > 0) {
                        totalDistanceMeters += distance;
                        validCount++;
                    }
                } catch (Exception e) {
                    log.debug("解析单条算路距离异常，跳过此条数据", e);
                }
            }
        }

        // 4. 如果没有有效距离数据，直接返回基础缓冲时间
        if (validCount == 0) {
            return null ;
        }

        // 5. 计算平均距离 (米)
        double avgDistanceMeters = totalDistanceMeters / validCount;

        // 6. 核心公式计算：
        // 距离转换：米 -> 公里 (avgDistanceMeters / 1000.0)
        // 骑行耗时：公里 / 速度 = 小时
        // 时间转换：小时 -> 分钟 (* 60)
        double travelTimeMinutes = (avgDistanceMeters / 1000.0) / SPEED_KM_PER_HOUR * 60.0;

        // 7. 加上缓冲时间 (出餐+取餐 15 分钟)
        double totalTimeMinutes = travelTimeMinutes + BUFFER_TIME_MINUTES;

        log.info("算路统计完毕: 成功解析 {} 条路线，平均距离 {} 米，纯骑行耗时 {} 分钟，总预计耗时 {} 分钟",
                validCount, String.format("%.2f", avgDistanceMeters),
                String.format("%.1f", travelTimeMinutes),
                String.format("%.1f", totalTimeMinutes));

        // 8. 配送时间通常向上取整（宁可多估1分钟，不能迟到1分钟，提升用户好评率）
        return Math.ceil(totalTimeMinutes);
    }
}