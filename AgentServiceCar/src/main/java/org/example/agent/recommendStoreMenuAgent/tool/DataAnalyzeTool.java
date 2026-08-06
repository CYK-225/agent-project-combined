package org.example.agent.recommendStoreMenuAgent.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.example.agent.client.service.ufan.ComplaintClientService;
import org.example.agent.client.service.ufan.EvaluateClientService;
import org.example.agent.client.service.ufan.OrderClientService;
import org.example.agent.recommendStoreMenuAgent.domain.condition.DataAnalyzeCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataAnalyzeTool {

    @Autowired
    private OrderClientService orderClientService;

    @Autowired
    private EvaluateClientService evaluateClientService;

    @Autowired
    private ComplaintClientService complaintClientService;


    @Tool(name = "get_order_stat_list", description = """
            查询指定时间范围内订单统计数据，并按指定维度进行分组汇总。
            
            使用场景：
            当需要统计订单数据（如订单量、营业额、营业天数），并按不同维度进行汇总分析时使用该工具。
            例如：
            - 查询某些商家的订单统计
            - 查询某个客户下所有商家的订单统计
            - 按日期查看订单趋势
            - 按餐段查看订单分布
            - 查看客户与商家组合的订单数据
            
            统计范围可以按：
            客户、商家、日期、餐段等维度进行分组统计。
            
            必填参数：
            beginDate：查询开始日期，格式 yyyy-MM-dd
            endDate：查询结束日期，格式 yyyy-MM-dd
            groupByType：分组类型
            merchantStoreIdList 或 companyIdList 至少传一个
            
            可选参数：
            intervalNoList：餐段筛选条件
            
            groupByType 分组类型说明：
            0 = 按客户公司分组
            1 = 按商家分组
            2 = 按日期分组
            3 = 按餐段分组
            4 = 按客户+商家组合分组
            
            返回 JSON 格式：
            
            {
              "status": 1,
              "message": "success",
              "data": {
                "columns": [
                  "orderNum",
                  "totalAmount",
                  "serviceDateNum",
                  "merchantStoreId",
                  "companyId",
                  "intervalNo",
                  "useDate",
                  "companyShortName",
                  "merchantStoreShortName"
                ],
                "keyRows": {
                  "1001": [
                    320,
                    15680.50,
                    30,
                    1001,
                    5001,
                    2,
                    "2026-03-01",
                    "xxx客户",
                    "xxx商家"
                  ]
                }
              }
            }
            
            字段说明：
            
            status：
            数据状态
            0 = 查询失败或参数错误
            1 = 查询成功
            
            message：
            提示信息
            
            data：
            统计数据结果
            
            columns：
            返回列名列表
            
            keyRows：
            按分组维度组织的数据集合。
            
            key 的含义取决于 groupByType：
            
            groupByType = 0
            key = companyId
            
            groupByType = 1
            key = merchantStoreId
            
            groupByType = 2
            key = useDate（日期）
            
            groupByType = 3
            key = intervalNo（餐段编号）
            
            groupByType = 4
            key = companyId_merchantStoreId（客户ID+商家ID组合）
            
            value 为统计数据数组，字段顺序与 columns 一一对应。
            
            列字段说明：
            
            orderNum：
            订单数量
            
            totalAmount：
            订单营业额
            
            serviceDateNum：
            营业天数
            
            merchantStoreId：
            商家ID
            
            companyId：
            客户公司ID
            
            intervalNo：
            餐段编号
            
            useDate：
            订单日期
            
            companyShortName:公司客户简称
            merchantStoreShortName:商家简称
            
            当没有查询到数据时：
            status = 1
            keyRows = {}
            """)
    public ToolResultBlock queryOrderStat(@ToolParam(name = "dataAnalyzeCondition", description = """
            订单统计查询参数对象。
            
            用于指定订单统计的查询条件，例如时间范围、客户范围、商家范围、餐段等。
            
            参数说明：
            
            beginDate：
            查询开始日期，格式 yyyy-MM-dd，例如 2026-03-01。
            
            endDate：
            查询结束日期，格式 yyyy-MM-dd，例如 2026-03-31。
            
            groupByType：
            分组类型（必传），用于决定统计结果按什么维度汇总。
            
            可选值：
            0 = 按客户公司分组
            1 = 按商家分组
            2 = 按日期分组
            3 = 按餐段分组
            4 = 按客户+商家组合分组
            
            companyIdList：
            客户公司ID列表。
            用于指定统计哪些客户的数据。
            如果不传，则表示统计所有客户。
            
            merchantStoreIdList：
            商家ID列表。
            用于指定统计哪些商家的数据。
            如果不传，则表示统计所有商家。
            
            companyIdList 与 merchantStoreIdList 不能同时为空。
            
            intervalNoList：
            餐段筛选条件（可选）。
            用于只统计指定餐段的数据。
            
            餐段枚举值：
            1 = 早餐
            2 = 午餐
            3 = 晚餐
            4 = 宵夜
            5 = 下午茶
            6 = 午晚餐
            
            使用规则：
            
            1. beginDate 与 endDate 必须同时提供。
            2. groupByType 必须提供。
            3. merchantStoreIdList 与 companyIdList 至少传一个。
            4. intervalNoList 不传表示查询全部餐段。
            """) DataAnalyzeCondition dataAnalyzeCondition, ToolExecutionContext context) {
        return ToolResultBlock.text(orderClientService.queryOrderStat(dataAnalyzeCondition, context));
    }


    @Tool(name = "get_evaluate_stat_list", description = """
            查询指定时间范围内的评价统计数据，并按指定维度进行汇总分析。
            
            使用场景：
            当需要分析客户或商家的评价情况时使用该工具，例如：
            - 查看某些客户公司的评价统计
            - 查看某些商家的评价统计
            - 按日期分析评价趋势
            - 按餐段分析评价分布
            - 查看某个客户在不同商家的评价情况
            
            统计指标包括：
            评价总数、好评数、差评数、平均评分等。
            
            必填参数：
            beginDate：查询开始日期，格式 yyyy-MM-dd
            endDate：查询结束日期，格式 yyyy-MM-dd
            groupByType：统计分组维度
            
            companyIdList 或 merchantStoreIdList 至少传一个。
            
            可选参数：
            intervalNoList：餐段筛选条件
            
            groupByType 分组类型：
            
            0 = 按客户公司分组
            1 = 按商家分组
            2 = 按日期分组
            3 = 按餐段分组
            4 = 按客户公司 + 商家组合分组
            
            返回 JSON 格式：
            
            {
              "status": 1,
              "message": "success",
              "data": {
                "columns": [
                  "evaluateNum",
                  "goodEvaluateNum",
                  "badEvaluateNum",
                  "avgScore",
                  "companyId",
                  "companyShortName",
                  "intervalNo",
                  "useDate",
                  "merchantStoreId",
                  "merchantStoreShortName"
                ],
                "keyRows": {
                  "1001": [
                    120,
                    110,
                    10,
                    4.6,
                    1001,
                    "某公司",
                    2,
                    "2026-03-01",
                    2001,
                    "一号食堂"
                  ]
                }
              }
            }
            
            字段说明：
            
            status：
            数据状态  
            0 = 查询失败或参数错误  
            1 = 查询成功
            
            message：
            提示信息
            
            data：
            评价统计数据
            
            columns：
            返回列名列表
            
            keyRows：
            按统计维度组织的数据集合。
            
            key 的含义由 groupByType 决定：
            
            groupByType = 0  
            key = companyId（客户公司ID）
            
            groupByType = 1  
            key = merchantStoreId（商家ID）
            
            groupByType = 2  
            key = useDate（日期）
            
            groupByType = 3  
            key = intervalNo（餐段编号）
            
            groupByType = 4  
            key = companyId_merchantStoreId（客户ID+商家ID组合）
            
            value 为统计数据数组，字段顺序与 columns 一一对应。
            
            列字段说明：
            
            evaluateNum：
            评价总数量
            
            goodEvaluateNum：
            好评数量
            
            badEvaluateNum：
            差评数量
            
            avgScore：
            平均评分
            
            companyId：
            客户公司ID
            
            companyShortName：
            客户公司简称
            
            merchantStoreId：
            商家ID
            
            merchantStoreShortName：
            商家简称
            
            intervalNo：
            餐段编号
            
            useDate：
            用餐日期
            
            当没有查询到数据时：
            
            status = 1  
            keyRows = {}
            """)
    public ToolResultBlock queryEvaluateStatList(@ToolParam(name = "dataAnalyzeCondition", description = """
            评价统计查询参数对象。
            
            用于指定评价统计的查询条件，例如时间范围、客户范围、商家范围、餐段等。
            
            参数说明：
            
            beginDate：
            查询开始日期，格式 yyyy-MM-dd，例如 2026-03-01。
            
            endDate：
            查询结束日期，格式 yyyy-MM-dd，例如 2026-03-31。
            
            groupByType：
            统计分组类型（必传），决定统计结果按什么维度汇总。
            
            可选值：
            
            0 = 按客户公司统计评价数据  
            1 = 按商家统计评价数据  
            2 = 按日期统计评价数据  
            3 = 按餐段统计评价数据  
            4 = 按客户公司 + 商家组合统计  
            
            companyIdList：
            客户公司ID列表。
            用于指定统计哪些客户公司的评价数据。
            如果不传，则表示所有客户。
            
            merchantStoreIdList：
            商家ID列表。
            用于指定统计哪些商家的评价数据。
            如果不传，则表示所有商家。
            
            companyIdList 与 merchantStoreIdList 不能同时为空。
            
            intervalNoList：
            餐段筛选条件（可选）。
            
            餐段枚举值：
            
            1 = 早餐  
            2 = 午餐  
            3 = 晚餐  
            4 = 宵夜  
            5 = 下午茶  
            6 = 午晚餐  
            
            使用规则：
            
            1. beginDate 与 endDate 必须同时提供。
            2. groupByType 必须提供。
            3. companyIdList 与 merchantStoreIdList 至少传一个。
            4. intervalNoList 不传表示统计所有餐段。
            """) DataAnalyzeCondition dataAnalyzeCondition, ToolExecutionContext context) {
        return ToolResultBlock.text(evaluateClientService.queryEvaluateStatList(dataAnalyzeCondition, context));
    }

    @Tool(name = "get_complaint_stat_list", description = """
            查询指定时间范围内的投诉统计数据，并按指定维度进行汇总分析。
            
            使用场景：
            当需要分析客户或商家的投诉情况时使用该工具，例如：
            
            - 查看某些商家的投诉统计
            - 查看某些客户公司的投诉统计
            - 按日期分析投诉趋势
            - 按餐段分析投诉分布
            - 查看某个客户在不同商家的投诉情况
            
            统计指标包括：
            投诉数量、严重投诉数、普通投诉数、投诉扣分、免单数量等。
            
            必填参数：
            beginDate：查询开始日期，格式 yyyy-MM-dd
            endDate：查询结束日期，格式 yyyy-MM-dd
            groupByType：统计分组维度
            
            merchantStoreIdList 或 companyIdList 至少传一个。
            
            可选参数：
            intervalNoList：餐段筛选条件
            
            groupByType 分组类型：
            
            0 = 按客户公司分组  
            1 = 按商家分组  
            2 = 按日期分组  
            3 = 按餐段分组  
            4 = 按客户公司 + 商家组合分组  
            
            返回 JSON 格式：
            
            {
              "status": 1,
              "message": "success",
              "data": {
                "columns": [
                  "seriousComplaintNum",
                  "normalComplaintNum",
                  "totalDeductScore",
                  "freeNum",
                  "useDate",
                  "intervalNo",
                  "companyId",
                  "companyShortName",
                  "merchantStoreId",
                  "merchantStoreShortName"
                ],
                "keyRows": {
                  "1001": [
                    2,
                    5,
                    80,
                    1,
                    "2026-03-01",
                    2,
                    5001,
                    "某公司",
                    1001,
                    "一号食堂"
                  ]
                }
              }
            }
            
            字段说明：
            
            status：
            数据状态  
            0 = 查询失败或参数错误  
            1 = 查询成功
            
            message：
            提示信息
            
            data：
            投诉统计数据
            
            columns：
            返回列名列表
            
            keyRows：
            按统计维度组织的数据集合。
            
            key 的含义由 groupByType 决定：
            
            groupByType = 0  
            key = companyId（客户公司ID）
            
            groupByType = 1  
            key = merchantStoreId（商家ID）
            
            groupByType = 2  
            key = useDate（日期）
            
            groupByType = 3  
            key = intervalNo（餐段编号）
            
            groupByType = 4  
            key = companyId_merchantStoreId（客户ID+商家ID组合）
            
            value 为统计数据数组，字段顺序与 columns 一一对应。
            
            列字段说明：
            
            seriousComplaintNum：
            严重投诉数量
            
            normalComplaintNum：
            普通投诉数量
            
            totalDeductScore：
            投诉产生的总扣分或扣款金额
            
            freeNum：
            投诉产生的免单数量
            
            companyId：
            客户公司ID
            
            companyShortName：
            客户公司简称
            
            merchantStoreId：
            商家ID
            
            merchantStoreShortName：
            商家简称
            
            intervalNo：
            餐段编号
            
            useDate：
            用餐日期
            
            当没有查询到数据时：
            
            status = 1  
            keyRows = {}
            """)
    public ToolResultBlock queryComplaintStat(@ToolParam(name = "dataAnalyzeCondition", description = """
            投诉统计查询参数对象。
            
            用于指定投诉统计的查询条件，例如时间范围、客户范围、商家范围、餐段等。
            
            参数说明：
            
            beginDate：
            查询开始日期，格式 yyyy-MM-dd，例如 2026-03-01。
            
            endDate：
            查询结束日期，格式 yyyy-MM-dd，例如 2026-03-31。
            
            groupByType：
            统计分组类型（必传），决定统计结果按什么维度汇总。
            
            可选值：
            
            0 = 按客户公司统计投诉数据  
            1 = 按商家统计投诉数据  
            2 = 按日期统计投诉数据  
            3 = 按餐段统计投诉数据  
            4 = 按客户公司 + 商家组合统计  
            
            companyIdList：
            客户公司ID列表。
            用于指定统计哪些客户公司的投诉数据。
            如果不传，则表示统计所有客户。
            
            merchantStoreIdList：
            商家ID列表。
            用于指定统计哪些商家的投诉数据。
            如果不传，则表示统计所有商家。
            
            companyIdList 与 merchantStoreIdList 不能同时为空。
            
            intervalNoList：
            餐段筛选条件（可选）。
            
            餐段枚举值：
            
            1 = 早餐  
            2 = 午餐  
            3 = 晚餐  
            4 = 宵夜  
            5 = 下午茶  
            6 = 午晚餐  
            
            使用规则：
            
            1. beginDate 与 endDate 必须同时提供。
            2. groupByType 必须提供。
            3. companyIdList 与 merchantStoreIdList 至少传一个。
            4. intervalNoList 不传表示统计所有餐段。
            """) DataAnalyzeCondition dataAnalyzeCondition, ToolExecutionContext context) {
        return ToolResultBlock.text(complaintClientService.queryComplaintStat(dataAnalyzeCondition, context));
    }

    @Tool(name = "get_complaint_list", description = """
             查询指定时间范围内、餐段内、商家内某个或多个客户公司、或某个多个商家的投诉明细列表。
            
             使用场景：
             当需要查看某个或多个客户公司或某个多个商家在指定时间范围内、餐段内、商家内产生的投诉记录明细时使用该工具。
            
             必填参数：
             beginDate：查询开始日期，格式 yyyy-MM-dd
             endDate：查询结束日期，格式 yyyy-MM-dd
            可选参数：
             intervalNoList：餐段枚举值列表
             merchantStoreIdList:商家id列表，不传代表所有商家
             companyIdList：客户公司ID列表，不传代表所有客户
             主语：merchantStoreIdList和companyIdList不能同时为空
            
            
             返回 JSON 格式：
            
             {
               "status": 1,
               "message": "success",
               "data": {
                 "columns": [
                   "date",
                   "merchantStoreId",
                   "merchantStoreShortName",
                   "content",
                   "complaintLevel",
                   "deduct",
                   "whetherFree"
                 ],
                 "rows": [
                   [
                     "2026-03-01",
                     1001,
                     "望京店",
                     "配送太慢",
                     2,
                     50,
                     1
                   ]
                 ]
               }
             }
            
             字段说明：
            
             status：
             数据状态
             0 = 查询失败或参数错误  
             1 = 查询成功
            
             message：
             提示信息
            
             data：
             表格数据结构
            
             columns：
             返回列名列表
            
             rows：
             数据行列表，每一行数据的字段顺序与 columns 一一对应
            
             列字段说明：
            
             date：
             投诉发生日期，格式 yyyy-MM-dd
            
             merchantStoreId：
             商家门店ID
            
             merchantStoreShortName：
             商家门店简称
            
             content：
             投诉内容描述
            
             complaintLevel：
             投诉级别  
             1 = 一般投诉  
             2 = 严重投诉  
             其他值 = 未知
            
             deduct：
             扣分或扣款金额（Decimal）
            
             whetherFree：
             是否免单  
             1 = 是  
             其他值 = 否
            
             当没有查询到数据时：
             status = 1  
             rows 为空数组
            """)
    public ToolResultBlock queryComplaintDetailList(@ToolParam(name = "dataAnalyzeCondition", description = """
            数据分析查询条件对象。
            用于查询订单统计、评价统计、投诉统计等数据。
            
            参数说明：
            
            companyIdList：
            客户公司ID列表，必传参数
            
            merchantStoreIdList：
            商家ID列表。当统计维度为客户时，需要指定商家时，传入该参数，不传代表所有商家
            
            beginDate：
            查询开始日期，格式 yyyy-MM-dd。
            
            endDate：
            查询结束日期，格式 yyyy-MM-dd。
            
            intervalNoList：
            餐段筛选条件，可选。
            可传多个餐段。
            
            枚举值：
            1 = 早餐
            2 = 午餐
            3 = 晚餐
            4 = 宵夜
            5 = 下午茶
            6 = 午晚餐
            
            规则说明：
            1. beginDate 和 endDate 必须同时提供。
            2. companyIdList 与 merchantStoreIdList 根据查询维度选择其中一个。
            3. intervalNoList 不传表示查询全部餐段。
            """) DataAnalyzeCondition dataAnalyzeCondition, ToolExecutionContext context) {
        return ToolResultBlock.text(complaintClientService.queryCompanyComplaintDetailList(dataAnalyzeCondition, context));
    }

}
