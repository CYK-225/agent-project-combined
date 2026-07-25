package org.example.sliders.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据协调节点 — 调用 SlidersReconciler Agent 去重、消歧、合并
 */
@Log4j2
@NodeAction(value = "sliders-reconciler", description = "SLIDERS 协调节点：去重、消歧、合并提取数据")
public class ReconcilerNode extends SimpleNodeAction {

    @Autowired
    private AgentPoolManager agentPoolManager;

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String taskId = (String) state.value("taskId").orElseThrow(
                () -> new IllegalStateException("state 中缺少 taskId"));

        log.info("[ReconcilerNode] ========== 开始数据协调 ==========");
        log.info("[ReconcilerNode] taskId={}", taskId);

        try {
            String threadId = "sliders-" + taskId;
            ReActAgent reconciler = agentPoolManager.getAgentWithSession("SlidersReconciler", threadId);
            log.info("[ReconcilerNode] Agent 获取成功: {}", reconciler.getName());

            String prompt = """
                    请对任务 %s 进行数据协调。
                    1. 调用 read_extracted_rows 查看所有提取数据
                    2. 识别主键，按主键分组去重 → 聚合 → 冲突解决 → 规范化
                    3. 调用 save_reconciled_table 保存合并结果
                    4. 完成后回复"协调完成"
                    """.formatted(taskId);

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(java.util.List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            log.info("[ReconcilerNode] 调用 Agent.call()...");
            Msg response = reconciler.call(userMsg).block(Duration.ofMinutes(5));
            log.info("[ReconcilerNode] ========== 协调完成 ==========");
            log.info("[ReconcilerNode] response={}", response);

            Map<String, Object> update = new HashMap<>();
            update.put("reconcilerResult", "COMPLETED");
            update.put("taskId", taskId);
            log.info("[ReconcilerNode] 返回 state update: {}", update.keySet());
            return update;

        } catch (Exception e) {
            log.error("[ReconcilerNode] ========== 协调异常 ==========");
            log.error("[ReconcilerNode] taskId={}", taskId, e);
            throw e;
        }
    }
}
