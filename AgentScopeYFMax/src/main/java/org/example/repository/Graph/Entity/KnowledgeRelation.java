package org.example.repository.Graph.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.util.ArrayList;
import java.util.List;

@RelationshipProperties
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRelation {

    @RelationshipId
    private Long id;
    /**
     * detail：存储格式化后的“全路径上下文”
     * 优化后格式示例：
     * Current: 启动服务
     * ↳ Step 1: 环境配置
     * ↳ Step 2: 初始化阶段
     */
    @Property("detail")
    private String detail;

    @Property("topic_id")
    private String topicId;

    /**
     * parentDetail：保持 List 结构，方便程序处理
     */
    @Property("parent_detail")
    @JsonIgnoreProperties
    @Builder.Default
    private List<String> parentDetail = new ArrayList<>();

    @Property("embedding")
    @JsonIgnoreProperties
    private List<Double> embedding;

    @TargetNode
    @JsonIgnoreProperties({"relationships", "embedding", "detail", "status", "description"})
    private KnowledgeNode target;
}