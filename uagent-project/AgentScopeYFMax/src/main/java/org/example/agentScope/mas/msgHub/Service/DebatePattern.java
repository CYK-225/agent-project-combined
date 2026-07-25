package org.example.agentScope.mas.msgHub.Service;



import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.MsgHub;
import org.example.agentScope.mas.msgHub.MsgAgentPool;
import org.example.agentScope.mas.msgHub.Definition.DebateResult;
import org.example.agentScope.mas.msgHub.Definition.HubPattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 辩论模式：多名辩手循环发言，由主持人周期性裁决。
 */
public class DebatePattern implements HubPattern<DebateResult> {

    // 主题
    private String topic;
    // 参与辩论的 Agent
    private List<String> debaters = new ArrayList<>();
    // 主持人?
    private String moderator;
    // 辩论的最大轮次
    private int maxRounds = 5;
    private Consumer<String> logger = System.out::println; // 默认输出到控制台

    // --- 配置方法 (Fluent API) ---

    public static DebatePattern build() {
        return new DebatePattern();
    }

    public DebatePattern topic(String topic) {
        this.topic = topic;
        return this;
    }

    public DebatePattern debaters(String... names) {
        this.debaters.addAll(Arrays.asList(names));
        return this;
    }

    public DebatePattern moderator(String name) {
        this.moderator = name;
        return this;
    }

    public DebatePattern maxRounds(int rounds) {
        this.maxRounds = rounds;
        return this;
    }

    public DebatePattern setLogger(Consumer<String> logger) {
        this.logger = logger;
        return this;
    }

    // --- 核心执行逻辑 ---

    @Override
    public DebateResult run(MsgAgentPool pool) {
        validate();
        AgentBase judgeAgent = pool.get(moderator);

        logger.accept(">>> [Debate] Start: " + topic);

        for (int round = 1; round <= maxRounds; round++) {
            logger.accept(String.format("--- Round %d ---", round));

            // 1. 辩论阶段 (在 MsgHub 内)
            try (MsgHub hub = pool.hub()
                    .join(debaters.toArray(new String[0]))
                    .join(moderator) // 主持人入场旁听
                    .build()) {

                hub.enter().block();

                for (String name : debaters) {
                    AgentBase agent = pool.get(name);
                    String promptText = String.format(
                            "Round %d/%d. Topic: %s. Please state your argument or rebut the previous speaker.",
                            round, maxRounds, topic
                    );

                    Msg msg = agent.call(Msg.builder()
                            .name("user")
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(promptText).build())
                            .build()).block();

                    logger.accept(String.format("[%s]: %s", name, msg.getTextContent()));
                }
            }

            // 2. 裁决阶段 (MsgHub 外)
            Msg judgePrompt = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder()
                            .text("Based on the debate above, has a conclusion been reached? Return JSON with 'finished' and 'correctAnswer'.")
                            .build())
                    .build();

            // 假设 Agent 支持 .call(msg, Class) 的结构化输出
            Msg response = judgeAgent.call(judgePrompt, DebateResult.class).block();
            DebateResult result = null;
            if (response != null) {
                result = response.getStructuredData(DebateResult.class);
            }

            if (result != null && result.finished) {
                result.roundsUsed = round;
                logger.accept(STR.">>> [Debate] Finished: \{result.correctAnswer}");
                return result;
            }
        }

        logger.accept(">>> [Debate] Unresolved.");
        return DebateResult.unresolved();
    }

    private void validate() {
        if (topic == null || moderator == null || debaters.size() < 2) {
            throw new IllegalStateException("DebatePattern requires topic, moderator, and at least 2 debaters.");
        }
    }
}
