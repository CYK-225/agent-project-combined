package com.cyk.Service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;

import com.cyk.events.EnsDataSyncEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ENScanApiService {

    @Value("${enscan.api.url}")
    private String enscanApiUrl;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    // 全局车轮战策略配置
    private static final List<String> DATA_SOURCES = Arrays.asList("rb");
    private volatile int globalSourceIndex = 0;


    public Map<String, Object> getCompanyInfo(String companyName) {
        Map<String, Object> finalResult = new HashMap<>();

        int sourceSwitchCount = 0;
        int totalSources = DATA_SOURCES.size();

        while (sourceSwitchCount < totalSources) {
            String activeSource = DATA_SOURCES.get(globalSourceIndex);
            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    // --- 步骤 1：模糊搜索获取列表 ---
                    String advanceUrl = enscanApiUrl + "/api/pro/advance_filter";
                    Map<String, Object> param1 = new HashMap<>();
                    param1.put("name", companyName);
                    param1.put("type", activeSource);

                    String url1 = HttpUtil.urlWithForm(advanceUrl, param1, StandardCharsets.UTF_8, false);
                    log.info("▶️ 正在查询 [{}] | 当前战力源: [{}] | 第 {} 次尝试", companyName, activeSource, attempt);

                    String res1 = HttpRequest.get(url1).timeout(90000).execute().body();

                    if (res1 == null || res1.isEmpty()) {
                        log.info("未查到企业数据 (接口返回空): [{}]", companyName);
                        return finalResult;
                    }

                    Map<String, Object> map1 = objectMapper.readValue(res1, new TypeReference<Map<String, Object>>() {});
                    Object codeObj = map1.get("code");
                    if (codeObj != null && !codeObj.toString().equals("200") && !codeObj.toString().equals("0")) {
                        throw new RuntimeException("风控状态码: " + codeObj);
                    }

                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) map1.get("data");
                    if (dataList == null || dataList.isEmpty()) {
                        log.warn("未查到企业数据 (data为空): [{}]", companyName);
                        return finalResult;
                    }

                    // 提取企业 ID
                    Map<String, Object> firstItem = dataList.get(0);
                    String pid = null;
                    String[] possibleIdKeys = {"pid", "id", "entid", "entId", "companyId"};
                    for (String key : possibleIdKeys) {
                        Object val = firstItem.get(key);
                        if (val != null) {
                            String strVal = String.valueOf(val).trim();
                            if (!strVal.isEmpty() && !"null".equalsIgnoreCase(strVal)) {
                                pid = strVal;
                                break;
                            }
                        }
                    }

                    if (pid == null) {
                        log.error("⚠️ 无法从源 [{}] 提取有效的企业ID。返回的原始字段为: {}", activeSource, firstItem.keySet());
                        throw new RuntimeException("解析异常：无法获取企业唯一标识ID");
                    }

                    // --- 步骤 2：获取详情 ---
                    Thread.sleep(1500);
                    String baseInfoUrl = enscanApiUrl + "/api/pro/get_base_info";
                    Map<String, Object> paramBase = new HashMap<>();
                    paramBase.put("name", pid);
                    paramBase.put("type", activeSource);

                    String urlBase = HttpUtil.urlWithForm(baseInfoUrl, paramBase, StandardCharsets.UTF_8, false);
                    String resBase = HttpRequest.get(urlBase).timeout(90000).execute().body();
                    Map<String, Object> mapBase = objectMapper.readValue(resBase, new TypeReference<Map<String, Object>>() {});

                    if (mapBase.get("code") != null && (Integer) mapBase.get("code") == 200) {
                        Map<String, Object> detailData = (Map<String, Object>) mapBase.get("data");
                        if (detailData != null && !detailData.isEmpty()) {
                            detailData.put("entName", cleanName(String.valueOf(detailData.get("entName"))));
                            finalResult.put("enterprise_info", detailData);

                            // 【调用封装组件】：1行代码搞定安全映射与入库
                            eventPublisher.publishEvent(new EnsDataSyncEvent(this, companyName, detailData));
                        } else {
                            throw new RuntimeException("获取详情成功，但详情数据(data)为空");
                        }
                    } else {
                        // 降级兜底处理
                        firstItem.put("entName", cleanName(String.valueOf(firstItem.get("entName"))));
                        finalResult.put("enterprise_info", firstItem);

                        // 【调用封装组件】：降级数据也一样落库
                        eventPublisher.publishEvent(new EnsDataSyncEvent(this, companyName, firstItem));
                    }

                    return finalResult;

                } catch (Exception e) {
                    log.warn("⚠️ 源 [{}] 异常 (企业: [{}], 第 {} 次): {}", activeSource, companyName, attempt, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(10000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return finalResult;
                        }
                    }
                }
            }

            log.error("❌ 数据源 [{}] 已阵亡！", activeSource);
            synchronized (this) {
                if (activeSource.equals(DATA_SOURCES.get(globalSourceIndex))) {
                    globalSourceIndex = (globalSourceIndex + 1) % totalSources;
                    log.warn("🔄 已切换为健康源: [{}] ！！！", DATA_SOURCES.get(globalSourceIndex));
                }
            }
            sourceSwitchCount++;
        }

        log.error("🛑 所有数据源全部阵亡！企业 [{}] 获取失败。", companyName);
        return finalResult;
    }

    private String cleanName(String name) {
        if (name == null || "null".equals(name)) return "";
        return name.replace("⌈", "").replace("⌋", "");
    }
}