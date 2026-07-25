package org.example.repository.Graph.Config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.neo4j.enabled", havingValue = "true", matchIfMissing = true)
public class Neo4jVectorConfig {

    private final Driver driver;

    @PostConstruct
    public void createVectorIndexes() {
        try (Session session = driver.session()) {
            log.info("Checking and creating vector indexes...");
            String createNodeIndexQuery = """
                CREATE VECTOR INDEX vector_index IF NOT EXISTS
                FOR (n:KnowledgeNode)
                ON (n.embedding)
                OPTIONS {indexConfig: {
                    `vector.dimensions`: 1536,
                    `vector.similarity_function`: 'cosine'
                }}
            """;
            session.run(createNodeIndexQuery);
            log.info("Node vector index 'vector_index' ensured.");

            // 2. 创建关系的向量索引 (针对 RELATED_TO 关系的 embedding 属性)
            // Neo4j 5.x 支持关系向量索引
            String createRelIndexQuery = """
                CREATE VECTOR INDEX relation_vector_index IF NOT EXISTS
                FOR ()-[r:RELATED_TO]-()
                ON (r.embedding)
                OPTIONS {indexConfig: {
                    `vector.dimensions`: 1536,
                    `vector.similarity_function`: 'cosine'
                }}
            """;
            session.run(createRelIndexQuery);
            log.info("Relationship vector index 'relation_vector_index' ensured.");

        } catch (Exception e) {
            log.error("Failed to create vector indexes", e);
        }
    }
}