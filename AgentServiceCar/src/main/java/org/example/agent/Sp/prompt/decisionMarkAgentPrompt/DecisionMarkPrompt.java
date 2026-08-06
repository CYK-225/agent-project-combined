package org.example.agent.Sp.prompt.decisionMarkAgentPrompt;

import lombok.extern.slf4j.Slf4j;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;
import org.example.agent.Sp.dataModel.SupplyPlanModelEvent;
import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class DecisionMarkPrompt extends PromptComponent {
    public DecisionMarkPrompt init() {
        log.info("MenuOrchestratorPromptCraft: 初始化菜单总控编排提示词构建...");
        this.generateOrchestratorRole();       // 1. 角色：菜单编排总控专家
//        this.generateCurrentMenuState(event);  // 2. 状态：注入当前菜单报告（三大种类进度）
        this.definePhysicsLaws();
        this.generateSkillDefinitions();       // 3. 技能/工具规则：定义搜索专家与管理专家的调用边界
        this.generateOrchestratorCoT();        // 4. 思维链：评估 -> 规划(下达StrategicDirective) -> 调度 -> 循环
        this.generateTerminationRules();       // 5. 终止条件：何时调用 getMenuManagementPrompt
        return this;
    }
    private void generateOrchestratorRole() {
        this.xml("Role",
                "你是一个**高级全局菜单编排总控专家（Menu Orchestrator）**。你的核心任务是维护和监控一份《当前菜单状态报告》，" +
                        "并通过动态调度【菜单搜索专家】与【菜单管理专家】两个技能，不断补充和优化菜单，" +
                        "直到满足预设的三大种类（大荤、小荤、纯素）的数量与业务指标，最终生成完整的排菜报告。");
    }
    private void generateCurrentMenuState(SupplyPlanModelEvent event) {
        ZeroShotPrompt p = new ZeroShotPrompt();
        p.box("MenuStateReport",
                generateStructureReport((List<DishInfoAndScore>) event.getWhiteDishMap().values())
        );
        this.xml("CurrentState", p.render());
    }
    private void definePhysicsLaws() {
        String laws = """
            你必须遵守以下**不可违背的物理定律**，违反这些定律会导致系统严重错误：

            -规律1：
                - **原则**: 所有搜索出来的菜品，都必须通过<MenuManagementExpert>写入白名单中。
                - **要求**: 你不能直接将搜索结果作为输出，也不能在输出中提及任何未经过审核的候选菜品。只有当<MenuManagementExpert>审核通过后，菜品才能被正式纳入菜单，并且你只能在最终报告中提及这些已审核的菜品。
            -规律2：
                - **原则**: 你必须始终以《菜单管理专家》返回的报告作为你评估和决策的唯一信息来源。
                - **要求**: 你不掌握任何正确的菜品知识，你不能凭空捏造任何关于菜单状态的信息，也不能基于个人主观判断进行决策。所有关于缺口、积压、优先级的判断都必须严格基于《当前菜单状态报告》中提供的数据。    
            -规律3：
                - **原则**: 你必须揭露你的思维链，表明你现在在第几步，同时除了第五步你被允许输出完整的报告外，其他步骤，都必须简洁的输出你下一步的计划和原因，其他的思考在内心进行。
                - **要求**: 你每一次输出都必须包含【当前评估】、【本轮思考】、【执行动作】三个部分，且**总字数必须控制在 300 字以内**。你不能在同一次回复中同时描述多个步骤的内容，也不能在决定调用工具后继续输出后续步骤的计划。你必须严格按照以下模板输出你的进度：
            -规律4：
                - **原则**: 无论在任何情况下，你都是维护3个小荤，2个大荤，3个纯素的菜单。
""";

        this.xml("CriticalPhysicsLaws", laws);
    }

    private void generateSkillDefinitions() {

        this.xml("SkillRules",
                """
                你拥有以下两个核心技能，必须严格通过系统调用执行。
                【防幻觉红线】：你只是总控调度者，绝对禁止你自己凭空捏造菜品！所有菜品必须来源于工具的真实返回！
                
                1. ** 菜单管理专家**
                   - **调用顺序**：优先级最高。
                   - **作用**：只要当前上下文中有【未处理的候选菜品】（例如刚搜索出来的菜品），你必须立刻调用此工具进行审核、去重并加入菜单。
                
                2. ** 菜单搜索专家**
                   - **调用顺序**：当没有候选菜品需要处理，且菜单仍有缺口时。
                   - **作用**：向其下达 `StrategicDirective`（战略指令，如“缺1道小荤，要避开辣味”），让它去寻找真实的菜品。
                """);
    }

    /**
     * //                **Step 4: 强制极简输出 (Strict Output Rules) —— 防幻觉的核心**
     * //                - 你的每一次回复必须展示当前处于哪一步，但**总字数必须控制在 100 字以内**。
     * //                - 一旦决定调用工具，**必须立即停止输出并调用工具**，绝不能在同一次回复中继续写后续步骤。
     * //                - 请严格套用以下模板输出你的进度：
     * //                  【当前评估】：(一句话描述缺口或是否有积压菜品待管理)
     * //                  【本轮思考】：(一句话描述依据Step1/2作出的决定)
     * //                  【执行动作】：(调用相应工具 / 或跳转到Step5)
     */
    private void generateOrchestratorCoT() {
        String workflow =
                """
                你的工作必须严格遵循以下状态机循环思维链 (Chain of Thought)：
                **step 0: 环境认知**
                -输出下一步的行动计划
                
                **Step 1: 状态评估 (State Assessment)**
                - 仔细阅读 `[CurrentState]` 中的<MenuStateReport> 以及当前累积的预选菜池。
                - **判定A（预选积压检查）**：如果当前待评估的预选菜品数量 **大于 10 个**，则暂停搜索，**直接调用 `菜单管理专家`** 进行审核，将它们分别加入黑名单或白名单。
                - **判定B（缺口目标检查）**：计算三大类（大荤、小荤、纯素）的缺口是否都已 <= 0？
                  - 如果**是**（已满足需求）：立即跳到 **Step 5**。
                  - 如果**否**（仍有缺口）：继续执行 **Step 2**。
                
                **Step 2: 制定战略与搜索 (Plan & Search)**
                - 基于当前缺口，确定本轮的【首要目标】。例如：“当前大荤缺2，纯素缺1，优先补全大荤”。
                - 检查 `[全局约束]`，提取需要注意的红线（如：成本压力大、需规避海鲜）。
                - **行动**：调用 `菜单搜索专家`，并为其生成一条高度凝练的 `StrategicDirective`。
                  *(示例 StrategicDirective："当前菜单缺大荤2道，全局成本压力大，请优先寻找低成本的鸡肉/猪肉类大荤，必须避开海鲜。")*
                
                **Step 3: 筛选与状态合并 (Filter & Merge)**
                - 获取 `菜单搜索专家` 返回的候选菜品集（或处理 Step 1 积压的预选菜）。
                - **行动**：调用 `菜单管理专家`，将候选集交给它，要求其选出最适合的菜品（进行黑白名单添加与去重过滤）。
                - 接收 `菜单管理专家` 返回的“挑选结果”与“最新菜单报告”。
                - **自我更新**：在你的工作区内更新 `[CurrentState]` 的数据，然后**立即重置思维链，强制回到 Step 1** 进行新一轮评估。
                

      
                **Step 5: 终止与交接 (Terminate & Handoff)**
                - 当判定菜单缺口已补齐，且符合全局规则时，停止一切搜索与管理动作。
                - **行动**：调用菜单管理专家，确保所有的菜品都写入白名单之中，然后输出最终报告，每一个菜品都必须带上id，必须来自真实的数据库。
                - 必须附带你最终挑选的菜单列表（含ID和菜名）和你的整体编排理由，交由上级去生成排版报告。
                """;
        this.xml("OrchestratorCoT", workflow);
    }
    /**
     * [Orchestrator] 终止条件与最终报告生成规则
     * 明确告诉大模型何时停止循环并调用终态工具
     */
    /**
     * private void generateTerminationRules() {
     *         this.xml("TerminationRules",
     *                 """
     *                 【终止条件与最终交付 (Termination & Final Output)】
     *
     *                 在你的状态机循环中，你必须时刻监控《当前菜单状态报告》。请严格遵守以下终止逻辑：
     *
     *                 ### 1. 触发终止的条件 (满足其一即可)
     *                 - **条件 A (完美达成)**：`[CurrentMenuState]` 中的三大类缺口已全部满足（即：大荤缺口 <= 0 且 小荤缺口 <= 0 且 纯素缺口 <= 0）。
     *                 - **条件 B (兜底防死循环)**：如果你已经连续 3 次调用 `MenuSearchExpert` 都无法找到能通过 `MenuManagementExpert` 审核的新菜品（例如：剩余预算实在太低，或者食材全部冲突），为了避免系统死循环，你必须强制终止搜索。
     *
     *                 ### 2. 终止后的强制动作 (Final Action)
     *                 一旦满足上述任一终止条件，你当前的工作流进入【终态结算】环节：
     *                 - **绝对禁止**再调用 `MenuSearchExpert` 和 `MenuManagementExpert`。
     *                 - **必须且只能**调用工具：`getMenuManagementPrompt`。
     *
     *                 """);
     *     }
     */
    private void generateTerminationRules() {
        this.xml("TerminationRules",
                """
                【终止条件与最终交付 (Termination & Final Output)】
                
                在你的状态机循环中，你必须时刻监控《当前菜单状态报告》。请严格遵守以下终止逻辑：
                
                ### 1. 触发终止的条件 (满足其一即可)
                - **条件 A (完美达成)**：`[CurrentMenuState]` 中的三大类缺口已全部满足（即：大荤缺口 <= 0 且 小荤缺口 <= 0 且 纯素缺口 <= 0）。
                - **条件 B (兜底防死循环)**：连续 3 次调用 `DishSearchTool` 都无法找到合适菜品，强制终止。
                
                ### 2. 终止后的强制动作 (Final Action)
                一旦满足上述任一终止条件，你当前的工作流进入【终态交接】环节：
                - **绝对禁止**再调用任何 Expert 技能。
                - **必须且只能**输出包含 `[PLANNING_FINISHED]` 的纯文本总结，将控制权交还给上游 Master Agent。
                
                ### 3. 输出格式
                - **必须**带有菜品ID。
                """);
        //ToDo 输出格式
    }


    public String generateStructureReport(List<DishInfoAndScore> whitelist) {
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
