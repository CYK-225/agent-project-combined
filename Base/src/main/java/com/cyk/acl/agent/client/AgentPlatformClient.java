package com.cyk.acl.agent.client;

import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import com.alibaba.fastjson2.JSONObject;

/**
 * AI 中台通信客户端接口
 * <p>
 * 负责工具平台与 AI 中台的所有 HTTP 通信
 * </p>
 */
public interface AgentPlatformClient {

    /**
     * 调用 AI 中台启动 Agent
     *
     * @param notifyDTO 调用请求（invoke 场景）
     * @return 是否调用成功
     */
    boolean invokeAgent(AgentTaskNotifyDTO notifyDTO);

    /**
     * 恢复挂起的 Agent
     *
     * @param notifyDTO 恢复请求（resume 场景）
     * @return 是否恢复成功
     */
    boolean resumeAgent(AgentTaskNotifyDTO notifyDTO);

    /**
     * 查询 Agent 任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    JSONObject getAgentTaskStatus(String taskId);

    /**
     * 取消 Agent 任务
     *
     * @param taskId 任务 ID
     * @return 是否取消成功
     */
    boolean cancelAgentTask(String taskId);

    /**
     * 查询 Agent 任务的多步骤聚合结果
     *
     * @param taskId 任务 ID（多次 invoke 共用同一个）
     * @return 聚合结果，包含所有步骤的 modelOutput
     */
    JSONObject getAgentTaskResults(String taskId);

    /**
     * 查询 Agent 任务详情（含执行明细）
     *
     * @param taskId 任务 ID
     * @return 任务详情（AgentTaskDetailVO: task + executionDetails）
     */
    JSONObject getAgentTaskDetail(String taskId);

    /**
     * 移除 Agent 会话缓存
     *
     * @param flowId    会话 ID（对应 AgentSessionCache 的 threadId）
     * @param agentName Agent 名称
     * @return 是否移除成功
     */
    boolean removeAgentCache(String flowId, String agentName);

    /**
     * 健康检查
     *
     * @return AI 中台是否可达
     */
    boolean healthCheck();
}
