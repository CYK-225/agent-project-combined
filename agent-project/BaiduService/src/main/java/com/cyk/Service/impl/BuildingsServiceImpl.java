package com.cyk.Service.impl;


import com.cyk.Enity.table.BuildingsEntity;
import com.cyk.Mapper.BuildingsMapper;
import com.cyk.Service.IBuildingsService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 楼宇表 服务层实现。
 */
@Slf4j
@Service
public class BuildingsServiceImpl extends ServiceImpl<BuildingsMapper, BuildingsEntity> implements IBuildingsService {

    @Override
    public void syncBuildingInfo(String buildingUid, String buildingName, String buildingLocation, String buildingAddress, List<String> companyUids) {

        // 1. 先去数据库查出旧的楼宇数据
        BuildingsEntity existBuilding = this.getById(buildingUid);

        BuildingsEntity buildingEntity = new BuildingsEntity();
        buildingEntity.setUid(buildingUid);
        buildingEntity.setName(buildingName);
        buildingEntity.setLocation(buildingLocation);
        buildingEntity.setAddress(buildingAddress);

        // ==========================================
        // 2. 核心修改：使用 Set 集合将新旧 UID 合并，自动去重
        // ==========================================
        Set<String> finalUids = new HashSet<>();

        // 2.1 如果数据库中已经有这个楼宇，且存在旧的公司 UID，先加进来
        if (existBuilding != null && existBuilding.getCompanyUidList() != null && existBuilding.getCompanyUidList().length > 0) {
            finalUids.addAll(Arrays.asList(existBuilding.getCompanyUidList()));
        }

        // 2.2 把本次新查出来的公司 UID 加进来
        if (companyUids != null && !companyUids.isEmpty()) {
            finalUids.addAll(companyUids);
        }

        // 3. 将合并后的最终数组赋值给实体
        if (!finalUids.isEmpty()) {
            buildingEntity.setCompanyUidList(finalUids.toArray(new String[0]));
        }

        // ==========================================
        // 4. 执行入库或更新
        // ==========================================
        boolean saveRes;
        if (existBuilding == null) {
            // 数据库中没有该楼宇，执行 INSERT
            saveRes = this.save(buildingEntity);
            log.info("【楼宇表同步】楼宇[{}]不存在，执行新增入库: {}", buildingName, saveRes ? "成功" : "失败");
        } else {
            // 数据库中已有该楼宇，执行 UPDATE
            // (MyBatis-Flex 默认会忽略 null 字段，如果 finalUids 是空的，它不会清空数据库原有数据)
            saveRes = this.updateById(buildingEntity);
            log.info("【楼宇表同步】楼宇[{}]已存在，执行更新操作: {}", buildingName, saveRes ? "成功" : "失败");
        }

        log.info("【楼宇表同步】楼宇[{}]最终 {} 个公司UID处理完成", buildingName, finalUids.size());
    }
}