package org.example.agent.Sp.dal.service;


import com.mybatisflex.core.service.IService;
import org.example.agent.Sp.dal.entity.DailyProcurementPlanEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日采购计划与消耗实录表 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface DailyProcurementPlanService extends IService<DailyProcurementPlanEntity> {

    List<Long> searchDishesByHistoricalSales(LocalDate historicalSalesStartDate, LocalDate historicalSalesEndDate, List<Long> notInId);
}