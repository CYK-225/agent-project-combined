package org.example.repository.Milvus.utils;

import com.google.gson.Gson;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;

import java.util.*;

/**
 * Milvus 向量搜索请求构建器 (链式调用版)
 */
public class MilvusReqBuilder {

    private static final Gson gson = new Gson();

    // ================== 预设模式常量 ==================
    public static final String MODE_FAST = "FAST";
    public static final String MODE_BALANCED = "BALANCED";
    public static final String MODE_ACCURATE = "ACCURATE";

    // ================== 内部成员变量 ==================
    private String fieldName;           // 向量字段名
    private float[] vector;             // 待搜索向量
    private int topK = 10;              // 默认 TopK 为 10
    private String filterExpr;          // 过滤表达式
    private final Map<String, Object> searchParams = new HashMap<>(); // 搜索参数容器

    // 私有构造，强制使用 create() 入口
    private MilvusReqBuilder() {}

    /**
     * 1. 入口方法
     */
    public static MilvusReqBuilder create() {
        return new MilvusReqBuilder();
    }

    // ================== 2. 链式设置方法 ==================

    /**
     * 设置目标向量字段名 (必填)
     */
    public MilvusReqBuilder field(String fieldName) {
        this.fieldName = fieldName;
        return this;
    }

    /**
     * 设置搜索向量 (必填)
     */
    public MilvusReqBuilder vector(float[] vector) {
        this.vector = vector;
        return this;
    }

    /**
     * 设置 TopK (选填，默认10)
     */
    public MilvusReqBuilder topK(int topK) {
        this.topK = topK;
        return this;
    }

    /**
     * 设置过滤表达式 (选填)
     * 例如: "category == 1 && price < 100"
     */
    public MilvusReqBuilder filter(String filterExpr) {
        this.filterExpr = filterExpr;
        return this;
    }

    /**
     * 设置搜索模式 (使用预设参数)
     * 注意：这会覆盖之前设置的冲突参数 (如 ef 或 nprobe)
     */
    public MilvusReqBuilder mode(String mode) {
        Map<String, Object> presetParams = getParamsByMode(mode);
        this.searchParams.putAll(presetParams);
        return this;
    }

    /**
     * 添加单个自定义搜索参数
     * 例如: putParam("radius", 0.8)
     */
    public MilvusReqBuilder withParam(String key, Object value) {
        this.searchParams.put(key, value);
        return this;
    }

    /**
     * 批量添加自定义搜索参数
     */
    public MilvusReqBuilder withParams(Map<String, Object> params) {
        if (params != null) {
            this.searchParams.putAll(params);
        }
        return this;
    }

    // ================== 3. 最终构建方法 ==================

    /**
     * 执行构建，生成 Milvus SDK 需要的 AnnSearchReq 对象
     */
    public AnnSearchReq build() {
        // 1. 校验必填项
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Milvus search fieldName cannot be empty");
        }
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Milvus search vector cannot be empty");
        }

        // 2. 兜底策略：如果用户没设置任何参数(也没调mode)，默认使用 BALANCED
        if (searchParams.isEmpty()) {
            this.mode(MODE_BALANCED);
        }

        // 3. 将 Map 参数转换为 JSON 字符串 (Gson)
        String paramJson = gson.toJson(searchParams);

        // 4. 封装向量
        FloatVec floatVec = new FloatVec(vector);

        // 5. 构建 Milvus 请求
        AnnSearchReq.AnnSearchReqBuilder builder = AnnSearchReq.builder()
                .vectorFieldName(fieldName)
                .vectors(Collections.singletonList(floatVec))
                .topK(topK)
                .params(paramJson); // 注入转换后的 JSON

        // 6. 注入过滤条件 (如果有)
        if (filterExpr != null && !filterExpr.trim().isEmpty()) {
            builder.expr(filterExpr);
        }

        return builder.build();
    }

    // ================== 辅助：模式定义 ==================
    private Map<String, Object> getParamsByMode(String mode) {
        Map<String, Object> params = new HashMap<>();
        if (mode == null) mode = MODE_BALANCED;

        switch (mode.toUpperCase()) {
            case MODE_FAST:
                params.put("ef", 32);       // HNSW
                // params.put("nprobe", 4); // IVF
                break;
            case MODE_ACCURATE:
                params.put("ef", 128);      // HNSW
                // params.put("nprobe", 64);// IVF
                break;
            case MODE_BALANCED:
            default:
                params.put("ef", 64);       // HNSW
                // params.put("nprobe", 16);// IVF
                break;
        }
        return params;
    }
}