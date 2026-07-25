package org.example.agent.recommendStoreMenuAgent.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.example.agent.client.service.ufan.CompanyClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CompanyTool {

    @Autowired
    private CompanyClientService companyClientService;

    @Tool(name = "query_company_list", description = """
            根据客户或公司名称查询符合条件的列表。
            
            使用场景：
            当需要根据公司名称、客户名称关键字查找客户公司信息时使用该工具。
            
            参数说明：
            name：客户公司名称或名称关键字（支持模糊匹配）
            
            返回 JSON 格式：
            
            {
              "status": 1,
              "message": "success",
              "data": {
                "columns": [
                  "id",
                  "name",
                  "shortName",
                  "city",
                  "province",
                  "scale",
                  "mealCount",
                  "onlineStatus"
                ],
                "rows": [
                  [
                    1,
                    "北京某某科技有限公司",
                    "某某科技",
                    "北京",
                    "北京",
                    500,
                    300,
                    0
                  ]
                ]
              }
            }
            
            字段说明：
            
            status：
            数据状态  
            0 = 查询失败或未找到数据  
            1 = 查询成功
            
            message：
            提示信息
            
            data：
            客户公司列表数据
            
            columns：
            返回列名列表
            
            rows：
            数据行列表，每一行数据的字段顺序与 columns 一一对应
            
            列字段说明：
            
            id：
            客户公司ID
            
            name：
            公司全称
            
            shortName：
            公司简称
            
            city：
            所在城市
            
            province：
            所在省份
            
            scale：
            公司规模（人数规模）
            
            mealCount：
            用餐人数
            
            onlineStatus：
            公司上线状态  
            0 = 上线  
            1 = 下线  
            2 = 删除  
            3 = 预上线
            
            当没有查询到数据时：
            status = 0
            data 不返回
            """)
    public ToolResultBlock queryCompanyList(@ToolParam(name = "name", description = "客户的简称、全称、名称") String name, ToolExecutionContext context) {
        return ToolResultBlock.text(companyClientService.queryCompanyList(name, context));
    }

}
