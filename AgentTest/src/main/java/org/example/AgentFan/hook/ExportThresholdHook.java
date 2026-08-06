package org.example.AgentFan.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导出阈值判断 Hook
 * <p>
 * 在工具执行后（PostActingEvent）检查查询结果行数，
 * 当行数 >= 101 时触发导出提示。
 * <p>
 * 对应原项目 _shared/export_helper.py 中的 build_need_export_response() 逻辑。
 */
@Slf4j
@Component
public class ExportThresholdHook implements Hook {

    /**
     * 导出阈值：当查询结果行数 >= 此值时触发导出提示
     * <p>
     * 与原项目 export_helper.py 中的 EXPORT_ROW_THRESHOLD = 101 一致
     */
    private static final int EXPORT_THRESHOLD = 101;

    /**
     * 用于从 JSON 结果中提取 row_count 的正则
     */
    private static final Pattern ROW_COUNT_PATTERN = Pattern.compile(
            "\"row_count\"\\s*:\\s*(\\d+)");

    /**
     * 用于从 JSON 结果中提取 status 的正则
     */
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "\"status\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PostActingEvent post) {
                onPostActing(post);
            }
        } catch (Exception e) {
            log.error("[ExportHook] 导出阈值检查异常: {}", e.getMessage(), e);
        }
        return Mono.just(event);
    }

    /**
     * PostActing: 在工具执行后检查导出阈值
     * <p>
     * 检查工具返回结果中的 row_count，
     * 如果 >= 101 且状态为 need_export，记录导出提示。
     */
    private void onPostActing(PostActingEvent event) {
        ToolResultBlock toolResult = event.getToolResult();
        if (toolResult == null) {
            return;
        }

        String resultContent = extractToolResultContent(toolResult);
        if (resultContent == null || resultContent.isBlank()) {
            return;
        }

        // 检查是否已经是 need_export 状态（由 Skill 脚本判断）
        if (resultContent.contains("need_export")) {
            handleExportRequest(event, resultContent);
            return;
        }

        // 检查 row_count 是否超过阈值（由 Hook 层判断）
        int rowCount = parseRowCount(resultContent);
        if (rowCount >= EXPORT_THRESHOLD) {
            log.info("[ExportHook] 检测到超过阈值的结果, rowCount={}", rowCount);
            handleThresholdExceeded(event, resultContent, rowCount);
        }
    }

    /**
     * 处理 Skill 脚本返回的 need_export 状态
     */
    private void handleExportRequest(PostActingEvent event, String resultContent) {
        int rowCount = parseRowCount(resultContent);

        log.info("""
                [ExportHook] ════════════════════════════════════════════════
                [ExportHook]   导出阈值触发
                [ExportHook]   查询结果行数: {}
                [ExportHook]   建议: 用户可回复「是」或「导出」生成 Excel 文件
                [ExportHook] ════════════════════════════════════════════════""",
                rowCount);

        // 提取导出指令（如果存在）
        String exportInstruction = extractExportInstruction(resultContent);
        if (exportInstruction != null) {
            log.info("[ExportHook] 导出指令: {}", truncate(exportInstruction, 100));
        }
    }

    /**
     * 处理 Hook 层检测到的阈值超限
     * <p>
     * 当 Skill 脚本未做阈值判断时，由 Hook 层补充检查。
     */
    private void handleThresholdExceeded(PostActingEvent event, String resultContent, int rowCount) {
        log.info("""
                [ExportHook] ════════════════════════════════════════════════
                [ExportHook]   Hook 层检测到导出阈值
                [ExportHook]   查询结果行数: {} (阈值: {})
                [ExportHook]   建议: 调用 data_export.py 或 bill_export.py 导出
                [ExportHook] ════════════════════════════════════════════════""",
                rowCount, EXPORT_THRESHOLD);
    }

    /**
     * 从工具结果中提取文本内容
     */
    private String extractToolResultContent(ToolResultBlock result) {
        List<ContentBlock> output = result.getOutput();
        if (output != null) {
            for (ContentBlock block : output) {
                if (block instanceof TextBlock tb) {
                    return tb.getText();
                }
            }
        }
        return null;
    }

    /**
     * 从 JSON 结果中解析 row_count
     */
    private int parseRowCount(String content) {
        Matcher matcher = ROW_COUNT_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.debug("[ExportHook] 解析 row_count 失败: {}", matcher.group(1));
            }
        }
        return 0;
    }

    /**
     * 从 JSON 结果中提取导出指令
     */
    private String extractExportInstruction(String content) {
        Pattern pattern = Pattern.compile("\"export_instruction\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public int priority() {
        return 30;
    }
}
