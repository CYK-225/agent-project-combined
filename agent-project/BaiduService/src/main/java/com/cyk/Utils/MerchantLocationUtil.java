package com.cyk.Utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MerchantLocationUtil {

    /**
     * 提取商家 POI 列表中的坐标点位
     *
     * @param results 百度地图 API 返回的 results 数组
     * @return 拼接好的坐标字符串，格式：纬度,经度|纬度,经度 (例如: "40.056878,116.30815|40.063597,116.364973")
     */
    public static String extractLocations(JSONArray results) {
        // 1. 基础校验
        if (results == null || results.isEmpty()) {
            return "";
        }

        List<String> locationPoints = new ArrayList<>();

        // 2. 遍历提取每一个商家的坐标
        for (int i = 0; i < results.size(); i++) {
            JSONObject poi = results.getJSONObject(i);

            // 安全防范：确保对象不为空，且包含 location 节点
            if (poi != null && poi.containsKey("location")) {
                JSONObject location = poi.getJSONObject("location");
                String lat = location.getStr("lat");
                String lng = location.getStr("lng");

                // 确保经纬度数据有效
                if (StrUtil.isNotBlank(lat) && StrUtil.isNotBlank(lng)) {
                    // 直接拼接 纬度,经度 并塞入集合
                    locationPoints.add(lat + "," + lng);
                }
            }
        }

        // 3. 使用 Hutool 的 CollUtil 将 List 用 "|" 符号连接成字符串
        return CollUtil.join(locationPoints, "|");
    }
}