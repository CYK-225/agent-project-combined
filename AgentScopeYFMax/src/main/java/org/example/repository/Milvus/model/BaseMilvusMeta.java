package org.example.repository.Milvus.model;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Milvus 实体基类 (Fastjson2 纯净版)
 * 1. 提供 Bean <-> Map 的自动转换能力
 * 2. 提供 search 时的 score 字段
 */
@Data
public abstract class BaseMilvusMeta implements Serializable {

    /**
     * 搜索相似度分数 (不存入 Milvus，仅用于接收搜索结果)
     * 配置 serialize = false 确保调用 toMilvusMap() 时不会包含此字段
     */
    @JSONField(serialize = false, deserialize = false)
    private Float score;

    /**
     * 将当前对象转换为 Milvus 需要的 Map<String, Object>
     * 使用 Fastjson2 替代 Jackson
     */
    @JSONField(serialize = false) // 防止此方法被误序列化为属性
    public Map<String, Object> toMilvusMap() {
        // JSONObject.from(this) 会将当前对象转换为 JSONObject
        // JSONObject 本身实现了 Map<String, Object> 接口
        // 它会自动忽略被标记为 @JSONField(serialize = false) 的字段 (如 score)
        return JSONObject.from(this);
    }
}