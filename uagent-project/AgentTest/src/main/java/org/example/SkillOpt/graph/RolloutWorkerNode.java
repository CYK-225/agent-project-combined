package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.agent.SkillOptTargetAgent;
import org.example.skillOpt.agent.SkillOptToolAgent;
import org.example.skillOpt.agent.SkillOptTraceHook;
import org.example.skillOpt.env.EnvAdapter;
import org.example.skillOpt.env.RolloutContext;
import org.example.skillOpt.env.RolloutResult;
import org.example.skillOpt.env.StepRecord;
import org.example.skillOpt.env.qa.QAEnvAdapter;
import org.example.skillOpt.env.search.SearchQAEnvAdapter;
import org.example.skillOpt.entity.SkillOptRolloutResultEntity;
import org.example.skillOpt.mapper.SkillOptRolloutResultMapper;

import java.util.*;

/**
 * Rollout Worker 基类 — 单个 rollout 执行（"前向传播"）。
 * <p>
 * 子类 RolloutWorker2/3/4 只需覆盖 variantIndex 和 action value。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>简单模式：无工具调用，Agent 直接返回答案</li>
 *   <li>工具模式：Agent 可调用工具，收集多轮轨迹</li>
 * </ul>
 *
 * @author zhilin
 */
@Slf4j
public abstract class RolloutWorkerNode extends SimpleNodeAction {

    /** 子类覆盖此方法返回 worker 序号（1-based） */
    protected abstract int getVariantIndex();

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);
        int variantIndex = getVariantIndex();
        String currentSkill = (String) state.value("currentSkill").orElse("");
        String envAdapterType = (String) state.value("envAdapterType").orElse("llm-qa");

        List<Map<String, Object>> trainBatch =
                (List<Map<String, Object>>) state.value("trainBatch").orElse(List.of());

        // 获取对应的 task instance（batch 中的第 variantIndex-1 个）
        boolean isOversized = variantIndex - 1 >= trainBatch.size();
        Map<String, Object> taskInstance = isOversized
                ? Map.of("question", "(empty task)", "_skipped", true)
                : trainBatch.get(variantIndex - 1);

        log.info("[RolloutWorker-{}] jobId={}, epoch={}, task={}, oversized={}",
                variantIndex, jobId, epoch, 
                truncate(String.valueOf(taskInstance.getOrDefault("question", "")), 60),
                isOversized);

        // 如果 batch 大小小于 worker 数量，直接返回跳过结果
        if (isOversized) {
            RolloutResult skippedResult = RolloutResult.builder()
                    .variantIndex(variantIndex)
                    .status("SKIPPED")
                    .hardScore(0.0)
                    .softScore(0.0)
                    .trajectory("")
                    .agentResponse("")
                    .taskInstance(taskInstance)
                    .toolCallingUsed(false)
                    .build();
            persistRolloutResult(jobId, epoch, variantIndex, skippedResult);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("rolloutResult_" + variantIndex, toResultMap(skippedResult));
            return output;
        }

        // 创建适配器和 prompt
        EnvAdapter envAdapter = createEnvAdapter(envAdapterType);
        String taskPrompt = envAdapter.buildRolloutPrompt(taskInstance, currentSkill);

        // 判断是否使用工具调用模式
        RolloutResult rolloutResult;
        if (envAdapter.supportsToolCalling()) {
            rolloutResult = executeToolRollout(jobId, epoch, variantIndex, currentSkill, taskPrompt, envAdapter, taskInstance);
        } else {
            rolloutResult = executeSimpleRollout(jobId, epoch, variantIndex, currentSkill, taskPrompt, envAdapter, taskInstance);
        }

        // 持久化 rollout 结果
        persistRolloutResult(jobId, epoch, variantIndex, rolloutResult);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rolloutResult_" + variantIndex, toResultMap(rolloutResult));
        return output;
    }

    /**
     * 执行简单 rollout（无工具调用）
     */
    private RolloutResult executeSimpleRollout(String jobId, int epoch, int variantIndex,
                                                String currentSkill, String taskPrompt,
                                                EnvAdapter envAdapter, Map<String, Object> taskInstance) {
        // 设置 Target Agent 上下文
        SkillOptTargetAgent.setContext(
                new SkillOptTargetAgent.TargetContext(jobId, epoch, variantIndex, currentSkill, taskPrompt));

        try {
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
            String threadId = "skillopt-rollout-" + jobId + "-" + epoch + "-" + variantIndex;
            ReActAgent targetAgent = poolManager.getAgentWithSession(
                    "SkillOptTarget", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = targetAgent.call(userMsg).block();
            String agentResponse = (response != null) ? response.getTextContent() : "";

            RolloutResult result = RolloutResult.builder()
                    .variantIndex(variantIndex)
                    .status("COMPLETED")
                    .hardScore(envAdapter.hardScore(
                            RolloutResult.builder()
                                    .agentResponse(agentResponse)
                                    .taskInstance(taskInstance)
                                    .build()))
                    .softScore(envAdapter.softScore(
                            RolloutResult.builder()
                                    .agentResponse(agentResponse)
                                    .taskInstance(taskInstance)
                                    .build()))
                    .trajectory(taskPrompt)
                    .agentResponse(agentResponse)
                    .taskInstance(taskInstance)
                    .toolCallingUsed(false)
                    .build();

            // 根据 hardScore 判断 PASSED/FAILED
            if (result.getHardScore() >= 1.0) {
                result.setStatus("PASSED");
            } else if (result.getSoftScore() > 0.3) {
                result.setStatus("PARTIAL");
            } else {
                result.setStatus("FAILED");
            }

            return result;

        } catch (Exception e) {
            log.error("[RolloutWorker-{}] 执行异常: {}", variantIndex, e.getMessage());
            return RolloutResult.builder()
                    .variantIndex(variantIndex)
                    .status("ERROR")
                    .hardScore(0.0)
                    .softScore(0.0)
                    .trajectory("Error: " + e.getMessage())
                    .agentResponse("")
                    .taskInstance(taskInstance)
                    .toolCallingUsed(false)
                    .build();
        } finally {
            SkillOptTargetAgent.clearContext();
        }
    }

    /**
     * 执行带工具调用的 rollout — 手动多轮工具调用循环。
     * <p>
     * 由于框架层面的函数调用注册存在兼容性问题，此方法采用文本解析方式实现工具调用：
     * 1. 发送任务 prompt 给 Agent
     * 2. 解析 Agent 响应中的工具调用模式（如 query_environment(query='...')）
     * 3. 通过 EnvAdapter 执行工具并获取结果
     * 4. 将工具结果作为后续消息发送给 Agent
     * 5. 重复直到 Agent 不再调用工具或达到最大步数
     */
    private RolloutResult executeToolRollout(String jobId, int epoch, int variantIndex,
                                              String currentSkill, String taskPrompt,
                                              EnvAdapter envAdapter, Map<String, Object> taskInstance) {
        log.info("[RolloutWorker-{}] 使用工具调用模式（手动循环）", variantIndex);

        SkillOptTraceHook.clearThreadTrace();

        SkillOptToolAgent.setContext(
                new SkillOptToolAgent.ToolTargetContext(
                        jobId, epoch, variantIndex, currentSkill, taskPrompt, envAdapter, taskInstance));

        int maxSteps = 5;
        List<StepRecord> stepRecords = new ArrayList<>();

        try {
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
            String threadId = "skillopt-tool-rollout-" + jobId + "-" + epoch + "-" + variantIndex;
            ReActAgent toolAgent = poolManager.getAgentWithSession(
                    "SkillOptToolTarget", threadId, null, List.of());

            // 第一轮：发送任务 prompt
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = toolAgent.call(userMsg).block();
            String agentResponse = (response != null) ? response.getTextContent() : "";
            String fullTrajectory = "";

            // 多轮工具调用循环
            for (int step = 0; step < maxSteps; step++) {
                // 解析工具调用
                log.info("[RolloutWorker-{}] Step {}: agentResponse length={}, preview={}",
                        variantIndex, step, agentResponse != null ? agentResponse.length() : 0,
                        agentResponse != null ? agentResponse.substring(0, Math.min(200, agentResponse.length())) : "null");
                var toolCall = parseToolCallFromText(agentResponse);
                log.info("[RolloutWorker-{}] Step {}: toolCall parsed={}", variantIndex, step,
                        toolCall != null ? toolCall.toolName() + "(" + toolCall.toolInput() + ")" : "NULL");
                if (toolCall == null) {
                    // 没有工具调用，Agent 给出了最终答案
                    StepRecord finalStep = StepRecord.builder()
                            .stepIndex(step)
                            .reasoning(agentResponse)
                            .action(agentResponse)
                            .isToolCall(false)
                            .build();
                    stepRecords.add(finalStep);
                    log.info("[RolloutWorker-{}] Step {}: 最终答案", variantIndex, step);
                    break;
                }

                // 记录工具调用步骤
                log.info("[RolloutWorker-{}] Step {}: 调用工具 {}({})",
                        variantIndex, step, toolCall.toolName(), toolCall.toolInput());

                // 执行工具
                var toolAction = org.example.skillOpt.env.ToolAction.builder()
                        .toolName(toolCall.toolName())
                        .toolInput(toolCall.toolInput())
                        .rawOutput(toolCall.rawCall())
                        .build();

                var feedback = envAdapter.executeToolAction(toolAction, Map.of());

                StepRecord toolStep = StepRecord.builder()
                        .stepIndex(step)
                        .reasoning(extractReasoning(agentResponse, toolCall.rawCall()))
                        .action(toolCall.rawCall())
                        .isToolCall(true)
                        .toolName(toolCall.toolName())
                        .toolInput(toolCall.toolInput())
                        .toolResult(feedback.getContent())
                        .build();
                stepRecords.add(toolStep);

                // 构建工具结果反馈消息
                String toolResultMsg = String.format(
                        "## Tool Execution Result\n\n" +
                        "You called: `%s`\n\n" +
                        "**Result:**\n%s\n\n" +
                        "Please analyze this result and either call another tool or provide your final answer " +
                        "formatted as: **Answer: <your answer>**",
                        toolCall.rawCall(), feedback.getContent());

                fullTrajectory += agentResponse + "\n\n[Tool Result]: " + feedback.getContent() + "\n\n";

                // 发送工具结果给 Agent
                Msg followUpMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(List.of(TextBlock.builder().text(toolResultMsg).build()))
                        .build();

                response = toolAgent.call(followUpMsg).block();
                agentResponse = (response != null) ? response.getTextContent() : "";
            }

            // 生成轨迹
            String trajectory = generateTrajectoryFromSteps(stepRecords, taskPrompt);
            // 使用 getFinalAnswer 从 stepRecords 中提取最终答案，比直接用 agentResponse 更准确
            RolloutResult tempResult = RolloutResult.builder()
                    .agentResponse(agentResponse)
                    .stepRecords(stepRecords)
                    .build();
            String finalAnswer = tempResult.getFinalAnswer();

            RolloutResult result = RolloutResult.builder()
                    .variantIndex(variantIndex)
                    .status("COMPLETED")
                    .hardScore(envAdapter.hardScore(
                            RolloutResult.builder()
                                    .agentResponse(finalAnswer)
                                    .taskInstance(taskInstance)
                                    .build()))
                    .softScore(envAdapter.softScore(
                            RolloutResult.builder()
                                    .agentResponse(finalAnswer)
                                    .taskInstance(taskInstance)
                                    .build()))
                    .trajectory(trajectory)
                    .agentResponse(finalAnswer)
                    .taskInstance(taskInstance)
                    .toolCallingUsed(true)
                    .stepRecords(stepRecords)
                    .totalSteps(stepRecords.size())
                    .maxStepsReached(stepRecords.size() >= maxSteps)
                    .build();

            if (result.getHardScore() >= 1.0) {
                result.setStatus("PASSED");
            } else if (result.getSoftScore() > 0.3) {
                result.setStatus("PARTIAL");
            } else {
                result.setStatus("FAILED");
            }

            log.info("[RolloutWorker-{}] 工具调用完成: steps={}, status={}, hardScore={}, softScore={}",
                    variantIndex, stepRecords.size(), result.getStatus(),
                    result.getHardScore(), result.getSoftScore());

            return result;

        } catch (Exception e) {
            log.error("[RolloutWorker-{}] 工具调用异常: {}", variantIndex, e.getMessage(), e);
            return RolloutResult.builder()
                    .variantIndex(variantIndex)
                    .status("ERROR")
                    .hardScore(0.0)
                    .softScore(0.0)
                    .trajectory("Error: " + e.getMessage())
                    .agentResponse("")
                    .taskInstance(taskInstance)
                    .toolCallingUsed(true)
                    .build();
        } finally {
            SkillOptToolAgent.clearContext();
            SkillOptTraceHook.clearThreadTrace();
        }
    }

    /**
     * 从 StepRecord 列表生成轨迹文本
     */
    private String generateTrajectoryFromSteps(List<StepRecord> stepRecords, String taskPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Task\n\n").append(taskPrompt).append("\n\n");
        sb.append("## Execution Trace\n\n");

        for (StepRecord step : stepRecords) {
            sb.append(step.toMarkdown()).append("\n");
        }

        return sb.toString();
    }

    /** 工具调用解析结果 */
    private record ToolCallInfo(String toolName, String toolInput, String rawCall) {}

    /**
     * 从 Agent 文本响应中解析工具调用。
     * 支持的模式：
     * - query_environment(query='...')
     * - query_environment(query="...")
     * - execute_action(action='...', reasoning='...')
     */
    private ToolCallInfo parseToolCallFromText(String text) {
        if (text == null || text.isBlank()) return null;

        // 匹配 query_environment(query='...') 或 query_environment(query="...")
        var queryPattern = java.util.regex.Pattern.compile(
                "query_environment\\s*\\(\\s*query\\s*=\\s*['\"](.+?)['\"]\\s*\\)",
                java.util.regex.Pattern.DOTALL);
        var queryMatcher = queryPattern.matcher(text);
        if (queryMatcher.find()) {
            String query = queryMatcher.group(1);
            return new ToolCallInfo("query_environment", query, queryMatcher.group(0));
        }

        // 匹配 execute_action(action='...', reasoning='...')
        var actionPattern = java.util.regex.Pattern.compile(
                "execute_action\\s*\\(\\s*action\\s*=\\s*['\"](.+?)['\"]\\s*(?:,\\s*reasoning\\s*=\\s*['\"](.+?)['\"])?\\s*\\)",
                java.util.regex.Pattern.DOTALL);
        var actionMatcher = actionPattern.matcher(text);
        if (actionMatcher.find()) {
            String action = actionMatcher.group(1);
            return new ToolCallInfo("execute_action", action, actionMatcher.group(0));
        }

        return null;
    }

    /**
     * 从 Agent 响应中提取工具调用之前的推理部分
     */
    private String extractReasoning(String fullResponse, String toolCallText) {
        if (fullResponse == null) return "";
        int idx = fullResponse.indexOf(toolCallText);
        if (idx > 0) {
            return fullResponse.substring(0, idx).trim();
        }
        return fullResponse.trim();
    }

    private EnvAdapter createEnvAdapter(String type) {
        if ("search-qa".equals(type)) {
            return new SearchQAEnvAdapter();
        }
        return new QAEnvAdapter();
    }

    private Map<String, Object> toResultMap(RolloutResult r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variantIndex", r.getVariantIndex());
        map.put("status", r.getStatus());
        map.put("hardScore", r.getHardScore());
        map.put("softScore", r.getSoftScore());
        map.put("trajectory", r.getTrajectory());
        map.put("agentResponse", r.getAgentResponse());
        map.put("taskInstance", r.getTaskInstance());
        map.put("toolCallingUsed", r.isToolCallingUsed());
        map.put("totalSteps", r.getTotalSteps());
        return map;
    }

    private void persistRolloutResult(String jobId, int epoch, int variantIndex, RolloutResult result) {
        try {
            SkillOptRolloutResultMapper mapper =
                    ApplicationContextProvider.getBean(SkillOptRolloutResultMapper.class);
            com.fasterxml.jackson.databind.ObjectMapper jsonMapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            SkillOptRolloutResultEntity entity = SkillOptRolloutResultEntity.builder()
                    .jobId(jobId)
                    .epoch(epoch)
                    .variantIndex(variantIndex)
                    .rolloutType("train")
                    .taskInstance(jsonMapper.writeValueAsString(result.getTaskInstance()))
                    .status(result.getStatus())
                    .hardScore(result.getHardScore())
                    .softScore(result.getSoftScore())
                    .trajectory(result.getTrajectory())
                    .agentResponse(result.getAgentResponse())
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("[RolloutWorker-{}] 持久化失败: {}", variantIndex, e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
