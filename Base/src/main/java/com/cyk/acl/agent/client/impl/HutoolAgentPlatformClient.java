package com.cyk.acl.agent.client.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cyk.acl.agent.client.AgentPlatformClient;
import com.cyk.acl.agent.dto.AgentTaskNotifyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于 Hutool 的 AI 中台通信客户端实现
 */
@Slf4j
@Component
public class HutoolAgentPlatformClient implements AgentPlatformClient {

    /** AI 中台地址 */
    @Value("${agent.bridge.agent-platform-url}")
    private String agentPlatformUrl;

    /** 本服务地址（用于容器回调，通过frp映射） */
    @Value("${agent.bridge.self-base-url}")
    private String selfBaseUrl;

    /** AI中台回调本服务地址（AI中台与本服务在同一机器，使用本地地址） */
    @Value("${agent.bridge.ai-platform-callback-url}")
    private String aiPlatformCallbackUrl;

    /** 回调路径 */
    @Value("${agent.bridge.callback-path:/api/v3/agent/callback}")
    private String callbackPath;

    /** HTTP 超时时间（毫秒） */
    @Value("${agent.bridge.timeout:30000}")
    private int timeout;

    @Override
    public boolean invokeAgent(AgentTaskNotifyDTO notifyDTO) {
        log.info("[AgentPlatformClient] 调用 Agent，taskId: {}", notifyDTO.getTaskId());

        try {
            notifyDTO.setCallbackUrl(aiPlatformCallbackUrl + callbackPath);
            // 直接序列化 DTO
            String jsonBody = JSON.toJSONString(notifyDTO);
            JSONObject body = JSON.parseObject(jsonBody);
            log.info("[AgentPlatformClient] 调用 Agent，taskId: {}, body: {}", notifyDTO.getTaskId(), body);
            // 发送 HTTP 请求给 AI 中台
            String url = agentPlatformUrl + "/api/agent/invoke";
            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body(body.toJSONString())
                    .timeout(timeout)
                    .execute();

            // 先检查HTTP状态码，避免将HTML错误页面当作JSON解析
            if (!response.isOk()) {
                log.error("[AgentPlatformClient] Agent 调用失败，HTTP状态码: {}, taskId: {}, 响应: {}",
                        response.getStatus(), notifyDTO.getTaskId(), response.body());
                return false;
            }

            JSONObject resp = JSON.parseObject(response.body());
            if (resp != null && resp.getBooleanValue("success")) {
                log.info("[AgentPlatformClient] Agent 调用成功，taskId: {}", notifyDTO.getTaskId());
                return true;
            } else {
                log.error("[AgentPlatformClient] Agent 调用失败，taskId: {}, resp: {}", notifyDTO.getTaskId(), resp);
                return false;
            }
        } catch (Exception e) {
            log.error("[AgentPlatformClient] 调用 AI 中台失败，taskId: {}", notifyDTO.getTaskId(), e);
            return false;
        }
    }

    @Override
    public boolean resumeAgent(AgentTaskNotifyDTO notifyDTO) {
        log.info("[AgentPlatformClient] 恢复 Agent（通过 invoke），taskId: {}, step: {}", notifyDTO.getTaskId(), notifyDTO.getStep());

        try {
            // resume 场景复用 invoke 接口（AI 中台的 invoke 支持"创建或恢复会话"）
            // 按文档 InvokeAgentRequest 格式组装请求体

            notifyDTO.setCallbackUrl(aiPlatformCallbackUrl + callbackPath);
            // 直接序列化 DTO
            String jsonBody = JSON.toJSONString(notifyDTO);
            JSONObject body = JSON.parseObject(jsonBody);
            log.info("[AgentPlatformClient] 调用 Agent，taskId: {}, body: {}", notifyDTO.getTaskId(), body);


            // 发送 HTTP 请求给 AI 中台（复用 invoke 端点）
            String url = agentPlatformUrl + "/api/agent/invoke";
            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body(body.toJSONString())
                    .timeout(timeout)
                    .execute();

            // 先检查HTTP状态码，避免将HTML错误页面当作JSON解析
            if (!response.isOk()) {
                log.error("[AgentPlatformClient] Agent 恢复失败，HTTP状态码: {}, taskId: {}, 响应: {}",
                        response.getStatus(), notifyDTO.getTaskId(), response.body());
                return false;
            }

            JSONObject resp = JSON.parseObject(response.body());
            if (resp != null && resp.getBooleanValue("success")) {
                log.info("[AgentPlatformClient] Agent 恢复成功（invoke），taskId: {}", notifyDTO.getTaskId());
                return true;
            } else {
                log.error("[AgentPlatformClient] Agent 恢复失败，taskId: {}, resp: {}", notifyDTO.getTaskId(), resp);
                return false;
            }
        } catch (Exception e) {
            log.error("[AgentPlatformClient] 恢复 Agent 失败，taskId: {}", notifyDTO.getTaskId(), e);
            return false;
        }
    }

    @Override
    public JSONObject getAgentTaskStatus(String taskId) {
        try {
            String url = agentPlatformUrl + "/api/agent/status/" + taskId;
            HttpResponse response = HttpRequest.get(url)
                    .timeout(10000)
                    .execute();

            // 先检查HTTP状态码，避免将HTML错误页面当作JSON解析
            if (!response.isOk()) {
                log.warn("[AgentPlatformClient] 查询任务状态失败，HTTP状态码: {}, taskId: {}",
                        response.getStatus(), taskId);
                return null;
            }

            return JSON.parseObject(response.body());
        } catch (Exception e) {
            log.error("[AgentPlatformClient] 查询任务状态失败，taskId: {}", taskId, e);
            return null;
        }
    }

    @Override
    public boolean cancelAgentTask(String taskId) {
        try {
            String url = agentPlatformUrl + "/api/agent/cancel/" + taskId;
            HttpResponse response = HttpRequest.post(url)
                    .timeout(10000)
                    .execute();

            // 先检查HTTP状态码，避免将HTML错误页面当作JSON解析
            if (!response.isOk()) {
                log.warn("[AgentPlatformClient] 取消任务失败，HTTP状态码: {}, taskId: {}, 响应: {}",
                        response.getStatus(), taskId, response.body());
                return false;
            }

            JSONObject resp = JSON.parseObject(response.body());
            return resp != null && resp.getBooleanValue("success");
        } catch (Exception e) {
            log.error("[AgentPlatformClient] 取消任务失败，taskId: {}", taskId, e);
            return false;
        }
    }

    @Override
    public JSONObject getAgentTaskResults(String taskId) {
        try {
            // 文档定义: GET /api/agent/detail/{taskId} — 返回任务信息 + 执行明细
            String url = agentPlatformUrl + "/api/agent/detail/" + taskId;
            HttpResponse response = HttpRequest.get(url)
                    .timeout(timeout)
                    .execute();

            // 先检查HTTP状态码，避免将HTML错误页面当作JSON解析
            if (!response.isOk()) {
                log.warn("[AgentPlatformClient] 查询任务详情失败，HTTP状态码: {}, taskId: {}",
                        response.getStatus(), taskId);
                return null;
            }

            return JSON.parseObject(response.body());
        } catch (Exception e) {
            log.error("[AgentPlatformClient] 查询任务详情失败，taskId: {}", taskId, e);
            return null;
        }
    }

    @Override
    public JSONObject getAgentTaskDetail(String taskId) {
        try {
            // 文档定义: GET /api/agent/detail/{taskId} — 返回任务信息 + 所有执行明细
            String url = agentPlatformUrl + "/api/agent/detail/" + taskId;
            HttpResponse response = HttpRequest.get(url)
                    .timeout(timeout)
                    .execute();

            // 先检查HTTP状态码，避免将HTML错误页面当作JSON解析
            if (!response.isOk()) {
                log.warn("[AgentPlatformClient] 查询任务详情失败，HTTP状态码: {}, taskId: {}",
                        response.getStatus(), taskId);
                return null;
            }

            return JSON.parseObject(response.body());
        } catch (Exception e) {
            log.error("[AgentPlatformClient] 查询任务详情失败，taskId: {}", taskId, e);
            return null;
        }
    }

    @Override
    public boolean removeAgentCache(String flowId, String agentName) {
        try {
            // 文档定义: POST /api/agent/cache/remove?flowId=xxx&agentName=xxx
            String url = agentPlatformUrl + "/api/agent/cache/remove"
                    + "?flowId=" + flowId
                    + "&agentName=" + agentName;
            HttpResponse response = HttpRequest.post(url)
                    .timeout(10000)
                    .execute();

            // 先检查HTTP状态码，避免将HTML错误页面当作JSON解析
            if (!response.isOk()) {
                log.warn("[AgentPlatformClient] 移除 Agent 会话缓存失败，HTTP状态码: {}, flowId: {}, agentName: {}",
                        response.getStatus(), flowId, agentName);
                return false;
            }

            JSONObject resp = JSON.parseObject(response.body());
            return resp != null && resp.getBooleanValue("success");
        } catch (Exception e) {
            log.error("[AgentPlatformClient] 移除 Agent 会话缓存失败，flowId: {}, agentName: {}", flowId, agentName, e);
            return false;
        }
    }

    @Override
    public boolean healthCheck() {
        try {
            // 文档定义: GET /api/agent/list — 查询任务列表，借此检测 AI 中台可达性
            String url = agentPlatformUrl + "/api/agent/list";
            HttpResponse response = HttpRequest.get(url)
                    .timeout(10000)
                    .execute();

            return response.isOk();
        } catch (Exception e) {
            log.error("[AgentPlatformClient] AI 中台健康检查失败", e);
            return false;
        }
    }
}
