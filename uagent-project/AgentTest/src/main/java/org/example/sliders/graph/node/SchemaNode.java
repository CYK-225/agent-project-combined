package org.example.sliders.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema 归纳节点 — 调用 SlidersSchemaAgent 设计关系型 Schema
 */
@Slf4j
@NodeAction(value = "sliders-schema", description = "SLIDERS Schema 节点：分析问题类型和文档结构，设计关系型 Schema")
public class SchemaNode extends SimpleNodeAction {

    @Autowired
    private AgentPoolManager agentPoolManager;

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String taskId = (String) state.value("taskId").orElseThrow(
                () -> new IllegalStateException("state 中缺少 taskId"));

        log.info("[SchemaNode] 开始 Schema 归纳，taskId={}", taskId);

        String threadId = "sliders-" + taskId;
        ReActAgent schemaAgent = agentPoolManager.getAgentWithSession("SlidersSchemaAgent", threadId);

        String prompt = """
                请对任务 %s 进行 Schema 归纳。
                1. 调用 read_task_and_chunks 获取任务问题和块摘要
                2. 分析问题类型和文档结构，设计关系型 Schema
                3. 调用 save_schema 保存 Schema
                4. 完成后回复"Schema 归纳完成"
                """.formatted(taskId);

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(java.util.List.of(TextBlock.builder().text(prompt).build()))
                .build();

        Msg response = schemaAgent.call(userMsg).block();
        log.info("[SchemaNode] Schema 归纳完成，taskId={}，response={}", taskId, response);

        Map<String, Object> update = new HashMap<>();
        update.put("schemaResult", "COMPLETED");
        update.put("taskId", taskId);
        return update;
    }
}
