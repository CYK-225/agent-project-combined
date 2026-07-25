package com.cyk.Service;

import com.cyk.Enity.table.BuildingsEntity;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 楼宇表 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface IBuildingsService extends IService<BuildingsEntity> {

    /**
     * 同步楼宇信息及入驻公司UID列表（存在则更新，不存在则新增）
     *
     * @param buildingUid      楼宇UID
     * @param buildingName     楼宇名称
     * @param buildingLocation 楼宇经纬度
     * @param buildingAddress  楼宇地址
     * @param companyUids      入驻公司的UID列表
     */
    void syncBuildingInfo(String buildingUid, String buildingName, String buildingLocation, String buildingAddress, List<String> companyUids);

}