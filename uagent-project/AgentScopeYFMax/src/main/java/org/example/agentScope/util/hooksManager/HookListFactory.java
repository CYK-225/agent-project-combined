package org.example.agentScope.util.hooksManager;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import lombok.extern.slf4j.Slf4j;

import org.example.agentScope.util.hooksManager.hooks.AllHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hook 策略工厂 —— 根据业务场景标识动态组装 Hook 列表。
 * <p>
 * 内部委托 {@link HookBuilder} 完成实际构建，
 * 同时保留对 Spring Bean（AllHook、ApplicationContext）的静态访问。
 * <p>
 * <b>两种调用方式：</b>
 * <pre>{@code
 * // 方式 1：工厂（固定策略）
 * List<Hook> hooks = HookListFactory.buildHookList("sp", sessionContext);
 *
 * // 方式 2：Builder（自由组装，见 HookBuilder）
 * List<Hook> hooks = HookBuilder.create().addDto(...).addFactory(...).build();
 * }</pre>
 *
 * @author zhilin
 */
@Component
@Slf4j
public class HookListFactory {

    // ======================== Spring 注入（静态持有） ========================

    private static AllHook allHook;
    private static ApplicationContext applicationContext;

    @Autowired
    public void setAllHook(AllHook allHookInstance) {
        HookListFactory.allHook = allHookInstance;
    }

    @Autowired
    public void setApplicationContext(ApplicationContext ctx) {
        HookListFactory.applicationContext = ctx;
    }

    // ======================== 公开 API ========================

    /**
     * 无 DTO 版本（向后兼容）。
     */
    public static List<Hook> buildHookList(String hooksType) {
        return buildHookList(hooksType, new SessionContext());
    }

    /**
     * 支持通过 {@link SessionContext} 传入会话级 DTO 的重载版本。
     * <p>
     * 内部使用 {@link HookBuilder} 构建，同时注入 Spring 管理的框架 Hook。
     *
     * @param hooksType     策略标识
     * @param sessionContext 会话上下文，携带业务 DTO
     * @return Hook 列表
     */
    public static List<Hook> buildHookList(String hooksType, SessionContext sessionContext) {
        return switch (hooksType) {
            case "Review" -> buildReviewHooks(sessionContext);
            case "log"    -> buildLogHooks(sessionContext);
            case "finance"-> buildFinanceHooks(sessionContext);
            case "sp"     -> buildSpHooks(sessionContext);
            case "debug"  -> buildDebugHooks(sessionContext);
            case "sliders"-> buildSlidersHooks(sessionContext);
            default -> {
                log.error("未识别的 hooksType: {}", hooksType);
                yield new ArrayList<>();
            }
        };
    }

    // ======================== 各策略实现 ========================

    /**
     * Review 策略：推理结果审计 + 动作拦截 + 最终结果修正。
     */
    private static List<Hook> buildReviewHooks(SessionContext sessionContext) {
        return HookBuilder.create()
                .add(allHook.getMyPostReasoningHook())
                .add(allHook.getMyPostReasoningHook())
                .add(allHook.getMyPreActingHook())
                .add(allHook.getMyPostActingHook())
                .add(allHook.getMyPostCallHook())
                .build();
    }

    /**
     * Log 策略：输入监控 + 流式输出实时日志。
     */
    private static List<Hook> buildLogHooks(SessionContext sessionContext) {
        return HookBuilder.create()
                .add(allHook.getMyPreCallHook())
                .add(allHook.getMyReasoningChunkHook())
                .add(allHook.getMyActingChunkHook())
                .add(new StudioMessageHook(StudioManager.getClient()))
                .build();
    }

    /**
     * Finance 策略：状态反馈 + Studio 监控。
     */
    private static List<Hook> buildFinanceHooks(SessionContext sessionContext) {
        HookBuilder builder = HookBuilder.create();

        try {
            Hook stateFeedbackHook = applicationContext.getBean("stateFeedbackHook", Hook.class);
            builder.add(stateFeedbackHook);
        } catch (Exception e) {
            log.warn("未找到 StateFeedbackHook Bean，跳过: {}", e.getMessage());
        }

        return builder
                .add(new StudioMessageHook(StudioManager.getClient()))
                .build();
    }

    /**
     * SP 策略：供给计划决策链路（PreCall + Chunk + 状态反馈 + Studio）。
     * <p>
     * 所需 DTO（通过 SessionContext 传入）:
     * <ul>
     *   <li>SupplyPlanModelEvent</li>
     *   <li>DecisionMarkEvent（可选）</li>
     * </ul>
     */
    private static List<Hook> buildSpHooks(SessionContext sessionContext) {
        return HookBuilder.create()
                .add(allHook.getMyPreCallHook())
                .add(allHook.getMyReasoningChunkHook())
                .add(allHook.getMyActingChunkHook())
                .addOptional(() -> tryGetBean("spStateFeedbackHook"))
                .add(new StudioMessageHook(StudioManager.getClient()))
                .build();
    }

    /**
     * Debug 策略：仅捕获异常。
     */
    private static List<Hook> buildDebugHooks(SessionContext sessionContext) {
        return HookBuilder.create()
                .add(allHook.getMyErrorHook())
                .build();
    }

    /**
     * SLIDERS 策略：全流程跟踪 + 日志 + 错误捕获 + JSON 修复。
     */
    private static List<Hook> buildSlidersHooks(SessionContext sessionContext) {
        return HookBuilder.create()
                .add(allHook.getMyPreCallHook())
                .add(allHook.getMyPreReasoningHook())   // JSON 修复：推理前扫描历史消息
                .add(allHook.getMyPreActingHook())       // JSON 修复：工具执行前校验参数
                .add(allHook.getMyReasoningChunkHook())
                .add(allHook.getMyActingChunkHook())
                .addOptional(() -> tryGetBean("slidersPipelineTrackerHook"))
                .build();
    }

    // ======================== 工具方法 ========================

    /**
     * 尝试从 Spring 容器获取 Bean，获取失败返回 null。
     */
    private static Hook tryGetBean(String beanName) {
        try {
            return applicationContext.getBean(beanName, Hook.class);
        } catch (Exception e) {
            log.warn("未找到 {} Bean，跳过: {}", beanName, e.getMessage());
            return null;
        }
    }
}
