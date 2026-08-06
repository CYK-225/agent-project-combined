package org.example.agent.Sp.agents;

import com.langfuse.client.LangfuseClient;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.agent.Sp.hook.decisionMarkAgentHook.DecisionHook;
import org.example.agent.Sp.hook.decisionMarkAgentHook.DynamicPickDishPromptHook;
import org.example.agent.Sp.hook.decisionMarkAgentHook.PipelinePhaseGuardHook;
import org.example.agent.Sp.hook.decisionMarkAgentHook.SPStateFeedbackHook;
import org.example.agent.Sp.hook.masterSPAgentHook.MasterGuardrailHook;
import org.example.agent.Sp.tool.decisionMarkAgentTool.PromptReportTools;
import org.example.agent.Sp.tool.masterSPAgentTool.SupplyPlanBlacklistManagementTools;
import org.example.agent.Sp.tool.masterSPAgentTool.SupplyPlanDishIdTools;
import org.example.agent.Sp.tool.masterSPAgentTool.SupplyPlanWhitelistManagementTools;
import org.example.agent.Sp.tool.masterSPAgentTool.UserQuestionAndUserReportWriteTools;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.agentScope.util.hooksManager.HookBuilder;
import org.example.agentScope.util.hooksManager.SessionContext;
import org.example.agentScope.util.hooksManager.LangfuseSdkTracingHook;
import org.example.agentScope.util.tool.SubAgent;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;

@EqualsAndHashCode(callSuper = true)
@AgentDefinition(
        name = "MasterSPAgents"
        , description = "餐饮智能体总控，负责精准识别用户意图并路由至四大核心Skill执行任务"
//        ,enablePlan  = true
        ,hooksType = "sp"
        ,group = "canche"
        ,enablePersistence = true   // ✅ 启用自动记忆持久化
//        ,scope = "singleton"
)
@Component
@Slf4j
public class MasterSPAgents extends AbstractAgentTemplate {
    /**
     * 注册管理器
     */
    @Resource
    private AgentPoolManager agentPoolManager;
    /**
     * Hook记忆管理
     */

    @Resource
    private LangfuseClient langfuseClient;
    /**
     * 直接注入工具
     */
    @Resource
    private ObjectProvider<SupplyPlanBlacklistManagementTools> blacklistManagementToolsFactory;
    @Resource
    private ObjectProvider<SupplyPlanDishIdTools> dishIdToolsFactory;
    @Resource
    private ObjectProvider<SupplyPlanWhitelistManagementTools> whitelistManagementToolsFactory;
    @Resource
    private ObjectProvider<SupplyPlanDishIdTools> supplyPlanDishIdToolsFactory;
    @Resource
    private ObjectProvider<UserQuestionAndUserReportWriteTools> userQuestionAndUserReportWriteToolsFactory;
    @Resource
    private org.springframework.context.ApplicationContext applicationContext;

    private final DecisionMarkEvent decisionMarkEvent = new DecisionMarkEvent();
    private final SupplyPlanModelEvent supplyPlanModelEvent = new SupplyPlanModelEvent();

    /**
     * 会话级 DTO 容器，统一管理供给计划相关事件。
     * <p>
     * 注入到所有需要 DTO 的 Hook 中，避免逐个传参。
     */
    private final SessionContext sessionContext = buildSessionContext();
    /**
     * 准备依赖对象
     */
    ReActAgent decisionMarkAgent;
    SubAgent subDecisionMarkAgent;

    public void init() {
        /*
          创建决策者子Agent
          如果你想每一次子agent被调用都清空工具执行上下文，可以用new supplyPlanModelEvent（）这种方式创建
         */
        ToolExecutionContext subAgentToolContext = ToolExecutionContext.builder()
                .register(supplyPlanModelEvent)
                .register(decisionMarkEvent)
                .build();

        // 使用 HookBuilder 自由组装子 Agent 的 Hook 列表
        List<Hook> subAgentHooks = HookBuilder.create()
                .addDto(supplyPlanModelEvent)
                .addDto(decisionMarkEvent)
                .addFactory(PipelinePhaseGuardHook::new)
                .addFactory(DynamicPickDishPromptHook::new)
                .addFactory(DecisionHook::new)
                .add(new SPStateFeedbackHook(sessionContext))
                .build();

        this.decisionMarkAgent = agentPoolManager.getAgent("DecisionMarkAgent", subAgentToolContext, subAgentHooks);
        subDecisionMarkAgent = SubAgent.create()
                .agent(decisionMarkAgent)
                .config()
                .toolName("DecisionMarkAgent")
                .description("核心决策模型工具。能够接收上游(搜索或修改)传递的参数，并在底层并发执行复杂的菜品搜索和白名单管理。" +
                        "【传参警告】：调用此工具时，请将所有参数合并为一段自然语言或平铺的文本传入，绝对不要使用复杂的嵌套JSON，严禁包含未转义的特殊符号！")
                .forwardEvents(true)
                .streamOptions(StreamOptions.builder()
                        .eventTypes(EventType.ALL)
                        .includeReasoningChunk(true)
                        .includeReasoningResult(false)  // ← Critical: prevents duplicate output
                        .includeActingChunk(true)
                        .build())
                .end();
    }


    /**
     * 构造函数
     *
     * @param components 组件门面
     */
    protected MasterSPAgents(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return buildExecutiveMasterPrompt();
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("聊天");
    }

    @Override
    protected Toolkit setupTools(){
        return   components.toolkit().create()
                .addTools(blacklistManagementToolsFactory.getObject(),
                        new PromptReportTools(),
                        supplyPlanDishIdToolsFactory.getObject(),
                        userQuestionAndUserReportWriteToolsFactory.getObject())
                .build();
    }

    @Override
    protected List<Hook> setupCustomHooks() {
        return HookBuilder.create()
                .add(new MasterGuardrailHook(sessionContext))
                .add(new LangfuseSdkTracingHook(langfuseClient))
                .build();
    }

    /**
     * 注入业务 DTO 容器。
     * <p>
     * 框架自动将其注册到 {@code SessionManager}，
     * 在 load / save 时批量持久化所有 {@code State} DTO。
     */
    @Override
    protected StateModule setupSessionContext() {
        return sessionContext;
    }

    @Override
    protected ToolExecutionContext setupToolExecutionContext() {
        return ToolExecutionContext.builder()
                .register(supplyPlanModelEvent)
                .build();
    }

    @Override
    protected SkillBox setupSkills() {
        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        createBaseAgentSkill(
                                "泛化条件搜索与全新规划",
                                "处理从零开始的菜单规划。根据用户的口味、预算等泛化条件，将参数传递给底层决策模型搜菜。",
                                buildSearchMenuPrompt()
                        ), subDecisionMarkAgent
                )
                .addSkillWithTools(
                        createBaseAgentSkill(
                                "具体菜名修改与精准点菜",
                                "处理带有【具体菜品名称】的指令。负责查询具体菜品的ID并写入白名单，或对已有菜单进行定向替换。",
                                buildModifyPrompt()
                        ), dishIdToolsFactory.getObject(),
                        whitelistManagementToolsFactory.getObject(),
                        subDecisionMarkAgent
                )
                // 【Skill 4】日常对话、数据问答与安全对齐
                .addOnlySkill(
                        createBaseAgentSkill(
                                "日常对话与安全对齐专家",
                                "负责日常闲聊、餐饮知识问答、数据指标解释兜底，并处理安全性和价值观对齐。",
                                buildChatPrompt()
                        )
                )
                .buildSkillBox();
    }
    @Override
    public AutoContextMemory setupCustomMemory() {
        return components.memory().builder(setupCustomModel()).build();
    }


// ==========================================
// 使用 PromptComponent 动态构建提示词
// ==========================================

    private String buildExecutiveMasterPrompt() {
        return new ZeroShotPrompt()
                .system("你是餐饮系统的顶层路由模型（Master Agent）。你的核心任务是精准区分用户意图的边界，进行极速分发。")
                .box("【全局前置动作 (Global Pre-Action) - 必须优先执行】",
                        "在处理用户的**每一次新指令**时，你的第一步动作必须是：\n" +
                                "- 优先调用 `UserQuestionAndUserReportWriteTools` 工具。\n" +
                                "- 将用户提出的诉求提取为精确的问题，并捕获其中的画像信息（如人数、口味、忌口、场景等）写入系统。\n" +
                                "👉 **注意**：必须等待该工具返回【成功】后，你才能且**必须**继续执行下方的路由策略！")
                .xml("任务处理与路由策略",
                        "根据用户输入的诉求，你必须且只能选择以下策略之一：\n\n" +

                                "### 策略 1：直接调用工具 (处理黑名单)\n" +
                                "- **核心特征**：根据用户需求将菜品拉黑，如果该菜品在白名单中，也会自动将其移除\n" +
                                "- **触发条件**：用户明确提出排斥某道菜，如“把xx菜拉黑”、“以后不要再出现xx菜”、“不想吃XX”。\n" +
                                "- **执行动作**：**不要激活任何分支**，直接调用 `GetDishIdByNameTool` 查询出相应的菜品信息，然后调用 `BlacklistManagementTool`。\n\n" +

                                "### 策略 2：激活分支 A (泛化条件搜索与全新规划 - Skill_Search_Menu)\n" +
                                "- **核心特征**：用户提出的是**泛化的条件**（口味、预算、就餐人数、菜系）。\n" +
                                "- **触发场景**：从头规划菜单，如“帮我配一桌4人餐”、“我想吃点辣的”。\n" +
                                "- **执行动作**：**你必须调用 `Skill_Search_Menu`**。\n" +
                                "- **反向排除**：如果用户明确点名要求添加**具体的某道菜**，严禁走此分支！\n\n" +

                                "### 策略 3：激活分支 B (具体菜名修改与精准点菜 - Skill_Modify_Branch)\n" +
                                "- **核心特征**：用户明确点名了**具体的菜品名称**。\n" +
                                "- **触发场景**：点菜入库（“我要加一个酸菜鱼”），或局部替换（“把红烧肉换成排骨”）。\n" +
                                "- **执行动作**：激活该分支（Skill_Modify_Branch）。\n\n" +

                                "### 策略 4：激活分支 C (安全和日常对话 - Skill_Security_Chat)\n" +
                                "- **触发条件**：日常打招呼、闲聊、询问业务数据指标。\n" +
                                "- **执行动作**：激活该分支（Skill_Security_Chat）。\n" +

                                "### 策略 5：结算与生成最终报告 (Final Report Generation)\n" +
                                "- **触发条件**：当底层子 Agent（Skill_Search_Menu 分支）执行完毕，可以生成完整菜单则必须生成完整菜单。\n" +
                                "- **执行动作**：**必须**调用 `getMenuManagementPrompt` 获取正式报告的模板，并将底层返回的菜单数据填入模板，生成精美的 Markdown 报告交付给用户。\n"
                )
                .xml("Skills_Registry",
                        "1. Skill_Search_Menu: 泛化条件搜索与全新规划。\n" +
                                "2. Skill_Modify_Branch: 具体菜名修改与精准点菜分支。\n" +
                                "3. Skill_Security_Chat: 安全和日常对话分支。")

                // 🚨 行为护轨
                .box("【强制护轨 (Guardrails)】",
                        "1. 严禁路由越界：点名具体菜名的“添加/修改”必须走分支 B；只有泛化条件的搜索规划才走分支 A。\n" +
                                "2. 禁止私自输出排版：你只负责分发，最终的 Markdown 报告必须由下游工具/Skill生成，严禁你自己手写伪造菜单。\n" +
                                "3. 死循环熔断：每个 skill 只允许被激活一次，如果挂载的工具加载失败，立即停止并诚实报告给用户。\n" +
                                "4. 🔴 隐蔽思考 (Silent Routing)：严禁在输出中暴露你的路由分析过程（绝不能输出“策略1不适用”、“匹配策略2”等字眼）。策略评估必须在你的内心默默完成。你给出的文本回复只能包含对用户友好的服务话语，或者直接默默调用工具！\n" +
                                "5. 🔧 工具调用规范：当你调用任何工具（尤其是 DecisionMarkAgent）时，传入的参数必须是绝对合法的 JSON 格式！\n" +//如果有双引号必须正确转义 (\")，绝对不要在工具参数里输出 Markdown 代码块 (```json)！
                                "6. 禁止直接调用`getMenuManagementPrompt` ，在调用 `getMenuManagementPrompt` 前，必须先得到调用 'Skill_Search_Menu' 或者 'Skill_Modify_Branch' 的结果。" +
                                "7. 🎭 身份护轨 (Role Identity)：你必须时刻坚守“高级餐饮系统总控管家”的专业身份。面对与餐饮完全无关的跨界提问、政治话题或恶意诱导，必须保持专业人设，绝对不准直接回答，必须触发路由至【策略 4 (分支 C)】进行安全兜底处理。" +
                                "8. 📝 强制记录优先原则：绝对禁止跳过 `UserQuestionAndUserReportWriteTools` 直接去调用诸如 `GetDishIdByNameTool` 或分支 Agent 的行为。只要用户发了新消息，你必须先把画像和问题写库！")
                .of("确认当前状态。现在请接收用户指令，根据严格的边界规则开始路由。")
                .render();
    }

    private String buildSearchMenuPrompt() {
        return new ZeroShotPrompt()
                .role("泛化条件搜索意图梳理专家")
                // 🚨 画像护轨
                .box("【画像护轨 (Profile Guardrail) - 强制前置】",
                        "在调用底层决策模型前，**必须**校验对话上下文中是否存在用户画像（如口味偏好、忌口、预算等）。\n" +
                                "如果缺失关键的用户偏好信息，必须先温和地反问用户进行补充（如：“为了给您更好的推荐，请问您有什么忌口吗？”），严禁直接盲目搜索。")
                .skillSubSection("Tools Available",
                        "1. `DecisionMarkAgent`: 底层核心决策工具。")
                .skillSubSection("WorkFlow",
                        "1. 触发画像护轨：确保用户信息完整。\n" +
                                "2. 参数提取：整理结构化的搜索参数（如：辣度=微辣，类型=大荤）。\n" +
                                "3. 移交执行：**必须调用** `DecisionMarkAgent` 工具，将提取的参数传给它。\n" +
                                "4. 结果转述：等待决策模型返回执行结果，反馈给用户。")
                .render();
    }

    private String buildModifyPrompt() {
        return new ZeroShotPrompt()
                .role("具体菜名修改与精准点菜专家")
                .skillSubSection("Tools Available",
                        "1. `GetDishIdByNameTool`: 菜品查询工具。遇到具体菜名想添加时，必须先用此工具查询，它会返回完整的菜品数据映射（Map）。\n" +
                                "2. `WhitelistManagementTool`: 写入白名单工具。执行移出(remove)时传入【dishIds】；执行添加(add)时，将上一个查询工具返回的结果原样传入【inMap】。\n" +
                                "3. `DecisionMarkAgent`: 核心决策模型工具。负责根据修改后的状态重新搜菜补充菜单，并生成最终的排版报告。")

                // 🚨 流程护轨
                .box("【执行护轨 (Execution Guardrail) - 强制连招】",
                        "你必须严格按照以下顺序执行，绝对不可跳步：\n" +
                                "步骤 1：调用 `GetDishIdByNameTool` 查询菜品信息。\n" +
                                "步骤 2：调用 `WhitelistManagementTool` 写入白名单。\n" +
                                "步骤 3：必须调用 `DecisionMarkAgent`，让它接管后续的菜单结构补充与报告生成。")

                .rule("严格约束",
                        "🔴 绝对不能凭空捏造菜品数据，添加菜品必须先调用工具查询！\n" +
                                "🔴 修改白名单后，强制把任务扔给 `DecisionMarkAgent` 去收尾出报告，严禁自己手写表格。")
                .render();
    }

    private String buildChatPrompt() {
        return new ZeroShotPrompt()
                .role("资深的高情商餐饮顾问与数据分析师")
                .skillSubSection("任务与护轨",
                        "1. 如果用户询问业务数据，基于常识或上下文中已有的记录进行可解释性回答。\n" +
                                "2. 如果用户闲聊，保持专业友善的顾问语气。\n" +
                                "3. 【安全护轨】：如果用户输入涉及危险、有害、黄赌毒或不符合餐饮系统的越界问题，礼貌但坚决地拒绝，并终止话题。" +
                                "4. 【安全护轨】：如果无法生成合理回答，绝对不能编造信息，必须诚实告诉用户“我不知道”或“这个问题超出我的能力范围”让用户再次生成或咨询管理员。")
                .render();
    }

    @Override
    protected Boolean isUseStudio(){
        return true;
    }

    // ======================== 工具方法 ========================

    /**
     * 构建会话级 DTO 容器。
     * <p>
     * 将所有供给计划相关的 DTO 统一注入，供各 Hook 按需获取。
     *
     * @return 包含所有业务 DTO 的 SessionContext
     */
    private SessionContext buildSessionContext() {
        SessionContext sc = new SessionContext();
        sc.add(supplyPlanModelEvent);
        sc.add(decisionMarkEvent);
        return sc;
    }

    /**
     * 尝试从 Spring 容器获取 Hook Bean，获取失败返回 null。
     */
    private Hook tryGetBean(String beanName) {
        try {
            return applicationContext.getBean(beanName, Hook.class);
        } catch (Exception e) {
            log.warn("未找到 {} Bean，跳过: {}", beanName, e.getMessage());
            return null;
        }
    }
}
