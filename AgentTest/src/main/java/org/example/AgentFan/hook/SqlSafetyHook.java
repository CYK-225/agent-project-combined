package org.example.AgentFan.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL 安全检查 Hook
 * <p>
 * 在工具调用前（PreActingEvent）检查 SQL 安全性，
 * 拦截非 SELECT 查询和危险关键字。
 * <p>
 * 对应原项目 data_search/scripts/sql_checker.py 和 db_manager.py 中的安全检查逻辑。
 */
@Slf4j
@Component
public class SqlSafetyHook implements Hook {

    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE",
            "EXEC", "EXECUTE", "GRANT", "REVOKE"
    );

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PreActingEvent pre) {
                onPreActing(pre);
            }
        } catch (Exception e) {
            log.error("[SqlSafetyHook] 安全检查异常: {}", e.getMessage(), e);
        }
        return Mono.just(event);
    }

    /**
     * PreActing: 在工具调用前进行 SQL 安全检查
     * <p>
     * 仅对包含 SQL 参数的工具调用进行检查。
     * 如果检测到危险 SQL，记录告警日志。
     */
    private void onPreActing(PreActingEvent event) {
        ToolUseBlock toolUse = event.getToolUse();
        String toolName = toolUse.getName();
        Map<String, Object> input = toolUse.getInput();

        if (input == null) {
            return;
        }

        // 提取 SQL 参数
        String sql = extractSql(input);
        if (sql == null || sql.isBlank()) {
            return;
        }

        log.info("[SqlSafetyHook] 检查 SQL 安全, tool={}, sql={}", toolName, truncate(sql, 80));

        // 检查是否是 SELECT
        String upperSql = sql.trim().toUpperCase();
        if (!upperSql.startsWith("SELECT")) {
            log.warn("[SqlSafetyHook] 拒绝非 SELECT 查询, tool={}, sql={}", toolName, truncate(sql, 50));
            // 注入安全错误到工具参数，使脚本返回错误
            injectSafetyError(input, "仅允许 SELECT 查询，拒绝: " + upperSql.split("\\s+")[0]);
            return;
        }

        // 检查危险关键字
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                log.warn("[SqlSafetyHook] 检测到危险关键字: {}, tool={}, sql={}",
                        keyword, toolName, truncate(sql, 50));
                injectSafetyError(input, "SQL 包含危险关键字: " + keyword);
                return;
            }
        }

        // 检查危险字符模式
        if (containsDangerousPattern(sql)) {
            log.warn("[SqlSafetyHook] 检测到危险字符模式, tool={}", toolName);
            injectSafetyError(input, "SQL 包含潜在危险字符模式");
            return;
        }

        log.info("[SqlSafetyHook] SQL 安全检查通过, tool={}", toolName);
    }

    /**
     * 从工具参数中提取 SQL
     */
    private String extractSql(Map<String, Object> input) {
        // 检查常见的 SQL 参数名
        for (String key : List.of("sql", "query", "SQL", "QUERY", "statement")) {
            if (input.containsKey(key)) {
                Object value = input.get(key);
                if (value instanceof String str) {
                    return str;
                }
            }
        }

        // 检查 args 数组中的 --sql 参数
        if (input.containsKey("args")) {
            Object args = input.get("args");
            if (args instanceof String str) {
                return extractSqlFromArgs(str);
            }
        }

        return null;
    }

    /**
     * 从 args 字符串中提取 --sql 参数值
     */
    private String extractSqlFromArgs(String args) {
        String[] parts = args.split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("--sql".equals(parts[i]) || "--query".equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return null;
    }

    /**
     * 检查是否包含危险字符模式
     * <p>
     * 检测 SQL 注入常见模式：注释符、分号拼接等
     */
    private boolean containsDangerousPattern(String sql) {
        // 检查 SQL 注释
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            return true;
        }
        // 检查分号拼接（多语句执行）
        String noStrings = sql.replaceAll("'[^']*'", ""); // 移除字符串字面量
        if (noStrings.contains(";")) {
            return true;
        }
        return false;
    }

    /**
     * 注入安全错误到工具参数
     * <p>
     * 通过修改工具参数，使 Skill 脚本能够感知安全拦截。
     */
    private void injectSafetyError(Map<String, Object> input, String errorMessage) {
        input.put("__sql_safety_blocked__", true);
        input.put("__sql_safety_error__", errorMessage);
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public int priority() {
        return 20;
    }
}
