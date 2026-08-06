package org.example.repository.Graph.Config;

import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableNeo4jRepositories(basePackages = "org.example.repository.Graph.Repository")
@ConditionalOnProperty(name = "spring.neo4j.enabled", havingValue = "true", matchIfMissing = true)
public class Neo4jConfig {

    /**
     * 事务管理器配置
     * Spring Data Neo4j 会自动注入 Driver（基于 application.yml 配置）
     */
    @Bean
    public Neo4jTransactionManager transactionManager(Driver driver,
                                                      DatabaseSelectionProvider databaseNameProvider) {
        return new Neo4jTransactionManager(driver, databaseNameProvider);
    }

    /**
     * (可选) 自定义 Cypher DSL 配置
     * 如果你需要用 Java 代码动态构建复杂 SQL，这个很有用
     */
    @Bean
    public org.neo4j.cypherdsl.core.renderer.Configuration cypherDslConfiguration() {
        return org.neo4j.cypherdsl.core.renderer.Configuration.newConfig()
                .withDialect(Dialect.NEO4J_5) // 指定 Neo4j 版本方言
                .build();
    }
}