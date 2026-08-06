package org.example.agent.Sp.hook.decisionMarkAgentHook;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * newSP 模块执行状态追踪器
 * 维护一次供给计划执行过程中的结构化状态，用于向 LLM 反馈当前执行进度和异常情况。
 * author: zhilin
 * 2026.04.24
 */
@Slf4j
public class SPStateTracker {

    public enum Step {
        IDLE("初始状态"),
        DISH_SEARCHING("正在搜索菜品"),
        DISH_SEARCHED("菜品搜索完成"),
        WHITELIST_MANAGING("正在管理白名单"),
        BLACKLIST_MANAGING("正在管理黑名单"),
        WHITELIST_UPDATED("白名单已更新"),
        BLACKLIST_UPDATED("黑名单已更新"),
        DISH_ID_QUERYING("正在查询菜品ID"),
        DISH_ID_QUERIED("菜品ID查询完成"),
        REPORT_READY("报告已生成"),
        ERROR("执行异常");

        private final String desc;

        Step(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    public enum ToolStatus {
        SUCCESS,
        EMPTY,
        ERROR
    }

    private Step currentStep = Step.IDLE;
    private String lastToolName;
    private ToolStatus lastToolStatus;
    private String detailHint;
    private boolean interventionNeeded;
    
    // 重试计数器：记录每个工具的重试次数
    private final Map<String, Integer> retryCountMap = new HashMap<>();
    private static final int MAX_RETRY_COUNT = 3;  // 最大重试次数

    // ========== 状态更新 ==========

    /**
     * 根据工具调用结果更新状态
     */
    public void recordToolResult(String toolName, String result) {
        this.lastToolName = toolName;
        this.interventionNeeded = false;

        switch (toolName) {
            case "DishSearchTool" -> handleDishSearchResult(result);
            case "MenuManagementTool" -> handleMenuManagementResult(result);
            case "manageBlacklist" -> handleBlacklistResult(result);
            case "WhitelistManagementTool" -> handleWhitelistResult(result);
            case "BlacklistManagementTool" -> handleBlacklistResult(result);
            case "GetDishIdByNameTool" -> handleDishIdQueryResult(result);
            default -> {
                // 未知工具不追踪
            }
        }
        
        // 如果检测到需要干预，检查是否超过最大重试次数
        if (interventionNeeded) {
            int retryCount = retryCountMap.getOrDefault(toolName, 0) + 1;
            retryCountMap.put(toolName, retryCount);
            
            if (retryCount >= MAX_RETRY_COUNT) {
                // 超过最大重试次数，标记为最终失败
                detailHint = "【已重试" + retryCount + "次仍失败】" + detailHint 
                        + " 建议：明确告知用户服务暂时不可用，建议稍后再试。";
                log.warn("[SPStateTracker] 工具 {} 已重试 {} 次仍失败，放弃重试", toolName, retryCount);
            } else {
                // 未超过重试次数，提示 LLM 重试
                detailHint = "【第" + retryCount + "次尝试，还可重试" + (MAX_RETRY_COUNT - retryCount) + "次】" + detailHint
                        + " 建议：请调整参数或策略后重新调用该工具。";
                log.info("[SPStateTracker] 工具 {} 第 {} 次尝试失败，建议重试", toolName, retryCount);
            }
        } else {
            // 成功时重置重试计数器
            retryCountMap.put(toolName, 0);
        }
    }

    private void handleDishSearchResult(String result) {
        if (result == null || result.isBlank() || "null".equals(result.trim())) {
            currentStep = Step.DISH_SEARCHED;
            lastToolStatus = ToolStatus.EMPTY;
            interventionNeeded = true;
            detailHint = "菜品搜索结果为空。建议：告知用户当前搜索条件下无匹配菜品，请用户调整搜索条件后重试。";
            return;
        }

        if (containsErrorPattern(result)) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "菜品搜索工具执行异常。原始错误：" + truncate(result, 200);
            return;
        }

        // 空列表（LLM 收到的序列化结果是 []）
        if (result.trim().equals("[]")) {
            currentStep = Step.DISH_SEARCHED;
            lastToolStatus = ToolStatus.EMPTY;
            interventionNeeded = true;
            detailHint = "菜品搜索结果为空列表。建议：告知用户当前搜索条件下无匹配菜品，请调整口味、辣度等条件。";
            return;
        }

        currentStep = Step.DISH_SEARCHED;
        lastToolStatus = ToolStatus.SUCCESS;
    }

    private void handleMenuManagementResult(String result) {
        if (result == null || result.isBlank()) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "菜单管理工具返回为空。建议：告知用户操作失败，建议重试。";
            return;
        }

        if (containsErrorPattern(result)) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "菜单管理工具执行异常。原始错误：" + truncate(result, 200);
            return;
        }

        if (result.contains("添加失败") || result.contains("操作类型只能是")) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "菜单管理工具返回错误：" + result + "。建议：根据错误信息调整操作参数后重试。";
            return;
        }

        currentStep = Step.WHITELIST_UPDATED;
        lastToolStatus = ToolStatus.SUCCESS;
    }

    private void handleBlacklistResult(String result) {
        if (result == null || result.isBlank()) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "黑名单管理工具返回为空。建议：告知用户操作失败，建议重试。";
            return;
        }

        if (containsErrorPattern(result)) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "黑名单管理工具执行异常。原始错误：" + truncate(result, 200);
            return;
        }

        if (result.contains("操作类型只能是")) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "黑名单管理工具参数错误：" + result + "。建议：检查操作类型参数是否正确。";
            return;
        }

        currentStep = Step.BLACKLIST_UPDATED;
        lastToolStatus = ToolStatus.SUCCESS;
    }

    private void handleWhitelistResult(String result) {
        if (result == null || result.isBlank()) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "白名单管理工具返回为空。建议：告知用户操作失败，建议重试。";
            return;
        }

        if (containsErrorPattern(result)) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "白名单管理工具执行异常。原始错误：" + truncate(result, 200);
            return;
        }

        if (result.contains("添加失败") || result.contains("操作类型只能是")) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "白名单管理工具返回错误：" + result + "。建议：根据错误信息调整操作参数。";
            return;
        }

        currentStep = Step.WHITELIST_UPDATED;
        lastToolStatus = ToolStatus.SUCCESS;
    }

    private void handleDishIdQueryResult(String result) {
        if (result == null || result.isBlank() || "null".equals(result.trim()) || "{}".equals(result.trim())) {
            currentStep = Step.DISH_ID_QUERIED;
            lastToolStatus = ToolStatus.EMPTY;
            interventionNeeded = true;
            detailHint = "菜品ID查询结果为空，未找到匹配的菜品。建议：告知用户该菜品名称未找到，请确认菜名是否正确。";
            return;
        }

        if (containsErrorPattern(result)) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "菜品ID查询工具执行异常。原始错误：" + truncate(result, 200);
            return;
        }

        currentStep = Step.DISH_ID_QUERIED;
        lastToolStatus = ToolStatus.SUCCESS;
    }

    // ========== 辅助方法 ==========

    private boolean containsErrorPattern(String result) {
        if (result == null || result.isBlank()) {
            return false;
        }
        
        // 检测明确的错误关键词（包括 JSON 解析错误、系统异常等）
        return result.contains("工具执行失败") 
                || result.contains("查询失败")
                || result.contains("数据库异常") 
                || result.contains("接口调用失败")
                || result.contains("Exception") 
                || result.contains("执行出错")
                || result.contains("Error")
                || result.contains("Failed")
                || result.contains("parse error")  // JSON 解析错误
                || result.contains("Invalid")       // 无效数据
                || result.contains("unexpected");   // 意外情况
    }

    /**
     * 截断字符串，超长部分用 "..." 代替，防止刷屏。
     *
     * @param text  原始文本
     * @param limit 最大长度
     * @return 截断后的文本
     */
    private static String truncate(String text, int limit) {
        if (text == null) return "null";
        if (text.length() <= limit) return text;
        return text.substring(0, limit) + "...";
    }

    /**
     * 生成状态上下文描述，用于注入 LLM
     */
    public String buildStateContext() {
        if (currentStep == Step.IDLE) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("当前工作流步骤: ").append(currentStep.name())
                .append("（").append(currentStep.getDesc()).append("）");
        if (lastToolName != null) {
            sb.append(" | 最近调用工具: ").append(lastToolName);
        }
        if (lastToolStatus != null) {
            sb.append(" | 工具状态: ").append(lastToolStatus.name());
        }
        return sb.toString();
    }

    public boolean needsIntervention() {
        return interventionNeeded;
    }

    public String buildInterventionHint() {
        if (!interventionNeeded || detailHint == null) {
            return "";
        }
        return detailHint;
    }

    public void reset() {
        currentStep = Step.IDLE;
        lastToolName = null;
        lastToolStatus = null;
        detailHint = null;
        interventionNeeded = false;
        retryCountMap.clear();  // 重置时清空重试计数器
    }

    public Step getCurrentStep() {
        return currentStep;
    }

    public String getLastToolName() {
        return lastToolName;
    }

    public ToolStatus getLastToolStatus() {
        return lastToolStatus;
    }
}