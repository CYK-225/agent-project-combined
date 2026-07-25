package org.example.agent.user.tool;


import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.linpeilie.Converter;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


import org.example.agent.user.dal.entity.UserCompanyRelationEntity;
import org.example.agent.user.dal.entity.UserInfoEntity;
import org.example.agent.user.dal.service.CustomerPreferenceDoService;
import org.example.agent.user.dataModel.UserPreferences;

import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.example.agent.user.dal.entity.table.UserCompanyRelationEntityTableDef.USER_COMPANY_RELATION_ENTITY;
import static org.example.agent.user.dal.entity.table.UserInfoEntityTableDef.USER_INFO_ENTITY;

@Slf4j
@Service
@Data
public class UserSqlTool {

    @Resource
    CustomerPreferenceDoService customerPreferenceDoService;
    private Converter converter=new Converter();



    @Tool(name = "sql_select_tool", description = "这是一个根据公司id，查询用户喜好信息的工具，输入是公司的ID，输出是查询结果")
    public String sqlTool(@ToolParam(
            name = "company_id", description = "公司的ID")
    String companyId) {
       Long  newcompanyId=Long.valueOf(companyId);
        try{
        System.out.println(STR."sqlTool被调用了，输入的公司ID是：\{newcompanyId}");
        List<Long> companyUserIds=customerPreferenceDoService.getUserCompanyRelationService().list(QueryWrapper.create()
                        .from(UserCompanyRelationEntity.class)
                        .select(USER_COMPANY_RELATION_ENTITY.USER_ID)
                         .where(USER_COMPANY_RELATION_ENTITY.COMPANY_ID.eq(newcompanyId))
                )
                .stream().map(UserCompanyRelationEntity::getUserId)
                .toList();

        List<UserInfoEntity> userInfoEntities=customerPreferenceDoService.getUserInfoService().getMapper()
                .selectListWithRelationsByQuery(QueryWrapper.create()
                        .from(USER_INFO_ENTITY)
                        .where(USER_INFO_ENTITY.USER_ID.in(companyUserIds))
                );
            List<UserPreferences> userPreferencesList=converter.convert(userInfoEntities, UserPreferences.class);

        //获取公司用户信息
        if (userPreferencesList.isEmpty()){
            return "没有查询到相关用户信息，请停止过度思考立刻让用户确认";
        }
        ZeroShotPrompt zero=new ZeroShotPrompt();
            userPreferencesList.forEach(zero::extractEasyBean);
        String result=zero.render();
            System.out.println(STR."sqlTool查询完成了，查询结果是：\{result}");
        return result;}catch (Exception e){
            log.error("sqlTool执行出错了，错误信息是：{}",e.getMessage());
            return "执行出错了，错误信息是："+e.getMessage();
        }
    }


}
