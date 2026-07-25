package org.example.agent.Sp.dal.service.impl;


import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.example.agent.Sp.dal.entity.DailyProcurementPlanEntity;
import org.example.agent.Sp.dal.mapper.DailyProcurementPlanMapper;
import org.example.agent.Sp.dal.service.DailyProcurementPlanService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

import static org.example.agent.Sp.dal.entity.table.DailyProcurementPlanEntityTableDef.DAILY_PROCUREMENT_PLAN_ENTITY;

/**
 * 每日采购计划与消耗实录表 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service("supplyPlanDailyProcurementPlanServiceImpl")
public class DailyProcurementPlanServiceImpl extends ServiceImpl<DailyProcurementPlanMapper, DailyProcurementPlanEntity> implements DailyProcurementPlanService {
    @Override
    public List<Long> searchDishesByHistoricalSales(LocalDate historicalSalesStartDate, LocalDate historicalSalesEndDate, List<Long> notInId){
        // 逻辑：总消耗 / (总消耗 + 总损耗)
        // 使用 NULLIF 防止分母为 0 导致报错
        String rateSql = """
        SUM(actual_consumed) / NULLIF(SUM(actual_consumed) + SUM(actual_loss), 0)
    """;

        return mapper.selectListByQueryAs(
                QueryWrapper.create()
                        .select(DAILY_PROCUREMENT_PLAN_ENTITY.DISH_ID)
                        // 查出总消耗和总损耗 (让你能看到具体的量)
                        .select("SUM(actual_consumed) AS total_consumed")
                        .select("SUM(actual_loss) AS total_loss")

                        //  计算历史综合消耗率
                        .select(rateSql + " AS historical_rate")

                        .from(DailyProcurementPlanEntity.class)
                        .where(DAILY_PROCUREMENT_PLAN_ENTITY.PLAN_DATE.between(historicalSalesStartDate, historicalSalesEndDate))
                        // 排除掉数据异常的记录 (消耗和损耗都为0的)
                        .and("actual_consumed + actual_loss > 0")
                        .and(DAILY_PROCUREMENT_PLAN_ENTITY.DISH_ID.notIn(notInId).when(!notInId.isEmpty()))
                        .groupBy(DAILY_PROCUREMENT_PLAN_ENTITY.DISH_ID)
                        // 过滤偶发数据
                        // 至少上架过 3 次才参与排名
                        .having(QueryMethods.count(DAILY_PROCUREMENT_PLAN_ENTITY.ID).ge(3))
                        // 排序
                        // historical_rate 越高，说明浪费越少，售出越多 -> 倒序
                        .orderBy("historical_rate", false)

                        .limit(48)
                ,Long.class
        );
    }

    // 1. 构造核心计算公式 (因为涉及加减除，直接写 String 片段最方便)



}
