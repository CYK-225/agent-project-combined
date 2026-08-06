package org.example.skillOpt.graph;

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
 * SkillOpt 主训练图 — 完整 6 阶段训练流水线 + epoch 循环。
 * <p>
 * 拓扑结构：
 * <pre>
 * START → init-job → epoch-setup → [rollout-1..K] (FanOut并行) → merge-rollout
 *   → reflect → aggregate → select → update → evaluate → epoch-gate(条件边)
 *       ├── continue → slow-update → meta-skill → epoch-setup (下一epoch)
 *       └── finish → finalize → END
 * </pre>
 *
 * @author zhilin
 */
@Slf4j
@GraphDefinition(
        name = "SkillOptTrainingLoop",
        description = "SkillOpt 6阶段训练循环：Rollout→Reflect→Aggregate→Select→Update→Evaluate + epoch循环",
        group = "skill-opt",
        scope = "prototype",
        checkpointStrategy = "memory",
        recursionLimit = 200
)
public class SkillOptGraph extends AbstractGraphTemplate {

    private static final List<String> ROLLOUT_BRANCHES = List.of(
            "skillopt-rollout-1", "skillopt-rollout-2",
            "skillopt-rollout-3", "skillopt-rollout-4"
    );

    public SkillOptGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {

        // ── 1. InitJob: 初始化训练任务 ──
        builder.addNode("init-job", components.nodeActions().get("skillopt-init-job"));
        builder.addEdge(com.alibaba.cloud.ai.graph.StateGraph.START, "init-job");

        // ── 2. EpochSetup: 准备当前 epoch 的数据 ──
        builder.addNode("epoch-setup", components.nodeActions().get("skillopt-epoch-setup"));
        builder.addEdge("init-job", "epoch-setup");

        // ── 3. Rollout: FanOut 并行 K 个 worker ──
        FanOutGraphPattern rolloutFanOut = FanOutGraphPattern.builder()
                .name("rollout-parallel")
                .description("并行执行 K 个 rollout（前向传播）")
                .fanoutNodeName("dispatch-rollouts")
                .branchNames(ROLLOUT_BRANCHES)
                .mergeNodeName("skillopt-merge-rollout")
                .nodeActionPool(components.nodeActions())
                .build();
        rolloutFanOut.apply(builder);

        // 连接 epoch-setup → dispatch-rollouts
        builder.addEdge("epoch-setup", "dispatch-rollouts");

        // ── 4. Reflect: 阶段2 — 梯度计算 ──
        builder.addNode("reflect", components.nodeActions().get("skillopt-reflect"));
        builder.addEdge("skillopt-merge-rollout", "reflect");

        // ── 5. Aggregate: 阶段3 — 梯度累积 ──
        builder.addNode("aggregate", components.nodeActions().get("skillopt-aggregate"));
        builder.addEdge("reflect", "aggregate");

        // ── 6. Select: 阶段4 — 梯度裁剪 ──
        builder.addNode("select", components.nodeActions().get("skillopt-select"));
        builder.addEdge("aggregate", "select");

        // ── 7. Update: 阶段5 — optimizer.step ──
        builder.addNode("update", components.nodeActions().get("skillopt-update"));
        builder.addEdge("select", "update");

        // ── 8. Evaluate: 阶段6 — 验证门控 ──
        builder.addNode("evaluate", components.nodeActions().get("skillopt-evaluate"));
        builder.addEdge("update", "evaluate");

        // ── 9. Epoch Gate: 条件路由 ──
        AsyncEdgeAction epochGate = state -> {
            int currentEpoch = (int) state.value("currentEpoch").orElse(0);
            int maxEpochs = (int) state.value("maxEpochs").orElse(5);

            if (currentEpoch >= maxEpochs) {
                log.info("[SkillOptGraph] 达到最大 epoch {}，finish", maxEpochs);
                return CompletableFuture.completedFuture("finish");
            }

            log.info("[SkillOptGraph] 继续 epoch {}/{}", currentEpoch, maxEpochs);
            return CompletableFuture.completedFuture("continue");
        };

        builder.addConditionalEdges("evaluate", epochGate)
                .route("continue", "slow-update")
                .route("finish", "finalize")
                .done();

        // ── 10. SlowUpdate: Epoch 级纵向比较 ──
        builder.addNode("slow-update", components.nodeActions().get("skillopt-slow-update"));
        builder.addEdge("slow-update", "meta-skill");

        // ── 11. MetaSkill: 优化器侧记忆 ──
        builder.addNode("meta-skill", components.nodeActions().get("skillopt-meta-skill"));
        builder.addEdge("meta-skill", "epoch-setup"); // 回到 epoch-setup 进入下一轮

        // ── 12. Finalize: 最终持久化 ──
        builder.addNode("finalize", components.nodeActions().get("skillopt-finalize"));
        builder.addEdge("finalize", "__END__");

        log.info("[SkillOptGraph] 图构建完成: init → epoch-setup → rollout(fan-out {}) → merge → reflect → aggregate → select → update → evaluate → epoch-gate → slow-update → meta-skill → epoch-setup / finalize",
                ROLLOUT_BRANCHES.size());
    }

    @Override
    protected OverAllState initialState() {
        Map<String, Object> init = new HashMap<>();
        init.put("jobId", "");
        init.put("currentEpoch", 0);
        init.put("maxEpochs", 5);
        init.put("batchSize", 4);
        init.put("editBudgetBase", 5);
        init.put("currentSkill", "");
        init.put("previousSkill", "");
        init.put("candidateSkill", "");
        init.put("bestSkill", "");
        init.put("bestValidationScore", 0.0);
        init.put("previousValidationScore", 0.0);
        init.put("metaSkill", "");
        init.put("protectedRegions", List.of());
        init.put("lrSchedulerType", "cosine");
        init.put("gateType", "mixed");
        init.put("envAdapterType", "llm-qa");
        init.put("rolloutResults", List.of());
        init.put("passedResults", List.of());
        init.put("failedResults", List.of());
        init.put("failurePatches", List.of());
        init.put("successPatches", List.of());
        init.put("aggregatedEdits", List.of());
        init.put("selectedEdits", List.of());
        init.put("gateAccepted", true);
        init.put("validationScore", 0.0);
        return new OverAllState(init);
    }
}
