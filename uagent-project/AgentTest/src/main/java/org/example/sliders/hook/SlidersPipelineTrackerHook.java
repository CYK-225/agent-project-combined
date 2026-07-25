package org.example.sliders.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SLIDERS 全流程跟踪 Hook
 * <p>
 * 可视化日志：每个阶段耗时 + 工具调用 + 最终汇总（问题 / 答案 / 总耗时）
 */
@Component
@Log4j2
public class SlidersPipelineTrackerHook implements Hook {

    @PostConstruct
    public void init() {
        log.info("[Tracker] SlidersPipelineTrackerHook 已加载");
    }

    // ── 每任务状态 ──
    private final Map<String, Long> pipelineStartTimes = new ConcurrentHashMap<>();   // taskId → start
    private final Map<String, String> questions = new ConcurrentHashMap<>();           // taskId → question
    private final Map<String, String> answers = new ConcurrentHashMap<>();             // taskId → answer
    private final Map<String, Long> agentStartTimes = new ConcurrentHashMap<>();       // agentName → start
    private final Map<String, Integer> agentToolCallCounts = new ConcurrentHashMap<>(); // agentName → count
    private final Map<String, Long> agentElapsedTotal = new ConcurrentHashMap<>();     // agentName → total ms
    private final Map<String, String> currentTaskId = new ConcurrentHashMap<>();       // agentName → taskId

    // ── 阶段元数据 ──
    private static final Map<String, String> STAGE = Map.of(
            "SlidersChunker",      "📦 文档分块 (Chunker)",
            "SlidersSchemaAgent",  "📐 Schema 归纳 (Schema)",
            "SlidersExtractor",    "🔍 结构化提取 (Extractor)",
            "SlidersReconciler",   "🔄 数据协调 (Reconciler)",
            "SlidersAnswer",       "💬 SQL 问答 (Answer)"
    );

    private static final Map<String, String> SYM = Map.of(
            "SlidersChunker",      "📦",
            "SlidersSchemaAgent",  "📐",
            "SlidersExtractor",    "🔍",
            "SlidersReconciler",   "🔄",
            "SlidersAnswer",       "💬"
    );

    // 阶段顺序（用于判断结束）
    private static final List<String> STAGE_ORDER = List.of(
            "SlidersChunker", "SlidersSchemaAgent", "SlidersExtractor",
            "SlidersReconciler", "SlidersAnswer"
    );

    // ==================== 事件分发 ====================

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PreReasoningEvent pre)        onPreReasoning(pre);
            else if (event instanceof PostReasoningEvent post) onPostReasoning(post);
            else if (event instanceof PreActingEvent pre)      onPreActing(pre);
            else if (event instanceof PostActingEvent post)    onPostActing(post);
        } catch (Exception e) {
            log.error("[Tracker] 日志异常: {}", e.getMessage(), e);
        }
        return Mono.just(event);
    }

    // ==================== PreReasoning: 阶段开始 ====================

    private void onPreReasoning(PreReasoningEvent event) {
        String agentName = event.getAgent().getName();
        String stage = STAGE.getOrDefault(agentName, agentName);
        String symbol = SYM.getOrDefault(agentName, "▶");

        agentStartTimes.put(agentName, System.currentTimeMillis());
        agentToolCallCounts.put(agentName, 0);

        // 提取 taskId
        String taskId = extractTaskId(event.getInputMessages());
        if (taskId != null) currentTaskId.put(agentName, taskId);

        // 流水线首次启动 → 记录开始时间 + 提取问题
        if (taskId != null && !pipelineStartTimes.containsKey(taskId)) {
            pipelineStartTimes.put(taskId, System.currentTimeMillis());
            String q = extractQuestion(event.getInputMessages());
            if (q != null) questions.put(taskId, q);
            log.info("""

                    ┌──────────────────────────────────────────────────────────────────┐
                    │  🚀 SLIDERS 流水线启动                                            │
                    │  TaskId : {}                                                     │
                    │  问题   : {}                                                     │
                    └──────────────────────────────────────────────────────────────────┘""",
                    taskId, truncate(q, 80));
        }

        // 判断是否是新阶段（Chunker = 第一阶段）
        boolean isFirst = "SlidersChunker".equals(agentName);
        if (isFirst) {
            log.info("""

                    ════════════════════════════════════════════════════════════════════
                    ▶  {} 开始
                    ════════════════════════════════════════════════════════════════════""",
                    stage);
        } else {
            log.info("""

                    ════════════════════════════════════════════════════════════════════
                    ▶  {} 开始
                    ════════════════════════════════════════════════════════════════════""",
                    stage);
        }
    }

    // ==================== PostReasoning: 阶段推理完成 ====================

    private void onPostReasoning(PostReasoningEvent event) {
        String agentName = event.getAgent().getName();
        String stage = STAGE.getOrDefault(agentName, agentName);
        String symbol = SYM.getOrDefault(agentName, "▶");

        long elapsed = getAndAccumElapsed(agentName);
        int toolCalls = agentToolCallCounts.getOrDefault(agentName, 0);

        // 检查是否有工具调用
        Msg response = event.getReasoningMessage();
        boolean hasToolCalls = false;
        if (response != null && response.getContent() != null) {
            hasToolCalls = response.getContent().stream()
                    .anyMatch(b -> b instanceof ToolUseBlock);
        }

        log.info("""

                └─ {} {} 推理完成 | 耗时: {} | 工具调用: {} 次 ────────┘""",
                symbol, stage, fmtMs(elapsed), toolCalls);

        // 如果是最后一个阶段（Answer）且不再有工具调用 → 流水线结束
        if ("SlidersAnswer".equals(agentName) && !hasToolCalls) {
            printSummary();
        }
    }

    // ==================== PreActing: 工具调用前 ====================

    private void onPreActing(PreActingEvent event) {
        String agentName = event.getAgent().getName();
        String symbol = SYM.getOrDefault(agentName, "▶");
        String toolName = event.getToolUse().getName();

        int count = agentToolCallCounts.merge(agentName, 1, Integer::sum);

        log.info("   │  {} #{} {}({})",
                symbol, count, toolName, summarizeToolParams(toolName, event.getToolUse()));
    }

    // ==================== PostActing: 工具执行后 ====================

    private void onPostActing(PostActingEvent event) {
        String agentName = event.getAgent().getName();
        String symbol = SYM.getOrDefault(agentName, "▶");
        String toolName = event.getToolUse().getName();

        ToolResultBlock toolResult = event.getToolResult();

        // 捕获 save_answer 的内容
        if ("save_answer".equals(toolName)) {
            Map<String, Object> input = event.getToolUse().getInput();
            if (input != null && input.containsKey("answer")) {
                String taskId = currentTaskId.getOrDefault(agentName, "unknown");
                answers.put(taskId, String.valueOf(input.get("answer")));
            }
        }

        String resultSummary = summarizeToolResult(toolName, toolResult);

        log.info("   │  {} → {}", symbol, resultSummary);
    }

    // ==================== 流水线汇总 ====================

    private void printSummary() {
        // 找到当前任务的 taskId
        String taskId = currentTaskId.values().stream().findFirst().orElse("unknown");
        Long start = pipelineStartTimes.get(taskId);
        long totalMs = start != null ? System.currentTimeMillis() - start : 0;

        String question = questions.getOrDefault(taskId, "(未捕获)");
        String answer = answers.getOrDefault(taskId, "(未捕获)");

        // 各阶段耗时
        StringBuilder stages = new StringBuilder();
        for (String agent : STAGE_ORDER) {
            Long elapsed = agentElapsedTotal.get(agent);
            String name = STAGE.getOrDefault(agent, agent);
            if (elapsed != null) {
                stages.append(String.format("   %-30s %s%n", name, fmtMs(elapsed)));
            }
        }

        log.info("""

                ╔══════════════════════════════════════════════════════════════════════╗
                ║                    📋 SLIDERS 流水线执行报告                            ║
                ╠══════════════════════════════════════════════════════════════════════╣
                ║  TaskId : {}
                ║  总耗时  : {}
                ╠══════════════════════════════════════════════════════════════════════╣
                ║  📝 问题:
                ║  {}
                ╠══════════════════════════════════════════════════════════════════════╣
                ║  💡 答案:
                ║  {}
                ╠══════════════════════════════════════════════════════════════════════╣
                ║  ⏱  各阶段耗时:
                {}╚══════════════════════════════════════════════════════════════════════╝""",
                taskId, fmtMs(totalMs), question, answer, stages);

        // 清理该任务的状态
        pipelineStartTimes.remove(taskId);
        questions.remove(taskId);
        answers.remove(taskId);
        agentStartTimes.clear();
        agentToolCallCounts.clear();
        agentElapsedTotal.clear();
        currentTaskId.clear();
    }

    // ==================== 辅助方法 ====================

    /** 计算本次耗时并累加到总耗时 */
    private long getAndAccumElapsed(String agentName) {
        Long start = agentStartTimes.get(agentName);
        long elapsed = start != null ? System.currentTimeMillis() - start : 0;
        agentElapsedTotal.merge(agentName, elapsed, Long::sum);
        return elapsed;
    }

    /** 从消息中提取 taskId */
    private String extractTaskId(List<Msg> messages) {
        if (messages == null) return null;
        for (Msg msg : messages) {
            if (msg.getContent() == null) continue;
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb) {
                    String text = tb.getText();
                    var m = java.util.regex.Pattern
                            .compile("(?:任务|taskId[=:]?|task[=:]?)\\s*([a-f0-9]{16})")
                            .matcher(text);
                    if (m.find()) return m.group(1);
                }
            }
        }
        return null;
    }

    /** 从消息中提取原始问题（取最后一段非 taskId 的文本） */
    private String extractQuestion(List<Msg> messages) {
        if (messages == null) return null;
        for (Msg msg : messages) {
            if (msg.getContent() == null) continue;
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb) {
                    String text = tb.getText();
                    // Node prompt 格式：包含 "请对任务 xxx 进行..."
                    // 问题在 Controller 保存，这里取 prompt 中的描述
                    if (text.contains("进行最终问答") || text.contains("进行数据协调")) {
                        continue; // 跳过 Node 的内部 prompt
                    }
                    // 取第一段有意义的文本作为问题
                    String trimmed = text.trim();
                    if (trimmed.length() > 10 && !trimmed.startsWith("请对任务")) {
                        return trimmed;
                    }
                }
            }
        }
        return null;
    }

    /** 工具参数摘要 */
    private String summarizeToolParams(String toolName, ToolUseBlock toolUse) {
        Map<String, Object> input = toolUse.getInput();
        if (input == null || input.isEmpty()) return "";

        return switch (toolName) {
            case "read_task_documents", "read_task_and_chunks", "read_schema_and_chunks",
                 "read_extracted_rows", "read_reconciled_data", "read_task_question" ->
                    "taskId=" + input.get("taskId");
            case "chunk_document" ->
                    truncate(String.valueOf(input.get("documentName")), 40);
            case "save_schema" ->
                    truncate(String.valueOf(input.get("schemaJson")), 60);
            case "save_extracted_rows" ->
                    truncate(String.valueOf(input.get("rowsJson")), 60);
            case "save_reconciled_table" ->
                    truncate(String.valueOf(input.get("rowsJson")), 60);
            case "save_chunks" ->
                    truncate(String.valueOf(input.get("chunksJson")), 60);
            case "execute_sql" ->
                    truncate(String.valueOf(input.get("sql")), 100);
            case "log_sql_query" ->
                    "sql=" + truncate(String.valueOf(input.get("sqlQuery")), 60);
            case "save_answer" ->
                    truncate(String.valueOf(input.get("answer")), 100);
            default -> truncate(input.toString(), 80);
        };
    }

    /** 工具结果摘要 */
    private String summarizeToolResult(String toolName, ToolResultBlock result) {
        if (result == null) return "(无结果)";

        String content = "";
        List<ContentBlock> output = result.getOutput();
        if (output != null) {
            for (ContentBlock block : output) {
                if (block instanceof TextBlock tb) {
                    content = tb.getText();
                    break;
                }
            }
        }
        if (content.isEmpty()) return "(空)";

        // 取第一行有意义的内容
        String firstLine = content.lines()
                .filter(l -> !l.isBlank() && !l.startsWith("✅") && !l.startsWith("❌"))
                .findFirst()
                .orElse(content.lines().findFirst().orElse(""));

        // 提取关键数字
        String key = switch (toolName) {
            case "read_task_documents"       -> extractNum(content, "共 (\\d+) 份");
            case "read_task_and_chunks"      -> extractNum(content, "共 (\\d+) 块");
            case "read_extracted_rows"       -> extractNum(content, "(\\d+) 行");
            case "read_reconciled_data"      -> extractNum(content, "(\\d+) 行");
            case "save_schema"               -> extractNum(content, "(\\d+) 个表");
            case "save_extracted_rows"       -> extractNum(content, "(\\d+) 行");
            case "save_reconciled_table"     -> extractNum(content, "(\\d+) 行");
            case "save_chunks"               -> extractNum(content, "(\\d+) 个");
            case "execute_sql"               -> extractNum(content, "(\\d+) 行");
            case "save_answer"               -> "✅ 答案已保存";
            case "log_sql_query"             -> "📝 已记录";
            default -> truncate(firstLine, 60);
        };

        String prefix = content.startsWith("❌") ? "❌" :
                        content.startsWith("✅") ? "✅" : "📄";

        return prefix + " " + key;
    }

    private String extractNum(String text, String pattern) {
        var m = java.util.regex.Pattern.compile(pattern).matcher(text);
        return m.find() ? m.group(1) + " " + pattern.substring(pattern.indexOf('(') + 1, pattern.indexOf(')')).replace("\\d+", "").replace("+", "")
                : truncate(text, 50);
    }

    private String fmtMs(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        return String.format("%dm%ds", ms / 60_000, (ms % 60_000) / 1000);
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public int priority() {
        return 100;
    }
}
