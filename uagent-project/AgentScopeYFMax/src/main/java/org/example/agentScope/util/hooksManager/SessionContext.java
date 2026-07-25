package org.example.agentScope.util.hooksManager;

import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.State;
import io.agentscope.core.state.StateModule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话级 DTO 容器 —— 实现 {@link StateModule}，可直接注册到 {@link io.agentscope.core.session.SessionManager}。
 * <p>
 * 各个 DTO 自行实现 {@link State} 标记接口，SessionContext 负责在 Hook 之间传递引用，
 * 并在 {@link #saveTo} / {@link #loadFrom} 时批量持久化 / 恢复所有 State DTO。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 1. 创建容器并注入 DTO
 * SessionContext sessionContext = new SessionContext();
 * sessionContext.add(supplyPlanModelEvent);
 * sessionContext.add(decisionMarkEvent);
 *
 * // 2. 注册到 SessionManager（框架自动持久化）
 * SessionManager.forSessionId(threadId)
 *     .withSession(postgresSession)
 *     .addComponent(sessionContext)   // ← 注册即可
 *     .addComponent(agent)
 *     .addComponent(memory);
 *
 * // 3. 恢复时逐个 loadFrom
 * sessionContext.loadFrom(session, sessionKey, SupplyPlanModelEvent.class);
 * sessionContext.loadFrom(session, sessionKey, DecisionMarkEvent.class);
 * }</pre>
 *
 * @author zhilin
 * @see StateModule
 * @see io.agentscope.core.session.SessionManager
 */
@Slf4j
public class SessionContext implements StateModule {

    private final Map<Class<?>, Object> store = new LinkedHashMap<>();
    /**
     * 添加常用的属性
     */

    @Getter
    private String threadId;

    public SessionContext withThreadId(String threadId) {
        this.threadId = threadId;
        return this;
    }

    // ======================== DTO 存取 ========================

    /**
     * 注入一个 DTO（按类型存储，同类型后者覆盖前者）。
     *
     * @param dto 要注入的 DTO 对象
     * @param <T> DTO 类型
     */
    public <T> void add(T dto) {
        if (dto != null) store.put(dto.getClass(), dto);
    }

    /**
     * 按类型获取 DTO。
     *
     * @param type DTO 的 Class
     * @param <T>  DTO 类型
     * @return DTO 实例，不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        return (T) store.get(type);
    }

    /**
     * 按类型获取 DTO，不存在时返回默认值。
     *
     * @param type         DTO 的 Class
     * @param defaultValue 默认值
     * @param <T>          DTO 类型
     * @return DTO 实例，不存在时返回 defaultValue
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(Class<T> type, T defaultValue) {
        return (T) store.getOrDefault(type, defaultValue);
    }

    /**
     * 是否包含指定类型的 DTO。
     *
     * @param type DTO 的 Class
     * @return 是否存在
     */
    public boolean contains(Class<?> type) {
        return store.containsKey(type);
    }

    /**
     * 获取所有已存储的 DTO（只读视图）。
     *
     * @return 不可修改的 Map
     */
    public Map<Class<?>, Object> getAll() {
        return Collections.unmodifiableMap(store);
    }

    // ======================== StateModule 实现 ========================

    /**
     * 批量持久化所有实现了 {@link State} 的 DTO。
     * <p>
     * 由 {@link io.agentscope.core.session.SessionManager#saveSession()} 自动调用，
     * key 使用 DTO 类的 {@link Class#getSimpleName() 简单类名}。
     * 未实现 State 的 DTO 会被跳过并打印警告。
     *
     * @param session    agentscope Session 实例
     * @param sessionKey 会话标识
     */
    @Override
    public void saveTo(Session session, SessionKey sessionKey) {
        for (Map.Entry<Class<?>, Object> entry : store.entrySet()) {
            Object dto = entry.getValue();
            if (dto instanceof State state) {
                String key = entry.getKey().getSimpleName();
                session.save(sessionKey, key, state);
                log.debug("[SessionContext] 已保存: {}", key);
            } else {
                log.warn("[SessionContext] {} 未实现 State 接口，跳过持久化",
                        entry.getKey().getSimpleName());
            }
        }
        log.info("[SessionContext] 批量保存完成，共 {} 个 DTO", store.size());
    }

    /**
     * 从 Session 恢复所有已注册类型的 DTO。
     * <p>
     * 由 {@link io.agentscope.core.session.SessionManager#loadIfExists()} 自动调用。
     * 仅恢复当前 store 中已存在的类型。
     *
     * @param session    agentscope Session 实例
     * @param sessionKey 会话标识
     */
    @Override
    public void loadFrom(Session session, SessionKey sessionKey) {
        for (Class<?> type : store.keySet()) {
            if (State.class.isAssignableFrom(type)) {
                @SuppressWarnings("unchecked")
                Class<? extends State> stateType = (Class<? extends State>) type;
                session.get(sessionKey, type.getSimpleName(), stateType)
                        .ifPresent(state -> {
                            store.put(type, state);
                            log.debug("[SessionContext] 已恢复: {}", type.getSimpleName());
                        });
            }
        }
    }

    /**
     * 从 Session 恢复指定类型的 DTO 并放入容器。
     * <p>
     * 适用于需要按需恢复的场景（如冷启动时逐个加载）。
     *
     * <pre>{@code
     * sessionContext.loadFrom(session, sessionKey, SupplyPlanModelEvent.class);
     * sessionContext.loadFrom(session, sessionKey, DecisionMarkEvent.class);
     * }</pre>
     *
     * @param session    agentscope Session 实例
     * @param sessionKey 会话标识
     * @param type       DTO 的 Class（必须实现 State）
     * @param <T>        DTO 类型
     */
    public <T extends State> void loadFrom(Session session, SessionKey sessionKey, Class<T> type) {
        session.get(sessionKey, type.getSimpleName(), type)
                .ifPresent(state -> {
                    store.put(type, state);
                    log.debug("[SessionContext] 已恢复: {}", type.getSimpleName());
                });
    }
}
