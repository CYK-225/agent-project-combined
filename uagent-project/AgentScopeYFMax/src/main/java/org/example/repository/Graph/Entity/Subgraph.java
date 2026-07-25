package org.example.repository.Graph.Entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * 子图 DTO
 * 用于存储从特定根节点开始的局部图或全图结构。
 * 支持：指定深度抓取 (如 3层) 或 全量抓取 (所有分支)。
 */

public final class Subgraph {

    @JsonProperty("topicId")
    private final String topicId;

    @JsonProperty("rootId")
    private final String rootId;

    @JsonProperty("depth")
    private final int depth;

    @JsonProperty("nodes")
    private final List<KnowledgeNode> nodes;

    @JsonProperty("edges")
    private final List<SubgraphEdge> edges;

    @JsonCreator
    public Subgraph(
            @JsonProperty("rootId") String rootId,
            @JsonProperty("depth") int depth,
            @JsonProperty("nodes") List<KnowledgeNode> nodes,
            @JsonProperty("topicId") String topicId,
            @JsonProperty("edges") List<SubgraphEdge> edges) {
        this.rootId = rootId;
        this.depth = depth;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(nodes)));
        this.edges = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(edges)));
        this.topicId = topicId;
    }

    /**
     * 静态工厂：捕获子图
     *
     * @param rootNode  根节点 (注意：如果是深层抓取，该对象必须包含完整的子级关系数据)
     * @param maxDepth  最大抓取深度：
     * > 0 : 抓取指定层数 (例如 3)
     * -1  : 抓取所有分支直到叶子节点 (Subgraph.DEPTH_UNLIMITED)
     * @return 构建好的子图
     */
    public static Subgraph capture(KnowledgeNode rootNode, int maxDepth) {
        if (rootNode == null) {
            throw new IllegalArgumentException("根节点不能为空！！");
        }

        Map<String, KnowledgeNode> visitedNodes = new HashMap<>();
        List<SubgraphEdge> capturedEdges = new ArrayList<>();

        // BFS 初始化
        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.offer(new NodeDepthPair(rootNode, 0));
        visitedNodes.put(rootNode.getNodeId(), rootNode);

        while (!queue.isEmpty()) {
            NodeDepthPair current = queue.poll();
            KnowledgeNode currentNode = current.node;
            int currentDepth = current.depth;

            // === 修改核心逻辑：判断是否继续向下探索 ===
            // 有限深度 -> 必须 currentDepth < maxDepth 才探索
            boolean canExplore = (currentDepth < maxDepth);
            if (canExplore) {
                List<KnowledgeRelation> relations = currentNode.getRelationships();

                if (relations != null) {
                    for (KnowledgeRelation rel : relations) {
                        KnowledgeNode targetNode = rel.getTarget(); // 使用你之前修改后的 getTarget()

                        if (targetNode != null) {
                            // 1. 收集边
                            capturedEdges.add(new SubgraphEdge(
                                    currentNode.getNodeId(),
                                    targetNode.getNodeId(),
                                    rel.getDetail()
                            ));

                            // 2. 收集节点 (防止环路死循环的关键)
                            if (!visitedNodes.containsKey(targetNode.getNodeId())) {
                                visitedNodes.put(targetNode.getNodeId(), targetNode);
                                queue.offer(new NodeDepthPair(targetNode, currentDepth + 1));
                            }
                        }
                    }
                }
            }
        }

        return new Subgraph(
                rootNode.getNodeId(),
                maxDepth,
                new ArrayList<>(visitedNodes.values()),
                rootNode.getTopicId(),
                capturedEdges
        );
    }

    // ================== Getters ==================

    public String getRootId() { return rootId; }
    public int getDepth() { return depth; }
    public List<KnowledgeNode> getNodes() { return nodes; }
    public List<SubgraphEdge> getEdges() { return edges; }
    public String getTopicId() { return topicId; }

    // ================== Inner Classes ==================

    private static class NodeDepthPair {
        KnowledgeNode node;
        int depth;

        NodeDepthPair(KnowledgeNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    public static class SubgraphEdge {
        @JsonProperty("from")
        private final String from;

        @JsonProperty("to")
        private final String to;

        @JsonProperty("relation")
        private final String relation;

        @JsonCreator
        public SubgraphEdge(
                @JsonProperty("from") String from,
                @JsonProperty("to") String to,
                @JsonProperty("relation") String relation) {
            this.from = from;
            this.to = to;
            this.relation = relation;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getRelation() { return relation; }
    }
}