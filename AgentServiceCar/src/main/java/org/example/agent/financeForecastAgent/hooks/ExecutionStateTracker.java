package org.example.agent.financeForecastAgent.hooks;

/**
 * 执行状态跟踪器
 * 维护一次查询执行过程中的结构化状态，用于向 LLM 反馈当前执行进度和异常情况。
 * author: zhilin
 * 2026.04.14
 */
public class ExecutionStateTracker {

    // ========== 工作流步骤 ==========
    public enum Step {
        IDLE("初始状态，等待用户输入"),
        PARAM_EXTRACTED("参数已提取，准备查询点位"),
        POSITION_QUERIED("点位列表已查询"),
        POSITION_MATCHED("点位已匹配，准备查询经营数据"),
        DATA_QUERIED("经营数据已查询，准备生成报告"),
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

    // ========== 工具调用状态 ==========
    public enum ToolStatus {
        SUCCESS,     // 正常返回
        EMPTY,       // 返回空数据
        NO_MATCH,    // 点位未匹配
        ERROR        // 调用失败
    }

    private Step currentStep = Step.IDLE;
    private String lastToolName;
    private ToolStatus lastToolStatus;
    private String detailHint;
    private int matchedPositionId = -1;
    private String matchedPositionName;
    private String availablePositions;
    private boolean interventionNeeded;

    // ========== 状态更新 ==========

    /**
     * 根据工具调用结果更新状态
     */
    public void recordToolResult(String toolName, String result) {
        this.lastToolName = toolName;
        this.interventionNeeded = false;

        if ("QueryPositionList".equals(toolName)) {
            handlePositionListResult(result);
        } else if ("QueryFoodTruckDataAnalysis".equals(toolName)) {
            handleDataAnalysisResult(result);
        }
    }

    private void handlePositionListResult(String result) {
        if (result == null || result.isBlank()) {
            currentStep = Step.POSITION_QUERIED;
            lastToolStatus = ToolStatus.EMPTY;
            interventionNeeded = true;
            detailHint = "点位列表接口返回为空。建议：告知用户当前无可用点位，请稍后重试。";
            return;
        }

        if (result.contains("接口调用失败") || result.contains("接口返回错误")) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "点位列表查询失败。建议：告知用户接口异常，建议稍后重试。";
            return;
        }

        currentStep = Step.POSITION_QUERIED;
        lastToolStatus = ToolStatus.SUCCESS;
        // 记录返回的点位信息（截取前500字符避免过长）
        availablePositions = result.length() > 500 ? result.substring(0, 500) + "..." : result;
    }

    private void handleDataAnalysisResult(String result) {
        if (result == null || result.isBlank() || "null".equals(result.trim())) {
            currentStep = Step.DATA_QUERIED;
            lastToolStatus = ToolStatus.EMPTY;
            interventionNeeded = true;
            detailHint = "经营数据接口返回为空。可能原因：该日期/餐段无经营记录。建议：告知用户该日期无数据，并询问是否查询其他日期。";
            return;
        }

        if (result.contains("接口调用失败") || result.contains("接口返回错误")) {
            currentStep = Step.ERROR;
            lastToolStatus = ToolStatus.ERROR;
            interventionNeeded = true;
            detailHint = "经营数据查询失败。建议：告知用户接口异常，建议稍后重试或更换查询条件。";
            return;
        }

        currentStep = Step.DATA_QUERIED;
        lastToolStatus = ToolStatus.SUCCESS;
    }

    /**
     * 标记点位匹配成功
     */
    public void markPositionMatched(int positionId, String positionName) {
        this.matchedPositionId = positionId;
        this.matchedPositionName = positionName;
        this.currentStep = Step.POSITION_MATCHED;
    }

    /**
     * 标记点位未匹配
     */
    public void markPositionNoMatch() {
        this.lastToolStatus = ToolStatus.NO_MATCH;
        this.interventionNeeded = true;
        this.detailHint = "未找到匹配点位。建议：向用户展示可用点位列表，请用户确认正确的点位名称。";
    }

    /**
     * 标记报告就绪
     */
    public void markReportReady() {
        this.currentStep = Step.REPORT_READY;
    }

    // ========== 状态上下文生成 ==========

    /**
     * 生成状态描述文本，用于注入到 LLM 上下文
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
        if (matchedPositionId > 0) {
            sb.append(" | 已匹配点位: ").append(matchedPositionName)
              .append("(ID=").append(matchedPositionId).append(")");
        }
        return sb.toString();
    }

    /**
     * 是否需要干预（异常状态需要引导 LLM 处理）
     */
    public boolean needsIntervention() {
        return interventionNeeded;
    }

    /**
     * 生成干预提示（给 LLM 的纠正建议）
     */
    public String buildInterventionHint() {
        if (!interventionNeeded || detailHint == null) {
            return "";
        }
        return detailHint;
    }

    /**
     * 重置状态（新会话开始时调用）
     */
    public void reset() {
        currentStep = Step.IDLE;
        lastToolName = null;
        lastToolStatus = null;
        detailHint = null;
        matchedPositionId = -1;
        matchedPositionName = null;
        availablePositions = null;
        interventionNeeded = false;
    }

    // ========== Getters ==========

    public Step getCurrentStep() {
        return currentStep;
    }

    public String getLastToolName() {
        return lastToolName;
    }

    public ToolStatus getLastToolStatus() {
        return lastToolStatus;
    }

    public String getAvailablePositions() {
        return availablePositions;
    }

    public int getMatchedPositionId() {
        return matchedPositionId;
    }
}
