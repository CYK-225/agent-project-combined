package org.example.agentScope.util.memory;

import io.agentscope.core.memory.reme.ReMeLongTermMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReMeLongTermMemoryBuilder {

    @Value("${reme.base-url}")
    private String baseUrl;

    public ReMeLongTermMemory build(String userId) {
        return ReMeLongTermMemory.builder()
                .userId(userId)
                .apiBaseUrl(baseUrl)
                .build();
    }
}
