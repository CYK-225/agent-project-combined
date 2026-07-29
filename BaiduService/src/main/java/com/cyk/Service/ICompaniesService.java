package com.cyk.Service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.cyk.Enity.table.CompaniesEntity;
import com.mybatisflex.core.service.IService;

/**
 * 公司表 (Excel全量字段) 服务层。
 */
public interface ICompaniesService extends IService<CompaniesEntity> {

    CompaniesEntity getName(String name);

    void updateIsProcessed(String companyId, Integer status);

    /**
     * 从百度地图API批量同步公司基础信息
     *
     * @param cleanedResults 清洗后的公司JSON数组 (包含 uid, name)
     * @param buildingUid    所属楼宇UID
     */

    void syncCompaniesFromBaidu(JSONArray cleanedResults, String buildingUid, String buildingName, JSONObject merchantInfo);
}