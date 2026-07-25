package org.example.agent.user;


import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import org.example.agent.recommendStoreMenuAgent.AgentService;
import org.example.agent.recommendStoreMenuAgent.domain.req.ChatRequest;
import org.example.agent.recommendStoreMenuAgent.domain.resp.ChatEvent;
import org.example.agent.recommendStoreMenuAgent.manager.MemoryManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/recommend-menu")
@RequiredArgsConstructor
public class RecommendStoreMenuController {
    @Autowired
    private AgentService agentService;

    @Autowired
    private MemoryManager memoryManager;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> chat(@RequestBody ChatRequest request) {
        return agentService
                .chat(request.getMessage(), request.getUserId())
                .map(event -> ServerSentEvent.<ChatEvent>builder().data(event).build());
    }

    @PostMapping(value = "/clear")
    public String clear(@RequestParam("userId") Long userId) {
        memoryManager.clear(String.valueOf(userId));
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status", 1);
        return jsonObject.toJSONString();
    }
}
