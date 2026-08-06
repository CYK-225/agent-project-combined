package org.example.AgentFan.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限注入 Hook
 * <p>
 * 在工具调用前（PreActingEvent）注入权限环境变量，
 * 使 Skill 脚本可通过 YOOFAN_TOKEN 环境变量获取用户权限。
 * <p>
 * 对应原项目 tool.py 中的权限注入逻辑和 _shared/permission_helper.py。
 */
@Slf4j
@Component
public class PermissionInjectionHook implements Hook {

    // 会话级 Token 缓存：sessionId → accessToken
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

    // 会话级 Skill 名称缓存：sessionId → skillName（用于判断是否需要商户权限）
    private final Map<String, String> sessionSkillNames = new ConcurrentHashMap<>();

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PreActingEvent pre) {
                onPreActing(pre);
            }
        } catch (Exception e) {
            log.error("[PermissionHook] 权限注入异常: {}", e.getMessage(), e);
        }
        return Mono.just(event);
    }

    /**
     * PreActing: 在工具调用前注入权限参数
     * <p>
     * 从 Agent 输入消息中提取 accessToken，缓存到会话上下文，
     * 供后续工具调用使用。
     */
    private void onPreActing(PreActingEvent event) {
        ToolUseBlock toolUse = event.getToolUse();
        String toolName = toolUse.getName();
        Map<String, Object> input = toolUse.getInput();

        // 提取 sessionId（从 agent 上下文或工具参数）
        String sessionId = extractSessionId(event, input);
        if (sessionId == null) {
            return;
        }

        // 从输入消息中提取 accessToken
        String accessToken = extractTokenFromMessages(event);
        if (accessToken != null) {
            sessionTokens.put(sessionId, accessToken);
            log.info("[PermissionHook] 缓存 accessToken, sessionId={}, token={}",
                    sessionId, maskToken(accessToken));
        }

        // 如果工具参数中包含 skill_name，记录用于后续判断
        if (input != null && input.containsKey("skill_name")) {
            String skillName = String.valueOf(input.get("skill_name"));
            sessionSkillNames.put(sessionId, skillName);
        }
    }

    /**
     * 获取会话的 accessToken
     */
    public String getAccessToken(String sessionId) {
        return sessionTokens.get(sessionId);
    }

    /**
     * 获取会话的 skillName
     */
    public String getSkillName(String sessionId) {
        return sessionSkillNames.get(sessionId);
    }

    /**
     * 判断是否需要强制商户权限（data_search 技能强制）
     */
    public boolean isForceMerchant(String sessionId) {
        String skillName = sessionSkillNames.get(sessionId);
        return "data_search".equals(skillName);
    }

    /**
     * 清理会话缓存
     */
    public void clearSession(String sessionId) {
        sessionTokens.remove(sessionId);
        sessionSkillNames.remove(sessionId);
    }

    // ==================== 辅助方法 ====================

    private String extractSessionId(PreActingEvent event, Map<String, Object> input) {
        // 从工具参数中提取
        if (input != null && input.containsKey("sessionId")) {
            return String.valueOf(input.get("sessionId"));
        }
        // 从 Agent 名称生成默认 sessionId
        String agentName = event.getAgent().getName();
        return agentName + "-" + Thread.currentThread().getId();
    }

    private String extractTokenFromMessages(PreActingEvent event) {
        // 从 Agent 的输入消息中提取 accessToken
        // Token 通常在用户请求的上下文中传递
        try {
            var agent = event.getAgent();
            // 尝试从工具参数中提取
            ToolUseBlock toolUse = event.getToolUse();
            Map<String, Object> input = toolUse.getInput();
            if (input != null) {
                // 检查常见的 token 参数名
                for (String key : List.of("accessToken", "token", "authorization", "YOOFAN_TOKEN")) {
                    if (input.containsKey(key)) {
                        return String.valueOf(input.get(key));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[PermissionHook] 提取 token 失败: {}", e.getMessage());
        }
        return null;
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    @Override
    public int priority() {
        return 10;
    }
}
