package org.example.agentScope.mas.msgHub.Service;



import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.MsgHub;
import lombok.Builder;
import org.example.agentScope.mas.msgHub.MsgAgentPool;
import org.example.agentScope.mas.msgHub.Definition.HubPattern;

import java.util.ArrayList;
import java.util.List;

/**
 * 协作综合模式：大家自由讨论几轮，最后由特定角色进行总结。
 */
@Builder
public class SynthesisPattern implements HubPattern<String> {

    private final String topic;
    private final String synthesizerName;
    private final List<String> contributors;
    private final int discussionRounds;



    @Override
    public String run(MsgAgentPool pool) {
        System.out.println(STR.">>> [Synthesis] Start: \{topic}");

        // 1. 自由讨论
        List<String> allParticipants = new ArrayList<>(contributors);
        allParticipants.add(synthesizerName);

        for (int i = 0; i < discussionRounds; i++) {
            try (MsgHub hub = pool.hub().join(allParticipants.toArray(new String[0])).build()) {
                hub.enter().block();
                for (String name : contributors) {
                    pool.get(name).call().block();
                }
            }
        }

        // 2. 最终总结
        AgentBase synthesizer = pool.get(synthesizerName);
        Msg summaryMsg = synthesizer.call(Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text("Summarize the discussion above.").build())
                .build()).block();

        return summaryMsg.getTextContent();
    }
}
