package org.example.agentScope.mas.msgHub.Definition;



import org.example.agentScope.mas.msgHub.MsgAgentPool;

/**
 * HubPattern
 * 定义一个多智能体协作模式的执行标准。
 * @param <T> 该模式执行后的返回结果类型
 */
public interface HubPattern<T> {
    /**
     * 执行模式逻辑
     * @param pool 提供智能体资源的 MsgAgentPool
     * @return 执行结果
     */
    T run(MsgAgentPool pool);
}