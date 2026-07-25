package org.example.repository.Graph.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.repository.Graph.Entity.KnowledgeNode;
import org.example.repository.Graph.Entity.Subgraph;
import org.example.repository.Graph.Entity.TopicGraph;
import org.example.repository.Graph.Service.KnowledgeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱核心控制器
 * 负责节点的 CRUD、语义搜索、子图探索及全量会话图构建
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.neo4j.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeGraphController {

    private final KnowledgeService knowledgeService;

    /**
     * [POST] 存储或更新节点
     * Service 会自动从 node 对象中提取 topicId 并处理向量存储
     */
    @PostMapping("/nodes")
    public ResponseEntity<KnowledgeNode> storeNode(@RequestBody KnowledgeNode node) {
        log.info("REST request to store/update node: {} for topic: {}", node.getNodeId(), node.getTopicId());
        KnowledgeNode result = knowledgeService.store(node);
        return ResponseEntity.ok(result);
    }

    /**
     * [POST] 创建智能关联
     * 接收参数: fromId, toId, relationLabel, topicId
     */
    @PostMapping("/links")
    public ResponseEntity<String> createLink(@RequestBody Map<String, String> payload) {
        String fromId = payload.get("fromId");
        String toId = payload.get("toId");
        String label = payload.getOrDefault("relationLabel", "Next Step");
        String topicId = payload.get("topicId"); // 从请求中获取会话标识

        if (fromId == null || toId == null || topicId == null) {
            return ResponseEntity.badRequest().body("fromId, toId, and topicId are all required.");
        }

        log.info("REST request to link nodes in topic {}: {} -> {}", topicId, fromId, toId);
        knowledgeService.link(fromId, toId, label, topicId);
        return ResponseEntity.ok("Link created successfully with cumulative context and session tracking.");
    }

    /**
     * [GET] 获取局部子图结构
     * 场景：以特定步骤为根节点，查看一定深度内的后续步骤
     */
    @GetMapping("/subgraph")
    public ResponseEntity<Subgraph> getSubgraph(
            @RequestParam String rootId,
            @RequestParam(defaultValue = "3") int depth) {

        log.info("REST request to get subgraph for root: {}, depth: {}", rootId, depth);
        Subgraph graph = knowledgeService.getGraph(rootId, depth);
        return ResponseEntity.ok(graph);
    }

    /**
     * [GET] 获取完整会话图 (新需求)
     * 场景：获取该 topicId 下所有的节点与边，形成完整的业务流程图
     */
    @GetMapping("/topic/{topicId}")
    public ResponseEntity<TopicGraph> getTopicGraph(@PathVariable String topicId) {
        log.info("REST request to get full topic graph for: {}", topicId);
        TopicGraph graph = knowledgeService.getTopicGraph(topicId);
        return ResponseEntity.ok(graph);
    }

    /**
     * [DELETE] 删除节点
     */
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(@PathVariable String nodeId) {
        log.info("REST request to delete node: {}", nodeId);
        knowledgeService.delete(nodeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [测试接口] 校验向量相似度计算
     * 用法: /api/graph/test-similarity?t1=启动MySQL&t2=开启数据库服务
     */
    @GetMapping("/test-similarity")
    public ResponseEntity<Map<String, Object>> testSimilarity(
            @RequestParam String t1,
            @RequestParam String t2) {
        return ResponseEntity.ok(knowledgeService.compareText(t1, t2));
    }

    /**
     * [GET] 高级语义搜索 (支持二审重排序)
     * 用法示例: /api/graph/search/advanced?query=启动&context=安装MySQL之后的操作&limit=5
     * @param query   一审关键词 (必填)
     * @param context 二审路径上下文 (选填，不填则退化为普通搜索)
     * @param limit   返回条数
     * @param threshold 阈值 (过滤分数较低的向量)
     */
    @GetMapping("/search/advanced")
    public ResponseEntity<List<Map<String, Object>>> searchNodesAdvanced(
            @RequestParam String query,
            @RequestParam(required = false) String context,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "0.7") Float threshold
            ) {

        log.info("REST request to advanced search. Query: [{}], Context: [{}]", query, context);

        List<Map<String, Object>> results = knowledgeService.searchWithRerank(query, context, limit, threshold);
        return ResponseEntity.ok(results);
    }
}