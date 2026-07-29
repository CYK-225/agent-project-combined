package com.cyk.Service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import com.cyk.Enity.table.CompaniesEntity;
import com.cyk.Mapper.CompaniesMapper;
import com.cyk.Service.ICompaniesService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 公司表 (Excel全量字段) 服务层实现。
 */
@Slf4j
@Service
public class CompaniesServiceImpl extends ServiceImpl<CompaniesMapper, CompaniesEntity> implements ICompaniesService {

    @Override
    public CompaniesEntity getName(String name){

        log.info("【公司表查询】根据公司名称查询: {}", name);
        log.info("【公司表查询】构造的查询条件: {}", QueryWrapper.create().eq(CompaniesEntity::getCompanyName,name).toString());
        return mapper.selectOneByQuery(QueryWrapper.create().eq(CompaniesEntity::getCompanyName,name));
    }

    @Override
    public void updateIsProcessed(String companyId, Integer status) {
        // 使用 MyBatis-Flex 的 UpdateChain 进行部分字段的高效更新，避免全表覆盖
        boolean updateResult = UpdateChain.of(CompaniesEntity.class)
                .set(CompaniesEntity::getInfoStatus, status)
                .where(CompaniesEntity::getUid).eq(companyId)
                .update();

        if (!updateResult) {
            log.warn("更新企业状态失败，未找到对应的公司ID: {}", companyId);
        }
    }

    @Override
    public void syncCompaniesFromBaidu(JSONArray cleanedResults, String buildingUid, String buildingName, JSONObject merchantInfo) {
        if (cleanedResults == null || cleanedResults.isEmpty()) {
            return;
        }

        // ==========================
        // 1. 解析商户基础数据与自动推断
        // ==========================
        String nearbyFastFoodCount = null;
        Integer avgDeliveryPrice = null;
        Integer minFastFoodPrice = null;
        Integer maxFastFoodPrice = null;
        String deliveryTimeStr = null;
        String diningConvenience = null; // 就餐便利度

        if (merchantInfo != null) {
            // 商家数量
            nearbyFastFoodCount = merchantInfo.getStr("total_count");

            // 根据快餐数量自动推断就餐便利度
            if (nearbyFastFoodCount != null) {
                int count = Integer.parseInt(nearbyFastFoodCount);
                if (count >= 40) diningConvenience = "好";
                else if (count >= 20) diningConvenience = "一般";
                else if (count >= 10) diningConvenience = "差";
                else diningConvenience = "极差";
            }

            // 客单价 (均价)
            Double avgPrice = merchantInfo.getDouble("average_price");
            if (avgPrice != null) {
                avgDeliveryPrice = avgPrice.intValue();
            }

            // ==========================================
            // ⚠️ 解析最低价和最高价
            // 注意：请确保你的 MerchantPriceUtil 算出来放进 JSON 里的 key 叫 "min_price" 和 "max_price"
            // 如果你 JSON 里的 key 叫别的（比如 "minPrice"），请把下面的 getDouble("...") 里的字符串改掉
            // ==========================================
            Double minPrice = merchantInfo.getDouble("min_price");
            if (minPrice != null) {
                minFastFoodPrice = minPrice.intValue();
            }

            Double maxPrice = merchantInfo.getDouble("max_price");
            if (maxPrice != null) {
                maxFastFoodPrice = maxPrice.intValue();
            }

            // 平均配送时长
            Double avgDeliveryTime = merchantInfo.getDouble("average_delivery_time");
            if (avgDeliveryTime != null) {
                deliveryTimeStr = String.format("%.1f分钟", avgDeliveryTime);
            }
        }

        List<CompaniesEntity> companyEntities = new ArrayList<>();
        List<String> uids = new ArrayList<>();

        // ==========================
        // 2. 组装公司实体，填充全部字段
        // ==========================
        for (int i = 0; i < cleanedResults.size(); i++) {
            JSONObject comp = cleanedResults.getJSONObject(i);
            String uid = comp.getStr("uid");

            CompaniesEntity company = new CompaniesEntity();
            // --- 核心标识 ---
            company.setUid(uid);
            company.setCompanyName(comp.getStr("name"));

            // --- 楼宇/区域关联 ---
            company.setBuildingUid(buildingUid);
            company.setBuildingName(buildingName);
            company.setDistrict(comp.getStr("area")); // 百度自带行政区

            // --- 园区类型 ---
            // ⚠️ 按需求直接写死为 "写字楼"
            company.setParkType("写字楼");

            company.setInfoStatus(0); // 0-未初始化

            // --- 外卖/商户环境数据 ---
            company.setNearbyFastFoodCount(nearbyFastFoodCount);
            company.setAvgDeliveryPrice(avgDeliveryPrice);
            // ⚠️ 写入最高价与最低价
            company.setMinFastFoodPrice(minFastFoodPrice);
            company.setMaxFastFoodPrice(maxFastFoodPrice);

            company.setPeakDeliveryTime(deliveryTimeStr);
            company.setDiningConvenience(diningConvenience);

            companyEntities.add(company);
            uids.add(uid);
        }

        // ==========================
        // 3. 查重及分拣逻辑 (防止重复插入报错)
        // ==========================
        List<CompaniesEntity> existCompanies = this.listByIds(uids);
        List<String> existUids = new ArrayList<>();
        if (existCompanies != null) {
            for (CompaniesEntity exist : existCompanies) {
                existUids.add(exist.getUid());
            }
        }

        List<CompaniesEntity> insertList = new ArrayList<>();
        List<CompaniesEntity> updateList = new ArrayList<>();
        for (CompaniesEntity entity : companyEntities) {
            if (existUids.contains(entity.getUid())) {
                updateList.add(entity);
            } else {
                insertList.add(entity);
            }
        }

        // ==========================
        // 4. 执行批量新增和批量更新
        // ==========================
        if (!insertList.isEmpty()) {
            boolean insertRes = this.saveBatch(insertList);
            log.info("【公司表同步】全字段批量新增 {} 条: {}", insertList.size(), insertRes ? "成功" : "失败");
        }
        if (!updateList.isEmpty()) {
            boolean updateRes = this.updateBatch(updateList);
            log.info("【公司表同步】全字段批量更新 {} 条: {}", updateList.size(), updateRes ? "成功" : "失败");
        }
    }
}