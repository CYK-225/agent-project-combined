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
 * 结构化提取节点 — 调用 SlidersExtractor Agent 按 Schema 提取数据
 */
@Slf4j
@NodeAction(value = "sliders-extractor", description = "SLIDERS 提取节点：按 Schema 从文档块中提取结构化数据")
public class ExtractorNode extends SimpleNodeAction {

    @Autowired
    private AgentPoolManager agentPoolManager;

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String taskId = (String) state.value("taskId").orElseThrow(
                () -> new IllegalStateException("state 中缺少 taskId"));

        log.info("[ExtractorNode] 开始结构化提取，taskId={}", taskId);

        String threadId = "sliders-" + taskId;
        ReActAgent extractor = agentPoolManager.getAgentWithSession("SlidersExtractor", threadId);

        String prompt = """
                请对任务 %s 进行结构化数据提取。
                1. 调用 read_schema_and_chunks 获取 Schema 和文档块
                2. 逐块按 Schema 提取结构化数据，每个值附带 quote、rationale、confidence
                3. 调用 save_extracted_rows 保存提取结果
                4. 完成后回复"提取完成"
                """.formatted(taskId);

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(java.util.List.of(TextBlock.builder().text(prompt).build()))
                .build();

        Msg response = extractor.call(userMsg).block();
        log.info("[ExtractorNode] 提取完成，taskId={}，response={}", taskId, response);

        Map<String, Object> update = new HashMap<>();
        update.put("extractorResult", "COMPLETED");
        update.put("taskId", taskId);
        return update;
    }
}
