package org.example.common.openai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.common.openai.config.OpenAiAdapterProperties;
import org.example.common.openai.dispatcher.StatelessAgentDispatcher;
import org.example.common.openai.hook.OpenAiFormatHook;
import org.example.common.openai.model.OpenAiChatRequest;
import org.example.common.stream.manager.SseEmitterManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI 兼容 API 控制器
 * <p>
 * 伪装成 OpenAI 服务端，让 LobeChat、ChatGPT-Next-Web 等前端无缝接入。
 * 通过 group 配置控制哪些 Agent 暴露给外部。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenAiApiController {

    private final AgentPoolManager agentPoolManager;
    private final StatelessAgentDispatcher dispatcher;
    private final OpenAiAdapterProperties properties;
    private final ObjectMapper objectMapper;

    // ==================== 模型列表 ====================

    /**
     * 返回可用模型列表
     * <p>
     * 只暴露属于配置分组（openai.adapter.group）的 Agent。
     * LobeChat 配置界面会调这个接口拉取可选模型。
     * </p>
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        String group = properties.getGroup();

        // 从分组索引获取该组所有 Agent 名称
        Set<String> agentNames = agentPoolManager.getGroupIndex()
                .getOrDefault(group, Set.of());

        List<Map<String, String>> models = agentNames.stream()
                .map(name -> {
                    AgentPoolManager.AgentMetadata meta =
                            agentPoolManager.getMetadataRegistry().get(name);
                    return Map.<String, String>of(
                            "id", name,
                            "object", "model",
                            "owned_by", "agentscope"
                    );
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "object", "list",
                "data", models
        ));
    }

    // ==================== 核心对话接口 ====================

    /**
     * OpenAI 兼容的 Chat Completions 接口
     * <p>
     * 同时支持流式 (SSE) 和非流式两种模式
     * </p>
     */
    @PostMapping(value = "/chat/completions")
    public Object chatCompletions(@RequestBody OpenAiChatRequest request) {

        String taskId = UUID.randomUUID().toString();
        String agentName = request.getModel();

        log.info("[OpenAI API] 收到请求, model={}, stream={}, messages={}",
                agentName, request.isStream(),
                request.getMessages() != null ? request.getMessages().size() : 0);

        // 1. 校验 Agent 是否属于允许的分组
        Set<String> allowedAgents = agentPoolManager.getGroupIndex()
                .getOrDefault(properties.getGroup(), Set.of());

        if (!allowedAgents.contains(agentName)) {
            log.warn("[OpenAI API] Agent 不在允许的分组中: {}, group={}", agentName, properties.getGroup());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of(
                            "message", "Model not found: " + agentName,
                            "type", "invalid_request_error",
                            "code", "model_not_found"
                    )
            ));
        }

        // 2. 流式模式
        if (request.isStream()) {
            return handleStream(request, taskId, agentName);
        }

        // 3. 非流式模式
        return handleNonStream(request, taskId, agentName);
    }

    // ==================== 流式处理 ====================

    private SseEmitter handleStream(OpenAiChatRequest request, String taskId, String agentName) {

        SseEmitter emitter = SseEmitterManager.createEmitter(taskId, properties.getSseTimeout());
        OpenAiFormatHook hook = new OpenAiFormatHook(taskId, agentName, objectMapper);

        Thread.ofVirtual().name("openai-stream-" + taskId.substring(0, 8)).start(() -> {
            try {
                log.info("[OpenAI API] 开始流式执行, taskId={}, agent={}", taskId, agentName);
                dispatcher.dispatch(request, hook);
                hook.finish();
                log.info("[OpenAI API] 流式执行完成, taskId={}", taskId);
            } catch (Exception e) {
                log.error("[OpenAI API] 流式执行异常, taskId={}", taskId, e);
                hook.errorFinish(e);
            }
        });

        return emitter;
    }

    // ==================== 非流式处理 ====================

    private ResponseEntity<Map<String, Object>> handleNonStream(
            OpenAiChatRequest request, String taskId, String agentName) {

        try {
            log.info("[OpenAI API] 开始非流式执行, agent={}", agentName);

            OpenAiFormatHook hook = new OpenAiFormatHook(taskId, agentName, objectMapper);
            String replyText = dispatcher.dispatch(request, hook);

            String chatId = "chatcmpl-" + taskId.replace("-", "").substring(0, 24);

            Map<String, Object> response = Map.of(
                    "id", chatId,
                    "object", "chat.completion",
                    "created", System.currentTimeMillis() / 1000,
                    "model", agentName,
                    "choices", List.of(
                            Map.of(
                                    "index", 0,
                                    "message", Map.of(
                                            "role", "assistant",
                                            "content", replyText != null ? replyText : ""
                                    ),
                                    "finish_reason", "stop"
                            )
                    ),
                    "usage", Map.of(
                            "prompt_tokens", 0,
                            "completion_tokens", 0,
                            "total_tokens", 0
                    )
            );

            log.info("[OpenAI API] 非流式执行完成, agent={}", agentName);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[OpenAI API] 非流式执行异常, agent={}", agentName, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", Map.of(
                            "message", "Internal error: " + e.getMessage(),
                            "type", "server_error"
                    )
            ));
        }
    }
}
