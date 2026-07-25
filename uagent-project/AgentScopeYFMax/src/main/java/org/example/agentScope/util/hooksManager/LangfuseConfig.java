package org.example.agentScope.util.hooksManager;

import com.langfuse.client.LangfuseClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangfuseConfig {

    @Bean
    public LangfuseClient langfuseClient() {
        // 使用 Builder 模式初始化
        return LangfuseClient.builder()
                .url("https://cloud.langfuse.com") // 根据需要替换为 US region 或本地部署地址
//                .url("https://us.cloud.langfuse.com") // 根据需要替换为 US region 或本地部署地址
                // 参数1: Public Key (username), 参数2: Secret Key (password)
//                .credentials("pk-lf-7273418f-515c-45c9-bd32-74b9bd32a697", "sk-lf-e8753517-b1b6-4e21-9c70-3ab5fc2af15e")
//                .credentials("pk-lf-fadc7759-73b0-4bbf-a8db-d54afe687db9", "sk-lf-f5e3c8d0-7aa4-4040-b4b2-9083bd370dce")
                .credentials("pk-lf-a0afd46b-7edf-4938-9d45-bfe99c5567c3", "sk-lf-2b9c158d-1b58-4ae2-97b6-8c4444882890")
                .build();
    }

}
