package org.example.agent.recommendStoreMenuAgent.manager;

import io.agentscope.core.memory.InMemoryMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MemoryManager {

    private final Map<String, InMemoryMemory> memoryStore = new ConcurrentHashMap<>();

    public InMemoryMemory get(String sessionId) {
        return memoryStore.computeIfAbsent(sessionId, k -> new InMemoryMemory());
    }

    public void clear(String sessionId) {
        memoryStore.remove(sessionId);
    }
}