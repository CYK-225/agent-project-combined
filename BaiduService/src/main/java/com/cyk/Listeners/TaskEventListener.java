package com.cyk.Listeners;

import com.cyk.Service.ICompaniesService;
import com.cyk.Service.ICategoriesService;
import com.cyk.Utils.EnsDataSyncComponent;
import com.cyk.Utils.SseEmitterManager;
import com.cyk.events.BusinessDataRequestEvent;
import com.cyk.events.CompanyProcessEvent;
import com.cyk.events.EnsDataSyncEvent;
import com.cyk.events.SsePushEvent;
import com.cyk.Enity.table.CompaniesEntity;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cyk.Utils.MapObjectUtil.*;

@Slf4j
@Component
public class TaskEventListener {

    @Resource
    private ICompaniesService iCompaniesService;
    @Resource
    private ICategoriesService iCategoriesService;
    @Resource
    private EnsDataSyncComponent ensDataSyncComponent;
    @Resource
    private SseEmitterManager sseEmitterManager;

    // 任务访问的目标网站地址（由 yml task.web-address 配置）
    @Value("${task.web-address.riskbird}")
    private String riskbirdWebAddress;

    @Value("${task.web-address.zhipin}")
    private String zhipinWebAddress;

    // =====================================================================
    // 1. 监听: ENS 数据抓取完成，进行同步落库
    // =====================================================================
    @Async // 异步执行，不阻塞 Base 模块的任务线程
    @EventListener
    public void handleEnsDataSyncEvent(EnsDataSyncEvent event) {
        log.info("[事件监听] 收到 ENS 数据同步请求，企业: {}", event.getCompanyName());
        ensDataSyncComponent.syncToDatabase(event.getCompanyName(), event.getDataMap());
    }

    // =====================================================================
    // 2. 监听: SSE 统一推送
    // =====================================================================
    @Async
    @EventListener
    public void handleSsePushEvent(SsePushEvent event) {
        if (event.getPayload() != null) {
            // 如果传了 payload，直接发 json
            sseEmitterManager.sendJsonEventByTaskId(event.getTaskId(), event.getPayload());
        } else {
            // 发送普通消息
            sseEmitterManager.sendEventByTaskId(event.getTaskId(), event.getCompanyName(), event.getMessage(), event.getData());
        }
    }

    // =====================================================================
    // 3. 监听: 任务状态更新 / 数据库写库请求 (对应原来的 saveRightEntity 和 updateCompanyStatus)
    // =====================================================================
    @Async
    @EventListener
    public void handleCompanyProcessEvent(CompanyProcessEvent event) {
        String action = event.getAction();
        String companyId = event.getCompanyId();
        
        switch (action) {
            case "UPDATE_DATA":
                log.info("[事件监听] 更新企业数据，ID: {}", companyId);
                Map<String, Object> dataMap = event.getDataMap();
                if (dataMap != null && !dataMap.isEmpty()) {
                    // 1. 用空实体接收 dataMap 映射，只有匹配字段会被赋值
                    CompaniesEntity partial = new CompaniesEntity();
                    partial = fillExistingBean(dataMap, partial);
                    // 2. 取出非空字段（即 dataMap 成功映射的字段）
                    Map<String, Object> nonNullFields = getNonNullPropertiesMap(partial);
                    if (!nonNullFields.isEmpty()) {
                        // 3. 用 UpdateChain 只更新这些字段，绝不触碰 info_status
                        UpdateChain<CompaniesEntity> chain = UpdateChain.of(CompaniesEntity.class);
                        for (Map.Entry<String, Object> entry : nonNullFields.entrySet()) {
                            String column = entry.getKey().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
                            chain.set(new QueryColumn(column), entry.getValue());
                        }
                        chain.where(CompaniesEntity::getUid).eq(companyId).update();
                    }
                }
                break;
            case "UPDATE_STATUS_1":
                log.info("[事件监听] 更新企业状态为已处理 (单任务)");
                iCompaniesService.updateIsProcessed(companyId, 1);
                break;
            case "UPDATE_STATUS_2":
                log.info("[事件监听] 更新企业状态为已处理 (全部任务)");
                iCompaniesService.updateIsProcessed(companyId, 2);
                break;
            default:
                log.warn("[事件监听] 未知的 CompanyProcess 动作: {}", action);
        }
    }

    // =====================================================================
    // 4. 监听: (同步请求) 索要待抓取字段 needFind 和提示词 mission
    // 注意：这里不能加 @Async，因为 Base 模块需要等它执行完拿到结果继续往下走！
    // =====================================================================
    @EventListener
    public void handleBusinessDataRequest(BusinessDataRequestEvent event) {
        log.info("[事件监听 - 同步] 收到创建/重试任务数据请求，企业: {}", event.getCompanyName());
        
        String companyName = event.getCompanyName();
        String taskType = event.getTaskType();
        
        // 1. 查询公司实体
        CompaniesEntity companiesEntity = iCompaniesService.getName(companyName);
        if (companiesEntity == null) {
            log.warn("[事件监听] 数据库中未找到公司: {}", companyName);
            return;
        }
        
        // 2. 将 ID 传回给 Base 模块
        event.setCompanyId(companiesEntity.getUid());

        // 3. 获取公司字段 Map
        Map<String, Object> companiesEntityNow = getAllPropertiesMap(companiesEntity);
        Map<String, Object> needFind = new HashMap<>();
        String mission = "";
        String webAddress = null;

        // 定义可获取字段
        List<String> nonAiObtainableFields = Arrays.asList("industry", "establishedYears", "registeredCapital", "keyContact");
        List<String> aiObtainableFields = Arrays.asList("genderRatio", "ageRatio", "avgSalary");

        // 4. 根据类型匹配
        switch (taskType) {
            case "ENS" -> {
                needFind = filterMapByKeysAndNullValue(companiesEntityNow, nonAiObtainableFields, true);
                mission = "rb";
                webAddress = riskbirdWebAddress;
            }
            case "AI" -> {
                if (event.isRetry()) {
                    // 补全遗漏："AI重试" 并拼接缺失的字段列表
                    mission = iCategoriesService.getCategoryByName("AI重试") + event.getFieldList();
                } else {
                    needFind = filterMapByKeysAndNullValue(companiesEntityNow, aiObtainableFields, true);
                    mission = iCategoriesService.getCategoryByName("获取性别信息");
                }
                webAddress = zhipinWebAddress;
            }
            case "SPECIAL" -> {
                // 补全遗漏：原本 onTaskFailed 里独有的 SPECIAL 重试逻辑
                if (event.isRetry()) {
                    mission = iCategoriesService.getCategoryByName("特殊重试") + event.getFieldList();
                }
            }
            case "OTHER" -> {
                needFind = filterMapByKeysAndNullValue(companiesEntityNow, event.getFieldList(), true);
                mission = iCategoriesService.getCategoryByName("获取性别信息");
            }
        }
        
        // 5. 组装完毕，回填到 event 中，Base 模块就能直接使用了！
        event.setNeedFind(needFind);
        event.setMission(mission);
        event.setWebAddress(webAddress);
    }
}