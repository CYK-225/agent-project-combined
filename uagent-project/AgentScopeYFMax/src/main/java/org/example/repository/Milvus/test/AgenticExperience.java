package org.example.repository.Milvus.test;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.example.repository.Milvus.model.BaseMilvusMeta;

import java.util.List;
import java.util.SortedMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgenticExperience extends BaseMilvusMeta {

    @JSONField(name = "experience_id")
    private Long experienceId;

    @JSONField(name = "problem_description")
    private String problemDescription;

    @JSONField(name = "problem_vector")
    private List<Float> problemVector; // 1536维稠密向量

    // 必填：Schema 中非函数生成的稀疏向量
    @JSONField(name = "problem_sparse_vector")
    private SortedMap<Long, Float> problemSparseVector;

    // 注意：以下两个 BM25 字段由 Milvus Function 自动生成，插入时无需赋值
    // 但为了查询结果能映射回来，保留字段定义
    @JSONField(name = "problem_bm25_vector")
    private SortedMap<Long, Float> problemBm25Vector;

    @JSONField(name = "playbook__bm25_vector")
    private SortedMap<Long, Float> playbookBm25Vector;

    @JSONField(name = "solution_content")
    private String solutionContent;

    @JSONField(name = "usage_count")
    private Integer usageCount; // Schema: Int32

    @JSONField(name = "complexity_level")
    private Integer complexityLevel; // Schema: Int8

    @JSONField(name = "domain_tags")
    private List<Long> domainTags; // Schema: Array<VarChar> -> List<String>

    @JSONField(name = "playbook_snippet")
    private String playbookSnippet;

    @JSONField(name = "confidence_score")
    private Float confidenceScore;

    @JSONField(name = "cost_estimate")
    private Float costEstimate;

    @JSONField(name = "created_timestamp")
    private Long createdTimestamp;

    @JSONField(name = "last_used_timestamp")
    private Long lastUsedTimestamp;

    @JSONField(name = "success_rate")
    private Float successRate;

    @JSONField(name = "experience_type")
    private String experienceType;

    @JSONField(name = "related_experience_ids")
    private List<Integer> relatedExperienceIds; // Schema: Array<Int32> -> List<Integer>
}