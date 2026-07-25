package org.example.repository.Graph.Repository;


import org.example.repository.Graph.Entity.KnowledgeNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// @Repository 已移除 — 由 Neo4jConfig 上的 @EnableNeo4jRepositories 负责扫描注册
// 当 spring.neo4j.enabled=false 时 Neo4jConfig 被跳过，此接口不会被加载
public interface KnowledgeRepository extends Neo4jRepository<KnowledgeNode, String> {

    // ==========================================
    // 1. 原子化写入 (修复了 Null 覆盖问题)
    // ==========================================

    /**
     * [更新版] 存储节点并保存 topicId
     */
    @Query("MERGE (n:KnowledgeNode {nodeId: $nodeId}) " +
            "ON CREATE SET n.detail = $detail, n.status = $status, n.embedding = $embedding, " +
            "n.description = $description, n.topic_id = $topicId " +
            "ON MATCH SET n.detail = $detail, n.status = $status, n.embedding = $embedding, " +
            "n.description = $description, n.topic_id = $topicId " +
            "RETURN n")
    KnowledgeNode saveOrUpdate(@Param("nodeId") String nodeId,
                               @Param("detail") String detail,
                               @Param("status") String status,
                               @Param("description") String description,
                               @Param("embedding") List<Double> embedding,
                               @Param("topicId") String topicId);

    /**
     * [更新版] 创建关系并保存 topicId
     */
    @Query("MATCH (from:KnowledgeNode {nodeId: $fromId}) " +
            "MATCH (to:KnowledgeNode {nodeId: $toId}) " +
            "OPTIONAL MATCH ()-[inRel]->(from) " +
            "WITH from, to, (coalesce(inRel.parent_detail, []) + [from.detail]) as newHistoryList " +
            "WITH from, to, newHistoryList, size(newHistoryList) as total " +
            "WITH from, to, newHistoryList, " +
            "     'Current: ' + newHistoryList[total-1] + " +
            "     CASE WHEN total > 1 " +
            "          THEN '\\n' + reduce(s='', i IN range(total-2, 0, -1) | " +
            "               s + ' ↳ [Step ' + (i+1) + ']: ' + newHistoryList[i] + (CASE WHEN i > 0 THEN '\\n' ELSE '' END)) " +
            "          ELSE '' " +
            "     END as formattedDetail " +
            "MERGE (from)-[r:RELATED_TO]->(to) " +
            "SET r.detail = formattedDetail, r.parent_detail = newHistoryList, " +
            "    r.embedding = $edgeEmbedding, r.topic_id = $topicId " + // <--- 确保边也存入 topic_id
            "RETURN from")
    KnowledgeNode createComplexRelation(@Param("fromId") String fromId,
                                        @Param("toId") String toId,
                                        @Param("edgeEmbedding") List<Double> edgeEmbedding,
                                        @Param("topicId") String topicId);

    /**
     * [会话查询] 获取指定 topicId 下的所有节点及其关联
     */
    @Query("MATCH (n:KnowledgeNode {topic_id: $topicId}) " +
            "OPTIONAL MATCH (n)-[r:RELATED_TO {topic_id: $topicId}]->(m:KnowledgeNode) " +
            "RETURN n, collect(r), collect(m)")
    List<KnowledgeNode> findAllByTopicId(@Param("topicId") String topicId);

    /**
     * 获取用于生成向量的上下文 (与存储格式保持一致)
     */
    @Query("MATCH (n:KnowledgeNode {nodeId: $nodeId}) " +
            "OPTIONAL MATCH ()-[inRel]->(n) " +
            "WITH n, (coalesce(inRel.parent_detail, []) + [n.detail]) as fullHistory " +
            "WITH fullHistory, size(fullHistory) as total " +
            "RETURN 'Source: ' + fullHistory[total-1] + " +
            "CASE WHEN total > 1 " +
            "     THEN '\\nContext History:\\n' + reduce(s='', i IN range(total-2, 0, -1) | " +
            "          s + 'Step ' + (i+1) + ': ' + fullHistory[i] + '\\n') " +
            "     ELSE '\\nRoot Node' END as fullContext")
    String getFullContextText(@Param("nodeId") String nodeId);


    // ==========================================
    // 4. 向量检索 (Vector Search)
    // ==========================================


    /**
     * [Search V3 Pro] 语义检索 + 智能上下文抓取 (性能优化版)
     * 逻辑：
     * 1. 向量检索召回 Top N 个候选节点。
     * 2. 针对每个节点，找到所有入边 (inRel)，按 parent_detail 长度倒序排列 (找最长历史路径)。
     * 3. 取出最佳上下文的 "文本" 和 "向量" (直接复用存储好的向量，避免 Service 层重算)。
     * 4. 组装返回结构，供 Service 层直接计算相似度。
     */
    @Query("CALL db.index.vector.queryNodes('vector_index', $limit, $queryVector) " +
            "YIELD node, score " +
            "WHERE score >= $threshold " +

            // --- 1. 寻找最佳上下文 (最长路径) ---
            "OPTIONAL MATCH ()-[inRel:RELATED_TO]->(node) " +
            "WITH node, score, inRel " +
            "ORDER BY size(coalesce(inRel.parent_detail, [])) DESC " +
            // 技巧：同时收集 文本(text) 和 向量(vector)，取第一条(head)
            "WITH node, score, head(collect({text: inRel.detail, vector: inRel.embedding})) as best_ctx " +

            // --- 2. 抓取当前节点的出边 (构建实体结构) ---
            "OPTIONAL MATCH (node)-[r:RELATED_TO]->(t:KnowledgeNode) " +
            "WITH node, score, best_ctx, collect(CASE WHEN r IS NULL THEN NULL ELSE { " +
            "    id: id(r), " +
            "    detail: r.detail, " +
            "    topic_id: r.topic_id, " +
            "    target: t { .*, nodeId: t.nodeId, topic_id: t.topic_id } " +
            "} END) as rels " +

            // --- 3. 返回最终 Map ---
            "RETURN { " +
            "    node: node { .*, nodeId: node.nodeId, topic_id: node.topic_id }, " +
            "    score: score, " +
            "    rels: rels, " +
            // 关键优化：如果存在最佳入边，用入边的文本和向量；否则(根节点)用节点自身的文本和向量
            "    history_context: coalesce(best_ctx.text, node.detail), " +
            "    history_vector:  coalesce(best_ctx.vector, node.embedding) " +
            "}")
    List<Map<String, Object>> searchNodes(@Param("queryVector") List<Double> queryVector,
                                          @Param("threshold") double threshold,
                                          @Param("limit") int limit);


    /**
     * 带分数的节点检索 (返回 Map)
     */
    @Query("CALL db.index.vector.queryNodes('vector_index', $limit, $queryVector) " +
            "YIELD node, score " +
            "WHERE score >= $threshold " +
            "RETURN node, score")
    List<Map<String, Object>> findSimilarNodesWithScore(@Param("queryVector") List<Double> queryVector,
                                                        @Param("threshold") double threshold,
                                                        @Param("limit") int limit);

    /**
     * 边向量搜索 (RAG 核心)
     * 在指定节点集合中搜索最相关的边
     */
    @Query("MATCH (n:KnowledgeNode)-[r:RELATED_TO]->(target:KnowledgeNode) " +
            "WHERE n.nodeId IN $nodeIds " +
            "AND r.embedding IS NOT NULL " +
            "WITH r, target, vector.similarity.cosine(r.embedding, $queryVector) AS score " +
            "WHERE score >= $threshold " +
            "RETURN r, target, score " +
            "ORDER BY score DESC " +
            "LIMIT $limit")
    List<Map<String, Object>> findEdgesByNodesAndEmbedding(@Param("nodeIds") List<String> nodeIds,
                                                           @Param("queryVector") List<Double> queryVector,
                                                           @Param("threshold") double threshold,
                                                           @Param("limit") int limit);


    // ==========================================
    // 5. 图谱结构查询 (For Subgraph DTO)
    // ==========================================

    /**
     * [关键] 查询指定深度的子图数据
     * 配合 Subgraph.capture(root, depth) 使用。
     * 逻辑：查找从 rootId 开始，路径长度 <= depth 的所有路径。
     * [*0..10] 是为了防止全图爆炸设置的安全上限。
     */
    @Query("MATCH p=(root:KnowledgeNode {nodeId: $rootId})-[*0..10]->(m) " +
            "WHERE length(p) <= $depth " +
            "RETURN root, collect(nodes(p)), collect(relationships(p))")
    Optional<KnowledgeNode> findDeepGraph(@Param("rootId") String rootId, @Param("depth") int depth);

    // ==========================================
    // 6. 其他基础查询
    // ==========================================

    Optional<KnowledgeNode> findByNodeId(String nodeId);

    Long deleteByNodeId(String nodeId);

    // 查找深度上下文
    @Query("MATCH (n:KnowledgeNode {nodeId: $nodeId})-[*1..3]-(m:KnowledgeNode) RETURN DISTINCT m")
    List<KnowledgeNode> findNodesByDepth(@Param("nodeId") String nodeId);

    // 路径查找
    @Query("MATCH p = shortestPath((start:KnowledgeNode {nodeId: $startId})-[*..10]-(end:KnowledgeNode {nodeId: $endId})) RETURN p")
    List<Object> findPathBetween(@Param("startId") String startId, @Param("endId") String endId);


    /**
     * [批量回溯] 查找指定节点集合的"最长祖先路径"来源边
     * 逻辑：
     * 1. 匹配所有目标节点 (target)。
     * 2. 找出所有指向这些目标节点的入边 (source)-[r]->(target)。
     * 3. 按关系中的 parent_detail 列表长度降序排列 (长度越长，说明祖先路径越完整)。
     * 4. 对每个 target 节点，只保留排在第一位的那条边 (head)。
     * * @param nodeIds 目标节点的 ID 列表
     * @return 返回 Map 包含: source(源节点), rel(关系), target(目标节点)
     */
    @Query("MATCH (target:KnowledgeNode) " +
            "WHERE target.nodeId IN $nodeIds " +
            "MATCH (source:KnowledgeNode)-[r:RELATED_TO]->(target) " +
            "WITH target, source, r " +
            // 关键排序：按祖先路径长度倒序 (若无 parent_detail 则视为长度 0)
            "ORDER BY size(coalesce(r.parent_detail, [])) DESC " +
            // 关键聚合：按 target 分组，只取由 collect 收集后的第一个结果 (即最长的那个)
            "WITH target, head(collect({s: source, r: r})) as best " +
            "RETURN best.s as source, best.r as rel, target")
    List<Map<String, Object>> findBestPathEdges(@Param("nodeIds") List<String> nodeIds);
}