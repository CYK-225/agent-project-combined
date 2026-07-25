package org.example.repository.Graph.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.repository.Graph.Entity.KnowledgeNode;
import org.example.repository.Graph.Entity.KnowledgeRelation;
import org.example.repository.Graph.Entity.Subgraph;
import org.example.repository.Graph.Entity.TopicGraph;
import org.example.repository.Graph.Repository.KnowledgeRepository;

import org.example.repository.Graph.Utils.Neo4jUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.repository.Milvus.embedding.EmbeddingUtils.embedDenseReturnDouble;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.neo4j.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;

    /**
     * [Action: Store] 存储或更新知识点
     * 逻辑：自动处理向量生成，并确保存储当前节点所属的 topicId。
     */
    @Transactional
    public KnowledgeNode store(KnowledgeNode node) {
        log.info("Storing knowledge node: {} for topic: {}", node.getNodeId(), node.getTopicId());

        // 1. 准备向量数据 (使用返回 List<Double> 的新工具方法)
        List<Double> numericEmbedding = node.getEmbedding();
        if ((numericEmbedding == null || numericEmbedding.isEmpty()) && node.getDetail() != null) {
            numericEmbedding = embedDenseReturnDouble(node.getDetail());
        }

        // 2. 调用原子化存储，显式传递各个参数及 topicId
        return knowledgeRepository.saveOrUpdate(
                node.getNodeId(),
                node.getDetail(),
                node.getStatus(),
                node.getDescription(),
                numericEmbedding,
                node.getTopicId()
        );
    }

    /**
     * [Action: Link] 建立携带"全息有序上下文"的智能关联
     * 逻辑：
     * 1. 抓取源节点的历史路径文本。
     * 2. 生成包含关系标签和历史背景的边向量。
     * 3. 在数据库中建立关联，并打上 topicId 标签实现会话隔离。
     */
    @Transactional
    public void link(String fromId, String toId, String relationLabel, String topicId) {
        log.info("Linking: {} -> {} in topic: {}", fromId, toId, topicId);

        // 1. 获取包含历史路径的完整上下文文本 (用于生成更有语义的边向量)
        String contextText = knowledgeRepository.getFullContextText(fromId);

        // 2. 生成边的向量
        String textToEmbed = relationLabel + " (" + contextText + ")";
        List<Double> edgeEmbedding = embedDenseReturnDouble(textToEmbed);

        // 3. 执行关联 (Repository 将自动处理 Step 1, Step 2... 格式的累积 detail)
        knowledgeRepository.createComplexRelation(fromId, toId, edgeEmbedding, topicId);
    }
    /**
     * [Search V3: Rerank & Final Filter]
     * 逻辑修正与性能优化版：
     * 1. 一审（召回）：使用 0.0 阈值从数据库"海选"大量候选集。
     * 2. 二审（打分）：利用数据库返回的预存向量计算上下文相似度（空间换时间）。
     * 3. 最终筛选：计算 (一审+二审)/2 的平均分，只有平均分 >= threshold 才能留下。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchWithRerank(String queryText, String contextText, int limit, double threshold) {
        // 1. 基础校验
        if (queryText == null || queryText.isBlank()) {
            return Collections.emptyList();
        }

        // 2. [一审：宽泛召回]
        // 扩大搜索范围 (limit * 5)，防止一审排名靠后的"偏科生"没机会进入二审
        List<Double> queryVector = embedDenseReturnDouble(queryText);
        int recallLimit = (limit <= 0) ? 20 : limit * 5;
        // 数据库层面不做严格过滤 (0.0)，确保拿到所有潜在相关节点
        double recallMinScore = 0.0;

        List<Map<String, Object>> rawResults = knowledgeRepository.searchNodes(queryVector, recallMinScore, recallLimit);

        if (rawResults.isEmpty()) return Collections.emptyList();

        log.info("Recall candidates: {}, Context provided: {}", rawResults.size(), (contextText != null && !contextText.isBlank()));

        // 3. [二审准备] 生成目标上下文的向量
        List<Double> targetContextVector = (contextText != null && !contextText.isBlank())
                ? embedDenseReturnDouble(contextText)
                : null;

        // 4. [流式处理：算分 -> 平均 -> 最终过滤 -> 排序]
        return rawResults.stream()
                .map(row -> {
                    // --- A. 提取一审分数 ---
                    Double vectorScore = (Double) row.get("score");

                    // --- B. 计算二审分数 ---
                    double contextScore;
                    if (targetContextVector != null) {
                        // 情况 1: 用户提供了上下文要求
                        // 优化点：直接从 Map 获取数据库查出的向量，不再实时调用 Embedding API
                        List<Double> historyVector = (List<Double>) row.get("history_vector");

                        if (historyVector != null && !historyVector.isEmpty()) {
                            // 纯内存计算，毫秒级响应
                            contextScore = Neo4jUtil.calculateCosineSimilarity(targetContextVector, historyVector);
                        } else {
                            // 有要求但节点无历史上下文，判 0 分
                            contextScore = 0.0;
                        }
                    } else {
                        // 情况 2: 用户未提供上下文
                        // 二审分默认等于一审分，保证平均分不变，逻辑退化为普通搜索
                        contextScore = vectorScore;
                    }

                    // --- C. 计算平均分 (最终成绩) ---
                    double finalScore = (vectorScore + contextScore) / 2.0;

                    // --- D. 封装数据 ---
                    Map<String, Object> mappedResult = mapSearchRow(row);
                    mappedResult.put("score", finalScore); // 覆盖为最终平均分

                    // 调试字段：方便前端查看分数构成
                    String contextPreview = (String) row.get("history_context");
                    mappedResult.put("debug_scores", Map.of(
                            "1_vector_recall", vectorScore,
                            "2_context_rerank", contextScore,
                            "3_final_avg", finalScore,
                            "used_context_preview", (contextPreview != null && contextPreview.length() > 20)
                                    ? contextPreview.substring(0, 20) + "..." : "N/A"
                    ));
                    return mappedResult;
                })
                // --- E. [核心逻辑] 在算出平均分后，才进行真正的"筛选" ---
                .filter(item -> (double) item.get("score") >= threshold)
                // --- F. 排序 ---
                .sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")))
                // --- G. 截断 ---
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * [辅助方法] Map -> KnowledgeNode 转换
     * 保持不变，用于将原始 Map 数据转为实体结构
     */
    private Map<String, Object> mapSearchRow(Map<String, Object> row) {
        Map<String, Object> nodeData = (Map<String, Object>) row.get("node");
        // 注意：这里的 score 是 map 过程中的临时变量，最终会被上面的流处理覆盖
        Double score = (Double) row.get("score");
        List<Map<String, Object>> relsData = (List<Map<String, Object>>) row.get("rels");

        KnowledgeNode entity = mapToEntity(nodeData);

        List<KnowledgeRelation> relations = new ArrayList<>();
        if (relsData != null) {
            for (Map<String, Object> r : relsData) {
                if (r == null) continue;
                Map<String, Object> targetData = (Map<String, Object>) r.get("target");
                KnowledgeRelation relEntity = KnowledgeRelation.builder()
                        .id((Long) r.get("id"))
                        .detail((String) r.get("detail"))
                        .topicId((String) r.get("topic_id"))
                        .target(mapToEntity(targetData))
                        .build();
                relations.add(relEntity);
            }
        }
        entity.setRelationships(relations);

        Map<String, Object> result = new HashMap<>();
        result.put("entity", entity);
        result.put("score", score);
        return result;
    }

    private KnowledgeNode mapToEntity(Map<String, Object> data) {
        if (data == null) return null;
        return KnowledgeNode.builder()
                .nodeId((String) data.get("nodeId"))
                .topicId((String) data.get("topic_id"))
                .detail((String) data.get("detail"))
                .status((String) data.get("status"))
                .description((String) data.get("description"))
                .relationships(new ArrayList<>())
                .build();
    }

    public Map<String, Object> compareText(String text1, String text2) {
        log.info("Testing similarity between: [{}] AND [{}]", text1, text2);

        // 1. 生成向量 (这里复用你现有的 embedding 工具)
        List<Double> v1 = embedDenseReturnDouble(text1);
        List<Double> v2 = embedDenseReturnDouble(text2);

        if (v1 == null || v2 == null) {
            throw new RuntimeException("Embedding generation failed for one of the inputs.");
        }

        // 2. 调用工具类计算相似度 (纯数学计算)
        double similarity = Neo4jUtil.calculateCosineSimilarity(v1, v2);

        // 3. 封装结果返回
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text1", text1);
        result.put("text2", text2);
        result.put("vectorDimension", v1.size()); // 顺便检查维度是否为 1536
        result.put("cosineSimilarity", similarity);

        // 简单评级
        String rating;
        if (similarity > 0.85) rating = "极高相关 (Strong)";
        else if (similarity > 0.75) rating = "相关 (Moderate)";
        else if (similarity > 0.70) rating = "弱相关 (Weak)";
        else rating = "不相关 (Irrelevant)";
        result.put("rating", rating);

        log.info("Calculated Similarity: {}", similarity);
        return result;
    }



    /**
     * [Action: Subgraph] 获取指定深度的子图结构
     * 场景：用于从特定根节点（如 "安装第一步"）向后探索。
     */
    @Transactional(readOnly = true)
    public Subgraph getGraph(String rootId, int depth) {
        log.info("Fetching subgraph for root: {}, depth: {}", rootId, depth);
        KnowledgeNode root = knowledgeRepository.findDeepGraph(rootId, depth)
                .orElseThrow(() -> new RuntimeException("Root node not found: " + rootId));

        return Subgraph.capture(root, depth);
    }

    /**
     * [Action: Topic Graph] 获取整个会话的完整图谱
     * 场景：基于 topicId 返回该任务下的所有节点和边，形成全局 DAG。
     */
    @Transactional(readOnly = true)
    public TopicGraph getTopicGraph(String topicId) {
        log.info("Generating full topic graph for: {}", topicId);
        // 1. 从数据库抓取该 topicId 下的所有节点及其内部连线
        List<KnowledgeNode> nodes = knowledgeRepository.findAllByTopicId(topicId);

        // 2. 使用静态工厂构建扁平化的图模型
        return TopicGraph.build(nodes, topicId);
    }

    /**
     * [Action: Delete] 删除节点
     */
    @Transactional
    public void delete(String nodeId) {
        log.info("Deleting node: {}", nodeId);
        knowledgeRepository.deleteByNodeId(nodeId);
    }
}