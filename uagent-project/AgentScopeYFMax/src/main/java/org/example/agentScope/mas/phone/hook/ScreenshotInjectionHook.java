package org.example.agentScope.mas.phone.hook;

import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 截图注入 + toolUseId 缓存 Hook
 * <p>
 * <h3>两个职责</h3>
 * <ol>
 *   <li>PreReasoning: 将最新截图注入到 LLM 上下文（瞬态，不进 memory）</li>
 *   <li>PreActing: 工具调用前缓存 toolUseId（回调恢复时使用）</li>
 * </ol>
 * <p>
 * <h3>截图设计</h3>
 * 使用 {@code AtomicReference} 瞬态存储，每次覆盖，不累积。
 * 通过 {@code setInputMessages()} 注入，只影响本次 LLM 调用，不进入 memory。
 */
@Slf4j
public class ScreenshotInjectionHook extends AbstractAgentHook {

    /** 瞬态截图 — 每次覆盖，不累积 */
    private final AtomicReference<String> currentScreenshot = new AtomicReference<>();

    /** 当前挂起的 toolUseId — PreActing 时自动存入，回调恢复时取出 */
    private final AtomicReference<String> pendingToolUseId = new AtomicReference<>();

    /** 手机工具名前缀 */
    private static final String PHONE_TOOL_PREFIX = "gui_";

    public ScreenshotInjectionHook() {
        super(3); // 优先级 3
    }

    // ==================== 截图 ====================

    /**
     * 更新截图（固定 image/png）
     * <p>
     * 容器回调可能返回 Data URI 格式（如 "data:image/png;base64,iVBOR..."），
     * Base64Source.data 只接受纯 base64 数据，需去除前缀。
     */
    public void updateScreenshot(String base64) {
        if (base64 != null) {
            // 去除 Data URI 前缀（如 "data:image/png;base64,"）
            int commaIndex = base64.indexOf(',');
            if (commaIndex > 0 && base64.startsWith("data:")) {
                base64 = base64.substring(commaIndex + 1);
            }
        }
        currentScreenshot.set(base64);
        log.debug("[ScreenshotHook] 截图已更新");
    }

    // ==================== toolUseId ====================

    /**
     * 获取 toolUseId（回调恢复时调用）
     */
    public String getAndClearPendingToolUseId() {
        return pendingToolUseId.get();
    }

    /**
     * 清除所有状态（任务结束时）
     */
    public void clear() {
        currentScreenshot.set(null);
        pendingToolUseId.set(null);
    }

    /**
     * 清除截图（兼容外部调用）
     */
    public void clearScreenshot() {
        currentScreenshot.set(null);
    }

    // ==================== Hook 事件 ====================

    /**
     * 工具调用前 — 自动缓存手机工具的 toolUseId
     */
    @Override
    protected void handlePreActing(PreActingEvent event) {
        String toolName = event.getToolUse().getName();
        if (toolName != null && toolName.startsWith(PHONE_TOOL_PREFIX)) {
            String id = event.getToolUse().getId();
            pendingToolUseId.set(id);
            log.info("[ScreenshotHook] 缓存 toolUseId: {} → {}", toolName, id);
        }
    }

    /**
     * 推理前 — 注入最新截图到 LLM 上下文
     */
    @Override
    protected void handlePreReasoning(PreReasoningEvent event) {
        String screenshot = currentScreenshot.get();
        if (screenshot == null || screenshot.isBlank()) {
            return;
        }

        List<Msg> messages = event.getInputMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // 构建图片块（固定 image/png）
        ImageBlock imageBlock = ImageBlock.builder()
                .source(Base64Source.builder()
                        .mediaType("image/png")
                        .data(screenshot)
                        .build())
                .build();

        // 找到最后一条 USER 消息，追加图片
        List<Msg> modified = new ArrayList<>(messages);
        boolean injected = false;

        for (int i = modified.size() - 1; i >= 0; i--) {
            Msg msg = modified.get(i);
            if (msg.getRole() == MsgRole.USER) {
                List<io.agentscope.core.message.ContentBlock> content = new ArrayList<>(
                        msg.getContent() != null ? msg.getContent() : List.of()
                );
                content.add(TextBlock.builder().text("\n[当前手机屏幕截图]").build());
                content.add(imageBlock);
                modified.set(i, Msg.builder()
                        .role(msg.getRole())
                        .content(content)
                        .build());
                injected = true;
                break;
            }
        }

        if (!injected) {
            modified.add(Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(
                            TextBlock.builder().text("[当前手机屏幕截图]").build(),
                            imageBlock
                    ))
                    .build());
        }

        event.setInputMessages(modified);
        log.debug("[ScreenshotHook] 截图已注入到推理上下文");
    }
}
