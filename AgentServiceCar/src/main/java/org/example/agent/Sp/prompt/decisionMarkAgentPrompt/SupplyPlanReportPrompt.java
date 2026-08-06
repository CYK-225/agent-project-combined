package org.example.agent.Sp.prompt.decisionMarkAgentPrompt;

import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.common.proptcraft.PromptComponent;

import java.util.HashMap;

import static org.example.common.commonUtils.JsonToLLMUtils.toJson;

public class SupplyPlanReportPrompt extends PromptComponent {
    public void init(SupplyPlanModelEvent event) {
        // 0. 解构数据
        String userProfile = event.getUserReport(); // 包含口味偏好、营养需求、过敏源
        String profitReport = event.getFinancialReport(); // 包含成本压力、利润目标
        String userQuestion = event.getUserQuestion();

        // 实体数据 (只读模式：用于提供证据)
        HashMap<Long, DishInfoAndScore> currentMenu = event.getWhiteDishMap();
        HashMap<Long, String> blackList = event.getBlackDishMap();

//        // 1. 注入全量上下文 (作为知识库)
        this.injectKnowledgeBase(userProfile, profitReport, currentMenu, blackList);

        // 2. 设定顾问角色 (解释性专家)
        this.defineConsultantRole();

        // 3. 定义解释逻辑 (核心：如何利用数据说话)
        this.defineExplainabilityLogic(userQuestion);

        // 4. 定义输出协议 (纯文本或简单包装)
        this.defineOutputProtocol();
    }

    /**
     * 1. 知识库注入
     * 这里不需要像之前那样为了操作而分类，而是为了检索而建立索引
     */
    private void injectKnowledgeBase(String cmReport, String ffReport,
                                     HashMap<Long, DishInfoAndScore> currentMenu,
                                     HashMap<Long, String> blackList) {

        // --- 依据层 ---
        this.xml("UserProfile", cmReport); // 解释“为什么适合你”的依据
        this.xml("ProfitConstraint", ffReport); // 解释“为什么性价比高”的依据

        // --- 事实层 (当前选定的菜单) ---
        // 重点：LLM 需要读取 DishInfoAndScore 中的 ingredients(食材), nutrition(营养), price(价格)
        this.xml("CurrentMenu", toJson(currentMenu));

    }

    /**
     * 2. 角色定义：咨询顾问
     */
    private void defineConsultantRole() {
        String rolePrompt = """
        你现在的身份是**资深膳食营养与供应链顾问**。
        你**没有修改菜单的权限**（这是其他系统的职责），你的唯一任务是：
        **基于数据，向用户解释当前菜单设计的合理性，并解答相关疑问。**
        
        你的回答必须具备：
        1. **可解释性 (Explainability)**：所有论点必须引用 XML 中的具体数据。不能凭空捏造。
        2. **高情商 (Empathy)**：语气专业、温和，理解用户的潜在担忧。
        3. **透明度 (Transparency)**：如果是因为成本或黑名单原因导致的问题，请委婉但诚实地说明。
        4. **允许失败 (Permission to Fail)**：如果数据不足以支持解释，可以坦诚告知用户“目前无法提供充分解释”。
        """;
        this.xml("RoleDefinition", rolePrompt);
    }

    /**
     * 3. 解释逻辑 (思维链)
     * 教会 LLM 如何通过连接不同的数据源来生成答案
     */
    private void defineExplainabilityLogic(String userQuestion) {
        this.xml("UserQuestion", userQuestion);

        String logic = """
        请根据用户的提问类型，采用对应的**解释策略**：

        **场景 A：用户询问“为什么选这道菜？”**
        - **步骤 1 (匹配)**: 在 <CurrentMenu> 中找到该菜品的详细信息（口味）。
        - **步骤 2 (关联)**:
            - 将菜品的【口味】与 <UserProfile> 中的【偏好】关联（例：“我知道您喜欢川湘口味，这道菜特意保留了微辣口感”）。
        
        **场景 B：用户询问“为什么没有某道菜（或某类食材）？”**
        - **步骤 1 (查黑名单)**: 检查 <BlacklistHistory>。如果存在，回答：“因为您在[日期]以‘[理由]’将其拉黑了，系统自动为您屏蔽。”

        **场景 C：用户询问“这顿饭营养达标吗？”**
        - **统计分析**: 遍历 <CurrentMenu> 的所有菜品，累加蛋白质、热量等数据。
        - **对比**: 将累加值与 <UserProfile> 中的建议摄入量对比，给出“偏高”、“达标”或“偏低”的结论。

        **场景 D：用户闲聊或模糊提问**
        - 引导用户关注菜单中的亮点（如：“您看这道[高分菜品名]，是我们根据您的口味特意搜索到的...”）。
        """;

        this.xml("ThinkingProcess", logic);
    }

    /**
     * 4. 输出协议
     * 此时只需要输出自然的回复文本
     */
    private void defineOutputProtocol() {
        // 简单输出，直接返回回复内容
        // 如果前端需要流式输出，这里可以直接要求输出 Markdown 格式的文本
        String protocol = """
        请直接输出回复内容，不要包含 JSON 格式，不要包含 XML 标签。
        严格使用 Markdown 格式优化排版（如对菜名加粗）。
        菜品要引用其 ID，格式为：**菜名**。
        
        **风格要求**：
        - 只要涉及菜品，请务必带上其 ID，格式为：**菜名**。
        - 引用数据时要自然，不要像报表一样罗列。
        """;

        this.xml("OutputProtocol", protocol);
    }


}
