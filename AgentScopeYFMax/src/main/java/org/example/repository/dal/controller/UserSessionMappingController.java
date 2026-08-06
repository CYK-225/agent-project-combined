package org.example.repository.dal.controller;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.github.linpeilie.Converter;

import jakarta.annotation.Resource;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.agentScope.util.session.PostgresSession;
import org.example.repository.dal.controller.VO.ChatMessage;
import org.example.repository.dal.controller.VO.ChatMessageVO;
import org.example.repository.dal.entity.AgentSessionDO;
import org.example.repository.dal.entity.UserSessionMappingEntity;
import org.example.repository.dal.mapper.AgentScopeSessionsMapper;
import org.example.repository.dal.service.IUserSessionMappingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户会话关联表 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/userSessionMapping")
public class UserSessionMappingController {

    @Autowired
    private IUserSessionMappingService userSessionMappingService;
    @Autowired
    private AgentScopeSessionsMapper agentScopeSessionsMapper;
    @Resource
    private AgentPoolManager agentPoolManager;
    @Resource
    private PostgresSession postgresSession;

    private Converter converter=new Converter();


    /**
     * 获取现有的agent分组，包含agent名称和描述，并具有排除分组的功能（用于区分，不同类别的Agent，比如辅助性的，测试中的，正式上线的）
     *
     */
    @PostMapping("/getAgentGroups")
    public Map<String, Map<String, String>> getAgentGroups(@RequestParam(required = false) List<String> groupsName){
        return agentPoolManager.getGroupedAgentDescriptionsExcluding(groupsName);
    }
    /**
 * 返回标题
 * 根据前端传入的数据返回一个标题
 */
@PostMapping("/newTitle")
public String createNewTitle(@RequestParam String sessionId,@RequestParam String userId ,@RequestParam String userInput){
    Msg user= Msg.builder()
            .name("user")
            .role(MsgRole.USER)
            .textContent(userInput)
            .build();
    String title=  agentPoolManager.getAgent("UserQuestion").call(user).block().getTextContent() ;
    if( update(UserSessionMappingEntity.builder().sessionid(sessionId).userid(userId).title(title).build())){
        return title;
    } else {
        throw new RuntimeException("更新失败请重试");
    }
}

    /**
     * 添加 用户会话关联表
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/getThreadID")
    public String getThreadID(@RequestParam String userID) {
        Snowflake snowflake = IdUtil.getSnowflake(1, 1);

// 生成 ID
        UserSessionMappingEntity userSessionMapping =  new UserSessionMappingEntity();
        userSessionMapping.setUserid(userID);
        userSessionMapping.setSessionid(snowflake.nextIdStr());

        if(userSessionMappingService.save(userSessionMapping)){
            return userSessionMapping.getSessionid();
        }else{
           throw new RuntimeException("创建失败请重试");
        }
    }



    /**
     * 根据主键删除用户会话关联表
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        if(userSessionMappingService.removeById(id)){
            postgresSession.deleteBySessionId((String) id);
            return true;
        }else {
            return false;
        }


    }


    /**
     * 根据主键更新用户会话关联表
     *
     * @param userSessionMapping 用户会话关联表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody UserSessionMappingEntity userSessionMapping) {
        return userSessionMappingService.updateById(userSessionMapping);
    }


    /**
     * 查询所有用户会话关联表（按更新时间倒序）
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<UserSessionMappingEntity> list() {
        // 使用 QueryWrapper 增加排序：按 update_time 降序排列
        QueryWrapper query = QueryWrapper.create()
                .orderBy(UserSessionMappingEntity::getUpdateTime, false);
        return userSessionMappingService.list(query);
    }


    /**
     * 根据用户会话关联表主键获取详细信息。
     *
     * @param id userSessionMapping主键
     * @return 用户会话关联表详情
     */
    @GetMapping("/getInfo/{id}")
    public UserSessionMappingEntity getInfo(@PathVariable Serializable id) {
        return userSessionMappingService.getById(id);
    }


    /**
     * 分页查询用户会话关联表（按更新时间倒序）
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<UserSessionMappingEntity> page(Page<UserSessionMappingEntity> page) {
        // 同样增加排序逻辑
        QueryWrapper query = QueryWrapper.create()
                .orderBy(UserSessionMappingEntity::getUpdateTime, false);
        return userSessionMappingService.page(page, query);
    }
    /**
     * 获取回写数据
     */

    @GetMapping("/historyMsg")
    public Page<ChatMessageVO> historyMsg(@RequestParam int size, @RequestParam int pageNumber, @RequestParam String sessionId){
        Page<AgentSessionDO> page = new Page<>(pageNumber, size);
        QueryWrapper query=QueryWrapper.create()
                .select(AgentSessionDO::getSessionId,AgentSessionDO::getStateData,AgentSessionDO::getItemIndex)
                .from(AgentSessionDO.class)
                .eq(AgentSessionDO::getSessionId,sessionId)
                .ne(AgentSessionDO::getStateKey,"autoContextMemory_workingMessages")
                .orderBy(AgentSessionDO::getItemIndex,true);

         agentScopeSessionsMapper.paginateAs(page, query, AgentSessionDO.class);
        return
                convertPage(page);
    }

    /**
     * 获取会话报错数据
     * @param sessionId
     * @return
     */
    @GetMapping("/getErrorMsg")
    public List<String> getErrorMsg(@RequestParam String sessionId ){
// 重新构造 QueryWrapper
        QueryWrapper query = QueryWrapper.create()
                // 1. 直接 select 提取出的 id
                .select("state_data::json->>'id' AS id")
                .from(AgentSessionDO.class)
                .eq(AgentSessionDO::getSessionId, sessionId)
                // 2. 增加 JSON 内部字段的条件过滤 (role 为 USER)
                .and("state_data::json->>'role' = ?", "USER");

// 3. 续写 mapper 调用
// 因为 SQL 只返回了一个字符串类型的 id 列，我们可以直接用 selectObjectListByQueryAs 映射为 String 列表


        return agentScopeSessionsMapper.selectObjectListByQueryAs(query, String.class);
    }


    public Page<ChatMessageVO> convertPage(Page<AgentSessionDO> doPage) {

        // 1. 初始化 VO 的分页对象，把 DO 分页的元数据（当前页、页大小、总条数）拷贝过去
        Page<ChatMessageVO> voPage = new Page<>(
                doPage.getPageNumber(),
                doPage.getPageSize(),
                doPage.getTotalRow()
        );

        List<AgentSessionDO> doList = doPage.getRecords();

        // 如果查出来没数据，直接返回空的分页对象
        if (doList == null || doList.isEmpty()) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }

        // 2. 初始化 VO 列表，指定容量避免扩容开销
        List<ChatMessageVO> voList = new ArrayList<>(doList.size());

        // 3. 遍历 DO 列表进行组装
        for (AgentSessionDO dataObj : doList) {
            ChatMessageVO viewObj = new ChatMessageVO();

            // 赋值 ID
            viewObj.setId(dataObj.getSessionId());

            // 赋值 Index

            viewObj.setIndex(dataObj.getItemIndex());

            // 赋值并解析 ChatMsg
            String rawMsg = dataObj.getStateData();
            if (rawMsg != null && !rawMsg.trim().isEmpty()) {
                // 一行代码使用 FastJSON 完成 String -> 对象的序列化
                ChatMessage parsedMsg = JSON.parseObject(rawMsg, ChatMessage.class);
                viewObj.setChatMessage(parsedMsg);
            }

            voList.add(viewObj);
        }

        // 4. 将转换好的列表塞回 VO 分页对象
        voPage.setRecords(voList);

        return voPage;
    }

}