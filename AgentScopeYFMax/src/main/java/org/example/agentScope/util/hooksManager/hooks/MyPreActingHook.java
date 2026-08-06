package org.example.agentScope.util.hooksManager.hooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.common.openai.hook.JsonRepairUtil;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 针对 Agent 执行动作（工具调用）前的拦截钩子
 * <p>
 * 核心职责：校验工具参数的 JSON 合法性，自动修复非法格式，
 * 解决 DashScope API 报错 "function.arguments 参数必须是 JSON 格式" 的问题。
 */
@Component
@Slf4j
public class MyPreActingHook implements Hook {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 【触发时机】
     * 1. 当 Agent 模型推理完成，解析出需要调用某个工具（Tool）的指令后立即触发。
     * 2. 此时 Agent 尚未开始执行具体的 Java 方法或 API 调用。
     * 3. 它是你进行"动作前校验"、"参数重写"或"人工干预挂起"的最佳切入点。
     *
     * @param event 事件对象，包含即将执行的工具信息
     * @return 返回 Mono 包装的事件，确保响应式链路不中断
     */
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreActingEvent preActingEvent) {
            var agent = preActingEvent.getAgent();
            ToolUseBlock toolUse = preActingEvent.getToolUse();
            String funcName = toolUse.getName();
            Map<String, Object> arguments = toolUse.getInput();

            log.info(">>> [Hook 审计] Agent [{}] 准备调用工具: {}", agent.getName(), funcName);
            log.info(">>> [原始参数] : {}", arguments);

            // ===== JSON 参数校验 + 自动修复 =====
            Map<String, Object> repaired = validateAndRepair(arguments, funcName, agent.getName());
            if (repaired != null && repaired != arguments) {
                // 参数被修复，重建 ToolUseBlock 并回写
                ToolUseBlock newToolUse = ToolUseBlock.builder()
                        .id(toolUse.getId())
                        .name(funcName)
                        .input(repaired)
                        .content(toolUse.getContent())
                        .metadata(toolUse.getMetadata())
                        .build();
                preActingEvent.setToolUse(newToolUse);
                log.info(">>> [Hook 修复] Agent [{}] 工具 {} 参数已修复", agent.getName(), funcName);
            }
            // ===== 修复逻辑结束 =====
        }

        return Mono.just(event);
    }

    /**
     * 校验工具参数是否为合法 JSON Map，不合法则尝试自动修复。
     *
     * @return 修复后的 Map（如果发生了修复），或 null（如果无需修复或修复失败）
     */
    private Map<String, Object> validateAndRepair(Map<String, Object> input, String funcName, String agentName) {
        if (input == null) {
            log.warn(">>> [Hook 校验] 工具 {} 参数为 null，跳过校验", funcName);
            return null;
        }

        // 1. 检查 Map 中是否有值是 String 类型且内容是非法 JSON
        boolean needRepair = false;
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && looksLikeJson(str) && !isValidJson(str)) {
                log.warn(">>> [Hook 校验] 工具 {} 参数 '{} = {}' 看起来像 JSON 但格式非法",
                        funcName, entry.getKey(), str.substring(0, Math.min(100, str.length())));
                needRepair = true;
                break;
            }
        }

        if (!needRepair) {
            return null;
        }

        // 2. 尝试修复
        Map<String, Object> repaired = new HashMap<>(input);
        for (Map.Entry<String, Object> entry : repaired.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && looksLikeJson(str) && !isValidJson(str)) {
                String fixed = JsonRepairUtil.repair(str);
                if (fixed != null) {
                    // 尝试解析为 Map/List，替换原值
                    try {
                        Object parsed = MAPPER.readValue(fixed, Object.class);
                        entry.setValue(parsed);
                        log.info(">>> [Hook 修复] 参数 '{}' 已修复并解析", entry.getKey());
                    } catch (Exception e) {
                        log.warn(">>> [Hook 修复] 参数 '{}' 修复后仍无法解析: {}", entry.getKey(), e.getMessage());
                    }
                }
            }
        }

        return repaired;
    }

    /**
     * 判断字符串是否"看起来像"JSON（以 { 或 [ 开头，去除空白后）
     */
    private boolean looksLikeJson(String str) {
        if (str == null) return false;
        String trimmed = str.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[")
                || trimmed.startsWith("```") || trimmed.startsWith("\"");
    }

    /**
     * 判断字符串是否为合法 JSON
     */
    private boolean isValidJson(String str) {
        try {
            MAPPER.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int priority() {
        return 5;
    }
}
