package org.example.repository.Graph.Entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 会话图 DTO
 * 以 topicId 为唯一标识，包含该会话内所有的知识节点和关联边
 */
@Getter
public final class TopicGraph {

    @JsonProperty("topicId")
    private final String topicId;

    @JsonProperty("nodes")
    private final List<KnowledgeNode> nodes;

    @JsonProperty("edges")
    private final List<TopicEdge> edges;

    @JsonCreator
    public TopicGraph(
            @JsonProperty("topicId") String topicId,
            @JsonProperty("nodes") List<KnowledgeNode> nodes,
            @JsonProperty("edges") List<TopicEdge> edges) {
        this.topicId = topicId;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(nodes)));
        this.edges = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(edges)));
    }

    /**
     * 静态工厂：基于节点列表构建会话图
     */
    public static TopicGraph build(List<KnowledgeNode> allNodes, String topicId) {
        if (allNodes == null || allNodes.isEmpty()) {
            return new TopicGraph(topicId, Collections.emptyList(), Collections.emptyList());
        }

        List<TopicEdge> capturedEdges = new ArrayList<>();
        
        // 遍历所有节点及其关系，提取出属于本会话的边
        for (KnowledgeNode node : allNodes) {
            if (node.getRelationships() != null) {
                for (KnowledgeRelation rel : node.getRelationships()) {
                    // 确保目标节点存在且属于同一 topicId（逻辑上 Repository 已过滤，此处做二次确认）
                    if (rel.getTarget() != null) {
                        capturedEdges.add(new TopicEdge(
                                node.getNodeId(),
                                rel.getTarget().getNodeId(),
                                rel.getDetail()
                        ));
                    }
                }
            }
        }

        return new TopicGraph(topicId, allNodes, capturedEdges);
    }

    /**
     * 内部类：表示会话中的边
     */
    @Getter
    public static class TopicEdge {
        private final String from;
        private final String to;
        private final String relation;

        public TopicEdge(String from, String to, String relation) {
            this.from = from;
            this.to = to;
            this.relation = relation;
        }
    }
}