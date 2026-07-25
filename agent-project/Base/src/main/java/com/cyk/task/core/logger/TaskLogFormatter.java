package com.cyk.task.core.logger;



import com.cyk.task.DAL.DO.TaskInfoEntity;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务日志格式化工具类
 * 
 * <p>【核心职责】</p>
 * 统一任务流程中的日志打印格式，提供清晰的任务追踪能力。
 * 
 * <p>【日志格式】</p>
 * <pre>
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  [步骤名称] 任务ID=123 | 公司=示例公司 | 类型=AI | 配置=qcc_pro        ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  任务详情:                                                               ║
 * ║    - 任务ID: 123                                                         ║
 * ║    - 公司名称: 示例公司                                                  ║
 * ║    - 任务类型: AI                                                        ║
 * ║    - 任务状态: PENDING                                                   ║
 * ║    - 配置名称: qcc_pro                                                   ║
 * ║    - 是否独占: false                                                     ║
 * ║    - 重试次数: 0                                                         ║
 * ║    - 待收集字段: [genderRatio, ageRatio, avgSalary]                     ║
 * ║    - 创建时间: 2026-03-17 10:30:00                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * </pre>
 * 
 * @author system
 * @since 1.0
 */
public class TaskLogFormatter {

    /**
     * 分隔线长度
     */
    private static final int LINE_LENGTH = 80;

    /**
     * 分隔字符
     */
    private static final String LINE_CHAR = "═";

    /**
     * 步骤枚举
     */
    public enum Step {
        CREATE("创建任务", "🆕"),
        ENQUEUE("任务入队", "📥"),
        DEQUEUE("任务出队", "📤"),
        EXECUTE_START("启动执行", "🚀"),
        EXECUTE_RUNNING("执行中", "⚙️"),
        CALLBACK("回调处理", "📞"),
        SUCCESS("执行成功", "✅"),
        FAIL("执行失败", "❌"),
        RETRY("任务重试", "🔄"),
        AUTH_BIND("绑定鉴权", "🔑"),
        AUTH_RELEASE("释放鉴权", "🔓"),
        QUEUE_STATUS("队列状态", "📊"),
        THREAD_POOL("线程池", "🏊");

        private final String description;
        private final String icon;

        Step(String description, String icon) {
            this.description = description;
            this.icon = icon;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
    }

    /**
     * 格式化任务基本信息（单行）
     * 
     * @param task 任务实体
     * @return 格式化的字符串
     */
    public static String formatTaskBrief(TaskInfoEntity task) {
        if (task == null) {
            return "任务为null";
        }
        return String.format("任务ID=%d | 公司=%s | 类型=%s | 状态=%s | 配置=%s",
                task.getId(),
                safeStr(task.getCompanyName()),
                safeStr(task.getTaskType()),
                safeStr(task.getStatus()),
                safeStr(task.getConfigName())
        );
    }

    /**
     * 格式化任务详情（多行）
     * 
     * @param task 任务实体
     * @return 格式化的字符串
     */
    public static String formatTaskDetail(TaskInfoEntity task) {
        if (task == null) {
            return "任务为null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("  任务详情:\n");
        sb.append(formatField("任务ID", task.getId()));
        sb.append(formatField("公司ID", task.getCompanyId()));
        sb.append(formatField("公司名称", task.getCompanyName()));
        sb.append(formatField("任务类型", task.getTaskType()));
        sb.append(formatField("任务状态", task.getStatus()));
        sb.append(formatField("配置名称", task.getConfigName()));
        sb.append(formatField("目标网站", task.getWebAddress()));
        sb.append(formatField("是否独占", task.getIsExclusive()));
        sb.append(formatField("重试次数", task.getRetryCount()));
        sb.append(formatField("失败原因", task.getFailureReason()));
        sb.append(formatField("任务内容", task.getMission()));
        
        if (task.getCollectedFields() != null && !task.getCollectedFields().isEmpty()) {
            sb.append(formatField("待收集字段", formatFieldList(task.getCollectedFields())));
        }
        
        if (task.getCreateTimeMs() != null) {
            sb.append(formatField("创建时间戳", task.getCreateTimeMs()));
        }
        
        return sb.toString();
    }

    /**
     * 格式化步骤日志（带边框）
     * 
     * @param step 步骤
     * @param task 任务实体
     * @param extraInfo 额外信息
     * @return 格式化的日志字符串
     */
    public static String formatStepLog(Step step, TaskInfoEntity task, String extraInfo) {
        StringBuilder sb = new StringBuilder();
        
        // 顶部边框
        sb.append("\n").append(repeatChar("╔", 1)).append(repeatChar(LINE_CHAR, LINE_LENGTH - 2)).append("╗\n");
        
        // 标题行
        String title = String.format("║ %s [%s] %s", 
                step.getIcon(), 
                step.getDescription(), 
                formatTaskBrief(task));
        sb.append(padRight(title, LINE_LENGTH - 1)).append("║\n");
        
        // 分隔线
        sb.append(repeatChar("╠", 1)).append(repeatChar(LINE_CHAR, LINE_LENGTH - 2)).append("╣\n");
        
        // 任务详情
        String detail = formatTaskDetail(task);
        for (String line : detail.split("\n")) {
            sb.append("║ ").append(padRight(line, LINE_LENGTH - 3)).append("║\n");
        }
        
        // 额外信息
        if (extraInfo != null && !extraInfo.isEmpty()) {
            sb.append("║ ").append(padRight("", LINE_LENGTH - 3)).append("║\n");
            for (String line : extraInfo.split("\n")) {
                sb.append("║ ").append(padRight(line, LINE_LENGTH - 3)).append("║\n");
            }
        }
        
        // 底部边框
        sb.append(repeatChar("╚", 1)).append(repeatChar(LINE_CHAR, LINE_LENGTH - 2)).append("╝");
        
        return sb.toString();
    }

    /**
     * 格式化简单步骤日志（不带边框，用于频繁的日志）
     * 
     * @param step 步骤
     * @param task 任务实体
     * @param extraInfo 额外信息
     * @return 格式化的日志字符串
     */
    public static String formatSimpleLog(Step step, TaskInfoEntity task, String extraInfo) {
        return String.format("[%s %s] %s %s",
                step.getIcon(),
                step.getDescription(),
                formatTaskBrief(task),
                extraInfo != null ? "| " + extraInfo : ""
        );
    }

    /**
     * 格式化队列状态日志
     * 
     * @param taskType 任务类型
     * @param queueSize 队列大小
     * @param runningCount 运行中数量
     * @param maxConcurrent 最大并发数
     * @return 格式化的日志字符串
     */
    public static String formatQueueStatus(String taskType, int queueSize, int runningCount, int maxConcurrent) {
        return String.format("\n%s [队列状态]\n" +
                        "║  任务类型: %s\n" +
                        "║  队列大小: %d\n" +
                        "║  运行中: %d / %d\n" +
                        "║  可用槽位: %d\n%s",
                repeatChar("╔", 1) + repeatChar(LINE_CHAR, LINE_LENGTH - 2) + "╗",
                taskType,
                queueSize,
                runningCount, maxConcurrent,
                maxConcurrent - runningCount,
                repeatChar("╚", 1) + repeatChar(LINE_CHAR, LINE_LENGTH - 2) + "╝"
        );
    }

    /**
     * 格式化重试日志
     * 
     * @param task 任务实体
     * @param retryCount 当前重试次数
     * @param maxRetry 最大重试次数
     * @param reason 重试原因
     * @return 格式化的日志字符串
     */
    /**
     * 格式化重试日志
     *
     * @param task 任务实体
     * @param retryCount 当前重试次数
     * @param maxRetry 最大重试次数
     * @param reason 重试原因
     * @return 格式化的日志字符串
     */
    public static String formatRetryLog(TaskInfoEntity task, int retryCount, int maxRetry, String reason) {
        return formatStepLog(Step.RETRY, task,
                String.format("重试进度: %d / %d\n重试原因: %s", retryCount, maxRetry, safeStr(reason)));
    }

    /**
     * 格式化鉴权绑定日志
     * 
     * @param task 任务实体
     * @param authId 鉴权ID
     * @param configName 配置名称
     * @return 格式化的日志字符串
     */
    public static String formatAuthBindLog(TaskInfoEntity task, Long authId, String configName) {
        return formatSimpleLog( Step.AUTH_BIND, task,
                String.format("鉴权ID=%d | 配置=%s", authId, safeStr(configName)));
    }

    /**
     * 格式化回调日志
     * 
     * @param taskId 任务ID
     * @param taskType 任务类型
     * @param success 是否成功
     * @param result 结果数据
     * @return 格式化的日志字符串
     */
    public static String formatCallbackLog(Long taskId, String taskType, boolean success, Map<String, Object> result) {
        return String.format("\n%s [回调处理]\n" +
                        "║  任务ID: %d\n" +
                        "║  任务类型: %s\n" +
                        "║  执行结果: %s\n" +
                        "║  返回数据: %s\n%s",
                repeatChar("╔", 1) + repeatChar(LINE_CHAR, LINE_LENGTH - 2) + "╗",
                taskId,
                taskType,
                success ? "✅ 成功" : "❌ 失败",
                result != null ? result.keySet() : "无",
                repeatChar("╚", 1) + repeatChar(LINE_CHAR, LINE_LENGTH - 2) + "╝"
        );
    }

    // ==================== 工具方法 ====================

    private static String formatField(String fieldName, Object value) {
        return String.format("    - %s: %s\n", fieldName, value != null ? value.toString() : "null");
    }

    private static String formatFieldList(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return "[]";
        }
        return fields.keySet().toString();
    }

    private static String safeStr(String str) {
        return str != null ? str : "null";
    }

    private static String repeatChar(String ch, int count) {
        return ch.repeat(Math.max(0, count));
    }

    private static String padRight(String str, int length) {
        if (str == null) {
            return repeatChar(" ", length);
        }
        int currentLength = getDisplayLength(str);
        if (currentLength >= length) {
            return str;
        }
        return str + repeatChar(" ", length - currentLength);
    }

    /**
     * 计算字符串的显示长度（考虑中文字符）
     */
    private static int getDisplayLength(String str) {
        if (str == null) {
            return 0;
        }
        int length = 0;
        for (char c : str.toCharArray()) {
            if (Character.toString(c).getBytes().length > 1) {
                length += 2; // 中文字符算2个宽度
            } else {
                length += 1;
            }
        }
        return length;
    }
}
