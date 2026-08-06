package org.example.AgentFan;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 权限注入工具类
 * <p>
 * 实现公司权限和商户权限的获取、SQL 权限注入、导出阈值判断等功能。
 */
@Log4j2
public class PermissionTools {

    // 表-公司权限字段映射
    private static final Map<String, String> TABLE_COMPANY_ID_COLUMN = Map.ofEntries(
            Map.entry("company", "id"),
            Map.entry("yxd_order", "companyID"),
            Map.entry("company_user", "companyID"),
            Map.entry("evaluate", "companyId"),
            Map.entry("complaint", "companyId"),
            Map.entry("merchant_position", "companyId"),
            Map.entry("position", "companyId"),
            Map.entry("position_group_config", "companyId"),
            Map.entry("etl_order_data_daily", "company_id")
    );

    // 表-商户权限字段映射
    private static final Map<String, String> TABLE_MERCHANT_ID_COLUMN = Map.ofEntries(
            Map.entry("merchant_store", "id"),
            Map.entry("store_extra", "id"),
            Map.entry("good", "merchantStoreId"),
            Map.entry("good_food_statistics", "merchantStoreId"),
            Map.entry("merchant_good", "merchantStoreId"),
            Map.entry("company_supplier", "merchantStoreId"),
            Map.entry("evaluate", "merchantStoreId"),
            Map.entry("complaint", "merchantStoreId"),
            Map.entry("merchant_position", "merchantStoreId"),
            Map.entry("yxd_order", "merchantStoreId"),
            Map.entry("etl_order_data_daily", "merchant_store_id"),
            Map.entry("etl_order_data_month", "merchant_store_id")
    );

    // 危险 SQL 关键字
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE", "EXEC", "EXECUTE"
    );

    // 危险字符模式
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile("['\";]");

    // 导出阈值
    private static final int EXPORT_THRESHOLD = 101;

    /**
     * 获取用户公司权限
     *
     * @param accessToken 用户访问令牌
     * @return 公司 ID 列表
     */
    @Tool(name = "get_user_companies", description = "根据用户 accessToken 获取其可访问的公司 ID 列表")
    public String getUserCompanies(
            @ToolParam(name = "accessToken", description = "用户访问令牌") String accessToken) {
        log.info("[PermissionTools] 获取用户公司权限, token={}", maskToken(accessToken));

        // TODO: 调用外部 API https://tcadmin.youfantech.cn/merchantManage/company/select
        // 这里返回模拟数据，实际实现需要注入 PermissionService
        List<Integer> companyIds = fetchCompanyPermissions(accessToken);

        if (companyIds == null) {
            return "{\"status\": \"skip\", \"message\": \"未传入 token，跳过公司权限过滤\"}";
        }
        if (companyIds.isEmpty()) {
            return "{\"status\": \"no_permission\", \"message\": \"用户无任何公司权限，返回空结果\"}";
        }

        return "{\"status\": \"ok\", \"companyIds\": " + companyIds + "}";
    }

    /**
     * 获取用户商户权限
     *
     * @param accessToken 用户访问令牌
     * @return 商户 ID 列表
     */
    @Tool(name = "get_user_merchants", description = "根据用户 accessToken 获取其可访问的商户 ID 列表")
    public String getUserMerchants(
            @ToolParam(name = "accessToken", description = "用户访问令牌") String accessToken) {
        log.info("[PermissionTools] 获取用户商户权限, token={}", maskToken(accessToken));

        // TODO: 调用外部 API /merchantManage/archive/v2/merchantAchiveList
        // 这里返回模拟数据，实际实现需要注入 MerchantPermissionService
        List<Integer> merchantIds = fetchMerchantPermissions(accessToken);

        if (merchantIds == null) {
            return "{\"status\": \"skip\", \"message\": \"未传入 token，跳过商户权限过滤\"}";
        }
        if (merchantIds.isEmpty()) {
            return "{\"status\": \"no_permission\", \"message\": \"用户无任何商户权限，返回空结果\"}";
        }

        return "{\"status\": \"ok\", \"merchantIds\": " + merchantIds + "}";
    }

    /**
     * SQL 安全检查
     *
     * @param sql SQL 语句
     * @return 检查结果
     */
    @Tool(name = "check_sql_safety", description = "检查 SQL 语句是否安全，仅允许 SELECT 查询")
    public String checkSqlSafety(
            @ToolParam(name = "sql", description = "待检查的 SQL 语句") String sql) {
        log.info("[PermissionTools] SQL 安全检查: {}", truncate(sql, 100));

        if (sql == null || sql.trim().isEmpty()) {
            return "{\"safe\": false, \"message\": \"SQL 语句为空\"}";
        }

        String upperSql = sql.trim().toUpperCase();

        // 检查是否是 SELECT 语句
        if (!upperSql.startsWith("SELECT")) {
            return "{\"safe\": false, \"message\": \"仅允许 SELECT 查询\"}";
        }

        // 检查危险关键字
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                return "{\"safe\": false, \"message\": \"包含危险关键字: " + keyword + "\"}";
            }
        }

        // 检查危险字符
        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            return "{\"safe\": false, \"message\": \"包含危险字符\"}";
        }

        return "{\"safe\": true, \"message\": \"SQL 安全检查通过\"}";
    }

    /**
     * 注入权限条件到 SQL
     *
     * @param sql            原始 SQL
     * @param companyIds     公司权限 ID 列表（逗号分隔），null 表示跳过，空串表示无权限
     * @param merchantIds    商户权限 ID 列表（逗号分隔），null 表示跳过，空串或 __none__ 表示无权限
     * @param forceMerchant  是否强制商户权限过滤
     * @return 注入后的 SQL
     */
    @Tool(name = "inject_permission", description = "将权限条件注入到 SQL 查询中")
    public String injectPermission(
            @ToolParam(name = "sql", description = "原始 SQL 语句") String sql,
            @ToolParam(name = "companyIds", description = "公司权限 ID 列表（逗号分隔），null 表示跳过，空串表示无权限") String companyIds,
            @ToolParam(name = "merchantIds", description = "商户权限 ID 列表（逗号分隔），null 表示跳过，空串或 __none__ 表示无权限") String merchantIds,
            @ToolParam(name = "forceMerchant", description = "是否强制商户权限过滤") boolean forceMerchant) {
        log.info("[PermissionTools] 注入权限, companyIds={}, merchantIds={}, forceMerchant={}",
                companyIds, merchantIds, forceMerchant);

        if (sql == null || sql.trim().isEmpty()) {
            return "{\"status\": \"error\", \"message\": \"SQL 语句为空\"}";
        }

        // 安全检查
        String safetyCheck = checkSqlSafety(sql);
        if (safetyCheck.contains("\"safe\": false")) {
            return "{\"status\": \"error\", \"message\": \"SQL 安全检查未通过\"}";
        }

        String resultSql = sql;

        // 注入公司权限
        if (companyIds != null) {
            if (companyIds.isEmpty()) {
                return "{\"status\": \"empty\", \"message\": \"用户无任何公司权限，返回空结果\"}";
            }
            String companyColumn = findCompanyColumn(sql);
            if (companyColumn != null) {
                resultSql = injectCondition(resultSql, companyColumn, companyIds);
            }
        }

        // 注入商户权限
        if (forceMerchant && merchantIds != null) {
            if (merchantIds.isEmpty() || "__none__".equals(merchantIds)) {
                return "{\"status\": \"empty\", \"message\": \"用户无任何商户权限，返回空结果\"}";
            }
            String merchantColumn = findMerchantColumn(sql);
            if (merchantColumn != null) {
                resultSql = injectCondition(resultSql, merchantColumn, merchantIds);
            }
        }

        return "{\"status\": \"ok\", \"sql\": \"" + escapeJson(resultSql) + "\"}";
    }

    /**
     * 判断是否需要导出
     *
     * @param rowCount 查询结果行数
     * @return 是否需要导出
     */
    @Tool(name = "check_export_threshold", description = "判断查询结果是否超过导出阈值（100 行）")
    public String checkExportThreshold(
            @ToolParam(name = "rowCount", description = "查询结果行数") int rowCount) {
        log.info("[PermissionTools] 导出阈值判断, rowCount={}", rowCount);

        if (rowCount >= EXPORT_THRESHOLD) {
            return "{\"need_export\": true, \"row_count\": " + rowCount +
                    ", \"message\": \"查询结果有 " + rowCount + " 行（已截断展示前5行），数据量较大，是否需要导出为 Excel 文件？请回复「是」或「导出」我将为您生成下载链接。\"}";
        }

        return "{\"need_export\": false, \"row_count\": " + rowCount + "}";
    }

    /**
     * 生成导出指令
     *
     * @param sql   完整 SQL（不带 LIMIT）
     * @param title 导出文件标题
     * @return 导出指令
     */
    @Tool(name = "generate_export_instruction", description = "生成导出 Excel 的执行指令")
    public String generateExportInstruction(
            @ToolParam(name = "sql", description = "完整 SQL（不带 LIMIT）") String sql,
            @ToolParam(name = "title", description = "导出文件标题（不超过20字）") String title) {
        log.info("[PermissionTools] 生成导出指令, title={}", title);

        return "{\"instruction\": \"调用 python_exe 工具，参数：skill_name=\\\"data_search\\\", script_name=\\\"data_export.py\\\", args=[\\\"--sql\\\", \\\"" +
                escapeJson(sql) + "\\\", \\\"--title\\\", \\\"" + escapeJson(title) + "\\\"]\"}";
    }

    /**
     * 脱敏命令用于日志
     *
     * @param command 原始命令
     * @return 脱敏后的命令
     */
    @Tool(name = "mask_command_for_log", description = "对命令中的敏感信息进行脱敏处理")
    public String maskCommandForLog(
            @ToolParam(name = "command", description = "待脱敏的命令") String command) {
        if (command == null) return "null";

        // 替换 --allowed-companies 1,2,3,4,5 为 --allowed-companies <hidden:5 ids>
        Pattern pattern = Pattern.compile("(--allowed-(?:companies|merchants)(?:=|\\s+))([0-9,]+)");
        Matcher matcher = pattern.matcher(command);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String ids = matcher.group(2);
            int count = ids.split(",").length;
            matcher.appendReplacement(sb, prefix + "<hidden:" + count + " ids>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ==================== 内部方法 ====================

    private List<Integer> fetchCompanyPermissions(String accessToken) {
        // TODO: 实际实现应调用 PermissionService.get_user_companies()
        // 模拟返回
        if (accessToken == null || accessToken.isEmpty()) {
            return null;
        }
        return List.of(1, 2, 3);
    }

    private List<Integer> fetchMerchantPermissions(String accessToken) {
        // TODO: 实际实现应调用 MerchantPermissionService.get_user_merchants()
        // 模拟返回
        if (accessToken == null || accessToken.isEmpty()) {
            return null;
        }
        return List.of(100, 200, 300);
    }

    private String findCompanyColumn(String sql) {
        String lowerSql = sql.toLowerCase();
        for (Map.Entry<String, String> entry : TABLE_COMPANY_ID_COLUMN.entrySet()) {
            if (lowerSql.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String findMerchantColumn(String sql) {
        String lowerSql = sql.toLowerCase();
        for (Map.Entry<String, String> entry : TABLE_MERCHANT_ID_COLUMN.entrySet()) {
            if (lowerSql.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String injectCondition(String sql, String column, String ids) {
        // 简单实现：在 WHERE 子句后追加 AND 条件
        String condition = column + " IN (" + ids + ")";

        if (sql.toUpperCase().contains("WHERE")) {
            return sql + " AND " + condition;
        } else {
            // 没有 WHERE 子句，在 FROM 后添加
            return sql.replaceFirst("(?i)(FROM\\s+\\w+)", "$1 WHERE " + condition);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
