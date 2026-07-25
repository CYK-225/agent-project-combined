package org.example.agentScope.mas.msgHub;


import org.example.agentScope.mas.msgHub.Definition.HubPattern;

public class HubEngine {

    // 防止实例化
    private HubEngine() {}

    /**
     * 在指定的 MsgAgentPool 上执行一个工作流模式
     */
    public static <T> T execute(MsgAgentPool pool, HubPattern<T> pattern) {
        return pattern.run(pool);
    }
}
