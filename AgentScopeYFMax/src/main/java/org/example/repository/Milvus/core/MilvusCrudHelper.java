package org.example.repository.Milvus.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.param.MetricType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.repository.Milvus.embedding.EmbeddingUtils;
import org.example.repository.Milvus.utils.HanLPUtils;
import org.example.repository.Milvus.utils.MilvusFilterTemplateBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.repository.Milvus.utils.HanLPUtils.calculateOverlap;

/**
 * 通用的 Milvus CRUD 工具类（无链式调用版本）。
 * <p>
 * 该工具类封装了 Milvus 的增删改查（CRUD）操作，仅支持通过 {@code Map<String, Object>} 传参。
 * 自动向量化功能可通过 {@link #prepareDataWithEmbedding} 辅助方法实现。
 * <p>
 * <strong>使用前必须通过构造函数传入已初始化的 {@link MilvusClientV2} 实例。</strong>
 */
@Component
@ConditionalOnProperty(name = "spring.ai.vectorstore.milvus.enabled", havingValue = "true", matchIfMissing = true)

public class MilvusCrudHelper {

    private final MilvusClientV2 client;

    private static final Gson gson = new Gson();

    /**
     * 构造函数，初始化 Milvus 客户端。
     *
     * @param client MilvusClientV2 实例，不能为空
     * @throws IllegalArgumentException 如果 client 为 null
     */
    public MilvusCrudHelper(MilvusClientV2 client) {
        if (client == null) {
            throw new IllegalArgumentException("MilvusClientV2 must not be null.");
        }
        this.client = client;
    }

    // ==================== 数据写入（仅支持 Map 传参） ====================

    /**
     * 批量插入数据。
     *
     * @param collectionName 集合名称
     * @param dataBaseName   数据库名称
     * @param dataList       要插入的数据列表，每个元素为一个 Map
     * @return InsertResp 插入响应对象，包含插入的数量等信息
     */
    public InsertResp insert(String collectionName, String dataBaseName, List<Map<String, Object>> dataList) {
        List<JsonObject> jsonList = dataList.stream()
                .map(MilvusCrudHelper::mapToJsonObject)
                .collect(Collectors.toList());

        InsertReq req = InsertReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .data(jsonList)
                .build();
        return client.insert(req);
    }

    /**
     * 插入或更新单条数据（Upsert）。
     * <p>如果主键存在则更新，不存在则插入。</p>
     *
     * @param collectionName 集合名称
     * @param dataBaseName   数据库名称
     * @param data           要插入或更新的数据 Map
     * @return UpsertResp Upsert 响应对象
     */
    public UpsertResp upsert(String collectionName, String dataBaseName, Map<String, Object> data) {
        return upsert(collectionName, dataBaseName, Collections.singletonList(data));
    }

    /**
     * 批量插入或更新数据（Upsert）。
     * <p>如果主键存在则更新，不存在则插入。</p>
     *
     * @param collectionName 集合名称
     * @param dataBaseName   数据库名称
     * @param dataList       要插入或更新的数据列表
     * @return UpsertResp Upsert 响应对象
     */
    public UpsertResp upsert(String collectionName, String dataBaseName, List<Map<String, Object>> dataList) {
        List<JsonObject> jsonList = dataList.stream()
                .map(MilvusCrudHelper::mapToJsonObject)
                .collect(Collectors.toList());

        UpsertReq req = UpsertReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .data(jsonList)
                .partialUpdate(true)

                .build();
        return client.upsert(req);
    }

    /**
     * 辅助方法：为单条数据自动添加稠密/稀疏向量。
     * <p>
     * 使用示例：
     * <pre>
     * Map<String, Object> data = new HashMap<>();
     * data.put("content", "Hello world");
     * data = helper.prepareDataWithEmbedding(data, "content", "embedding", "sparse_vector");
     * helper.insert("my_collection", data);
     * </pre>
     *
     * @param data               原始数据
     * @param sourceTextField    文本字段名（如 "content"）
     * @param targetDenseField   目标稠密向量字段（可为 null，若不需生成则传 null）
     * @param targetSparseField  目标稀疏向量字段（可为 null，若不需生成则传 null）
     * @return 填充向量后的新数据映射
     */
    public Map<String, Object> prepareDataWithEmbedding(
            Map<String, Object> data,
            String sourceTextField,
            String targetDenseField,
            String targetSparseField) {

        Map<String, Object> result = new HashMap<>(data);
        if (data.containsKey(sourceTextField)) {
            String text = (String) data.get(sourceTextField);

            if (targetDenseField != null) {
                float[] denseVec = EmbeddingUtils.embedDense(text);
                result.put(targetDenseField, denseVec);
            }

            if (targetSparseField != null) {
//                Map<Long, Float> sparseVec = EmbeddingUtils.embedSparse(text);
//                result.put(targetSparseField, sparseVec);
            }
        }
        return result;
    }

    // ==================== 查询操作 ====================

    /**
     * 执行标量查询（Query）。
     *
     * @param collectionName 集合名称
     * @param filter         过滤表达式（如 "id > 10"）
     * @param outputFields   需要返回的字段列表
     * @param limit          返回记录数限制
     * @param offset         偏移量
     * @param dataBaseName   数据库名称
     * @return QueryResp 查询响应结果
     */
    public QueryResp query(String collectionName, MilvusFilterTemplateBuilder.TemplateResult filter, List<String> outputFields, Long limit, Long offset, String dataBaseName) {
        System.out.println(STR."Executing query with filter: \{filter.getExpression()} and params: \{filter.getParams()}");
        QueryReq req = QueryReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .filter(filter.getExpression())
                .filterTemplateValues(filter.getParams())
                .outputFields(outputFields)
                .limit(limit)
                .offset(offset)
                .build();
        return client.query(req);
    }
    /**
     * 重载 执行标量查询（Query）可以直接使用表达式。
     *
     * @param collectionName 集合名称
     * @param filter         过滤表达式（如 "id > 10"）
     * @param outputFields   需要返回的字段列表
     * @param limit          返回记录数限制
     * @param offset         偏移量
     * @param dataBaseName   数据库名称
     * @return QueryResp 查询响应结果
     */
    public QueryResp query(String collectionName, String filter, List<String> outputFields, Long limit, Long offset, String dataBaseName) {
        QueryReq req = QueryReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(outputFields)
                .limit(limit)
                .offset(offset)
                .build();
        return client.query(req);
    }

    /**
     * 查询符合条件的所有字段数据。
     *
     * @param collectionName 集合名称
     * @param filter         过滤表达式
     * @param limit          返回记录数限制
     * @param offset         偏移量
     * @param dataBaseName   数据库名称
     * @return QueryResp 查询响应结果
     */
    public QueryResp queryAll(String collectionName, MilvusFilterTemplateBuilder.TemplateResult filter, Long limit, Long offset, String dataBaseName) {
        return query(collectionName, filter, List.of("*"), limit, offset, dataBaseName);
    }

    /**
     * 根据 ID 列表查询数据（Get）。
     *
     * @param collectionName 集合名称
     * @param ids            主键 ID 列表
     * @param outputFields   需要返回的字段列表
     * @param limit          返回记录数限制（可选）
     * @param dataBaseName   数据库名称
     * @return QueryResp 查询响应结果
     */
    public QueryResp queryByIds(String collectionName, List<Object> ids, List<String> outputFields, Long limit, String dataBaseName) {
        QueryReq req = QueryReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .ids(ids)
                .limit(limit)
                .outputFields(outputFields)
                .build();
        return client.query(req);
    }

    public QueryResp queryAllByIds(String collectionName, List<Object> ids, Long limit, String dataBaseName) {
        QueryReq req = QueryReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .ids(ids)
                .limit(limit)
                .outputFields(List.of("*"))
                .build();
        return client.query(req);
    }

    /**
     * 统计符合条件的记录总数。
     *
     * @param collectionName 集合名称
     * @param filter         过滤表达式（如 "age > 20"，若统计全部传 ""）
     * @param dataBaseName   数据库名称
     * @return 符合条件的记录总数，若无结果返回 0
     */
    public Long count(String collectionName, MilvusFilterTemplateBuilder.TemplateResult filter, String dataBaseName) {
        QueryResp resp = query(collectionName, filter, List.of("count(*)"), null, null, dataBaseName);
        if (!resp.getQueryResults().isEmpty()) {
            return (Long) resp.getQueryResults().getFirst().getEntity().get("count(*)");
        }
        return 0L;
    }
    // ==================== 删除操作 ====================

    /**
     * 根据 ID 列表批量删除数据。
     *
     * @param collectionName 集合名称
     * @param dataBaseName   数据库名称
     * @param ids            要删除的主键 ID 列表
     * @return DeleteResp 删除响应结果
     */
    public DeleteResp deleteByIds(String collectionName, String dataBaseName, List<Object> ids) {
        DeleteReq req = DeleteReq.builder()
                .collectionName(collectionName)
                .databaseName(dataBaseName)
                .ids(ids)
                .build();
        return client.delete(req);
    }

    /**
     * 根据过滤条件删除数据。
     *
     * @param collectionName 集合名称
     * @param dataBaseName   数据库名称
     * @param filter         删除条件表达式
     * @return DeleteResp 删除响应结果
     */
    public DeleteResp deleteByFilter(String collectionName, String dataBaseName, String filter) {
        DeleteReq req = DeleteReq.builder()
                .collectionName(collectionName)
                .databaseName(dataBaseName)
                .filter(filter)
                .build();
        return client.delete(req);
    }

    /**
     * 释放集合（Release Collection）。
     * <p>将集合从内存中释放，搜索前需重新加载。</p>
     *
     * @param collectionName 集合名称
     */
    public void releaseCollection(String collectionName) {
        client.releaseCollection(
                ReleaseCollectionReq.builder()
                        .collectionName(collectionName)
                        .build()
        );
    }

    /**
     * 加载集合（Load Collection）。
     * <p>将集合加载到内存中以便进行搜索。</p>
     *
     * @param collectionName 集合名称
     */
    public void loadCollection(String collectionName) {
        client.loadCollection(
                LoadCollectionReq.builder()
                        .collectionName(collectionName)
                        .build()
        );

    }

    // ==================== 混合搜索 ====================

    /**
     * 执行混合搜索（自动处理文本转向量）。
     * <p>根据传入的文本，自动生成稠密向量（通过 EmbeddingUtils）或 文本嵌入（EmbeddedText），
     * 构建多路搜索请求，并支持基于 MetricType 的原生分数过滤，最后使用 RRF 进行重排。</p>
     *
     * @param hybridSearch   混合搜索参数对象
     * @param dataBaseName   数据库名称
     * @param collectionName 集合名称
     * @param UseKWS         是否启用关键词重排
     * @return SearchResp 搜索响应结果
     */
    public SearchResp hybridSearch(HybridSearch hybridSearch, String dataBaseName, String collectionName, Boolean UseKWS) {
        String queryText = hybridSearch.getQueryText();
        List<String> vectorFields = hybridSearch.getVectorFields();
        String filter = hybridSearch.getFilter();
        int topK = hybridSearch.getTopK();
        Double scoreFilter = hybridSearch.getScoreFilter();
        MetricType metricType = hybridSearch.getMetricType();

        List<AnnSearchReq> searchRequests = new ArrayList<>();

        // 生成原生分数过滤参数 (Range Search Params)
        Map<String, Object> rangeSearchParams = generateRangeSearchParams(metricType, scoreFilter);

        for (String field : vectorFields) {
            String fieldLower = field.toLowerCase();
            // 判定是否为稀疏向量或全文检索字段 (BM25/Sparse)
            boolean isSparseOrBm25 = fieldLower.contains("sparse") || fieldLower.contains("bm25") || fieldLower.contains("text");

            AnnSearchReq.AnnSearchReqBuilder reqBuilder = AnnSearchReq.builder()
                    .vectorFieldName(field)
                    .filter(filter)
                    // 为了 RRF 效果更好，单路召回数量通常设为最终 topK 的倍数
                    .topK(topK *
                            (
                                (hybridSearch.getSearchTopKMultiplier()==null ? 4 : hybridSearch.getSearchTopKMultiplier())
                                        + (UseKWS ? 2:0)
                            )

                            );



            if (isSparseOrBm25) {
                // 修改: 使用 EmbeddedText，交由 Milvus 服务端处理 (Function/Analyzer)
                // 移除了手动 EmbedSparse / BGE-M3 的逻辑
                reqBuilder.vectors(Collections.singletonList(new EmbeddedText(queryText)));
            } else {
                // 稠密向量: 使用 embedDense
                float[] denseQueryVec = EmbeddingUtils.embedDense(queryText);
                reqBuilder.vectors(Collections.singletonList(new FloatVec(denseQueryVec)));
                // 注入分数过滤参数
                if (!rangeSearchParams.isEmpty()) {
                    reqBuilder.params(gson.toJson(rangeSearchParams) );
                }
            }

            searchRequests.add(reqBuilder.build());
        }

        // RRF 重排配置，k 值通常建议 60
        RRFRanker rrfRanker = RRFRanker.builder()
                .k(hybridSearch.getRrfK()==null ? 60 : hybridSearch.getRrfK())
                .build();

        HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .searchRequests(searchRequests)
                .topK(topK)
                .functionScore(
                        FunctionScore.builder()
                                .addFunction(rrfRanker)
                                .build()
                )
                .outFields(hybridSearch.getOutFields() != null ? hybridSearch.getOutFields() : List.of("*"))
                .build();
        System.out.println(STR."Constructed HybridSearchReq: \{gson.toJson(hybridSearchReq)}");
        return getSearchResp(hybridSearch, UseKWS, hybridSearchReq);
    }
    /**
     * 执行自定义混合搜索（需手动提供 SearchRequests）。
     * <p>适用于调用者已经构建好 AnnSearchReq 列表的场景。</p>
     *
     * @param hybridSearch   混合搜索参数对象，需包含 searchRequests
     * @param dataBaseName   数据库名称
     * @param collectionName 集合名称
     * @return SearchResp 搜索响应结果
     */
    public SearchResp hybridSearchDIY(
            HybridSearch hybridSearch, String dataBaseName, String collectionName,Boolean UseKWS
    ) {
        HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                .databaseName(dataBaseName)
                .collectionName(collectionName)
                .searchRequests(hybridSearch.getSearchRequests())
                .topK(hybridSearch.getTopK())
                .outFields(hybridSearch.getOutFields()!=null ? hybridSearch.getOutFields() : List.of("*"))
                .functionScore(
                        FunctionScore.builder()
                                .addFunction(
                                        RRFRanker.builder().k( hybridSearch.getRrfK()==null ? 60 : hybridSearch.getRrfK()).build()
                                )
                                .build()
                )

                .build();

        return getSearchResp(hybridSearch, UseKWS, hybridSearchReq);
    }

    private SearchResp getSearchResp(HybridSearch hybridSearch, Boolean UseKWS, HybridSearchReq hybridSearchReq) {
       if (UseKWS==null) UseKWS=false;
        SearchResp searchResp = client.hybridSearch(hybridSearchReq);
        System.out.println(searchResp);
        if ( hybridSearch.getFieldName() != null && hybridSearch.getQueryText() != null&&UseKWS) {
            searchResp = rerankByKeywordScalar(hybridSearch.getFieldName(), hybridSearch.getQueryText(), searchResp,hybridSearch.getRrfK()==null ? 60 : hybridSearch.getRrfK(),hybridSearch.getTopK());
        }
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

        for (List<SearchResp.SearchResult> results : searchResults) {
            for (SearchResp.SearchResult result : results) {
                float rawScore = (float) result.getScore();

                // 场景 1: 获取 0.0 - 1.0 的归一化分数
                // 传入 k=60, 路数=2 (Sparse + Dense)
                float normalized = normalize(rawScore, hybridSearch.getRrfK(), hybridSearch.getVectorFields().size()+ (UseKWS ? 1:0));


                // 场景 2: 获取百分制分数 (保留 1 位小数)
                // 例如 rawScore 是 0.0327... -> 输出 100.0
                float percent = toPercentage(rawScore, hybridSearch.getRrfK(), hybridSearch.getVectorFields().size()+(UseKWS ? 1:0)
                        , 1);

                System.out.println("原始分: " + rawScore);
                System.out.println("匹配度: " + percent + "%");

                // 如果您想把分数改写回 result 对象 (注意: 这会改变原有分数的含义)
                 result.setScore(normalized);
            }

        }
        return searchResp;
    }

    // ==================== 统一搜索 ====================

    /**
     * 执行统一搜索操作。
     * <p>支持全文检索、向量搜索、分组搜索、过滤以及标量关键词重排等多种组合功能。</p>
     *
     * @param request        统一搜索请求配置对象
     * @param dataBaseName   数据库名称
     * @param collectionName 集合名称
     * @return SearchResp 搜索响应结果
     * @throws IllegalArgumentException 如果 request 为 null 或缺少必要的查询参数（Vector 或 Text）
     */
    public SearchResp performUnifiedSearch(UnifiedSearchRequest request,
                                           String dataBaseName, String collectionName) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }

        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .databaseName(dataBaseName)
                .topK(request.getTopK());

        if (request.isEnableFullTextSearch() && request.getQueryText() != null && !request.getQueryText().isEmpty()) {
            builder.data(Collections.singletonList(new EmbeddedText(request.getQueryText())));
            String annsField = request.getAnnsField() != null ? request.getAnnsField() : "sparse_vector";
            builder.annsField(annsField);
        } else if (request.getQueryVector() != null) {
            builder.data(Collections.singletonList(request.getQueryVector()));
            String annsField = request.getAnnsField() != null ? request.getAnnsField() : "embedding";
            builder.annsField(annsField);
        } else {
            throw new IllegalArgumentException("Either queryVector or queryText (with enableFullTextSearch=true) must be provided");
        }
// 2. 设置分组 (Grouping)
        if (request.isEnableGrouping() && request.getGroupByField() != null) {
            builder.groupByFieldName(request.getGroupByField());
            if (request.getGroupSize() != null) {
                builder.groupSize(request.getGroupSize());
            }
            if (request.getStrictGroupSize() != null) {
                builder.strictGroupSize(request.getStrictGroupSize());
            }
        }
// 3. 设置过滤 (Filter)
        if (request.isEnableFiltering() && request.getFilter() != null && !request.getFilter().isEmpty()) {
            builder.filter(request.getFilter());
        }

    // 4. 设置搜索参数 (SearchParams & Metric Filter)
        Map<String, Object> finalSearchParams = new HashMap<>();
        if (request.getSearchParams() != null) {
            finalSearchParams.putAll(request.getSearchParams());
        }
        // 动态添加分数过滤参数 (如 radius)
        if (request.getScoreFilter() != null && request.getMetricType() != null) {
            finalSearchParams.putAll(generateRangeSearchParams(request.getMetricType(), request.getScoreFilter()));
        }
        if (!finalSearchParams.isEmpty()) {
            builder.searchParams(finalSearchParams);
        }

// 5. 输出字段
        if (request.getOutputFields() != null && !request.getOutputFields().isEmpty()) {
            builder.outputFields(request.getOutputFields());
        }

        SearchReq searchReq = builder
                .build();
        SearchResp result = client.search(searchReq);
// 6. 结果分数调整 (针对 IP/Cosine/L2 的显示调整)
        List<List<SearchResp.SearchResult>> results = result.getSearchResults();
        results.forEach(
                resList -> resList.forEach(
                        res -> res.setScore(calculateScore(res.getScore(), request.getMetricType()))
                )
        );
// 7. 关键词标量重排 (Rerank)
        if ( request.getFieldName() != null && request.getQueryText() != null) {
            result = rerankByKeywordScalar(request.getFieldName(), request.getQueryText(), result, 0,request.getTopK());
        }
        return result;
    }




    /**
     * 简易版统一搜索方法
     * <p>核心目标：以最少的参数完成最常用的搜索（支持稠密向量、稀疏向量或文本）</p>
     *
     * @param collectionName 集合名称 (e.g., "agentic_experience_bank")
     * @param vectorFieldName 向量字段名 (e.g., "problem_vector" 或 "problem_sparse_vector")
     * @param queryData      查询数据，支持：
     * 1. List<Float>: 稠密向量搜索
     * 2. String: 文本搜索 (需集合配置了Function或客户端处理了Embedding)
     * @param filter         过滤表达式 (e.g., "usage_count > 10")，可为 null
     * @param topK           返回结果数量
     * @param outputFields   需要返回的标量字段列表 (e.g., ["solution_content", "complexity_level"])
     * @return SearchResp    搜索响应
     */

    public SearchResp simpleUnifiedSearch(String collectionName,
                                          String vectorFieldName,
                                          Object queryData,
                                          String filter,
                                          int topK,
                                          List<String> outputFields,
                                          Double scoreFilter,
                                          MetricType metricType) {

        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(vectorFieldName)
                .topK(topK);

        // --- 数据类型处理 (移除 SortedMap) ---
        if (queryData instanceof String) {
            // 文本 -> EmbeddedText (适用于配置了 Analyzer/Function 的字段)
            builder.data(Collections.singletonList(new EmbeddedText((String) queryData)));
        } else if (queryData instanceof List) {
            // List<Float> -> FloatVec
            try {
                List<Float> floatList = (List<Float>) queryData;
                builder.data(Collections.singletonList(new FloatVec(floatList)));
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("For dense vector search, queryData must be List<Float>", e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported queryData type. Only String and List<Float> are supported.");
        }

        if (filter != null && !filter.isEmpty()) {
            builder.filter(filter);
        }

        if (outputFields != null && !outputFields.isEmpty()) {
            builder.outputFields(outputFields);
        }

        // --- 分数过滤参数 ---
        Map<String, Object> searchParams = new HashMap<>();
        if (scoreFilter != null && metricType != null) {
            searchParams.putAll(generateRangeSearchParams(metricType, scoreFilter));
        }

        if (!searchParams.isEmpty()) {
            builder.searchParams(searchParams);
        }

        try {
            return client.search(builder.build());
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            throw new RuntimeException("Milvus search execution failed", e);
        }
    }



    // ==================== 私有辅助方法 ====================
    /**
     * 根据 MetricType 生成 Range Search 参数
     *
     * @param metricType 度量类型 (L2, IP, COSINE)
     * @param threshold  阈值 (对于 L2 是最大距离，对于 IP 是最小分数)
     * @return 包含 radius 和 range_filter 的参数 Map
     */
    private Map<String, Object> generateRangeSearchParams(MetricType metricType, Double threshold) {
        Map<String, Object> params = new HashMap<>();

        // 基础校验
        if (threshold == null || metricType == null || metricType == MetricType.None) {
            return params;
        }

        // 1. 设置 radius (主阈值)
        params.put("radius", threshold);

        // 2. 根据度量类型设置 range_filter (辅助边界)
        // Milvus 会在 radius 和 range_filter 构成的区间内进行过滤
        switch (metricType) {
            case L2:
            case HAMMING:
            case JACCARD:
                // === 距离度量 (越小越相似) ===
                // 逻辑: 我们希望找到距离 < threshold 的向量
                // 区间: [0.0, threshold)
                // radius = threshold (外边界)
                // range_filter = 0.0 (内边界/最完美匹配)
                params.put("range_filter", 0.0);
                break;

            case IP:
            case COSINE:
                // === 相似度度量 (越大越相似，包括 BM25) ===
                // 逻辑: 我们希望找到分数 > threshold 的向量
                // 区间: (threshold, Infinity]
                // radius = threshold (下限)
                // range_filter = Infinity (上限/最完美匹配)
                params.put("range_filter", Float.MAX_VALUE);
                break;

            default:
                // 默认情况下不设置 range_filter，依赖 Milvus 默认行为
                break;
        }

        return params;
    }
    /**
     * 将 Map 转换为 Gson 的 JsonObject。
     *
     * @param map 输入的 Map 数据
     * @return 转换后的 JsonObject
     */
    private static JsonObject mapToJsonObject(Map<String, Object> map) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            jsonObject.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
        }
        return jsonObject;
    }
    /**
     * 将 RRF 原始分数归一化到 0.0 - 1.0 区间
     *
     * @param rawScore  Milvus 返回的原始 RRF 分数
     * @param k         RRF 算法中的 k 值 (通常为 60)
     * @param numFields 参与混合搜索的字段数量 (路数，例如稀疏+稠密就是 2)
     * @return 归一化后的分数 (0.0 ~ 1.0)
     */
    private  float normalize(float rawScore, int k, int numFields) {
        System.out.println("lushu"+numFields);
        if (numFields <= 0 || k < 0) {
            throw new IllegalArgumentException("Parameters must be positive");
        }

        // 1. 计算单路最高分 (即 rank=1 时的得分: 1 / (k + 1))
        double maxScorePerRoute = 1.0 / (k + 1.0);

        // 2. 计算理论总最高分 (假设在所有路数中都排第 1)
        double theoreticalMaxScore = maxScorePerRoute * numFields;

        // 3. 计算归一化比率
        double normalized = rawScore / theoreticalMaxScore;

        // 4. 防止浮点数精度问题导致略微超过 1.0
        return (float) Math.min(Math.max(normalized, 0.0), 1.0);
    }

    /**
     * 将 RRF 原始分数转换为百分制 (0 - 100)，并保留指定小数位
     *
     * @param rawScore  Milvus 返回的原始 RRF 分数
     * @param k         RRF 算法中的 k 值
     * @param numFields 参与混合搜索的路数
     * @param scale     保留的小数位数 (例如 2)
     * @return 百分制分数 (例如 98.50)
     */
    public  float toPercentage(float rawScore, int k, int numFields, int scale) {
        float normalized = normalize(rawScore, k, numFields);
        float percentage = normalized * 100;

        // 使用 BigDecimal 进行四舍五入
        return BigDecimal.valueOf(percentage)
                .setScale(scale, RoundingMode.HALF_UP)
                .floatValue();
    }

    /**
     * 根据度量类型调整分数。
     *
     * @param score      原始分数
     * @param metricType 度量类型（IP, COSINE 等）
     * @return 调整后的分数
     */
    private float calculateScore(float score, MetricType metricType) {
        return (metricType == MetricType.IP || metricType == MetricType.COSINE) ? score : (1 - score);
    }
    /**
     * 使用关键词标量重叠度对搜索结果进行重排序 (真正的多路 RRF 累加版)。
     * <p>
     * 逻辑：
     * 1. 提取查询文本中的关键词，与结果实体中指定字段的关键词列表计算重叠度 (0.0 ~ 1.0)。
     * 2. 将重叠度分数线性映射为虚拟排名 (Virtual Rank)，保证 1.0分对应第1名，0.0分对应惩罚垫底名次。
     * 3. 【核心修正】原生分数累加：直接将第三路（关键词路）的 RRF 得分追加到 Milvus 返回的已有分数上，构成完整的三路 RRF。
     * 4. 重新排序并进行 TopK 截断。
     * </p>
     *
     * @param fieldName  包含关键词列表的字段名称
     * @param query      查询文本
     * @param searchResp 原始搜索结果 (假设已包含 Dense + Sparse 双路 RRF 累加得分)
     * @param RRF_K      RRF 算法的平滑常数 (须与外层混合检索保持统一，通常取 60)
     * @param topK       最终保留的结果数量
     * @return 重排序后的搜索响应
     */
    public SearchResp rerankByKeywordScalar(String fieldName, String query, SearchResp searchResp, int RRF_K, Integer topK) {
        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
        List<String> queryKeywords = HanLPUtils.extractKeywords(query, 50);

        // 如果未提取到查询关键词，退化为原有的双路检索，直接返回
        if (queryKeywords == null || queryKeywords.isEmpty()) {
            return searchResp;
        }
        Set<String> setQuery = new HashSet<>(queryKeywords);

        // ==========================================
        // 算法参数配置
        // ==========================================
        // 设定惩罚倍率：1.0分的权重是0.0分的多少倍？(推荐 20)
        int penaltyRatio = 20;
        // 自适应计算惩罚底座排名 (例如 K=60, 倍率=20 时，底座排名为 1140)
        int virtualMaxRank = (penaltyRatio - 1) * RRF_K;

        for (List<SearchResp.SearchResult> resList : results) {
            if (resList == null || resList.isEmpty()) continue;

            // ==========================================
            // 遍历计算并追加第三路 RRF 分数
            // ==========================================
            for (SearchResp.SearchResult res : resList) {

                // 1. 获取前两路 (Dense + Sparse) 的原生 RRF 累加分数
                float originalMilvusScore = res.getScore();

                // 2. 计算关键词重叠度分数 (0.0 ~ 1.0)
                float keywordScore = 0.0f;
                Object keywordsObj = res.getEntity().get(fieldName);

                if (keywordsObj instanceof List) {
                    List<String> docKeywords = (List<String>) keywordsObj;
                    keywordScore = calculateOverlap(docKeywords, setQuery);
                } else if (keywordsObj instanceof String) {
                    keywordScore = calculateOverlap(HanLPUtils.extractKeywords((String) keywordsObj, 50), setQuery);
                } else {
                    System.out.println(STR."Warning: Result with ID \{res.getId()} has non-list, non-string keywords field");
                }

                // 兜底防御：确保分数严格在 [0.0, 1.0] 范围内，防止异常分词结果越界
                keywordScore = Math.max(0.0f, Math.min(1.0f, keywordScore));

                // 🌟 3. 将绝对分数线性映射为虚拟排名 (Score-to-Rank)
                // Score 1.0 -> Rank 1
                // Score 0.0 -> Rank virtualMaxRank (例如 1140)
                int virtualKeywordRank = (int) (1 + (1.0f - keywordScore) * (virtualMaxRank - 1));

                // 🌟 4. 计算第三路（关键词路）的 RRF 得分
                float keywordRrfScore = 1.0f / (RRF_K + virtualKeywordRank);

                // 🌟 5. 真正的多路追加：原有两路之和 + 第三路得分
                res.setScore(originalMilvusScore + keywordRrfScore);
            }

            // ==========================================
            // 全局重排与截断
            // ==========================================

            // 按累加后的最终三路 RRF 分数降序排列
            resList.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

            // 执行 TopK 原地截断
            if (topK != null && resList.size() > topK) {
                resList.subList(topK, resList.size()).clear();
            }
        }

        return searchResp;
    }
//
    // ==================== 统一搜索请求配置类 ====================


    /**
     * 统一搜索请求参数封装类。
     * <p>
     * 该类用于封装 {@link io.milvus.param.dml.SearchParam} 所需的参数，
     * 统一处理向量搜索(ANN)、全文检索(Full Text)以及 Milvus 2.4+ 的分组搜索(Group By)。
     * </p>
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UnifiedSearchRequest {

        /**
         * 【核心参数】查询向量。
         * 对应 Milvus search 中的 `vectors` 参数。
         * 如果是图片或文本搜向量，需先经过 Embedding 模型转化为浮点向量。
         */
        private FloatVec queryVector;

        /**
         * 【核心参数】查询文本。
         * 1. 在启用全文检索 (`enableFullTextSearch=true`) 时，作为稀疏向量生成的输入。
         * 2. 在业务层，可能用于记录日志或作为 Embedding 模型的原始输入。
         */
        private String queryText;

        /**
         * 【功能开关】是否启用全文检索 (Full Text Search / Sparse Search)。
         * 对应 Milvus 的稀疏向量搜索或 BM25 功能。
         */
        private boolean enableFullTextSearch;

        /**
         * 【功能开关】是否启用分组搜索 (Group By Search)。
         * 对应 Milvus `searchParams` 中的 `group_by_field` 配置。
         * 允许根据特定标量字段对搜索结果进行聚合（如按文档 ID 去重）。
         */
        private boolean enableGrouping;

        /**
         * 【功能开关】是否启用标量过滤。
         * 决定是否将 `filter` 字段中的布尔表达式应用到搜索请求中。
         */
        private boolean enableFiltering;

        /**
         * 【功能开关】是否启用范围搜索 (Range Search)。
         * 如果启用，通常需要配合 `searchParams` 中的 `radius` (半径) 和 `range_filter` (范围) 参数。
         */
        private boolean enableRangeSearch;

        /**
         * 【分组参数】分组字段名称。
         * 对应 Milvus 的 `group_by_field`。通常是 INT64 或 VARCHAR 类型的标量字段。
         * 仅在 `enableGrouping=true` 时生效。
         */
        private String groupByField;

        /**
         * 【分组参数】每个分组返回的条目数。
         * 对应 Milvus 的 `group_size`。默认为 1。
         */
        private Integer groupSize;

        /**
         * 【分组参数】是否严格限制分组大小。
         * 对应 Milvus 的 `strict_group_size`。
         * 如果为 true，若某组数据不足 groupSize，可能会通过其他方式补充或严格截断。
         */
        private Boolean strictGroupSize;

        /**
         * 【过滤参数】标量过滤表达式 (Boolean Expression)。
         * 对应 Milvus 的 `expr` 或 `filter`。
         * 示例: "age > 20 && status in [1, 2]"。
         */
        private String filter;

        /**
         * 【返回参数】指定返回的标量字段列表。
         * 对应 Milvus 的 `outFields` (或 output_fields)。
         * 设置为 ["*"] 可返回所有标量字段（注意性能开销）。
         */
        private List<String> outputFields;

        /**
         * 【搜索参数】特定索引的搜索参数集合。
         * 对应 Milvus 的 `params` JSON 字符串或 Map。
         * 常见参数：
         * <ul>
         * <li>HNSW 索引: "ef"</li>
         * <li>IVF 索引: "nprobe"</li>
         * <li>范围搜索: "radius", "range_filter"</li>
         * </ul>
         */
        private Map<String, Object> searchParams;

        /**
         * 【核心参数】目标向量字段名称。
         * 指定在 Collection 中的哪一列向量字段上进行相似度检索 (e.g., "user_embedding")。
         */
        private String annsField;

        /**
         * 【核心参数】返回的最相似结果数量 (Limit)。
         * 对应 Milvus 的 `topK`。限制最终返回的行数。
         * 注意：Milvus 通常限制 topK <= 16384。
         */
        private int topK;

        /**
         * 【度量类型】距离计算方式。
         * 对应 Milvus 的 `metricType`。
         * 必须与建表/建索引时指定的类型一致 (L2, IP, COSINE)。
         */
        private MetricType metricType;

        /**
         * 【业务参数】是否使用关键词标量重排序。
         * 这通常不是 Milvus 原生参数，而是业务层逻辑。
         * 可能指在向量搜索后，再根据关键词匹配度对结果进行二次排序。
         */
        private Boolean isUseRerankByKeywordScalar;

        /**
         * 【权重参数】元数据权重 / 混合权重。
         * 可能用于加权排序逻辑，或者在多路召回融合时作为该路搜索的权重因子。
         */
        private Double metaWeight;

        /**
         * 【字段标识】通用字段名称。
         * 此字段用途较模糊，可能与 `annsField` 重复，或是用于指定主要处理的文本字段名称。
         */
        private String fieldName;
        /**
         * 过滤的分数
         */
        private Double scoreFilter;
    }
    // ==================== 混合搜索请求配置类 ====================

    /**
     * 混合搜索请求参数封装类。
     * <p>
     * 用于封装 {@link io.milvus.param.dml.HybridSearchParam}。
     * 允许在一个请求中对多个向量字段进行搜索，并使用 Reranker (如 WeightedRanker 或 RRFRanker) 对结果进行融合。
     * </p>
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HybridSearch {

        /**
         * 【查询输入】原始查询文本。
         * 用于转换为各路搜索所需的向量。
         */

        private String queryText;

        /**
         * 【目标字段】参与混合搜索的向量字段列表。
         * 例如：["face_vector", "voice_vector"] 或 ["dense_vector", "sparse_vector"]。
         */

        private List<String> vectorFields;

        /**
         * 【核心参数】最终返回的 TopK。
         * 在多路结果融合（Rerank）之后，截取的前 K 个结果。
         */

        private int topK;

        /**
         * 【过滤参数】全局过滤表达式。
         * 如果业务逻辑是将此 Filter 应用于所有子搜索请求 (searchRequests)，则需在构建 AnnSearchReq 时注入。
         */

        private String filter;

        /**
         * 【核心参数】单路搜索请求列表。构成多路搜索
         * 对应 Milvus SDK 的 `reqs` 列表。
         * 列表中每一个 `AnnSearchReq` 代表针对某一个向量字段的独立搜索请求。
         */

        private List<AnnSearchReq> searchRequests;


        /**
         * 【字段标识】业务字段名。
         * 含有关键词列表的字段名称，用于分词重排序的
         */

        private String fieldName;

        /**
         * 输出的字段列表
          */

        private List<String> outFields;

        /**
         * 过滤的分数
         */
        private Double scoreFilter;

        /**
         * 【度量类型】距离计算方式。
         * 对应 Milvus 的 `metricType`。
         * 必须与建表/建索引时指定的类型一致 (L2, IP, COSINE)。
         */
        private MetricType metricType;
        /**
         * rrf重排序的值
         */
        private Integer rrfK;
        /**
         * 搜索topk的倍数，单路召回数量通常设为最终 topK 的倍数
          */
         private  Integer searchTopKMultiplier;
    }

}