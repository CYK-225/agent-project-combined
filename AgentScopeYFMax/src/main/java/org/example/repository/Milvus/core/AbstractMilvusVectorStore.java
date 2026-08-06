package org.example.repository.Milvus.core;



import com.alibaba.fastjson2.JSON;

import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import org.example.repository.Milvus.model.BaseMilvusMeta;
import org.example.repository.Milvus.utils.MilvusFilterTemplateBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用 Milvus 向量存储服务基类
 * @param <T> 具体的实体类型，必须继承 BaseMilvusMeta
 */
@Slf4j
public abstract class AbstractMilvusVectorStore<T extends BaseMilvusMeta> {

    protected final MilvusCrudHelper milvusCrudHelper;
    protected final String collectionName;
    protected final String databaseName;
    private final Class<T> entityType;

    /**
     * 构造函数
     * @param milvusCrudHelper Milvus 工具类
     * @param collectionName 集合名称
     * @param databaseName 数据库名称
     * @param entityType 实体类的 Class 对象 (用于反序列化)
     */
    protected AbstractMilvusVectorStore(MilvusCrudHelper milvusCrudHelper,
                                        String collectionName,
                                        String databaseName,
                                        Class<T> entityType) {
        this.milvusCrudHelper = milvusCrudHelper;
        this.collectionName = collectionName;
        this.databaseName = databaseName;
        this.entityType = entityType;
    }

    // ==================== 增 / 改 ====================

    /**
     * 批量新增
     */
    public List<Object> add(List<T> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> fieldsList = entities.stream()
                .map(BaseMilvusMeta::toMilvusMap)
                .collect(Collectors.toList());

        return milvusCrudHelper.insert(collectionName, databaseName, fieldsList)
                .getPrimaryKeys(); // 返回主键列表
    }

    /**
     * 批量 Upsert (存在则更新，不存在则插入)
     */
    public List<Object> upsert(List<T> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> fieldsList = entities.stream()
                .map(BaseMilvusMeta::toMilvusMap)
                .collect(Collectors.toList());

        return milvusCrudHelper.upsert(collectionName, databaseName, fieldsList)
                .getPrimaryKeys();
    }

    // ==================== 删 ====================

    /**
     * 根据 ID 列表删除
     */
    public Boolean deleteByIds(@NotNull List<Object> idList) {
        if (idList.isEmpty()) return false;
        return milvusCrudHelper.deleteByIds(collectionName, databaseName, idList)
                .getDeleteCnt() >= idList.size(); // 注意：Milvus 有时返回的 deleteCnt 只是估算，视版本而定
    }

    /**
     * 根据 Filter 表达式删除
     */
    public Boolean deleteByFilter(String filter) {
        return milvusCrudHelper.deleteByFilter(collectionName, databaseName, filter)
                .getDeleteCnt() >= 0;
    }

    // ==================== 查 (Query) ====================

    /**
     * 根据 ID 查询
     */
    public List<T> queryByIds(List<Object> ids, List<String> outputFields) {
        // limit 设为 ids 的大小
        QueryResp resp = milvusCrudHelper.queryByIds(collectionName, ids, outputFields, (long) ids.size(), databaseName);
        return convertQueryResp(resp);
    }
    /**
     * 根据ID查询全部字段
     */
    public List<T> queryAllByIds(List<Object> ids) {
        // limit 设为 ids 的大小
        QueryResp resp = milvusCrudHelper.queryAllByIds(collectionName, ids,  (long) ids.size(), databaseName);
        return convertQueryResp(resp);
    }

    /**
     * 根据 Filter 查询 (支持分页)
     */
    public List<T> query(MilvusFilterTemplateBuilder.TemplateResult filter, List<String> outputFields, Long limit, Long offset) {
        QueryResp resp = milvusCrudHelper.query(collectionName, filter, outputFields, limit, offset, databaseName);
        return convertQueryResp(resp);
    }

    /**
     * 查询所有 (实际是带 limit 的 filter 遍历，filter 默认为空字符串表示全表? 视 helper 实现而定，通常传 "")
     */
    public List<T> queryAll(MilvusFilterTemplateBuilder.TemplateResult filter, Long limit, Long offset) {
        QueryResp resp = milvusCrudHelper.queryAll(collectionName, filter, limit, offset, databaseName);
        return convertQueryResp(resp);
    }



    // ==================== 搜 (Search) ====================

    /**
     * 混合搜索 (Hybrid Search)
     */
    public List<T> hybridSearch(MilvusCrudHelper.HybridSearch hybridSearch,Boolean UseKWS) {
        SearchResp resp = milvusCrudHelper.hybridSearch(hybridSearch, databaseName, collectionName,UseKWS);
        return convertSearchResp(resp);
    }

    /**
     * 自定义混合搜索 (DIY)
     */
    public List<T> hybridSearchDIY(MilvusCrudHelper.HybridSearch hybridSearch,Boolean UseKWS) {
        SearchResp resp = milvusCrudHelper.hybridSearchDIY(hybridSearch, databaseName, collectionName,UseKWS);
        return convertSearchResp(resp);
    }

    /**
     * 统一搜索 (Unified Search)
     */
    public List<T> performUnifiedSearch(MilvusCrudHelper.UnifiedSearchRequest request) {
        SearchResp resp = milvusCrudHelper.performUnifiedSearch(request, databaseName, collectionName);
        return convertSearchResp(resp);
    }

    // ==================== 内部转换工具 ====================

    /**
     * 将 QueryResp 转换为实体列表
     */
    protected List<T> convertQueryResp(QueryResp resp) {
        if (resp == null || resp.getQueryResults() == null) return Collections.emptyList();

        return resp.getQueryResults().stream()
                .map(r -> JSON.to(entityType, r.getEntity())) // 使用 Fastjson2 转换
                .collect(Collectors.toList());
    }

    /**
     * 将 SearchResp 转换为实体列表，并注入 Score
     */
    protected List<T> convertSearchResp(SearchResp resp) {
        if (resp == null || resp.getSearchResults().isEmpty()) return Collections.emptyList();

        // 这里取 getSearchResults().get(0) 是因为我们通常只处理单条查询向量对应的结果集
        return resp.getSearchResults().getFirst().stream()
                .map(r -> {
                    // 1. Map 转 Entity (使用 Fastjson2)
                    T entity = JSON.to(entityType, r.getEntity());
                    // 2. 注入 Score
                    entity.setScore(r.getScore());
                    return entity;
                })
                .collect(Collectors.toList());
    }
}
