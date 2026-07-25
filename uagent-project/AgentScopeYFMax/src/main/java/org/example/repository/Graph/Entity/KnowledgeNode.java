package org.example.repository.Graph.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.util.ArrayList;
import java.util.List;

@Node("KnowledgeNode")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) //以此去掉了DTO，空的字段就不返回了
public class KnowledgeNode {

    @Id
    private String nodeId; // 直接用 nodeId 作为主键，省去 Long id 的转换麻烦

    @Property("topic_id")
    private String topicId;

    @Property("detail")
    private String detail;

    @Property("status")
    private String status;

    @Property("embedding")
    @JsonIgnoreProperties
    private List<Double> embedding;

    @Property("description")
    private String description;

    // --- 关系定义 ---
    // 这里的关键是：fetch时SDN会查出关系，但序列化给前端时，
    // 我们让 Jackson 忽略掉 KnowledgeRelation 里的 heavy 字段
    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    @JsonIgnoreProperties({"parentDetail", "embedding"})
    private List<KnowledgeRelation> relationships = new ArrayList<>();
}