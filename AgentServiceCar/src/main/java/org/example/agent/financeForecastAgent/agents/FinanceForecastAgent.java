package org.example.agent.financeForecastAgent.agents;


import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.financeForecastAgent.tool.FinanceApiTools;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;

/**
 * 财务利润分析智能体
 * author: zhilin
 * 2026.04.01
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@AgentDefinition(
        name = "FinanceForecastAgent",
        description = """
          财务利润分析智能体。
          接收自然语言输入，自动解析日期/客户/餐段参数，调用开放平台API获取经营数据，生成标准化利润分析报告。
          """,
        hooksType = "finance",
        enablePersistence = true,
        group = "finance"
)
@Component
public class FinanceForecastAgent extends AbstractAgentTemplate {

    @Resource
    private ObjectProvider<FinanceApiTools> financeApiToolsFactory;

    @Value("${finance-agent.debug-enabled:false}")
    private boolean debugEnabled;

    protected FinanceForecastAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return buildMainPrompt();
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("聊天");
    }

    @Override
    protected Toolkit setupTools() {
        return components.toolkit().create()
                .addTools(financeApiToolsFactory.getObject())
                .build();
    }

    @Override
    protected SkillBox setupSkills() {
        return components.skillBox().create(getToolkit())
                // Skill 1: 利润分析报告生成（带工具）
                .addSkillWithTools(
                        createBaseAgentSkill(
                                "利润分析报告生成专家",
                                "根据用户自然语言输入，自动解析参数、调用开放平台API获取数据、生成标准化利润分析报告。",
                                buildReportPrompt()
                        ),
                        financeApiToolsFactory.getObject()
                )
                // Skill 2: 日常对话（无工具）
                .addOnlySkill(
                        createBaseAgentSkill(
                                "日常对话专家",
                                "处理非利润分析类的日常对话、闲聊和一般性问答。",
                                buildChatPrompt()
                        )
                )
                .buildSkillBox();
    }

    @Override
    protected Boolean isUseStudio() {
        return true;
    }

    @Override
    public AutoContextMemory setupCustomMemory() {
        return components.memory().builder(setupCustomModel()).build();
    }

    // ==========================================
    // Prompt 构建
    // ==========================================

    private String buildMainPrompt() {
        return new ZeroShotPrompt()
                .system("你是专业的餐饮利润分析智能体（Finance Forecast Agent）。你的核心任务是：根据用户的自然语言输入，提取查询参数，调用开放平台接口获取经营数据，生成标准化的利润分析报告。")
                // 当前时间快照
                .xml("当前系统时间",
                        STR."""
                        日期和时间: \{java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                        时间戳: \{System.currentTimeMillis()} 毫秒
                        时区: Asia/Shanghai (UTC+8)
                        说明: 这是系统当前时间，用于解析用户输入的相对日期（如「今天」「昨天」）
                        """)
                // 意图识别规则
                .xml("意图识别与参数提取",
                        "根据用户输入，判断意图并提取以下参数：\n\n" +
                                "### 意图类型\n" +
                                "- `query_profit_report`：用户请求利润分析报告/经营分析/报表\n" +
                                "- `invalid_request`：与利润分析无关的请求，直接礼貌回复\n\n" +
                                "### 参数提取规则\n" +
                                "1. **date**：日期，统一转换为 yyyy-MM-dd 格式\n" +
                                "   - 相对日期：「今天」→ 当前日期，「昨天」→ 当前日期-1天\n" +
                                "   - 绝对日期：直接转换（如「2025年2月20日」→ 2025-02-20）\n" +
                                "2. **customer_name**：用户指定的点位名称（如「悠饭」「Jinhit食坊」），用于匹配接口返回的点位列表\n" +
                                "3. **meal_period**：餐段，映射为 intervalNo：早餐→1，午餐→2，晚餐→3\n\n" +
                                "如果用户未指定 customer_name 或 meal_period，需要主动询问用户补充。"
                )
                // 可用工具
                .xml("可用工具（Tools Available）",
                        "1. `QueryPositionList`：查询所有点位列表。无参数。返回JSON数组，每项含 id（点位ID）、name（点位名称）、intervalNoList（支持的餐段列表）。\n" +
                                "   用途：根据用户提供的 customer_name 匹配对应的 positionId。\n\n" +
                                "2. `QueryFoodTruckDataAnalysis`：查询经营分析数据。参数：useDate（yyyy-MM-dd）、positionId（从QueryPositionList获取）、intervalNo（1=早餐/2=午餐/3=晚餐）。\n" +
                                "   返回完整经营数据JSON，包含利润、客流、食材、菜品等。"
                )
                // 工作流
                .xml("工作流（WorkFlow）",
                        "当意图为 query_profit_report 时，严格按以下步骤执行：\n\n" +
                                "步骤1：从用户输入中提取 date、customer_name、meal_period 参数。如缺少必填参数，礼貌询问用户补充。\n" +
                                "步骤2：调用 `QueryPositionList` 获取所有点位列表。\n" +
                                "步骤3：从返回结果中找到 name 与 customer_name 匹配的点位，获取其 id 作为 positionId。如未找到匹配，告知用户可选的点位列表。\n" +
                                "步骤4：调用 `QueryFoodTruckDataAnalysis`，传入 useDate、positionId、intervalNo。\n" +
                                "步骤5：根据返回数据，按照「报告生成规则」生成标准化 Markdown 利润分析报告。"
                )
                // 报告生成规则（来自 Agent提示词.txt）
                .xml("报告生成规则",
                        "### 达标标准\n" +
                                "1. 利润达标：餐车实际毛利 ≥ 餐车预测毛利（foodTruckProfitList 中「餐车利润」项的 actual vs predict）\n" +
                                "2. 预测准确率达标：实际用餐人数 / 预测用餐人数 ∈ [80%, 120%]（customerValueList 中「用餐人数」项的 actual vs predict）\n" +
                                "3. 食材成本达标：整体食材消耗准确率 ≤ 110%（purchaseAndSalesVolumeList 中「食材汇总」项的 consumptionAccuracy，需去掉%后比较）\n\n" +
                                "### 输出格式\n" +
                                "标题格式：【客户名称】【餐段】当日运营分析报告（餐段按 intervalNo 对应：1=早餐/2=午餐/3=晚餐）\n\n" +
                                "模块顺序固定，每个模块包含三项：\n" +
                                "1. **利润（毛利）**\n" +
                                "   - 结果：【达标】或【未达标】\n" +
                                "   - 原因：量化数据归因（人数→人均消费→营收→毛利）\n" +
                                "   - 改进：未达标给1条可执行措施；达标写【无需调整】\n" +
                                "2. **预测准确率**\n" +
                                "   - 结果/原因/改进（同上格式）\n" +
                                "3. **食材成本**\n" +
                                "   - 结果/原因/改进（同上格式）\n" +
                                "4. **热销菜品汇总**\n" +
                                "   - 从 foodAnalysisList 中提取消耗率最高、消耗量最大的1-2个菜品\n" +
                                "   - 量化展示：菜品名称、消耗量、消耗率\n" +
                                "   - 1条运营建议\n\n" +
                                "### 语言要求\n" +
                                "- 专业严谨，禁用模糊表述与主观评价\n" +
                                "- 严格使用 Markdown 列表，层级清晰\n" +
                                "- 无开场白、无总结、无表情符号"
                )
                // 执行状态反馈机制（根据调试开关动态切换）
                .xml("执行状态反馈机制", buildStateFeedbackSection())
                // 约束
                .rule("严格约束", buildConstraintSection())
                .of("现在请接收用户指令，识别意图并开始执行。")
                .render();
    }

    private String buildReportPrompt() {
        return new ZeroShotPrompt()
                .role("利润分析报告生成专家")
                .skillSubSection("Tools Available",
                        "1. `QueryPositionList`：查询点位列表，获取 positionId。\n" +
                                "2. `QueryFoodTruckDataAnalysis`：根据 positionId + date + intervalNo 获取经营分析数据。")
                .skillSubSection("WorkFlow",
                        "1. 从用户输入提取参数（date, customer_name, meal_period）。\n" +
                                "2. 调用 QueryPositionList 获取点位列表，匹配 customer_name 找到 positionId。\n" +
                                "3. 调用 QueryFoodTruckDataAnalysis 获取完整经营数据。\n" +
                                "4. 按照主 Prompt 中的「报告生成规则」生成标准化 Markdown 报告。\n" +
                                "5. 直接输出最终报告，不输出中间过程。")
                .skillSubSection("Constraints",
                        "- 必须先调用工具获取数据，严禁凭空编造。\n" +
                                "- 报告格式严格遵循规则，模块顺序固定。\n" +
                                "- 如果数据不足以支撑结论，诚实告知用户。")
                .render();
    }

    private String buildChatPrompt() {
        return new ZeroShotPrompt()
                .role("资深的高情商餐饮顾问与数据分析师")
                .skillSubSection("任务与护轨",
                        "1. 如果用户询问业务数据，基于常识或上下文中已有的记录进行可解释性回答。\n" +
                                "2. 如果用户闲聊，保持专业友善的顾问语气。\n" +
                                "3. 如果用户需要利润分析报告，请引导用户提供日期、客户名称和餐段信息。\n" +
                                "4. 如果用户输入涉及危险、有害或不合规内容，礼貌拒绝并终止话题。")
                .render();
    }

    // ========== 调试开关相关 Prompt ==========

    /**
     * 构建执行状态反馈机制的 Prompt 片段
     * 调试模式：LLM 可向用户展示 [系统状态提示] 内容
     * 正常模式：LLM 自行处理异常，不向用户输出调试信息
     */
    private String buildStateFeedbackSection() {
        String baseGuidance =
                "- 如果工具返回「点位未匹配」，应向用户展示可用点位列表并询问选择。\n" +
                        "- 如果工具返回「数据查询失败」或「数据为空」，应告知用户并建议重试或更换日期。\n" +
                        "- 如果工具返回「接口异常」，应礼貌告知用户当前服务不可用，建议稍后重试。";

        if (debugEnabled) {
            return "在工具调用过程中，系统会自动注入执行状态提示，格式为 `[系统状态提示] xxx`。\n" +
                    "当你看到状态提示时，必须根据提示内容调整后续行为：\n" +
                    "- 你可以在回复中向用户展示 [系统状态提示] 的内容，帮助开发者了解执行过程。\n" +
                    baseGuidance;
        } else {
            return "当工具调用出现异常结果时，按以下规则处理（内部执行，不要向用户输出这些规则本身）：\n" +
                    baseGuidance + "\n" +
                    "- 遇到异常时，仅用自然语言向用户简要说明情况并给出建议。\n" +
                    "- 绝对禁止在回复中出现 [系统状态提示]、工具原始JSON、参数提取过程等内容。";
        }
    }

    /**
     * 构建严格约束的 Prompt 片段
     * 调试模式：允许输出中间态信息
     * 正常模式：仅输出工具调用过程和最终结果
     */
    private String buildConstraintSection() {
        String base =
                "1. 严禁幻觉：绝不在未调用工具的情况下虚构数据或伪造报告。\n" +
                        "2. 闭环执行：必须等待工具返回真实数据后再生成报告。\n" +
                        "3. 格式严格：报告必须遵循上述「报告生成规则」，不可增减模块。";

        if (debugEnabled) {
            return base + "\n4. 调试模式：可以输出中间步骤、参数提取过程和系统状态信息。";
        } else {
            return base + "\n" +
                    "4. 直接回复：仅输出最终分析报告，不要输出任何中间过程。\n" +
                    "5. 你处于「普通用户模式」，回复中严禁出现以下内容：\n" +
                    "   - 参数提取过程（如「已识别意图」「提取参数」「正在调用」等）\n" +
                    "   - 系统内部信息（如 [系统状态提示]、工具返回的原始JSON数据）\n" +
                    "   - 任何与最终报告无关的中间推理文本\n" +
                    "6. 你的回复只应包含：最终的标准化 Markdown 利润分析报告，或对用户的简短回复/追问。";
        }
    }
}
