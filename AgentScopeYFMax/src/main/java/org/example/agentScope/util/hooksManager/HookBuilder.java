package org.example.agentScope.util.hooksManager;

import io.agentscope.core.hook.Hook;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Hook 构建器 —— 跳过 {@link HookListFactory}，自由组装 Hook 列表。
 * <p>
 * <b>三种使用方式：</b>
 *
 * <h3>方式 1：工厂（快捷，固定策略）</h3>
 * <pre>{@code
 * SessionContext sessionContext = new SessionContext();
 * sessionContext.add(supplyEvent);
 * List<Hook> hooks = HookListFactory.buildHookList("sp", sessionContext);
 * }</pre>
 *
 * <h3>方式 2：Builder（自由组装）</h3>
 * <pre>{@code
 * List<Hook> hooks = HookBuilder.create()
 *         .addDto(supplyEvent)                                  // 注入会话级 DTO
 *         .addDto(decisionEvent)
 *         .addFactory(sc -> new DecisionHook(sc))               // 工厂：需要 DTO，延迟创建
 *         .addFactory(sc -> new MasterGuardrailHook(sc))        // 工厂：需要 DTO，延迟创建
 *         .addFactory(sc -> new DynamicPickDishPromptHook(sc))  // 工厂：需要 DTO，延迟创建
 *         .add(new SPStateFeedbackHook())                       // 直接：不需要 DTO，立即添加
 *         .addOptional(() -> tryGetBean("myBean"))              // 可选：null 自动跳过
 *         .build();
 * }</pre>
 *
 * <h3>方式 3：混合 —— 工厂结果 + 追加自定义 Hook</h3>
 * <pre>{@code
 * List<Hook> hooks = HookBuilder.from(HookListFactory.buildHookList("log"))
 *         .addDto(myDto)
 *         .addFactory(sc -> new MyCustomHook(sc))
 *         .build();
 * }</pre>
 *
 * @author zhilin
 */
@Slf4j
public class HookBuilder {

    /**
     * -- GETTER --
     *  获取内部的 SessionContext（用于后续持久化）。
     *
     * @return SessionContext 实例
     */
    @Getter
    private final SessionContext sessionContext;
    private final List<Function<SessionContext, Hook>> hookFactories = new ArrayList<>();
    private final List<Hook> directHooks = new ArrayList<>();

    private HookBuilder() {
        this.sessionContext = new SessionContext();
    }

    private HookBuilder(SessionContext existingContext) {
        this.sessionContext = existingContext;
    }

    // ======================== 静态入口 ========================

    /**
     * 创建空的 Builder。
     *
     * @return 新的 HookBuilder 实例
     */
    public static HookBuilder create() {
        return new HookBuilder();
    }

    /**
     * 从已有的 Hook 列表开始构建（追加模式）。
     * <p>
     * 典型场景：工厂产出基础列表 + Builder 追加业务 Hook。
     *
     * @param existing 已有的 Hook 列表
     * @return 新的 HookBuilder 实例
     */
    public static HookBuilder from(List<Hook> existing) {
        HookBuilder builder = new HookBuilder();
        builder.directHooks.addAll(existing);
        return builder;
    }

    // ======================== DTO 注入 ========================

    /**
     * 注入会话级 DTO（可链式调用多次）。
     * <p>
     * DTO 按类型存储在 {@link SessionContext} 中，
     * 后续通过 {@link #addFactory(Function)} 按需获取。
     *
     * @param dto DTO 对象
     * @param <T> DTO 类型
     * @return this（链式调用）
     */
    public <T> HookBuilder addDto(T dto) {
        sessionContext.add(dto);
        return this;
    }

    // ======================== Hook 添加（三种方式，语义清晰） ========================

    /**
     * 直接添加一个 Hook 实例 —— 不依赖 DTO，立即可用。
     * <p>
     * <b>不允许传 null。</b>如果 Hook 可能为 null，请用 {@link #addOptional(Supplier)}。
     *
     * <pre>{@code
     * .add(new SPStateFeedbackHook())
     * .add(new StudioMessageHook(StudioManager.getClient()))
     * }</pre>
     *
     * @param hook Hook 实例
     * @return this（链式调用）
     * @throws IllegalArgumentException 如果 hook 为 null
     */
    public HookBuilder add(Hook hook) {
        if (hook == null) {
            throw new IllegalArgumentException("Hook 不允许为 null，可能为 null 时请使用 addOptional(Supplier)");
        }
        directHooks.add(hook);
        return this;
    }

    /**
     * 添加 Hook 工厂函数 —— 延迟创建，依赖 {@link SessionContext} 中的 DTO。
     * <p>
     * Hook 的创建延迟到 {@link #build()} 时执行，确保所有 DTO 已注入。
     *
     * <pre>{@code
     * .addFactory(sc -> new DecisionHook(sc))
     * .addFactory(sc -> new DynamicPickDishPromptHook(
     *         sc.get(SupplyPlanModelEvent.class),
     *         sc.get(DecisionMarkEvent.class)))
     * }</pre>
     *
     * @param hookFactory 接收 SessionContext，返回 Hook 实例
     * @return this（链式调用）
     */
    public HookBuilder addFactory(Function<SessionContext, Hook> hookFactory) {
        hookFactories.add(hookFactory);
        return this;
    }

    /**
     * 可选添加 —— 通过 Supplier 获取 Hook，返回 null 时自动跳过，不报错。
     * <p>
     * 典型场景：从 Spring 容器获取 Bean，Bean 不存在时不中断构建。
     *
     * <pre>{@code
     * .addOptional(() -> applicationContext.getBean("myBean", Hook.class))
     * }</pre>
     *
     * @param hookSupplier 返回 Hook 实例或 null 的 Supplier
     * @return this（链式调用）
     */
    public HookBuilder addOptional(Supplier<Hook> hookSupplier) {
        hookFactories.add(sc -> hookSupplier.get());
        return this;
    }

    // ======================== 构建 ========================

    /**
     * 构建最终的 Hook 列表。
     * <p>
     * 执行顺序：先添加的先执行（与 Hook priority 无关，由列表顺序决定）。
     *
     * @return Hook 列表
     */
    public List<Hook> build() {
        List<Hook> result = new ArrayList<>(directHooks);
        for (Function<SessionContext, Hook> factory : hookFactories) {
            try {
                Hook hook = factory.apply(sessionContext);
                if (hook != null) {
                    result.add(hook);
                }
            } catch (Exception e) {
                log.error("[HookBuilder] 创建 Hook 失败: {}", e.getMessage(), e);
            }
        }
        log.info("[HookBuilder] 构建完成，共 {} 个 Hook", result.size());
        return result;
    }

}
