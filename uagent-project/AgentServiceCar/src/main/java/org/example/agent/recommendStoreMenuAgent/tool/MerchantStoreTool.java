package org.example.agent.recommendStoreMenuAgent.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.example.agent.client.service.ufan.MerchantClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MerchantStoreTool {

    @Autowired
    private MerchantClientService merchantClientService;

    @Tool(
            name = "search_merchant_store",
            description = """
                    根据用户输入的商家名称关键字，搜索匹配的商家列表。
                    
                    ====================
                    【使用场景（必须遵守）】
                    ====================
                    1. 只要用户提到“商家名称/简称/门店”，必须优先调用本工具
                    2. 在未获得 merchantStoreId 之前，禁止调用其他商家相关工具
                    3. 模型本身没有商家数据，不能凭空回答，必须依赖本工具
                    
                    ====================
                    【输入参数】
                    ====================
                    name：商家名称关键字（支持简称 / 全称 / 模糊搜索）
                    
                    ====================
                    【返回结果】
                    ====================
                    返回JSON字符串，结构如下：
                    
                    {
                      "status": "unique_match | multiple_matches | no_match",
                      "message": "提示信息",
                      "data": {
                        "columns": [
                          "index",
                          "id",
                          "shortName",
                          "name",
                          "onlineTime",
                          "province",
                          "city",
                          "district",
                          "address",
                          "leader",
                          "phone"
                        ],
                        "rows": [
                          [1, 11, "海底捞", "海底捞火锅", "2024-01-01", "北京", "北京", "朝阳", "xxx路", "张三", "138xxxx"]
                        ]
                      }
                    }
                    
                    ====================
                    【字段说明】
                    ====================
                    
                    一、status（必须优先判断）
                    - unique_match：唯一匹配（可直接使用该商家ID继续查询详情）
                    - multiple_matches：多个匹配（必须让用户选择 index）
                    - no_match：未匹配到商家（需要提示用户重新输入）
                    
                    二、data（表格结构）
                    ⚠️ 这是“表格结构”，不是对象数组！
                    
                    columns 与 rows 一一对应：
                    - index：商家序号（用于用户选择）
                    - id：商家ID（后续调用 get_merchant_full_info 必须使用）
                    - shortName：商家简称
                    - name：商家全称
                    - onlineTime：上线时间
                    - province/city/district：省市区
                    - address：详细地址
                    - leader：负责人
                    - phone：联系电话
                    
                    示例解析方式：
                    columns = ["index","id","shortName",...]
                    rows = [
                      [1, 11, "海底捞", ...]
                    ]
                    
                    => 第1列是 index，第2列是 id，以此类推
                    
                    ====================
                    【重要交互规则（必须遵守）】
                    ====================
                    
                    1. 如果 status = "no_match"
                       → 回复用户“未找到商家”，并引导重新输入
                    
                    2. 如果 status = "multiple_matches"
                       → 必须列出 index + 商家名称，让用户选择
                       → 不允许自动选择
                    
                    3. 如果 status = "unique_match"
                       → 直接获取 id
                       → 然后调用 get_merchant_full_info 查询详情
                    
                    4. 不允许跳过本工具直接回答商家信息
                    
                    5. data 是表格结构，必须按 columns 解析，不是对象数组
                    
                    """
    )
    public ToolResultBlock searchMerchantStore(@ToolParam(name = "name", description = "用户提供的商家全称、简称、名称等关键字") String name, ToolExecutionContext context) {
        return ToolResultBlock.text(merchantClientService.searchStore(name, context));
    }

    @Tool(
            name = "get_merchant_full_info",
            description = """
                    根据商家ID查询商家的完整信息（包含基础信息、热门菜品、图片、评价）。
                    
                    ====================
                    【使用场景】
                    ====================
                    - 用户已经明确指定某一个具体商家时才允许调用
                    - 不可用于模糊查询或推荐场景
                    
                    ====================
                    【输入参数】
                    ====================
                    merchantStoreId：商家ID（必填，Long类型）
                    
                    ====================
                    【返回结果】
                    ====================
                    返回JSON字符串，结构如下：
                    
                    {
                      "status": 1,
                      "message": "",
                    
                      "shortname": "商家简称",
                      "name": "商家全称",
                      "onlineTime": "上线时间",
                      "province": "省",
                      "city": "市",
                      "district": "区",
                      "address": "详细地址",
                      "leader": "负责人",
                      "phone": "联系电话",
                      "mealType": "餐段类型",
                      "foodSeries": "菜系",
                      "merchantFoodType": "菜品类型",
                      "intervalNoStr": "主营餐段",
                    
                      "topGoods": {
                        "columns": [
                          "id",
                          "goodName",
                          "goodImgUrl",
                          "totalSale",
                          "goodScore",
                          "repurchaseCount"
                        ],
                        "rows": [
                          [1, "宫保鸡丁", "http://xxx.jpg", 1000, "4.8", 200]
                        ]
                      },
                    
                      "images": [
                        "图片URL1",
                        "图片URL2"
                      ],
                    
                      "reviews": [
                        "评价内容1",
                        "评价内容2"
                      ]
                    }
                    
                    ====================
                    【字段详细说明】
                    ====================
                    
                    一、基础信息（来自 MerchantStore）
                    - shortname：商家简称
                    - name：商家全称
                    - onlineTime：上线时间
                    - province/city/district：省市区
                    - address：地址
                    - leader：负责人
                    - phone：联系电话
                    - mealType：餐段类型（如早/中/晚餐）
                    - foodSeries：菜系（如川菜、粤菜等）
                    - merchantFoodType：菜品类型
                    - intervalNoStr：主营餐段描述
                    
                    二、热门菜品（topGoods，来自 Good）
                    ⚠️ 这是“表格结构”，不是对象数组！
                    
                    字段说明（columns 顺序对应 rows）：
                    - id：菜品ID
                    - goodName：菜品名称
                    - goodImgUrl：菜品图片URL（完整路径）
                    - totalSale：总销量
                    - goodScore：评分（字符串类型，如 "4.8"）
                    - repurchaseCount：复购数
                    
                    示例解析方式：
                    columns = ["id","goodName",...]
                    rows = [
                      [1,"宫保鸡丁",...]
                    ]
                    
                    => 第1列对应 id，第2列对应 goodName，以此类推
                    
                    三、images
                    - 商家现场图片URL列表（已拼接完整域名）
                    
                    四、reviews
                    - 最近180天内的商品评价内容（仅文本列表）
                    
                    ====================
                    【重要约束（必须遵守）】
                    ====================
                    1. 必须先判断 status == 1 才能使用数据
                    2. topGoods 是二维表结构，必须结合 columns 解析，不是对象数组
                    3. 不要臆造字段，必须严格按照返回JSON解析
                    4. 字段可能为空，需要做空值判断
                    5. goodScore 是字符串，不是数值类型
                    """
    )
    public ToolResultBlock getMerchantFullInfo(
            @ToolParam(name = "merchantStoreId", description = "商家ID") Long merchantStoreId,
            ToolExecutionContext toolExecutionContext
    ) {
        return ToolResultBlock.text(merchantClientService.getMerchantFullInfo(merchantStoreId, toolExecutionContext));
    }

    @Tool(name = "get_store_recommend_menu", description = """
            获取指定商家的推荐菜品列表，用于生成或分析下周菜单推荐。
            
            ## 适用场景
            当用户询问以下问题时使用：
            - 查询商家的推荐菜品
            - 生成下周菜单推荐
            - 分析哪些菜品适合上菜单
            - 查看菜品评分、销量、评价内容
            - 分析菜品表现（销量、评分、复购率等）
            
            ## 输入参数
            merchantStoreId: 商家ID
            beginDate：菜单开始日期(周一)，格式yyyy-MM-dd
            endDate：菜单截止日期(周日)，格式yyyy-MM-dd
            
            ## 返回数据格式(JSON)
            
            {
              "status": 1,
              "message": "找到N个下周菜单推荐菜品",
              "dateRange": "下周排期时间范围",
              "columns": [
                "id",
                "goodName",
                "foodType",
                "totalSale",
                "goodScore",
                "goodFoodDesc",
                "tastePositiveCount",
                "averageScore",
                "evaluateContentList",
                "serviceDesc",
                "positiveRate",
                "repurchaseRate"
              ],
              "datas": [
                [字段值列表]
              ],
              "goodNum": 菜品数量,
              "lastWeekGoodNum": 上周上过菜单的菜品数量
            }
            
            ## 字段说明
            
            id: 菜品ID
            goodName: 菜品名称
            foodType: 菜品分类
            totalSale: 销量
            goodScore: 综合评分（新菜品可能为"-"）  
            goodFoodDesc: 是否是好吃菜品 (好吃菜品=是 非好吃菜品=否)  
            tastePositiveCount: 好评数  
            averageScore: 评分    
            evaluateContentList: 用户评价内容列表  
            serviceDesc: 上周是否供餐 (上周已供餐=是 上周未供餐=否)
            positiveRate:好评率
            repurchaseRate：复购率
            
            ## 说明
            - 菜品已按综合评分降序排序
            - 新菜品可能没有评分
            - 可结合销量、评分、复购率和评价内容进行推荐分析
            """)
    public ToolResultBlock getStoreRecommendMenu(
            @ToolParam(name = "merchantStoreId", description = "商家id") Long merchantStoreId,
            @ToolParam(name = "beginDate", description = "菜单开始日期") String beginDate,
            @ToolParam(name = "endDate", description = "菜单截止日期") String endDate,
                                                 ToolExecutionContext context) {
        return ToolResultBlock.text(merchantClientService.getStoreRecommendMenu(merchantStoreId,beginDate,endDate, context));
    }

}
