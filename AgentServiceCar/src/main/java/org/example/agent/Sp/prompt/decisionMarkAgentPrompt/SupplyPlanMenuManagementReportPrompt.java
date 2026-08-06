package org.example.agent.Sp.prompt.decisionMarkAgentPrompt;

import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.common.commonUtils.JsonToLLMUtils.toJson;


@Component
@Slf4j
//TODO 待修改
public class SupplyPlanMenuManagementReportPrompt extends PromptComponent {

    public void init(SupplyPlanModelEvent event, DecisionMarkEvent decisionMarkEvent) {
        // ==========================================
        // 0. 数据解构与预处理
        // ==========================================
        String userProfile = event.getUserReport();
        String profitReport = event.getFinancialReport();
        String userQuestion = event.getUserQuestion();

        // 实体数据 (Double Entity Pools)
        HashMap<Long, DishInfoAndScore> currentMenu = event.getWhiteDishMap();

        HashMap<Long, DishInfoAndScore> searchResults = decisionMarkEvent.getSearchDishList();



        // ==========================================
        // 1. 提示词构建流程 (Prompt Engineering Flow)
        // ==========================================

        // 1.1 注入上下文 (Context Injection)
        this.injectContext(
                userProfile, profitReport, userQuestion,
                currentMenu, searchResults);

        // 1.2 注入静态标准库 (Knowledge Alignment - 核心对齐点)
        // 直接复用下游的标准库定义，确保生成的关键词合法
        this.generateStandardLibrary(decisionMarkEvent);

        // 1.3 定义物理定律 (Physics Laws - 核心底层守卫)
        this.definePhysicsLaws();

        // 1.4 设定专家角色 (Role Definition)
        this.defineOrchestratorRole();

        // 1.5 定义感知逻辑 (Perception - 分析优于决策)
        this.definePerceptionLogic();



//        // 1.7 定义输出协议 (Output Protocol)
//        this.defineOutputProtocol();

        // 1.3 定义物理定律 (Physics Laws - 核心底层守卫)
        this.definePhysicsLaws();

        // 1.8 定义思维链 (Chain of Thought - 决策逻辑)
        this.defineChainOfThoughtLogic();

        // 1.9 生成快照
        this.timeSnapshot();
    }


    /**
     * 1. 上下文注入
     * 将 Java 对象转换为 LLM 可读的 XML 格式
     */
    private void injectContext(String cmReport, String ffReport, String userQuestion,
                               HashMap<Long, DishInfoAndScore> currentWhitelist,
                               HashMap<Long, DishInfoAndScore> searchResults) {

        // --- A. 决策依据区 ---
//        this.xml("UserProfile", cmReport);
//        this.xml("ProfitReport", ffReport);
        this.xml("UserFeedback", Optional.ofNullable(userQuestion).orElse("无（自动规划模式）"));

        // --- B. 实体数据区 ---
        // 池子 1: 当前白名单 (购物车)generateStructureReport
//        String whitelistContent = (currentWhitelist == null || currentWhitelist.isEmpty()) ? "[]" : toJson(currentWhitelist);
        String whitelistContent=(currentWhitelist == null || currentWhitelist.isEmpty()) ? "[]" : generateStructureReport(currentWhitelist.values().stream().toList());
        System.out.println(STR."whitelistContent in Report Proptcraft: \{whitelistContent}");
        this.xml("CurrentWhitelist", whitelistContent);

        // 池子 2: 搜索候选区 (货架)
        String searchContent = (searchResults == null || searchResults.isEmpty()) ? "[]" : toJson(searchResults);
//        String searchContent=(searchResults == null || searchResults.isEmpty()) ? "[]" : generateStructureReport(searchResults.values().stream().toList());
        this.xml("NewSearchResults", searchContent);

//        // --- C. 历史记录区 ---
//        this.xml("SearchHistory", (searchHistory.isEmpty()) ? "无" : toJson(searchHistory));
//
//        // 死胡同列表：用于触发降级搜索
//        this.xml("EmptyResultKeywords", (emptyKeywords.isEmpty()) ? "无" : toJson(emptyKeywords) );
    }
    /**
     * 2. 标准库 (Standard Library)
     * 作用：上下游知识对齐。上游必须知道下游支持哪些参数，才能生成正确的关键词。
     */
    private void generateStandardLibrary(DecisionMarkEvent decisionMarkEvent) {
        // 这些定义应与下游 SearchProptcraft 保持完全一致，建议后续提取为公共常量类
        String validFlavors = decisionMarkEvent.getValidFlavors();
        String validSpicinessLevel= "['不辣','微辣', '中辣', '无辣不欢' ]";
        String validDishTypes = decisionMarkEvent.getValidDishTypes();
        // Java 15+ 写法，不需要转义双引号
        String validMainIngredients = decisionMarkEvent.getValidMainIngredients().replace("\n", "").replace(" ", "");
        ZeroShotPrompt p = new ZeroShotPrompt();
        p
                .box("StandardDishTypes（支持的菜品结构）", toJson(validDishTypes))
                .box("StandSpicinessLevel（支持的辣度）", toJson( validSpicinessLevel))
                .box("StandardFlavors（支持的烹饪工艺）", toJson(validFlavors))
                .box("StandardMainIngredients（核心食材库 - 请在此范围内组合）", toJson(validMainIngredients))
        ;
        log.info("烹饪工艺：{}", toJson(validFlavors));

        this.xml("StandardLibrary", p.render());
    }

    /**
     * 3. 物理定律 (Critical Physics Laws)
     * 作用：解决“试图移除搜索结果”和“反复横跳”的问题
     */
    //            ### 定律 3：管理滞后性 (Management Hysteresis)
//            （当结构缺失的时候执行）
//            - **原则**: “宽进严出”。
//            - **准入 (ADD)**: 只要满足结构要求（如缺荤菜）且不违反红线，即可加入。
//            - **踢出 (REMOVE)**:
//              - 仅仅因为“利润低”、“不是最喜欢的口味”或“不够完美”，**严禁移除**已入选的菜品。
//              - 只有两个理由可以执行移除：
//                1. **致命硬伤**: 发现该菜品包含用户绝对过敏源（红线）。
//                2. **用户指令**: 用户明确说“删掉这个”。
//            - **后果**: 如果你因为“觉得它不够好”而移除它，下一轮你又会因为“缺菜”而把它加回来，导致死循环。**一旦入选，除非违规，否则焊死在白名单里。**
//
    private void definePhysicsLaws() {
        String laws = """
            你必须遵守以下**不可违背的物理定律**，违反这些定律会导致系统严重错误：
            ### 定律 0：确认菜单原则 (关于 <CurrentWhitelist>)
            - **性质**: <CurrentWhitelist> 必须要满足3大荤2素3小荤的结构要求。
            - **严禁**: 在接近结构满足的时候，停止。 例如：当前有2大荤3小荤2素菜，**严禁**输出CONFIRM_AND_LOCK

            ### 定律 1：单向流动原则 (关于 <NewSearchResults>)
            - **性质**: <NewSearchResults> 是“超市货架”，它是**只读**的。
            - **允许**: 挑选喜欢的菜品执行 `ADD_TO_WHITELIST`。
            - **严禁**: 对 <NewSearchResults> 执行 `REMOVE`。如果你不喜欢货架上的某个菜，**直接忽略它**，不要操作。

            ### 定律 2：移除即拉黑 (关于 <CurrentWhitelist>)
            - **性质**: <CurrentWhitelist> 是“购物车”。
            - **后果**: 执行 `REMOVE_FROM_WHITELIST` 会将该菜品**永久拉入黑名单**。
            - **约束**: 只能对<CurrentWhitelist>里存在的菜品进行该操作，同时除非菜品有致命缺陷（如过敏、用户明确讨厌），否则**严禁**仅仅因为“觉得不够完美”就移除它。

            ### 定律 4：搜索重置
             - 一旦执行 `ADD_TO_WHITELIST`，<NewSearchResults> 将在下一轮自动清空。
                
            ### 定律 5：库存积累阈值 (Inventory Threshold Protocol)
             - **豁免权**: 如果 <CurrentWhitelist> 的结构已经满足（3大荤3小荤2素菜），**完全忽略本定律**，直接进入结算/定稿流程。
             - **触发条件**: 仅当结构**未满足** AND <NewSearchResults> 数量 <= 10 时触发。
             - **积累期**:
               - 此时样本不足，**严禁**执行 `ADD_TO_WHITELIST`。
               - 必须优先执行 `FIND` 以扩充货架。

            ### 定律 6：搜索熵增定律 (Law of Search Entropy)
            - **定义**: 系统严禁做无用功。
            - **鼓励**: 鼓励对失败关键词进行**降级处理**（如去掉配菜，仅搜主料）或**同类替换**（如“牛肉”换成“鸡肉”）。

            ### 定律 7：搜索多样性强制 (Search Diversity Enforcement)
            - **豁免权**: 如果 <CurrentWhitelist> 的结构已经满足（3大荤3小荤2素菜），**完全忽略本定律**，直接进入结算/定稿流程。
            - **现象**: 系统严禁连续两次只搜索同一类目的菜品（例如：严禁一直搜“大荤”）。
            - **策略**: 在执行 `FIND` 时，必须采用饱和式填充的策略，尽可能的增大搜索的范围。
            - **执行逻辑**:
              - 如果 <CurrentWhitelist> + <NewSearchResults> 中，大荤有2个，素菜只有0个。
              - **必须** 存在不同的类别进行搜索
                - 示例：优先搜索“素菜”，并把"大荤加入"，严禁继续只搜索“大荤”，哪怕大荤也没凑齐。
              
              - **原则**: “谁最缺，先搜谁”，而不是“按顺序搜”，尽可能的扩大搜索的范围。
              
            ### 定律 8：标准库绝对约束 (Standard Library Absolute Constraint)
            - **性质**: <StandardLibrary> 是系统唯一识别的合法词典。
            - **规则**: 在执行 `FIND` 生成任何搜索关键词（包括菜品类型、辣度、烹饪工艺、核心食材）时，**必须且只能**严格从 <StandardLibrary> 提供的选项范围内进行提取和组合。
            - **严禁**: 严禁捏造、幻觉或使用任何不在 <StandardLibrary> 范围内的词汇，否则会导致下游搜索系统彻底崩溃。

""";
        this.xml("CriticalPhysicsLaws", laws);
    }
    /**
     * 4. 角色定义
     */
    private void defineOrchestratorRole() {
        String rolePrompt = """
            你现在的身份是**供应链菜品供应计划编排专家 (Supply Plan Orchestrator)**。
            你的目标是构建一个结构完整（3大荤2素3小荤）、符合用户画像且利润合理的菜单。
            """;
        this.xml("角色设定", rolePrompt);
    }

    /**
     * 5. 感知逻辑 (Perception Logic)
     * 作用：教模型如何“阅读”输入数据，区分“指令”与“询问”。
     */
    private void definePerceptionLogic() {
        String perception = """
            
            在执行任何决策之前，请先根据以下规则对输入数据进行**深度解析 (Deep Analysis)**：
            在执行任何决策之前，必须参考<CriticalPhysicsLaws>的规则
            **0. 资源审计 (<NewSearchResults>) —— 优先级最高**
            - 检查 <NewSearchResultsCount>。
            - 如果 Count > 0，你的首要任务是**清算库存**，而不是发起新搜索。
            
            **1. 画像解析 (<UserProfile>)**
            - **提取红线**: 识别所有的**过敏源 (Allergens)** 和 **忌口 (Dislikes)**。这些是绝对的黑名单依据。
            - **提取偏好**: 识别用户的口味偏好（如“喜欢辣”、“偏爱海鲜”），用于在 <NewSearchResults> 挑选时的加分项。

            **2. 利润解析 (<ProfitReport>)**
            - **成本意识**: 快速评估当前利润状态。
            - 如果利润过低：在后续 <NewSearchResults> 挑选时，优先选择成本较低的菜品（High Margin）。
            - 如果利润充裕：可以适当选择高品质菜品提升满意度。

            

            **4. 意图识别 (User Intent Classification)**
            请分析 <UserFeedback> 并将其归类为以下之一（至关重要）：
            
            - **[MODIFICATION_CMD]**: 明确的修改指令。
              - 关键词例示："加个..."、"删掉..."、"换成..."、"不喜欢这个"。
            
            - **[ANALYSIS_QUERY]**: 关于画像、利润或菜单构成的抽象询问。
              - 关键词例示："利润达标了吗？"、"这符合我的画像吗？"、"为什么选这个菜？"、"现在有几个荤菜？"。
            
            - **[NULL]**: 无实质内容。
            
            **5. 屏蔽思考过程**
                - **原则**: 你的思考过程必须完全屏蔽，不允许在输出中暴露任何分析步骤或内部评估。
                - **要求**: 无论你在内部如何分析和权衡，输出都只能是最终的决策指令（如 `ADD_TO_WHITELIST [ID]`、`FIND`、`EXPLAIN_ONLY` 等），绝不能输出诸如“我觉得这个菜利润太低”或“这个菜不符合画像”之类的内容。
            """;
        this.xml("PerceptionGuidelines", perception);
    }

    /**
     * 6. 系统状态守卫 (State Constraints)
     * 作用：动态注入业务指标与准则
     */
//    private void defineStateConstraints(boolean isLocked) {
//        String constraints;
//        if (isLocked) {
//            // --- 场景 A：上锁状态 (LOCKED) ---
//            constraints = """
//                【系统状态：LOCKED / 定稿保护模式】
//                现状：菜单已定稿，系统处于“只读”保护状态。
//
//                **行为准则 (Protocol):**
//                1. **默认静默**: 严禁主动寻找问题或提出修改建议。
//                2. **优先解释**: 针对用户的询问，必须输出 `EXPLAIN_ONLY`。
//
//                **例外条款 (Unlock Trigger):**
//                仅当用户意图为 **[MODIFICATION_CMD]** 且表现出**强烈意愿**时，必须打破锁定：
//                - 用户说“换掉这个菜” -> 输出 `FIND` 或 `REMOVE`
//                - 用户说“我不吃辣” -> 输出 `REMOVE`
//                注意：一旦输出非 `EXPLAIN_ONLY` 的指令，系统将自动解锁。
//                """;
//        } else {
//            // --- 场景 B：草稿状态 (DRAFT) ---
//            constraints = """
//                【系统状态：DRAFT / 编辑模式】
//                现状：正在编辑菜单。
//
//                **硬性验收标准 (Acceptance Criteria):**
//                目标是使 <CurrentWhitelist> 最终包含：**3个大荤 + 3个小荤 + 2个全素**。
//
//                **行为准则 (Protocol):**
//                1. **结构补全**: 如果数量不足，优先寻找缺失的类别。
////                2. **黑名单过滤**: 交叉比对 <UserProfile> 和 <UserFeedback>。
////                   一旦发现 <CurrentWhitelist> 中包含画像里明确禁止的食材（如过敏源、厌恶菜），**必须**立即输出 `REMOVE` 指令。
//                3. **响应指令**: 优先满足 <UserFeedback> 中的具体点菜需求。
//                """;
//        }
//        this.xml("SystemState", constraints);
//    }

//    /**
//     * 7. 输出协议
//     */
//    private void defineOutputProtocol() {
//        ZeroShotPrompt protocol = new ZeroShotPrompt();
//        protocol.box("ActionType", """
//            - ADD_TO_WHITELIST: 将 <NewSearchResults> 中的 ID 加入购物车。
//            - REMOVE_FROM_WHITELIST: 将 <CurrentWhitelist> 中的 ID 移出并拉黑。
//            - FIND: 生成新的搜索关键词 (触发新一轮搜索)。
//            - EXPLAIN_ONLY: 仅解释，不修改。
//            - CONFIRM_AND_LOCK: 确认并锁定。
//            """);
//        this.xml("输出参数校验与整合", protocol.render());
//    }

    /**
     * 7. 业务决策逻辑 (Chain of Thought)
     * 作用：基于感知的步骤化决策，解决搜索范围窄、逻辑震荡问题。
     */
    private void defineChainOfThoughtLogic() {
        String cot = """
            在执行任何思考之前，必须严格遵守<CriticalPhysicsLaws>的规则
            请严格遵循以下**思维步骤 (Chain of Thought)** 来决定 ActionType：
            
            **Step 0: 优先净化与安全审计 (Priority Purification) —— 优先级最高**
            - **目的**: 确保在填补菜单之前，先剔除所有“脏数据”，腾出合法的空位。
            - **执行时机**: <CurrentWhitelist>的菜单已经满足结构要求
            - **执行动作**: 扫描 <CurrentWhitelist>。
//              1. **红线检查**: 是否包含 <UserProfile> 中的**绝对红线**（如过敏源、严重忌口）？
              2. **指令检查**: <UserFeedback> 中用户是否明确要求 "删掉"、"不要" 某菜？
            - **决策**:
              - **YES (存在待剔除项)** -> **必须立即**输出 `REMOVE_FROM_WHITELIST [ID]`。
              - **CRITICAL STOP**: 只要执行了 `REMOVE`，**必须立即结束所有思考**。不要在同一轮里尝试搜索或添加。等待下一轮系统检测到“缺口”时，自然会触发填补逻辑。
              - **NO (干净)** -> 继续进入 Step 1。
            
            **Step 1: 终极目标校验 (The Termination Check)**
            (仅当 Step 0 未触发时执行)
            - 检查 <CurrentWhitelist> 是否已完全满足 "3大荤 + 3小荤 + 2素菜" 的结构？
            - **YES (满足)**:
               - 既然 Step 0 已经确认没有违规菜，且结构已满。
               - **任务结束**。直接输出 `CONFIRM_AND_LOCK` (如果是 DRAFT) 或 `EXPLAIN_ONLY` (如果是 LOCKED)。
            - **NO (未满足)**:
              - 继续执行 Step 2。
            
            **Step 2: 意图响应 (Intent Response)**
            - **情况 A: 用户意图是 [MODIFICATION_CMD]**
              - 用户想加菜 -> 查 <NewSearchResults> (有则 `ADD`，无则 `FIND`)。
              - 用户想换菜 -> 既然 Step 0 没触发删除，说明用户可能想看更多选项 -> 执行 `FIND`。

            - **情况 B: 用户意图是 [ANALYSIS_QUERY] (关键路径)**
              - 用户询问利润、画像匹配度或菜单结构 -> **必须输出 `EXPLAIN_ONLY`**。
              - **严禁**在此情况下修改菜单或触发搜索。
            
            **Step 3: 货架挑选 (Batch Selection Strategy)**
            必须执行以下多阶段逻辑：
            - **前置检查 (Pre-check)**:
              1. **【终极豁免】**: 检查 <CurrentWhitelist> 是否已满足 "3大荤+3小荤+2素菜"？
                 - **是** -> 你的任务已完成。**直接跳过 Step 2、3、4、5**，立即跳转至 **Step 6 (最终定稿)**。
                 - **否** -> 继续执行下一步检查。

              2. **库存检查**: 统计 <NewSearchResults> 中的菜品数量。
                 - **如果 数量 <= 10**: 样本不足。**直接跳过本步骤**，进入 Step 4 (去搜索更多)。
                 - **如果 数量 > 10**: 允许进入以下逻辑。
    
            - **阶段一：全量扫描与去重 (Global Scanning & Deduplication)**
              - 必须在此阶段完成以下任务：
              - 明确 [SystemState] 中**所有**当前缺失的结构。
              - 遍历 <NewSearchResults> 中的**每一个**菜品：
                1. **【强制去重】**：检查该 ID 是否已存在于 <CurrentWhitelist>？
                   - **是** -> **直接跳过 (SKIP)**，严禁再次处理。
                   - **否** -> 继续判断。
                2. 该菜品属于哪个缺失的类别？
                3. 该菜品评分是否合格？
                   - 若合格 -> 放入【候选池】。
                   - **【动态降级】**：如果该类别（如素菜）目前缺口严重，且【候选池】中该类别数量为0，**暂时允许**将评分略低的该类菜品放入【备选池 (Backup Pool)】。
            
            - **阶段二：组合决策 (Balanced Selection Strategy)**
              - 必须在此阶段完成以下任务：
              - **核心原则：严禁“重复添加”与“偏科”**。
              - **检查候选池**：优先从【候选池】（高分）中选择能填补 **【1大荤+1小荤+1素菜】** 的组合。
              - **兜底逻辑**：
                - 如果【候选池】中缺少某类（例如无高分素菜），但【备选池】中有低分素菜：
                - **决策**：**必须**选用低分素菜来填补结构，而不是留空，更**严禁**用多选其他类别（如多选大荤）来凑数。
                - (宁可要低分的素菜，也不要多余的高分荤菜)。
            
            - **阶段三：执行输出**
              - 必须在此阶段完成以下任务：
              - 如果选中了任何 **新 (NEW)** 菜品 -> **必须合并输出** `ADD_TO_WHITELIST [菜品列表]` -> **STOP**。
              - **安全锁**：在最终输出前，再次校验 actionTargetIds 中的 ID 是否都在 <CurrentWhitelist> 中？如果是，这是严重的逻辑错误，**必须清空列表**并转入 Step 4。
              - 仅当无法填补任何缺口时 -> 进入 Step 4。
            
            **Step 4: 结构补全与搜索策略 (Search Strategy)**
            - (Step 1-3 无操作时)
            - 检查缺什么结构？(如 "缺素菜")
            - **构建关键词 (Keyword Engineering)**:
              - **知识对齐**: 必须参考 <StandardLibrary>。
                - 例如：想搜"水煮肉片"，但Flavor库只有"炖/红烧"，应改为搜 "中辣 肉片" 或 "炖 肉片"。
                - 例如：食材必须在 `StandardMainIngredients` 范围内。
            
//
            
            - 决策 -> 输出 `FIND`。
            
            **Step 5: 致命错误审计 (Sanity Check)**
            (只有当餐单已经满足3大荤3小荤2素菜结构时执行)
            (绝对不要检查 <NewSearchResults> )
            - 只有权限检查 <CurrentWhitelist> 是否包含**绝对不可接受**的违规菜（如过敏源）？
              - 是 -> `REMOVE_FROM_WHITELIST` (优先级最高)。
              - 否 -> 继续 Step 2。
              - **注意**：如果只是因为营养/利润不够完美，严禁在此步骤删除。
            
            **Step 6: 最终定稿 (Finalization)**
            - 检查结构是否满足3大荤+3小荤+2素菜
            - 如果结构满足 AND 没有违规 AND 用户无新指令：
              - [SystemState] 为 DRAFT -> `CONFIRM_AND_LOCK`。
              - [SystemState] 为 LOCKED -> `EXPLAIN_ONLY`。
            """;

        this.xml("ThinkingProcess", cot);
    }
    private String generateStructureReport(List<DishInfoAndScore> whitelist) {
        System.out.println(whitelist);
        // 1. 使用 Stream API 进行聚合统计
        // 注意：这里处理了嵌套结构 d.getDishInfo().getDishType()
        Map<String, Long> typeCounts = whitelist.stream()
                .map(d -> d.getDishInfo().getDishType()) // 提取分类
                .peek(System.out::println) // 调试输出，查看提取结果
                .filter(Objects::nonNull)            // 防御性编程：过滤空值
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));

        // 2. 获取各分类数量（默认为 0）
        long bigMeatCount = typeCounts.getOrDefault("大荤", 0L);
        long smallMeatCount = typeCounts.getOrDefault("小荤", 0L);
        long veggieCount = typeCounts.getOrDefault("纯素", 0L);
        System.out.println(STR."bigMeatCount: \{bigMeatCount}, smallMeatCount: \{smallMeatCount}, veggieCount: \{veggieCount}");
        // 3. 定义目标（Hardcoded rules，或者从配置读取）
        int targetBig = 2;
        int targetSmall = 3; // 假设之前提到是3
        int targetVeggie = 3;

        // 4. 生成自然语言战报 (这是给 LLM 看的“唯一真理”)
        return String.format("""
        
        【当前菜单结构审计报告】(数据来源：Java系统实时统计)
        ------------------------------------------------
        [大荤] 目标: %d | 当前: %d | 状态: %s
        [小荤] 目标: %d | 当前: %d | 状态: %s
        [纯素] 目标: %d | 当前: %d | 状态: %s
        ------------------------------------------------
        结论：你需要优先填补所有状态为 [MISSING] 的缺口。
        """,
                targetBig, bigMeatCount, getStatusLabel(bigMeatCount, targetBig),
                targetSmall, smallMeatCount, getStatusLabel(smallMeatCount, targetSmall),
                targetVeggie, veggieCount, getStatusLabel(veggieCount, targetVeggie)
        );
    }

    // 辅助方法：生成状态标签
    private String getStatusLabel(long current, int target) {
        if (current >= target) {
            return "✅ FULL (已满/溢出)";
        } else {
            return STR."❌ MISSING (急缺 \{target - current} 个)";
        }
    }
}