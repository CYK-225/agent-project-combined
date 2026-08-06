package org.example.repository.Graph.Utils;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Neo4jUtil {

    /**
     * 计算两个向量的余弦相似度
     *
     * @param v1 向量 A
     * @param v2 向量 B
     * @return 相似度分数 (范围 -1.0 到 1.0，越接近 1.0 表示越相似)
     */
    public static double calculateCosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty()) {
            throw new IllegalArgumentException("Vectors cannot be null or empty");
        }
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            double val1 = v1.get(i);
            double val2 = v2.get(i);

            dotProduct += val1 * val2;
            normA += val1 * val1;
            normB += val2 * val2;
        }

        // 防止除以零 (虽然理论上非零向量模长不会为0)
        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
