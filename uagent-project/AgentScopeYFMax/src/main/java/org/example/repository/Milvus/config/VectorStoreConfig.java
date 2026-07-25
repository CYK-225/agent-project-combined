package org.example.repository.Milvus.config;/**
 * @Auter zzh
 * @Date 2025/10/13
 */



/**
 * @projectName: AItest
 * @package: org.example.aitest.service.Retrieval.RAG
 * @className: config
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/13 23:28
 * @version: 1.0
 */


import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;


import org.example.repository.Milvus.core.MilvusCrudHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConditionalOnProperty(name = "spring.ai.vectorstore.milvus.enabled", havingValue = "true", matchIfMissing = true)
public class VectorStoreConfig {


    @Value("${spring.ai.vectorstore.milvus.client.host}")
    private String milvusHost;
    @Value("${spring.ai.vectorstore.milvus.client.port}")
    private Integer milvusPort;


    @Bean
    public MilvusCrudHelper milvusCrudHelper(){
        return new MilvusCrudHelper(new MilvusClientV2(ConnectConfig.builder()
                .uri("http://"+milvusHost+":"+milvusPort).build()));
    }

}
