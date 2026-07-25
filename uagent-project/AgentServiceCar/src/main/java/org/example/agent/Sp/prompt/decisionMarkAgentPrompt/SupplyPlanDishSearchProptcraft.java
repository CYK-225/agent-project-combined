package org.example.agent.Sp.prompt.decisionMarkAgentPrompt;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.DecisionMarkEvent;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;

import java.time.LocalDate;

import static org.example.common.commonUtils.JsonToLLMUtils.toJson;

@NoArgsConstructor
@Slf4j
//TODO 有问题需要修改
public class SupplyPlanDishSearchProptcraft extends PromptComponent {

    // =================================================================================
    // 模式 ：搜索模式 (Search Mode) - 对应 FIND
    // 特点：模糊匹配、权重计算、多维度分析、需要标准库防幻觉
    // =================================================================================
    public void init(SupplyPlanModelEvent event, DecisionMarkEvent decisionMarkEvent) {
        log.info("SupplyPlanSearchProptcraft: 初始化搜索模式提示词构建...");
        this.definePhysicsLaws();
        this.generateSearchRole();           // 1. 角色：推荐算法专家
        this.generateStandardLibrary(decisionMarkEvent);      // 2. 标准库：严格限制 Flavor/DishType
        this.generateSearchData(event);      // 3. 数据：注入画像、报表、建议
        this.generateDimensionRules();       // 4. 维度定义：销售/评分/营收
        this.generateSearchToolRules(); //  工具选择与参数规则
        this.generateWeightCalculationRules();// 5. 权重计算：动态评分逻辑
        this.generateSearchCoT();            // 6. 思维链：意图 -> 维度 -> 权重 -> 参数

        // 记录时间快照（通用）
        this.timeSnapshot();
//        this.generateSearchOutputFormat();   // 8. 输出：SearchDishes Tool
    }



    // ---------------------------------------------------------------------------------
    //  组件实现细节
    // ---------------------------------------------------------------------------------
    private void definePhysicsLaws() {
        String laws = """
            你必须遵守以下**不可违背的物理定律**，违反这些定律会导致系统严重错误：

            -规律1：
                - **原则**: 无论在任何情况下，你都是维护3个小荤，2个大荤，3个纯素的菜单。
            -规律2：
                - **原则**:用户没有提及具体菜名，不允许擅自填充菜名
            -规律3：
                - **原则**:StandardDishTypes，StandSpicinessLevel，StandardFlavors，StandardMainIngredients这四个参数必须来源<StandardLibrary>
""";

        this.xml("CriticalPhysicsLaws", laws);
    }
    /**
     * [Search] 角色
     */
    private void generateSearchRole() {
        this.xml("Role",
                "你是一个**高级菜品推荐算法专家**。你的任务是基于用户画像、盈利报告和用户模糊的口语需求，" +
                        "计算搜索权重，设置搜索参数，挖掘最匹配的菜品候选集。");
    }


    /**
     * [Search] 数据注入 - 侧重分析报告
     */
    private void generateSearchData(SupplyPlanModelEvent event) {
        // 注入用户画像和商业报告
        this.xml("UserProfile", event.getUserReport()); // 包含口味偏好、营养需求、过敏源
        this.xml("ProfitReport", event.getFinancialReport()); // 包含成本压力、利润目标
    }



    /**
     * [Search Only] 标准库 - 防止搜索参数幻觉
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
        this.xml("StandardLibrary", p.render());
    }

    /**
     * [Search Only] 维度定义
     * [2025-01-XX 重构]: 对齐新的“消耗/货损”与“主观/客观”逻辑
     */
    private void generateDimensionRules() {
        ZeroShotPrompt dimensionDefPrompt = new ZeroShotPrompt();

        dimensionDefPrompt
                // 对应 enableHistoricalSales
                .box("SalesDimension",
                        """
                                【菜品历史销售维度 (客观行为)】
                                定义：关注历史上真实的“送达消耗率”和“光盘率”。反映客观上用户是否把菜吃完了。
                                触发语境：提到“受欢迎”、“吃得干净”、“不剩菜”、“利用率高”、“真实销量”、“实际表现”等。""")

                // 对应 enableHistoricalRating
                .box("RatingDimension",
                        """
                                【菜品历史用户评分维度 (主观评价)】
                                定义：关注食客在问卷或评价中的主观喜好。注意：可能存在滞后性。
                                触发语境：提到“口碑”、“评价好”、“高分”、“大家觉得好吃”、“众口难调”、“问卷反馈”等。""")

                // 对应 enableAvgConsumption + enablePredictedRevenue
                .box("RevenueDimension",
                        """
                                【菜品预测营收维度 (明日收益)】
                                定义：综合预测明天的“人均消耗量”(吃多少) 与 “人均货损量”(剩多少)。这是衡量收益的核心维度。
                                触发语境：spReport 重点分析盈利时，或提到“高毛利”、“赚钱”、“效益最大化”、“减少浪费”、“管饱”、“实惠”等。
                                """);

        this.xml("DimensionDefinitions", dimensionDefPrompt.render());
    }

    /**
     * [Search Only] 权重计算规则
     * [2025-01-XX 重构]: 增加 Step-by-Step 权重推演示例，强化对“预测维度”优先级的理解
     */
    private void generateWeightCalculationRules() {
        ZeroShotPrompt prompt = new ZeroShotPrompt();
        prompt.box("WeightCalculationRules",
                """
                        计算 `dimensionWeights` (Map<String,Integer>) 时，严禁使用死板的默认值。请严格遵循以下**动态推演逻辑**：
                        
                        ### 1. 基础定义 (Strict Keys)
                        Map 的 Key 必须严格限制为以下四个：`['属性', '销售', '用户', '预测']`。
                        - **属性**: 口味、食材、价格区间。
                        - **销售**: 历史客观销量、送达消耗率。
                        - **用户**: 历史主观评分、口碑。
                        - **预测**: 明日预期收益、人均消耗(管饱)、货损(防浪费)。
                        
                        ### 2. 动态打分三步法 (The 3-Step Logic)
                        1. **Baseline (基准)**: 所有维度默认为 **1**。
                        2. **Activation (激活)**: 凡是 userQuery 或 Report 提及的维度，+1 (变为2)。
                        3. **Focus (聚焦 - 核心修改点)**:
                                       - **战略接管 (Override)**: 检查 `StrategicDirective`。
                                         - 若上游强调“利润/成本/浪费” -> 强制 **`预测`=5**。
                                         - 若上游强调“口味/想吃/好评” -> 强制 **`用户`=5**。
                                         - 若上游强调“补全结构/找大荤” -> 强制 **`属性`=5**。
                                       - 若无明确战略指令，再参考用户的强烈意愿。
                        ### 3. 思考推演示例 (Chain of Thought Examples)
                        
                        **场景 A: 纯用户导向 (找好吃的)**
                        - **输入**: 用户说“给我找个好吃的辣菜”，不关心价格。
                        - **推演**:
                          1. '辣菜' -> 激活【属性维度】。
                          2. '好吃的' -> 激活【用户维度】(历史口碑) 或 【销售维度】(大家都在吃)。
                          3. **定权**: 用户意图是唯一核心。属性=5, 用户=5。
                        - **结果**: `{'属性': 5, '用户': 5, '销售': 2, '预测': 1}`
                        
                        **场景 B: 商业强约束 (强制防浪费)**
                        - **输入**: 用户说“随便来个菜”，但 `spReport` 警告“明日必须降低货损，减少浪费”。
                        - **推演**:
                          1. '降低货损/浪费' -> 激活【预测维度】(关注剩余量)。
                          2. **定权**: 这是一个强制性的商业指令，优先级高于模糊的用户需求。预测=5。
                        - **结果**: `{'预测': 5, '属性': 2, '用户': 1, '销售': 1}`
                        
                        **场景 C: 混合博弈 (既要又要)**
                        - **输入**: 用户说“要个大家爱吃的(客观)”，但 `spReport` 建议“提升明天的客单量(管饱)”。
                        - **推演**:
                          1. '大家爱吃/不剩菜' -> 激活【销售维度】(历史客观行为)。
                          2. '提升客单量/管饱' -> 激活【预测维度】(明日人均消耗)。
                          3. **定权**: 两者都很重要，双核心。
                        - **结果**: `{'销售': 4, '预测': 4, '属性': 1, '用户': 1}`
                        
                        **场景 D: 上游强制干预 (利润自救)**
                        - **StrategicDirective**: "目前利润严重不足，必须寻找低成本菜品。"
                        - **推演**: 上游发出了最高级别的商业指令。无视用户对“好吃”的模糊需求，全力保利润。
                        - **结果**: `{'预测': 5, '属性': 2, '销售': 1, '用户': 1}` (预测维度包含营收与货损)
            
                        **场景 E: 结构补缺 (常规)**
                        - **StrategicDirective**: "当前菜单缺一个素菜，请补位。"
                        - **推演**: 核心任务是属性匹配（找素菜）。
                        - **结果**: `{'属性': 5, '用户': 3, '销售': 2, '预测': 2}`
                        ""\");
                        
                        """



        );
        this.xml("WeightCalculationRules", prompt.render());
    }

    /**
     * [Search Only] 思维链
     * [2025-01-XX 重构]:
     * 1. 包含对 StandardMainIngredients 的真实存在性校验。
     * 2. 强化预测维度的组合策略。
     */
    private void generateSearchCoT() {
        String workflow =
                """
                你的决策必须遵循以下思维链 (Chain of Thought)：
                
                0. **战略对齐 (Strategic Alignment)**:
                   - 阅读 [StrategicDirective]。
                     - **核心冲突检查**:
                         - 关键词说 "大荤"，但指导说 "不要肥肉"。
                         - **执行策略**: 在调用 `searchDish` 时，虽然 `dishType='大荤'`，但必须在 `name` 或 `mainIngredient` 参数中**主动避开** "五花肉"、"肥牛" 等高脂食材，优先选择 "鸡胸"、"里脊"。
                     - **阅读 `StrategicDirective`**: 上游编排专家为什么要发起这次 `FIND`？
                     - 是为了“结构补全”（如缺大荤）？ -> 加入 `dishType` 参数。
                     - 是为了“替换违规菜”（如过敏）？ -> 加入 `mainIngredient` (避开红线)。
                     - 是为了“寻找新口味”（拓宽范围）？ -> 确保关键词不与历史重复。
                   - **结合 `userQuery`**: 提取显性的口味需求（如“微辣”）。
                   - **注意**：对于模糊探索，必须采用“饱和式参数填充”策略。
                
                1. **特征提取 (Feature Extraction)**:
                   - 从 `userQuery` 提取显性需求（如“微辣”、“有土豆的”）。
                   - 从 `spReport` 分析隐性商业目标（重点关注：是侧重“明日收益/防浪费”还是“历史口碑/销量”）？
                   - 从 `cmReport` 提取口味偏好。
    
                2. **维度激活与权重 (Dimension & Weight)**:
                   - **回顾** [WeightCalculationRules] 中的推演逻辑。
                   - **识别核心矛盾**：是“用户想吃”(属性/用户) 重要，还是“报表要求”(预测) 重要？
                   - **冲突仲裁**: 如果 [StrategicDirective] 要求“保利润”，而 [cmReport] 要求“吃好的”，**以上游战略为准**。
                   - **计算** `dimensionWeights` (Key: 属性, 销售, 用户, 预测)。
                   - **整理输出** 四个参数dimensionWeightsAttribute，dimensionWeightsSales，dimensionWeightsUsers，dimensionWeightsRevenue
    
                3. **语义泛化与范围扩张 (Semantic Expansion - 关键步骤)**:
                   - **逻辑转换**：你的下游搜索是 `IN` 查询模式。这意味着**参数填得越多，搜索到的范围越大，越安全**。
                   - **食材爆炸 (Ingredient Explosion)**：
                     - 如果用户说“来个**荤菜**” -> 你必须把标准库里**所有**肉类（红烧肉, 排骨, 鸡腿, 牛肉...）全部填入 `mainIngredient`。
                     - 如果用户说“**素菜**” -> 将标准库里**所有**蔬菜（白菜, 土豆, 青椒...）全部填入。
                     - 如果用户说“**下饭菜**” -> 填入所有重口味食材及佐料（辣椒, 酱油, 蒜苔...）。
                   - **佐料兜底**：为了保证命中率，除非用户明确忌口，否则**尽量把通用的“调味品食材”（食盐, 酱油, 白糖, 生姜, 大蒜, 花椒）加入 `mainIngredient` 列表**。
                
                4. **参数策略构造 (Strategic Parameterization)**:
                   - **预测策略 (Crucial)**: 若激活【预测维度】(意图是找高收益、管饱、不浪费)，**必须同时开启** `enableAvgConsumption` 和 `enablePredictedRevenue`，以平衡消耗与货损，算出真实收益。若上游提到“防浪费/高收益/管饱”，**必须开启** `enableAvgConsumption` 和 `enablePredictedRevenue`。
                   - **价格策略**: 若上游提到“成本控制”，严格计算并填充 `maxCostPrice`。
                   - **历史策略**: 仔细区分主观 (`enableHistoricalRating`) 和 客观行为 (`enableHistoricalSales`)，不要混淆。
                   - **价格/成本**: 区分 `minPrice`(用户售价) 和 `minCostPrice`(采购成本)。
    
                5. **标准库合规校验 (Validation)**:
                   - `dishType`: 必须严格匹配 ['大荤', '小荤', '纯素']。
                   - `spicinessLevel`: 必须严格匹配 ['不辣', '微辣', '中辣', '无辣不欢']。
                   - `flavor`: 必须来自 `StandardFlavors`。
                   - `mainIngredient`:
                    1.**必须检查是否包含在 `StandardMainIngredients` 中**。如果不完全匹配，请尝试映射到最接近的标准食材；若无法映射，则忽略该参数以防搜索失败。
                    2.遍历你在第3步生成的庞大食材列表。
                    3.**必须确保**每一个词都在 `StandardMainIngredients` 中。
                    4.**映射逻辑**：如果用户提到的词（如“耗儿鱼”）不在库中，**不要丢弃**！必须映射到库中存在的近义词（如映射为“鱼块”），或者映射到该食材所属的类别（如映射为“大蒜”、“生姜”等佐料）以保证能搜出东西。
                6. **最终参数组装 (Final Assembly)**:
                   - 确保 `mainIngredient` 列表长度足够长（建议 5-10 个以上），以充分利用 IN 查询的广度。
                   - 一定要调用工具进行搜索。
                """;
        this.xml("SearchCoT", workflow);
    }



    /**
     * [Search Only] searchDish 工具的具体参数规则
     * [2025-01-XX 修正]: 修复了Boolean开关的业务语义，修正了价格区间的逻辑
     */
    private void generateSearchToolRules() {
        // 动态计算日期，供提示词使用
        String today = LocalDate.now().plusDays(1).toString();
        String thirtyDaysAgo = LocalDate.now().minusMonths(1).toString();

        String rules =
                STR."""
            ## 工具 `searchDish` 参数填充核心规则 (Strict Rules)

            ### 1. Boolean 维度开关 (基于语境的智能开启)
            请仔细分析 `userQuery` 和 `spReport`/`cmReport`和suggestions和 的隐含意图，按需开启以下开关：

            "### 2. 明日预测维度 (Prediction for Tomorrow)" +
            "**核心逻辑**：这两个开关决定了对“明天”菜品表现的预测。由于菜品存在“可食部”差异（如带骨肉），**通常建议同时开启**这两个开关，以便算法综合计算“食用部分的真实评分”。\n\n" +

            "**【最佳实践】**：当涉及“推荐明日菜品”、“预测高收益”、“找大家爱吃的”、“实惠管饱”等常规意图时 -> **同时将以下两个参数设为 true**。" +

            "* **`enableAvgConsumption` (预测人均消耗)**:" +
            "    * **定义**: 预测明天用户会“吃进去多少”。消耗量高代表用户爱吃（摄入意愿强）。" +
            "    * **特殊独立开启场景**: 仅当用户明确只关心“分量大”、“能不能让学生吃饱”而不关心成本或浪费时。" +
            "* **`enablePredictedRevenue` (预测人均货损/浪费)**:" +
            "    * **定义**: 预测明天会有多少“被剩下”。货损低代表浪费少（光盘率高）。" +
            "    * **特殊独立开启场景**: 仅当用户明确只关心“减少汤水”、“减少骨头多的”、“单纯为了节约”而不关心用户是否爱吃时。" +

            "### 2. 历史回溯维度 (History Analysis)" +
            "**核心逻辑**：区分“用户嘴上说喜欢的(主观)”和“用户身体力行吃光的(客观)”。" +

            "* **`enableHistoricalRating` (历史问卷评分 - 主观)**:" +
            "    * **定义**: 基于历史问卷调查的主观打分。" +
            "    * **局限性**: 数据可能滞后，且受季节影响大（如冬天对凉菜评分低不代表不好吃）。" +
            "    * **触发语境**: 只有当用户明确提到“大家**觉得**怎么样”、“评价/口碑如何”、“问卷分高”时开启。" +
            "* **`enableHistoricalSales` (历史送达消耗率 - 客观)**:" +
            "    * **定义**: 历史上真实的“光盘率”（实际吃掉量 / 送达量）。" +
            "    * **优势**: 反映了真实的进食行为。当提到“实际受欢迎程度”、“不剩菜”、“真实销量”时开启。" +
            "    * **关联日期规则**: 开启时必须填充时间窗口。默认策略：" +
            "        - `historicalSalesStartDate`: **" +\{thirtyDaysAgo}  + "**" +
            "        - `historicalSalesEndDate`: **" + \{today} + "**" +

            ### 2. 双重价格/成本区间 (Context Awareness)
            严谨区分“卖给客户的价格”与“餐厅采购的成本”：
            * **售价区间 (`minPrice`, `maxPrice`)**:
                * 语境关键字：“售价”、“卖多少钱”、“餐标”、“预算”、“便宜”、“贵”。
                * 示例：“找20块以下的菜” -> `maxPrice=20.0`。
            * **成本区间 (`minCostPrice`, `maxCostPrice`)**:
                * 语境关键字：“成本”、“进货价”、“原料贵”、“高利润空间”(暗示低成本)、“控制采购价”。
                * 示例：“成本控制在5块以内” -> `maxCostPrice=5.0`。

            ### 3. 基础属性 (Enumeration & Library)
            * **`name`**: 菜品名称列表，精确批量匹配，当输入出现准确的菜名时候，可以用于精确查询，非必填。
            * **`dishType`**: 必须是 `['大荤', '小荤', '纯素']` 之一。模糊需求（如“肉菜”）默认转为 `大荤`。
            * **`spicinessLevel`**: 必须是 `['不辣', '微辣', '中辣', '无辣不欢']` 之一。
            * **`flavor`**: 必须从 StandardLibrary 中选取最接近的工艺。
            * **`dimensionWeightsAttribute`: 【属性维度】权重，数值 1-5，默认为 1。对应菜品口味、食材等。
            * **`dimensionWeightsSales`: 【销售维度】权重，数值 1-5，默认为 1。对应历史销量、消耗率。
            * **`dimensionWeightsUsers`: 【用户维度】权重，数值 1-5，默认为 1。对应历史评分、好评率。
            * **`dimensionWeightsRevenue`: 【预测维度】权重，数值 1-5，默认为 1。对应预测营收、货损。
            * **`mainIngredient`**: 必须从StandardMainIngredients中选取食材，食材的数量要尽可能的全面，至少四个以上，并尽可能覆盖主料，辅料和调味品。

            """;

        this.xml("SearchToolRules", rules);
    }

}
