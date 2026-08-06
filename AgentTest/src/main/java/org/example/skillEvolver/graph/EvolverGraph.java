package org.example.skillEvolver.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.builder.GraphBuilder;
import org.example.graph.workflow.annotation.GraphDefinition;
import org.example.graph.workflow.core.AbstractGraphTemplate;
import org.example.graph.workflow.core.GraphComponentFacade;
import org.example.graph.workflow.pattern.FanOutGraphPattern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SkillEvolver 图工作流定义（含 VerifySkillNode 发布门控）。
 * <p>
 * 核心循环：Strategize → Explore（FanOut 并行 K 个 trial）→ Analyze → Update → Verify，
 * 通过条件边判断是否继续迭代或进入 Finalize。
 *
 * <pre>
 * START
 *   ↓
 * strategize → generate-verifier → [explore-1, explore-2, ..., explore-K] → merge-trials
 *                                                        ↓
 *                                                     analyze
 *                                                        ↓
 *                                                   update-skill
 *                                                        ↓
 *                                                   verify-skill（门控）
 *                                                        ↓
 *                                           iterate_or_finish（条件边）
 *                                          ↙                  ↘
 *                                  strategize              finalize → END
 *                                 (继续迭代)             (验证+持久化)
 * </pre>
 *
 * @author zhilin
 */
@Slf4j
@GraphDefinition(
        name = "SkillEvolverLoop",
        description = "Skill 进化循环图：Strategize → Explore(FanOut) → Analyze → Update → Verify → 循环/终止",
        group = "skill-evolver",
        scope = "prototype",
        checkpointStrategy = "memory",
        recursionLimit = 50
)
public class EvolverGraph extends AbstractGraphTemplate {

    private static final int DEFAULT_K = 4;
    private static final List<String> EXPLORE_BRANCHES = List.of(
            "evolver-explore-1", "evolver-explore-2",
            "evolver-explore-3", "evolver-explore-4"
    );

    public EvolverGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {

        // ── 1. Strategize：策略变体生成 ──
        builder.addNode("strategize", components.nodeActions().get("evolver-strategize"));

        // ── 入口边：START → strategize ──
        builder.addEdge(com.alibaba.cloud.ai.graph.StateGraph.START, "strategize");

        // ── 1.5 Generate Verifier：LLM 自动生成验证规则（用户未传 verifier 时生效） ──
        builder.addNode("generate-verifier", components.nodeActions().get("evolver-generate-verifier"));
        builder.addEdge("strategize", "generate-verifier");

        // ── 2. Explore：FanOut 并行 K 个 trial ──
        FanOutGraphPattern exploreFanOut = FanOutGraphPattern.builder()
                .name("explore-parallel")
                .description("并行执行 K 个策略变体 trial")
                .fanoutNodeName("dispatch-trials")
                .branchNames(EXPLORE_BRANCHES)
                .mergeNodeName("evolver-merge-trials")
                .nodeActionPool(components.nodeActions())
                .build();
        exploreFanOut.apply(builder);

        // 连接 generate-verifier → dispatch-trials
        builder.addEdge("generate-verifier", "dispatch-trials");

        // ── 3. Analyze：Trace 差异分析 + 多维评分 ──
        builder.addNode("analyze", components.nodeActions().get("evolver-analyze"));
        builder.addEdge("evolver-merge-trials", "analyze");

        // ── 4. Update Skill：保守编辑模式 + 4 种决策 ──
        builder.addNode("update-skill", components.nodeActions().get("evolver-update-skill"));
        builder.addEdge("analyze", "update-skill");

        // ── 5. Verify Skill：发布门控（SkillClaw skill_verifier 移植） ──
        builder.addNode("verify-skill", components.nodeActions().get("evolver-verify-skill"));
        builder.addEdge("update-skill", "verify-skill");

        // ── 6. 条件边：迭代 or 终止 ──
        AsyncEdgeAction iterateOrFinish = state -> {
            int current = (int) state.value("currentIteration").orElse(0);
            int max = (int) state.value("maxIterations").orElse(2);
            boolean verified = (boolean) state.value("verificationPassed").orElse(true);
            String updateAction = (String) state.value("updateAction").orElse("improve_skill");

            // 验证未通过 → 结束（回滚后不再继续）
            if (!verified) {
                log.info("[EvolverGraph] 验证未通过，finalize（回滚后）");
                return CompletableFuture.completedFuture("finish");
            }

            // UpdateSkillNode 决定 skip → 结束
            if ("skip".equals(updateAction)) {
                log.info("[EvolverGraph] LLM 决定跳过，finalize");
                return CompletableFuture.completedFuture("finish");
            }

            // 达到最大迭代 → 结束
            String route = current < max ? "continue" : "finish";
            log.info("[EvolverGraph] 条件路由: iteration={}/{}, route={}", current, max, route);
            return CompletableFuture.completedFuture(route);
        };

        builder.addConditionalEdges("verify-skill", iterateOrFinish)
                .route("continue", "strategize")
                .route("finish", "finalize")
                .done();

        // ── 7. Finalize：最终验证 + 持久化 ──
        builder.addNode("finalize", components.nodeActions().get("evolver-finalize"));
        builder.addEdge("finalize", "__END__");

        log.info("[EvolverGraph] 图构建完成: strategize → explore(fan-out {}) → merge → analyze → update → verify → iterate/finish",
                EXPLORE_BRANCHES.size());
    }

    @Override
    protected OverAllState initialState() {
        Map<String, Object> init = new HashMap<>();
        init.put("currentIteration", 0);
        init.put("maxIterations", 2);
        init.put("nExploration", DEFAULT_K);
        init.put("taskId", "");
        init.put("taskInstruction", "");
        init.put("taskData", "");
        init.put("strategyVariants", List.of());
        init.put("trialResults", List.of());
        init.put("analysisReport", "");
        init.put("currentSkill", "");
        init.put("previousSkill", "");
        init.put("bestReward", 0.0);
        init.put("verifier", "");
        init.put("verificationPassed", true);
        init.put("updateAction", "improve_skill");
        init.put("bestSkillContent", "");
        init.put("bestSkillVersion", "");
        init.put("bestRewardEver", 0.0);
        return new OverAllState(init);
    }
}
