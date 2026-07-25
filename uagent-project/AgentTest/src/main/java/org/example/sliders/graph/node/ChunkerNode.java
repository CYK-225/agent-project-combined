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
 * 分块节点 — 调用 SlidersChunker Agent 对文档进行分块
 */
@Slf4j
@NodeAction(value = "sliders-chunker", description = "SLIDERS 分块节点：读取文档并按段落边界分块")
public class ChunkerNode extends SimpleNodeAction {

    @Autowired
    private AgentPoolManager agentPoolManager;

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String taskId = (String) state.value("taskId").orElseThrow(
                () -> new IllegalStateException("state 中缺少 taskId"));

        log.info("[ChunkerNode] 开始分块，taskId={}", taskId);

        String threadId = "sliders-" + taskId;
        ReActAgent chunker = agentPoolManager.getAgentWithSession("SlidersChunker", threadId);

        String prompt = """
                请对任务 %s 的文档进行分块处理。
                1. 调用 read_task_documents 读取所有文档
                2. 对每个文档调用 chunk_document 进行分块
                3. 完成后回复"分块完成"
                """.formatted(taskId);

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(java.util.List.of(TextBlock.builder().text(prompt).build()))
                .build();

        Msg response = chunker.call(userMsg).block();
        log.info("[ChunkerNode] 分块完成，taskId={}，response={}", taskId, response);

        Map<String, Object> update = new HashMap<>();
        update.put("chunkerResult", "COMPLETED");
        update.put("taskId", taskId);
        return update;
    }
}
