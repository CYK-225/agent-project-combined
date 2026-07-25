package org.example.agent.user.agents;

import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import lombok.*;

import org.example.agent.user.tool.CustomerTools;
import org.example.agent.user.tool.UserSqlTool;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;


@EqualsAndHashCode(callSuper = true)
@AgentDefinition(
        name = "MasterUserAgents"
        , description = "餐饮智能体总控，负责理解用户意图并路由使用不同的skill，完成任务"
//        ,enablePlan  = true
        ,hooksType = "log"
        ,group = "canche"
        ,enablePersistence = true   // ✅ 启用自动记忆持久化
//        ,scope = "singleton"
)
@Component
public class MasterUserAgents extends AbstractAgentTemplate {

    @Resource
    private ObjectProvider<UserSqlTool> userSqlToolsFactory;

    /**
     * 构造函数
     *
     * @param components 组件门面
     */
    protected MasterUserAgents(AgentComponentFacade components) {
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
        System.out.println("threadID:"+super.getThreadID());
        return components.toolkit().create().build();
    }
    @Override
    protected Boolean isUseStudio(){
        return true;
    }

    @Override
    protected SkillBox setupSkills() {
        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        (createBaseAgentSkill(
                                "餐饮数据对话分析专家",
                                "专用于解答餐饮数据的具体指标、趋势等提问，通过自然语言提供数据洞察，直接给出结论，不输出复杂表格。",
                                buildChatPrompt()
                        )),userSqlToolsFactory.getObject(),new CustomerTools()
                )
                .addSkillWithTools(
                        createBaseAgentSkill(
                                "餐饮标准报告生成专家",
                                "专用于根据用户需求拉取全量业务数据，并严格按照标准的 Markdown 格式输出菜单规划报告。",
                                buildGeneratePrompt()
                        ),userSqlToolsFactory.getObject(),new CustomerTools()

                )
                .addSkillWithTools(
                        createBaseAgentSkill(
                                "餐饮报告编辑专家",
                                "专门用于修改和微调已有的餐饮报告文本，在严格保持原有的 Markdown 表格排版结构不变的前提下更新内容。",
                                buildModifyPrompt()
                        ),new CustomerTools()
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
                // 1. 明确身份：业务指挥中心
                .system("你扮演餐饮业务指挥中心（Executive Master Agent）。你的核心职责是：精准识别用户意图，调度底层专家技能（Skills），并整合工具返回的真实信息交付最终成果。")

                // 2. 意图识别与技能路由：参考 defineTaskRouter 风格
                .xml("任务意图路由",
                        "根据用户输入的诉求性质，你必须将其路由至对应的执行链路：\n\n" +
                                "### 意图 A：数据洞察与趋势询问 (Skill_Analysis)\n" +
                                "- **核心诉求**：询问具体数值、质疑数据逻辑或对比趋势。\n" +
                                "- **执行链路**：先激活专家skill->激活技能 -> 遵循专家skill的提示词 -> 产出纯文本业务洞察。\n\n" +
                                "### 意图 B：标准报告生成 (Skill_Report_Gen)\n" +
                                "- **核心诉求**：从零开始生成完整业务报告、菜单规划或标准化 Markdown 报告。\n" +
                                "- **执行链路**：先激活专家skill->激活技能 -> 遵循专家skill的提示词 -> 不断循环刚才的过程，直到-> 产出完整 Markdown 报告。\n\n" +
                                "### 意图 C：报告局部编辑 (Skill_Edit)\n" +
                                "- **核心诉求**：修改现有报告的文字，保持原有排版和表格结构不变。\n" +
                                "- **执行链路**：先激活专家skill->激活技能 -> 调用对应编辑工具 -> 产出修改后的全量内容。"+
                                "### 意图 D：自由聊天 (Skill_Edit)\n" +
                                "- **核心诉求**：正常聊天，不需要提前激活专家。\n" +
                                "- **执行链路**：正常根据用户需求聊天或者调用工具。"+
                                "### 意图 E：测试 (Skill_Edit)\n" +
                                "- **核心诉求**：激活测试skill。\n" +
                                "- **执行链路**：激活skill，然后调用getDiningReportStandardGuide()。"
                )

                // 3. 技能注册表：明确每个专家技能的职能
                .xml("Skills_Registry",
                        "1. Skill_Analysis: 餐饮数据对话分析专家。利用 sql_select_tool 的结果直接提供结论，不输出复杂表格。\n" +
                                "2. Skill_Report_Gen: 餐饮标准报告生成专家。必须综合数据、指标标准和报告模板，产出专业的 Markdown 报告。\n" +
                                "3. Skill_Edit: 餐饮报告编辑专家。在保持原排版结构前提下进行内容微调。")

                // 4. 交付约束：确保不输出中间状态，直接给结果
                .rule("交付物规范（StrictConstraints）",
                        "🔴 严禁幻觉：禁止基于通用知识虚构数据。所有数字必须来源于 Observation 中的真实返回。\n" +
                                "🔴 禁止中间态输出：严禁在最终回复前输出“正在调用工具”、“准备开始计算”等描述。用户只关心最终的业务解答或报告全文。\n" +
                                "🔴 结构一致性：对于【意图 B】，输出的报告必须严格符合 getDiningReportStandardGuide 提供的 Markdown 结构，且数据填充准确。\n" +
                                "🔴 终态交付：无论中间调用了多少次工具，你的最后一次输出必须是解决问题的最终答案（Final Answer）。"+
                                "🔴 激活skill：每个skill，只允许被激活一次，如果skill携带的工具没有正确加载，则直接报告用户。")



                .of("确认当前状态。现在请接收用户指令，根据意图路由开始执行。")
                .render();
    }
    private String buildChatPrompt() {
        // 使用匿名内部类实例化抽象类，利用链式 API 构建 Prompt
        return new ZeroShotPrompt()
                .role("你是一个资深的餐饮数据分析师。你的任务是根据用户的提问，提供精准的数据洞察。")
                .skillSubSection("ps",
                        "你需要的工具，现在都已激活，请看完md后，返回主循环使用工具完成任务，禁止不断激活工具，如果出现工具未出现的情况，请直接结束循环报告用户")
                .skillSubSection("Tools Available",
                        "1. `sql_select_tool`: 用于执行 SQL 获取原始数据。\n" +
                                "2. `getCateringAnalysisMetricsPrompt`: 获取分析指标指南。")
                .skillSubSection("WorkFlow",
                        "1. 分析用户问题，调用 `sql_select_tool` 生成并执行 SQL 获取数据。\n" +
                                "2. 调用 `getCateringAnalysisMetricsPrompt` 获取指标计算规则。\n" +
                                "3. 用自然、专业的对话口吻回答用户，直接给出结论和关键数据。")
                .skillSubSection("Constraints",
                        "- 🔴 绝对不要输出冗长的表格或 Markdown 复杂排版。\n" +
                                "- 🔴 如果数据不足以支撑结论，请诚实地告诉用户“当前数据不足”。")
                .skillSubSection("ps",
                        "请参考skill的建议，一步一步调用工具，现在工具链已经刷新，请根据skill新激活的工具，完成任务,绝不可中途结束")
                .render();
    }

    private String buildGeneratePrompt() {
        return new ZeroShotPrompt()
                .role("你是一个严谨的餐饮报告生成专家。你的唯一目标是产出符合公司标准格式的菜单规划报告。")
                .skillSubSection("ps",
                        "你需要的工具，现在都已激活，请看完md后，返回主循环使用工具完成任务，禁止不断激活工具，如果出现工具未出现的情况，请直接结束循环报告用户")

                .skillSubSection("Tools Available",
                        "1. `sql_select_tool`: 用于获取全量业务数据。\n" +
                                "2. `getCateringAnalysisMetricsPrompt`: 获取指标计算规则。\n" +
                                "3. `getDiningReportStandardGuide`: 获取报告 Markdown 模板。")
                .skillSubSection("WorkFlow",
                        "1. 调用 `getDiningReportStandardGuide` 获取标准的 Markdown 报告模板。\n" +
                                "2. 调用 `sql_select_tool` 提取所需的全量底层数据。\n" +
                                "3. 调用 `getCateringAnalysisMetricsPrompt` 获取量化标准，进行数据汇总。\n" +
                                "4. 思考计算并将计算结果完美填入模板中。")
                .skillSubSection("Constraints",
                        "- 🔴 你的最终输出必须 100% 遵循 `getDiningReportStandardGuide` 提供的 Markdown 结构！\n" +
                                "- 🔴 绝对禁止私自增删表格的列名，禁止改变原定的各级标题。不要包含多余的开头寒暄。")

                .render();
    }

    private String buildModifyPrompt() {
        return new ZeroShotPrompt()
                .role("你是一个专业的文档排版编辑。你的任务是根据用户的修改指令，更新现有的报告文本。")
                .box("Context", "用户会在对话中提供【原始报告内容】和【修改指令】。")
                .skillSubSection("Tools Available",
                        "1. `getDiningReportStandardGuide`: 仅用于获取格式参考，防止你在修改时破坏原本的排版。")
                .skillSubSection("WorkFlow",
                        "1. 仔细阅读上下文中的原报告和修改指令。\n" +
                                "2. 仅对要求修改的字句、风险等级或菜品进行局部替换。")
                .skillSubSection("Constraints",
                        "- 🔴 你的修改必须基于用户提供的原文！绝对禁止虚构未提供的数据。\n" +
                                "- 🔴 无论怎么修改内容，必须原封不动地保持原有的 Markdown 表格和标题骨架！\n" +
                                "- 🔴 只输出修改后的完整报告，不要解释你修改了哪里。")
                .skillSubSection("ps",
                        "请参考skill的建议，一步一步调用工具，绝不可中途结束")
                .render();
    }




}
