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
 * 问答节点 — 调用 SlidersAnswer Agent 基于协调数据回答用户问题
 */
@Log4j2
@NodeAction(value = "sliders-answer", description = "SLIDERS 问答节点：基于协调数据回答用户问题并保存答案")
public class AnswerNode extends SimpleNodeAction {

    @Autowired
    private AgentPoolManager agentPoolManager;

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String taskId = (String) state.value("taskId").orElseThrow(
                () -> new IllegalStateException("state 中缺少 taskId"));

        log.info("[AnswerNode] ========== 开始最终问答 ==========");
        log.info("[AnswerNode] taskId={}", taskId);
        log.info("[AnswerNode] state keys={}", state.data().keySet());

        try {
            String threadId = "sliders-" + taskId;
            log.info("[AnswerNode] 获取 Agent: SlidersAnswer, threadId={}", threadId);

            ReActAgent answerAgent = agentPoolManager.getAgentWithSession("SlidersAnswer", threadId);
            log.info("[AnswerNode] Agent 获取成功: {}", answerAgent.getName());

            // 从 state 中获取问题（SlidersGraph 或 Controller 层设置）
            String question = state.value("question")
                    .map(Object::toString)
                    .orElse("(问题未传入，请调用 read_task_question)");

            String prompt = """
                    任务 ID: %s
                    用户问题: %s
                    
                    请直接调用 execute_sql 查询数据，然后调用 save_answer 保存答案。
                    """.formatted(taskId, question);

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(java.util.List.of(TextBlock.builder().text(prompt).build()))
                    .build();

            log.info("[AnswerNode] 调用 Agent.call()...");
            Msg response = answerAgent.call(userMsg).block(Duration.ofMinutes(5));
            log.info("[AnswerNode] ========== 问答完成 ==========");
            log.info("[AnswerNode] response={}", response);

            Map<String, Object> update = new HashMap<>();
            update.put("answerResult", "COMPLETED");
            update.put("taskId", taskId);
            return update;

        } catch (Exception e) {
            log.error("[AnswerNode] ========== 问答异常 ==========");
            log.error("[AnswerNode] taskId={}", taskId, e);
            throw e;
        }
    }
}
