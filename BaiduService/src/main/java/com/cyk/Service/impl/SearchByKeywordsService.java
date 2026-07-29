package com.cyk.Service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.cyk.Enity.SearchBuildingsTo;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SearchByKeywordsService {

    @Value("${baidu.map.ak}")
    private String ak;

    private final String url = "https://api.map.baidu.com/place/v2/search";

    /**
     * 搜索区域内写字楼
     */
    public JSONObject searchBuildings(SearchBuildingsTo searchBuildingsTo) {
        log.info("==> 开始执行搜索区域内写字楼任务");

        // 1. 基础校验
        if (searchBuildingsTo == null || StringUtils.isBlank(searchBuildingsTo.getLocation())) {
            log.warn("参数校验失败：搜索区域(bounds)为空");
            throw new RuntimeException("请传入经纬度");
        }

        // 2. 构造参数 Map
        Map<String, Object> paramMap = new HashMap<>();

        paramMap.put("query", searchBuildingsTo.getKeywords());
        paramMap.put("location", searchBuildingsTo.getLocation());
        paramMap.put("radius", searchBuildingsTo.getRadius());
        paramMap.put("output", "json");
        paramMap.put("page_size", 20);
        paramMap.put("ak", this.ak);


        //打印参数(包括半径)
        log.info("【参数】: {}", paramMap);

        String fullUrl = HttpUtil.urlWithForm(this.url, paramMap, null, false);
        log.info("【完整请求路径】: {}", fullUrl);

        // 3. 发送请求 (加入 try-catch 捕获可能的网络连接超时异常)
        String result;
        try {
            result = HttpUtil.get(this.url, paramMap);
        } catch (Exception e) {
            log.error("调用百度地图API网络请求异常: {}", e.getMessage(), e);
            throw new RuntimeException("请求百度地图服务失败，请检查网络");
        }

        // 4. 解析结果
        JSONObject output = JSONUtil.parseObj(result);
        int status = output.getInt("status");

        // 校验百度返回的业务状态码
        if (status != 0) {
            String errorMsg = output.getStr("message");
            log.error("百度地图API业务报错! status: {}, message: {}", status, errorMsg);
            // 将百度的真实报错信息抛出，方便前端或全局异常处理器定位问题（比如 AK错误、并发超限等）
            throw new RuntimeException("百度地图 API 调用失败: " + errorMsg);
        }
        // 5. 打印成功日志，方便观察数据量
        JSONArray resultsArray = output.getJSONArray("results");
        int count = (resultsArray != null) ? resultsArray.size() : 0;
        log.info("<== 百度地图API调用成功, 状态正常, 共查找到 {} 条 [{}] 的数据", count, searchBuildingsTo.getKeywords());

        return output;
    }


}