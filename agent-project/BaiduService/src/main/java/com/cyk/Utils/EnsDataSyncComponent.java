package com.cyk.Utils;

import cn.hutool.core.util.StrUtil;
import com.cyk.Enity.table.CompaniesEntity;
import com.cyk.Service.ICompaniesService;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ENS 数据同步组件
 * 负责将 ENS 返回的 Map 数据结构，安全映射并写入到 PG 数据库的公司实体中
 */
@Slf4j
@Component
public class EnsDataSyncComponent {

    @Resource
    private ICompaniesService companiesService;

    /**
     * 将 ENS 数据安全地同步到 PG 数据库
     * 使用 UpdateChain 按需更新字段，避免全量 updateById 覆盖 info_status 等其他字段
     *
     * @param companyName 企业名称 (用于查询数据库记录)
     * @param info        ENS 返回的企业详情 Map
     */
    public void syncToDatabase(String companyName, Map<String, Object> info) {
        if (info == null || info.isEmpty()) {
            return;
        }

        try {
            // 1. 确认公司存在
            CompaniesEntity existCompany = companiesService.getName(companyName);
            if (existCompany == null) {
                log.warn("数据库中未找到公司[{}], 无法更新ENS数据", companyName);
                return;
            }

            System.out.println("ens返回的Map数据结构:" + info);

            // 2. 使用 UpdateChain 只更新 ENS 相关字段，不触碰 info_status
            UpdateChain<CompaniesEntity> chain = UpdateChain.of(CompaniesEntity.class)
                    .where(CompaniesEntity::getUid).eq(existCompany.getUid());

            // 注册资本 (varchar 50) <- recConcat
            if (info.get("recConcat") != null) {
                chain.set(CompaniesEntity::getRegisteredCapital, StrUtil.subPre(String.valueOf(info.get("recConcat")), 50));
            }

            // 企业成立年限 (varchar 50) <- esDate
            if (info.get("esDate") != null) {
                chain.set(CompaniesEntity::getEstablishedYears, StrUtil.subPre(String.valueOf(info.get("esDate")), 50));
            }

            // 公司所属行业 (varchar 50) <- opScope
            if (info.get("opScope") != null) {
                chain.set(CompaniesEntity::getIndustry, StrUtil.subPre(String.valueOf(info.get("opScope")), 50));
            }

            // 关键联系人线索 (varchar 255) <- telList
            if (info.get("telList") != null) {
                System.out.println("ens返回的联系人数组" + info.get("telList"));
                chain.set(CompaniesEntity::getKeyContact, StrUtil.subPre(String.valueOf(info.get("telList")), 255));
            }

            // 楼层信息/详细地址 (varchar 50) <- yrAddress
            if (info.get("yrAddress") != null) {
                chain.set(CompaniesEntity::getFloorInfo, StrUtil.subPre(String.valueOf(info.get("yrAddress")), 50));
            }

            boolean updateRes = chain.update();
            log.info("公司[{}] ENS数据提取并同步入库: {}", companyName, updateRes ? "成功" : "失败");

        } catch (Exception e) {
            log.error("同步公司[{}]ENS数据入库时发生异常: {}", companyName, e.getMessage());
        }
    }
}