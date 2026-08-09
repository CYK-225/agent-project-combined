package com.cyk.Service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.cyk.Service.IBuildingsService;
import com.cyk.Service.ICompaniesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SearchCompanyByBuildingService {

    @Value( "${baidu.map.ak}")
    private String ak;

    @Value("${baidu.map.search-url}")
    private String Search_Url;

    @Value("${baidu.map.detail-url}")
    private String Detail_Url;

    @Resource
    private IBuildingsService buildingsService;

    @Resource
    private ICompaniesService companiesService;

    @Resource
    private GetMerchantListService getMerchantListService;

    public JSONObject searchCompanyByBuilding(String BuildingLocation, String BuildingUid) {
        try {
            if (StrUtil.isBlank(BuildingLocation)) {
                throw new RuntimeException("请传入BuildingLocation");
            }
            if (StrUtil.isBlank(BuildingUid)) {
                throw new RuntimeException("请传入BuildingUid");
            }

            // 以楼宇为中心，搜索商户时的半径
            String radius = "1000";

            // ==========================================
            // 第一步：根据楼宇uid获取楼宇详细信息 (名称、地址等) 以及周边商户
            // ==========================================
            log.info("【获取周边商户数据】 坐标: {}, 半径: {}", BuildingLocation, radius);
            JSONObject merchantInfo = getMerchantListService.getMerchantCount(BuildingLocation, radius);

            JSONObject buildingDetail = getBuildingDetailByUid(BuildingUid);
            JSONObject detailResult = buildingDetail.getJSONObject("result");
            if (detailResult == null) {
                throw new RuntimeException("未从百度地图获取到有效的楼宇详情数据");
            }
            String buildingName = detailResult.getStr("name");
            String buildingAddress = detailResult.getStr("address");

            log.info("【搜索楼宇入驻公司】: {} (地点:{})", buildingName, BuildingLocation);

            // ==========================================
            // 第二步 & 第三步：循环查询周边公司并进行数据清洗
            // ==========================================
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("query", "公司");
            paramMap.put("location", BuildingLocation);
            paramMap.put("radius", 60);
            paramMap.put("radius_limit", "true");
            paramMap.put("output", "json");
            paramMap.put("page_size", 20); // 单页最大 20 条
            paramMap.put("scope", 2);
            paramMap.put("ak", this.ak);

            JSONArray allCleanedResults = new JSONArray(); // 存放清洗后的所有公司数据
            List<String> allCompanyUids = new ArrayList<>(); // 存放清洗后的所有公司UID
            int pageNum = 0; // 百度 API 页码从 0 开始

            log.info("==> 开始循环获取楼宇内公司数据, 检索坐标: {}", BuildingLocation);

            while (true) {
                paramMap.put("page_num", pageNum);
                log.info("正在请求公司列表第 {} 页 (page_num={})...", pageNum + 1, pageNum);

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

                // 3. 获取当前页数据
                JSONArray currentResults = output.getJSONArray("results");

                // 兜底保护：如果返回数据为空，结束循环
                if (currentResults == null || currentResults.isEmpty()) {
                    break;
                }

                // 4. 清洗当前页数据并追加到总集合
                for (int i = 0; i < currentResults.size(); i++) {
                    JSONObject comp = currentResults.getJSONObject(i);
                    String uid = comp.getStr("uid");
                    String name = comp.getStr("name");

                    // 清洗规则：剔除没有名称或 UID 的无效数据
                    if (StrUtil.isNotBlank(uid) && StrUtil.isNotBlank(name)) {
                        allCleanedResults.add(comp);
                        allCompanyUids.add(uid);
                    }
                }

                // 5. 判断是否到底，不到 20 条说明已经是最后一页
                if (currentResults.size() < 20) {
                    break;
                }

                // 继续下一页
                pageNum++;
            }

            log.info("<== 数据提取完成，共请求了 {} 页，获取到 {} 家有效公司数据", pageNum + 1, allCompanyUids.size());

            // ==========================================
            // 第四步：组装楼宇信息并写入 PG 数据库
            // ==========================================
            buildingsService.syncBuildingInfo(BuildingUid, buildingName, BuildingLocation, buildingAddress, allCompanyUids);

            // ==========================================
            // 第五步：将公司 UID 和 名称 批量插入/更新到公司表
            // ==========================================
            companiesService.syncCompaniesFromBaidu(allCleanedResults, BuildingUid, buildingName, merchantInfo);

            // ==========================================
            // 第六步：组装最终返回结果
            // ==========================================
            JSONObject finalOutput = new JSONObject();
            finalOutput.set("status", 0);
            finalOutput.set("message", "ok");
            finalOutput.set("total_count", allCleanedResults.size()); // 返回有效公司总数
            finalOutput.set("results", allCleanedResults); // 返回所有干净的公司数组
            finalOutput.set("merchantInfo", merchantInfo);

            return finalOutput;

        } catch (Exception e) {
            log.error("【搜索楼宇入驻公司异常】: {}", e.getMessage(), e);
            return JSONUtil.createObj().set("status", 500).set("message", "搜索失败: " + e.getMessage());
        }
    }

    /**
     * 新增方法：根据 UID 查询楼宇详情
     * @param uid 百度地图POI的uid
     * @return 包含详情信息的JSONObject
     */
    public JSONObject getBuildingDetailByUid(String uid) {
        if (StrUtil.isBlank(uid)) {
            throw new RuntimeException("请传入楼宇UID");
        }

        log.info("【查询楼宇详情】: {}", uid);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("uid", uid);
        paramMap.put("output", "json");
        paramMap.put("scope", 2); // scope=2 表示获取详细信息
        paramMap.put("ak", this.ak);

        String result = HttpUtil.get(this.Detail_Url, paramMap);
        JSONObject output = JSONUtil.parseObj(result);

        int status = output.getInt("status");
        if (status != 0) {
            String errorMsg = output.getStr("message");
            log.error("百度地图详情API业务报错! status: {}, message: {}", status, errorMsg);
            throw new RuntimeException("百度地图详情 API 调用失败: " + errorMsg);
        }

        return output;
    }
}
