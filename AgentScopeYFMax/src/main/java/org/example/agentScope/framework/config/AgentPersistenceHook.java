package org.example.agentScope.framework.config;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.session.SessionManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Data
public class AgentPersistenceHook implements Hook {

    private final String sessionId;
    private final SessionManager sessionManager;

    // 用于暂存当前轮次的用户输入
    private List<Msg> pendingUserMsgs = new ArrayList<>();

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        


        // 2. 拦截 AI 回复完成 (PostCall)
         if (event instanceof PostCallEvent postEvent) {
            return Mono.fromRunnable(() -> {
                try {

                    // 先保存 Agent 内部状态
                    sessionManager.saveSession();

                } catch (Exception e) {
                    log.error("持久化失败, Session: {}", sessionId, e);
                }
            }).thenReturn(event);
        }

        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 10;
    }


}